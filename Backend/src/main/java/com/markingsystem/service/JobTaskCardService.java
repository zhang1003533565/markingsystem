package com.markingsystem.service;

import com.markingsystem.dto.JobTaskCardDTO;
import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.JobFavorite;
import com.markingsystem.entity.JobResourceGenerationLog;
import com.markingsystem.entity.JobTaskCard;
import com.markingsystem.entity.LearningProgress;
import com.markingsystem.entity.Major;
import com.markingsystem.entity.Student;
import com.markingsystem.exception.RateLimitException;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.JobFavoriteRepository;
import com.markingsystem.repository.JobResourceGenerationLogRepository;
import com.markingsystem.repository.JobTaskCardRepository;
import com.markingsystem.repository.LearningProgressRepository;
import com.markingsystem.repository.MajorRepository;
import com.markingsystem.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 岗位学习任务卡服务（以岗定教）。
 * <p>
 * 负责用户专属学习任务卡的获取（缓存优先，无缓存则 AI 生成）与级联删除，
 * 复用 job_resource_generation_logs 表按 generationType=TASK_CARD 独立限流：
 * - 板块池：每日 5 次（含任务卡生成 TASK_CARD 与任务卡刷新 TASK_REFRESH，与学习资源的板块额度相互独立）
 * - 任务卡「刷新」操作类型：每日最多 3 次（无论刷哪个岗位，跨岗位合计受限）
 */
@Service
public class JobTaskCardService {

    /** 任务卡板块每日总额度（含生成与刷新，独立于学习资源板块额度） */
    private static final int DAILY_TASK_CARD_BOARD_QUOTA = 5;
    /** 任务卡「刷新」操作类型每日上限（清缓存后重新 AI 生成，跨岗位合计 3 次/天） */
    private static final int DAILY_TASK_CARD_REFRESH_CAP = 3;
    /** 生成日志类型：与 AUTO/MANUAL 并列，走同一张日志表 */
    private static final String GENERATION_TYPE = "TASK_CARD";
    /** 刷新日志类型：独立计数任务卡刷新次数（板块池与刷新类型 3 次上限共用） */
    private static final String REFRESH_TYPE = "TASK_REFRESH";
    /** 刷新时并入学生情况摘要的「已掌握路径步骤」最大条数与最大字数（学情摘要总长受客户端 300 字上限约束） */
    private static final int MAX_MASTERED_STEPS = 5;
    private static final int MAX_MASTERED_STEPS_LEN = 160;

    private final JobTaskCardRepository taskCardRepository;
    private final JobResourceGenerationLogRepository logRepository;
    private final JobFavoriteRepository jobFavoriteRepository;
    private final StudentRepository studentRepository;
    private final ClassInfoRepository classInfoRepository;
    private final MajorRepository majorRepository;
    private final LearningProgressRepository progressRepository;
    private final JobTaskCardClient client;
    private final AiJobGenerationGate aiJobGenerationGate;

    /**
     * 任务卡按 (userId:recommendationId) 维度的生成锁，防止并发请求对同一岗位重复生成
     * 并重复写入（撞唯一索引 idx_jtc_user_job）。与学习资源侧 perJobLocks 设计一致：
     * 锁条目使用后不再移除，容量上界为「用户数 × 收藏岗位数」级别，可接受。
     */
    private final Map<String, Object> perJobLocks = new ConcurrentHashMap<>();

    public JobTaskCardService(
            JobTaskCardRepository taskCardRepository,
            JobResourceGenerationLogRepository logRepository,
            JobFavoriteRepository jobFavoriteRepository,
            StudentRepository studentRepository,
            ClassInfoRepository classInfoRepository,
            MajorRepository majorRepository,
            LearningProgressRepository progressRepository,
            JobTaskCardClient client,
            AiJobGenerationGate aiJobGenerationGate) {
        this.taskCardRepository = taskCardRepository;
        this.logRepository = logRepository;
        this.jobFavoriteRepository = jobFavoriteRepository;
        this.studentRepository = studentRepository;
        this.classInfoRepository = classInfoRepository;
        this.majorRepository = majorRepository;
        this.progressRepository = progressRepository;
        this.client = client;
        this.aiJobGenerationGate = aiJobGenerationGate;
    }

    /** 岗位维度的串行化任务，允许抛出受检异常 */
    @FunctionalInterface
    private interface JobTaskCardTask<T> {
        T run() throws Exception;
    }

    private <T> T executeWithJobLock(Long userId, Long recommendationId, JobTaskCardTask<T> task) throws Exception {
        String lockKey = userId + ":" + recommendationId;
        Object lock = perJobLocks.computeIfAbsent(lockKey, k -> new Object());
        synchronized (lock) {
            return task.run();
        }
    }

    /**
     * 获取某用户对某岗位的学习任务卡。
     * - 如果数据库已有数据，直接返回（不消耗额度）
     * - 如果数据库无数据，调用 AI 生成（消耗任务卡额度）
     *
     * 注意：本方法不加 @Transactional，AI 调用与落库都不在长事务内。
     * save / logGeneration 由各自仓库的短事务提交；锁内先写后提交，等待线程
     * 拿到锁后能读到已提交数据，从而避免重复生成与唯一索引冲突。
     */
    public JobTaskCardDTO getOrCreateTaskCard(Long userId, Long recommendationId) throws Exception {
        return executeWithJobLock(userId, recommendationId,
                () -> getOrCreateTaskCardInternal(userId, recommendationId));
    }

    private JobTaskCardDTO getOrCreateTaskCardInternal(Long userId, Long recommendationId) throws Exception {
        Optional<JobTaskCard> existing =
                taskCardRepository.findByUserIdAndRecommendationId(userId, recommendationId);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }
        checkBoardQuota(userId);
        return generateAndSave(userId, recommendationId, false);
    }

    /**
     * 刷新某岗位的学习任务卡：清缓存后由 AI 重新生成。
     * 前置校验：
     * - 当前任务卡全部步骤必须已完成（防止学生未学完就反复刷新）；
     * - 「刷新」操作类型每日最多 3 次（TASK_REFRESH，跨岗位合计）；
     * - 任务卡板块每日总额 5 次（TASK_CARD 生成 + TASK_REFRESH 刷新合计）。
     * 生成成功后会清空该岗位的 TASK_STEP / PATH_STEP 进度（AI 重生成后步骤编号变更，旧标记失效）。
     * 刷新时会先将该岗位已完成的 PATH_STEP 步骤（"已掌握学习路径步骤"）并入学情摘要，
     * 供 AI 跳过已掌握内容、生成衔接进阶的下一步路径。
     * 刷新仅记一条 TASK_REFRESH（计入板块池与「刷新」3 次上限），不再额外记 TASK_CARD。
     */
    public JobTaskCardDTO refreshTaskCard(Long userId, Long recommendationId) throws Exception {
        return executeWithJobLock(userId, recommendationId,
                () -> refreshTaskCardInternal(userId, recommendationId));
    }

    private JobTaskCardDTO refreshTaskCardInternal(Long userId, Long recommendationId) throws Exception {
        checkRefreshCap(userId);
        checkBoardQuota(userId);
        checkAllStepsCompleted(userId, recommendationId);
        return generateAndSave(userId, recommendationId, true);
    }

    /** 校验当前岗位任务卡的全部步骤是否已完成，未全部完成时拒绝刷新 */
    private void checkAllStepsCompleted(Long userId, Long recommendationId) {
        Optional<JobTaskCard> existing =
                taskCardRepository.findByUserIdAndRecommendationId(userId, recommendationId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("请先生成学习任务卡后再刷新");
        }
        long stepTotal = LearningProgressService.countContentLines(existing.get().getSteps());
        long done = progressRepository.countByUserIdAndRecommendationIdAndItemTypeAndCompletedTrue(
                userId, recommendationId, LearningProgress.TYPE_TASK_STEP);
        if (stepTotal < 1 || done < stepTotal) {
            throw new IllegalArgumentException("请先完成当前任务卡的全部步骤后再刷新");
        }
    }

    private JobTaskCardDTO generateAndSave(Long userId, Long recommendationId, boolean refresh) throws Exception {
        String jobTitle = resolveJobTitle(userId, recommendationId);
        String studentProfile = resolveStudentProfile(userId);
        if (refresh) {
            // 刷新时把已完成的个性化学习路径步骤（PATH_STEP）并入学情，
            // 让 AI 跳过已掌握内容、生成衔接进阶的下一步路径（真正的个性化进阶）。
            String masteredPathSteps = resolveCompletedPathSteps(userId, recommendationId);
            if (!masteredPathSteps.isBlank()) {
                studentProfile = studentProfile.isBlank()
                        ? masteredPathSteps
                        : studentProfile + "；" + masteredPathSteps;
            }
        }
        // 与学习资源共用门控：避免多路 deepseek-reasoner 同时跑导致互相超时
        final String profileForAi = studentProfile;
        JobTaskCardClient.GeneratedTaskCard generated = aiJobGenerationGate.call(
                "task-card:" + userId + ":" + recommendationId,
                () -> client.generateTaskCard(jobTitle, profileForAi));

        if (refresh) {
            // 刷新：先清理旧卡与任务/路径步骤进度，避免旧进度挂到新卡同序号步骤上
            taskCardRepository.deleteByUserIdAndRecommendationId(userId, recommendationId);
            progressRepository.deleteByUserIdAndRecommendationIdAndItemType(
                    userId, recommendationId, LearningProgress.TYPE_TASK_STEP);
            progressRepository.deleteByUserIdAndRecommendationIdAndItemType(
                    userId, recommendationId, LearningProgress.TYPE_PATH_STEP);
        }

        JobTaskCard entity = new JobTaskCard();
        entity.setUserId(userId);
        entity.setRecommendationId(recommendationId);
        entity.setJobTitle(jobTitle);
        entity.setTaskName(generated.taskName());
        entity.setWorkScenario(generated.workScenario());
        entity.setSteps(generated.steps());
        entity.setSafetyPoints(generated.safetyPoints());
        entity.setKnowledgePoints(generated.knowledgePoints());
        entity.setSkillPoints(generated.skillPoints());
        entity.setCampusResources(generated.campusResources());
        entity.setLearningPath(generated.learningPath());
        entity.setSource(client.source());
        entity.setModelName(client.modelName());
        entity.setGeneratedAt(LocalDateTime.now());
        JobTaskCard saved = taskCardRepository.save(entity);
        // 首次生成记 TASK_CARD；刷新只记 TASK_REFRESH（二者均计入板块池，避免一次刷新双扣）
        if (refresh) {
            logRefresh(userId, recommendationId);
        } else {
            logGeneration(userId, recommendationId);
        }
        return toDto(saved);
    }

    /** 取消收藏时级联删除该岗位的学习任务卡 */
    @Transactional
    public void deleteTaskCardByJob(Long userId, Long recommendationId) {
        taskCardRepository.deleteByUserIdAndRecommendationId(userId, recommendationId);
    }

    /**
     * 仅读取某岗位已生成的任务卡缓存，不做 AI 生成、不消耗额度。
     * 供首页「发展助手」聚合视图等只读场景使用，避免页面打开即触发自动生成烧额度。
     */
    public JobTaskCardDTO getCachedTaskCard(Long userId, Long recommendationId) {
        return taskCardRepository.findByUserIdAndRecommendationId(userId, recommendationId)
                .map(this::toDto)
                .orElse(null);
    }

    /** 查询用户今日的任务卡板块额度，并附带任务卡「刷新」操作类型的额度 */
    public Map<String, Object> getQuotaInfo(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long boardUsed = logRepository.countTaskCardBoardCalls(userId, todayStart);
        Map<String, Object> quota = new LinkedHashMap<>();
        // used/remaining 与板块池对齐，避免客户端误用 remaining 却因 board 已满 429
        quota.put("used", boardUsed);
        quota.put("total", DAILY_TASK_CARD_BOARD_QUOTA);
        quota.put("remaining", Math.max(0, DAILY_TASK_CARD_BOARD_QUOTA - boardUsed));
        quota.put("boardUsed", boardUsed);
        quota.put("boardTotal", DAILY_TASK_CARD_BOARD_QUOTA);
        quota.put("boardRemaining", Math.max(0, DAILY_TASK_CARD_BOARD_QUOTA - boardUsed));

        long refreshUsed = logRepository.countTaskRefreshCalls(userId, todayStart);
        quota.put("refreshUsed", refreshUsed);
        quota.put("refreshTotal", DAILY_TASK_CARD_REFRESH_CAP);
        quota.put("refreshRemaining", Math.max(0, DAILY_TASK_CARD_REFRESH_CAP - refreshUsed));
        return quota;
    }

    private String resolveJobTitle(Long userId, Long recommendationId) {
        JobFavorite favorite = jobFavoriteRepository.findByUserIdAndRecommendationId(userId, recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("请先收藏该岗位后再获取学习任务卡"));
        return favorite.getJobTitle();
    }

    /**
     * 组装学生情况摘要（专业/年级/班级），用于 AI 生成个性化学习路径。
     * 信息不全时返回空串，客户端会给出通用建议。
     */
    private String resolveStudentProfile(Long userId) {
        Student student = studentRepository.findByUserId(userId).orElse(null);
        if (student == null) {
            return "";
        }
        String className = student.getClassName();
        if (className == null || className.isBlank()) {
            return "";
        }
        String grade = "";
        String majorName = "";
        Optional<ClassInfo> classInfo = classInfoRepository.findByName(className.trim());
        if (classInfo.isPresent()) {
            grade = classInfo.get().getGrade() == null ? "" : classInfo.get().getGrade();
            Long majorId = classInfo.get().getMajorId();
            if (majorId != null) {
                majorName = majorRepository.findById(majorId).map(Major::getName).orElse("");
            }
        }
        StringBuilder profile = new StringBuilder();
        if (!majorName.isBlank()) {
            profile.append("专业：").append(majorName).append("；");
        }
        if (!grade.isBlank()) {
            profile.append("年级：").append(grade).append("；");
        }
        profile.append("班级：").append(className.trim());
        return profile.toString();
    }

    /**
     * 读取该岗位已完成的个性化学习路径步骤（PATH_STEP，itemTitle 为该步文字），
     * 刷新时拼入学情摘要，供 AI 跳过已掌握内容、生成衔接进阶的下一步路径。
     * 无已完成步骤或步骤文字缺失时返回空串。
     */
    private String resolveCompletedPathSteps(Long userId, Long recommendationId) {
        List<LearningProgress> records = progressRepository
                .findByUserIdAndRecommendationIdAndItemType(userId, recommendationId, LearningProgress.TYPE_PATH_STEP);
        if (records.isEmpty()) {
            return "";
        }
        // 只取已完成的步骤，按步骤序号升序（itemKey 为 "1"/"2"…，按数字比较），限量限长
        List<LearningProgress> done = records.stream()
                .filter(r -> Boolean.TRUE.equals(r.getCompleted()))
                .sorted(Comparator.comparingInt(r -> parseStepKey(r.getItemKey())))
                .toList();
        if (done.isEmpty()) {
            return "";
        }
        StringBuilder summary = new StringBuilder("已掌握学习路径步骤：");
        int count = 0;
        for (LearningProgress record : done) {
            String title = record.getItemTitle();
            if (title == null || title.isBlank()) {
                continue;
            }
            if (count >= MAX_MASTERED_STEPS
                    || summary.length() >= MAX_MASTERED_STEPS_LEN) {
                break;
            }
            if (count > 0) {
                summary.append("；");
            }
            count++;
            summary.append(count).append(". ").append(title);
        }
        return count == 0 ? "" : summary.toString();
    }

    /** 解析路径步骤序号（"1"/"2"/"10"…），解析失败按 0 处理 */
    private int parseStepKey(String itemKey) {
        if (itemKey == null) {
            return 0;
        }
        try {
            return Integer.parseInt(itemKey.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 校验任务卡板块每日总额（TASK_CARD 生成 + TASK_REFRESH 刷新合计 5 次/天） */
    private void checkBoardQuota(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long used = logRepository.countTaskCardBoardCalls(userId, todayStart);
        if (used >= DAILY_TASK_CARD_BOARD_QUOTA) {
            throw new RateLimitException("今日学习任务卡板块次数已用完（" + DAILY_TASK_CARD_BOARD_QUOTA + " 次/天），请明天再试");
        }
    }

    /** 校验任务卡「刷新」操作类型每日上限（TASK_REFRESH 跨岗位合计 3 次/天） */
    private void checkRefreshCap(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long used = logRepository.countTaskRefreshCalls(userId, todayStart);
        if (used >= DAILY_TASK_CARD_REFRESH_CAP) {
            throw new RateLimitException("今日任务卡刷新次数已用完（" + DAILY_TASK_CARD_REFRESH_CAP + " 次/天），请明天再试");
        }
    }

    private void logGeneration(Long userId, Long recommendationId) {
        JobResourceGenerationLog logEntity = new JobResourceGenerationLog();
        logEntity.setUserId(userId);
        logEntity.setRecommendationId(recommendationId);
        logEntity.setGenerationType(GENERATION_TYPE);
        logEntity.setSuccess(true);
        logEntity.setGeneratedAt(LocalDateTime.now());
        logRepository.save(logEntity);
    }

    /** 记录一次任务卡刷新（TASK_REFRESH，计入任务卡板块池与「刷新」3 次上限） */
    private void logRefresh(Long userId, Long recommendationId) {
        JobResourceGenerationLog logEntity = new JobResourceGenerationLog();
        logEntity.setUserId(userId);
        logEntity.setRecommendationId(recommendationId);
        logEntity.setGenerationType(REFRESH_TYPE);
        logEntity.setSuccess(true);
        logEntity.setGeneratedAt(LocalDateTime.now());
        logRepository.save(logEntity);
    }

    private JobTaskCardDTO toDto(JobTaskCard entity) {
        JobTaskCardDTO dto = new JobTaskCardDTO();
        dto.setId(entity.getId());
        dto.setRecommendationId(entity.getRecommendationId());
        dto.setJobTitle(entity.getJobTitle());
        dto.setTaskName(entity.getTaskName());
        dto.setWorkScenario(entity.getWorkScenario());
        dto.setSteps(entity.getSteps());
        dto.setSafetyPoints(entity.getSafetyPoints());
        dto.setKnowledgePoints(entity.getKnowledgePoints());
        dto.setSkillPoints(entity.getSkillPoints());
        dto.setCampusResources(entity.getCampusResources());
        dto.setLearningPath(entity.getLearningPath());
        dto.setSource(entity.getSource());
        dto.setModelName(entity.getModelName());
        dto.setGeneratedAt(entity.getGeneratedAt());
        return dto;
    }
}
