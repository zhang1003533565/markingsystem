package com.markingsystem.service;

import com.markingsystem.entity.JobFavorite;
import com.markingsystem.entity.JobLearningResource;
import com.markingsystem.entity.JobTaskCard;
import com.markingsystem.entity.LearningProgress;
import com.markingsystem.repository.JobFavoriteRepository;
import com.markingsystem.repository.JobLearningResourceRepository;
import com.markingsystem.repository.JobTaskCardRepository;
import com.markingsystem.repository.LearningProgressRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 学生岗位学习进度服务。
 * <p>
 * 负责：
 * - 学习资源（RESOURCE）、任务卡步骤（TASK_STEP）、个性化路径步骤（PATH_STEP）的完成标记与取消；
 * - 资源解锁（RESOURCE_UNLOCK）：打开阅读器后写入，标记完成前必须存在；
 * - 汇总每个收藏岗位的完成度（用于发展助手「学习记录」视图）。
 */
@Service
public class LearningProgressService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            LearningProgress.TYPE_RESOURCE,
            LearningProgress.TYPE_TASK_STEP,
            LearningProgress.TYPE_PATH_STEP,
            LearningProgress.TYPE_DAILY_CHECKIN,
            LearningProgress.TYPE_RESOURCE_UNLOCK
    );

    private final LearningProgressRepository progressRepository;
    private final JobFavoriteRepository jobFavoriteRepository;
    private final JobLearningResourceRepository jobLearningResourceRepository;
    private final JobTaskCardRepository jobTaskCardRepository;

    public LearningProgressService(
            LearningProgressRepository progressRepository,
            JobFavoriteRepository jobFavoriteRepository,
            JobLearningResourceRepository jobLearningResourceRepository,
            JobTaskCardRepository jobTaskCardRepository) {
        this.progressRepository = progressRepository;
        this.jobFavoriteRepository = jobFavoriteRepository;
        this.jobLearningResourceRepository = jobLearningResourceRepository;
        this.jobTaskCardRepository = jobTaskCardRepository;
    }

    /** 校验岗位是否属于该用户收藏，防止越权 */
    private void checkOwnedJob(Long userId, Long recommendationId) {
        jobFavoriteRepository.findByUserIdAndRecommendationId(userId, recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("请先收藏该岗位后再操作学习进度"));
    }

    /**
     * 标记某项学习内容为已完成（幂等，重复标记直接返回）。
     * 不加 @Transactional：并发下「先查后插」可能撞唯一索引，改为捕获冲突后幂等返回，
     * 避免在事务内捕获 DataIntegrityViolationException 引发 rollback-only 问题。
     */
    public void markCompleted(Long userId, Long recommendationId, String itemType, String itemKey, String itemTitle) {
        if (recommendationId == null || recommendationId <= 0) {
            // 仅每日打卡类型允许 recommendationId=0；其它类型仍需绑定到具体收藏岗位
            if (!LearningProgress.TYPE_DAILY_CHECKIN.equals(itemType)) {
                throw new IllegalArgumentException("岗位参数无效");
            }
            recommendationId = 0L;
        }
        if (itemType == null || itemType.isBlank() || !ALLOWED_TYPES.contains(itemType)) {
            throw new IllegalArgumentException("记录类型无效");
        }
        // 解锁记录只能通过 unlockResource 写入，禁止经 complete 接口伪造
        if (LearningProgress.TYPE_RESOURCE_UNLOCK.equals(itemType)) {
            throw new IllegalArgumentException("请使用解锁接口打开学习资源");
        }
        if (itemKey == null || itemKey.isBlank()) {
            throw new IllegalArgumentException("记录标识无效");
        }
        // 每日打卡：仅允许当天，且 itemKey 必须是 yyyy-MM-dd
        if (LearningProgress.TYPE_DAILY_CHECKIN.equals(itemType)) {
            LocalDate checkinDate;
            try {
                checkinDate = LocalDate.parse(itemKey, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                throw new IllegalArgumentException("打卡日期格式无效，应为 yyyy-MM-dd");
            }
            if (!checkinDate.equals(LocalDate.now())) {
                throw new IllegalArgumentException("仅支持打卡当天，不能补录或预打卡");
            }
        }
        if (!LearningProgress.TYPE_DAILY_CHECKIN.equals(itemType)) {
            checkOwnedJob(userId, recommendationId);
            validateItemBelongsToJob(userId, recommendationId, itemType, itemKey);
        }
        // 学习资源：必须先解锁（打开过阅读器），防止不学就刷完成
        if (LearningProgress.TYPE_RESOURCE.equals(itemType)) {
            boolean unlocked = progressRepository
                    .findByUserIdAndRecommendationIdAndItemTypeAndItemKey(
                            userId, recommendationId, LearningProgress.TYPE_RESOURCE_UNLOCK, itemKey)
                    .isPresent();
            if (!unlocked) {
                throw new IllegalArgumentException("请先打开学习资源后再标记完成");
            }
        }
        Optional<LearningProgress> existing = progressRepository
                .findByUserIdAndRecommendationIdAndItemTypeAndItemKey(userId, recommendationId, itemType, itemKey);
        if (existing.isPresent()) {
            LearningProgress record = existing.get();
            if (!Boolean.TRUE.equals(record.getCompleted())) {
                record.setCompleted(true);
                record.setCompletedAt(LocalDateTime.now());
                progressRepository.save(record);
            }
            return;
        }
        LearningProgress record = new LearningProgress();
        record.setUserId(userId);
        record.setRecommendationId(recommendationId);
        record.setItemType(itemType);
        record.setItemKey(itemKey);
        record.setItemTitle(itemTitle);
        record.setCompleted(true);
        record.setCompletedAt(LocalDateTime.now());
        try {
            progressRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            // 并发下另一请求已写入同一条记录，视为幂等成功
        }
    }

    /**
     * 解锁某学习资源（打开阅读器时调用，幂等）。
     * 写入 TYPE_RESOURCE_UNLOCK 记录后，才允许标记 RESOURCE 完成。
     */
    public void unlockResource(Long userId, Long recommendationId, String itemKey, String itemTitle) {
        if (recommendationId == null || recommendationId <= 0) {
            throw new IllegalArgumentException("岗位参数无效");
        }
        if (itemKey == null || itemKey.isBlank()) {
            throw new IllegalArgumentException("资源标识无效");
        }
        checkOwnedJob(userId, recommendationId);
        validateResourceExists(userId, recommendationId, itemKey);
        Optional<LearningProgress> existing = progressRepository
                .findByUserIdAndRecommendationIdAndItemTypeAndItemKey(
                        userId, recommendationId, LearningProgress.TYPE_RESOURCE_UNLOCK, itemKey);
        if (existing.isPresent()) {
            return;
        }
        LearningProgress record = new LearningProgress();
        record.setUserId(userId);
        record.setRecommendationId(recommendationId);
        record.setItemType(LearningProgress.TYPE_RESOURCE_UNLOCK);
        record.setItemKey(itemKey);
        record.setItemTitle(itemTitle);
        record.setCompleted(true);
        record.setCompletedAt(LocalDateTime.now());
        try {
            progressRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            // 并发幂等
        }
    }

    /** 校验完成项确实属于该岗位当前内容（防伪造 itemKey） */
    private void validateItemBelongsToJob(Long userId, Long recommendationId, String itemType, String itemKey) {
        if (LearningProgress.TYPE_RESOURCE.equals(itemType)) {
            validateResourceExists(userId, recommendationId, itemKey);
            return;
        }
        if (LearningProgress.TYPE_TASK_STEP.equals(itemType) || LearningProgress.TYPE_PATH_STEP.equals(itemType)) {
            JobTaskCard card = jobTaskCardRepository.findByUserIdAndRecommendationId(userId, recommendationId)
                    .orElseThrow(() -> new IllegalArgumentException("请先生成学习任务卡后再操作"));
            String text = LearningProgress.TYPE_TASK_STEP.equals(itemType)
                    ? card.getSteps()
                    : card.getLearningPath();
            long total = countContentLines(text);
            int stepIndex;
            try {
                stepIndex = Integer.parseInt(itemKey.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("步骤标识无效");
            }
            if (stepIndex < 1 || stepIndex > total) {
                throw new IllegalArgumentException("步骤不存在或已失效，请刷新后重试");
            }
        }
    }

    private void validateResourceExists(Long userId, Long recommendationId, String itemKey) {
        long resourceId;
        try {
            resourceId = Long.parseLong(itemKey.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("资源标识无效");
        }
        boolean exists = jobLearningResourceRepository
                .findByUserIdAndRecommendationIdOrderBySortOrderAsc(userId, recommendationId)
                .stream()
                .anyMatch(r -> r.getId() != null && r.getId() == resourceId);
        if (!exists) {
            throw new IllegalArgumentException("学习资源不存在或已失效，请刷新后重试");
        }
    }

    /** 取消某项学习内容的完成标记（幂等） */
    @Transactional
    public void unmarkCompleted(Long userId, Long recommendationId, String itemType, String itemKey) {
        // 每日打卡：按 (userId, type, itemKey) 定位，避免 recommendationId=0 干扰
        if (LearningProgress.TYPE_DAILY_CHECKIN.equals(itemType)) {
            List<LearningProgress> records = progressRepository.findByUserIdAndItemType(userId, itemType);
            records.stream()
                    .filter(r -> itemKey != null && itemKey.equals(r.getItemKey()))
                    .findFirst()
                    .ifPresent(progressRepository::delete);
            return;
        }
        if (recommendationId == null || recommendationId <= 0) {
            throw new IllegalArgumentException("岗位参数无效");
        }
        checkOwnedJob(userId, recommendationId);
        Optional<LearningProgress> existing = progressRepository
                .findByUserIdAndRecommendationIdAndItemTypeAndItemKey(userId, recommendationId, itemType, itemKey);
        existing.ifPresent(progressRepository::delete);
    }

    /** 查询某收藏岗位的完成记录（按类型分组返回 itemKey + itemTitle） */
    public Map<String, Object> getJobProgress(Long userId, Long recommendationId) {
        checkOwnedJob(userId, recommendationId);
        List<LearningProgress> records = progressRepository.findByUserIdAndRecommendationId(userId, recommendationId);
        List<Map<String, Object>> resources = new ArrayList<>();
        List<Map<String, Object>> taskSteps = new ArrayList<>();
        List<Map<String, Object>> pathSteps = new ArrayList<>();
        List<Map<String, Object>> unlockedResources = new ArrayList<>();
        for (LearningProgress record : records) {
            if (!Boolean.TRUE.equals(record.getCompleted())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", record.getItemKey());
            item.put("title", record.getItemTitle());
            item.put("completedAt", record.getCompletedAt());
            switch (record.getItemType()) {
                case LearningProgress.TYPE_RESOURCE -> resources.add(item);
                case LearningProgress.TYPE_TASK_STEP -> taskSteps.add(item);
                case LearningProgress.TYPE_PATH_STEP -> pathSteps.add(item);
                case LearningProgress.TYPE_RESOURCE_UNLOCK -> unlockedResources.add(item);
                default -> {
                    // 忽略每日打卡等其他类型
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recommendationId", recommendationId);
        result.put("resources", resources);
        result.put("taskSteps", taskSteps);
        result.put("pathSteps", pathSteps);
        result.put("unlockedResources", unlockedResources);
        return result;
    }

    /** 取消收藏时级联删除该岗位的全部学习进度/打卡记录 */
    @Transactional
    public void deleteProgressByJob(Long userId, Long recommendationId) {
        progressRepository.deleteByUserIdAndRecommendationId(userId, recommendationId);
    }

    /** 汇总用户所有收藏岗位的学习进度（用于发展助手「学习记录」视图） */
    public List<Map<String, Object>> getOverview(Long userId) {
        List<JobFavorite> favorites = jobFavoriteRepository.findByUserId(userId);
        List<Map<String, Object>> overview = new ArrayList<>();
        for (JobFavorite favorite : favorites) {
            Long recommendationId = favorite.getRecommendationId();

            // 资源总数：该岗位已生成的学习资源条数（上限 6）
            List<JobLearningResource> resources =
                    jobLearningResourceRepository.findByUserIdAndRecommendationIdOrderBySortOrderAsc(userId, recommendationId);
            long resourceTotal = resources.size();

            // 任务步骤 / 路径步骤总数：解析任务卡中的换行条目（与前端 parseLines 口径一致）
            Optional<JobTaskCard> taskCardOpt = jobTaskCardRepository.findByUserIdAndRecommendationId(userId, recommendationId);
            long taskStepTotal = 0;
            long pathStepTotal = 0;
            if (taskCardOpt.isPresent()) {
                JobTaskCard taskCard = taskCardOpt.get();
                taskStepTotal = countContentLines(taskCard.getSteps());
                pathStepTotal = countContentLines(taskCard.getLearningPath());
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recommendationId", recommendationId);
            row.put("jobTitle", favorite.getJobTitle());
            row.put("salary", favorite.getSalary());
            row.put("skills", favorite.getSkills());
            row.put("resourceTotal", resourceTotal);
            row.put("resourceCompleted", progressRepository.countByUserIdAndRecommendationIdAndItemTypeAndCompletedTrue(
                    userId, recommendationId, LearningProgress.TYPE_RESOURCE));
            row.put("taskStepTotal", taskStepTotal);
            row.put("taskStepCompleted", progressRepository.countByUserIdAndRecommendationIdAndItemTypeAndCompletedTrue(
                    userId, recommendationId, LearningProgress.TYPE_TASK_STEP));
            row.put("pathStepTotal", pathStepTotal);
            row.put("pathStepCompleted", progressRepository.countByUserIdAndRecommendationIdAndItemTypeAndCompletedTrue(
                    userId, recommendationId, LearningProgress.TYPE_PATH_STEP));
            overview.add(row);
        }
        return overview;
    }

    /**
     * 每日打卡概览：今日是否打卡、连续打卡天数、最近 7 天内已打卡日期集合。
     * {@code recent7} 仅为已打卡日期（yyyy-MM-dd），不含未打卡日；前端自行展开近 7 日日历。
     */
    public Map<String, Object> getDailyCheckinOverview(Long userId) {
        List<LearningProgress> records = progressRepository.findByUserIdAndItemType(userId, LearningProgress.TYPE_DAILY_CHECKIN);
        Set<String> checkedDates = new TreeSet<>();
        for (LearningProgress record : records) {
            if (Boolean.TRUE.equals(record.getCompleted()) && record.getItemKey() != null) {
                checkedDates.add(record.getItemKey());
            }
        }
        LocalDate today = LocalDate.now();
        String todayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        boolean todayChecked = checkedDates.contains(todayKey);

        // 连续天数：从今天（含）向前回溯，最多追溯到 checkedDates 为空
        int streak = 0;
        LocalDate cursor = today;
        while (checkedDates.contains(cursor.format(DateTimeFormatter.ISO_LOCAL_DATE))) {
            streak++;
            cursor = cursor.minusDays(1);
        }

        // 最近 7 天内已打卡的日期（升序）
        List<String> recent7 = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String dateKey = today.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            if (checkedDates.contains(dateKey)) {
                recent7.add(dateKey);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayChecked", todayChecked);
        result.put("streakDays", streak);
        result.put("recent7", recent7);
        return result;
    }

    /**
     * 统计有效内容行数：trim、去序号前缀、丢弃空行。
     * 与前端 {@code parseLines} 口径一致，避免刷新门槛 / 进度分母偏差。
     */
    static long countContentLines(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        long count = 0;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim().replaceFirst("^\\d+[.、)]\\s*", "");
            if (!line.isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
