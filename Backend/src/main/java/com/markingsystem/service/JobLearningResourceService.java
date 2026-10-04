package com.markingsystem.service;

import com.markingsystem.dto.JobLearningResourceDTO;
import com.markingsystem.dto.JobResourceAsyncResult;
import com.markingsystem.entity.JobFavorite;
import com.markingsystem.entity.JobLearningResource;
import com.markingsystem.entity.JobResourceGenerationLog;
import com.markingsystem.entity.LearningProgress;
import com.markingsystem.exception.RateLimitException;
import com.markingsystem.repository.JobFavoriteRepository;
import com.markingsystem.repository.JobLearningResourceRepository;
import com.markingsystem.repository.JobResourceGenerationLogRepository;
import com.markingsystem.repository.LearningProgressRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 岗位学习资源服务。
 * 负责用户专属岗位学习资源的获取（缓存优先，无缓存则异步 AI 生成）、刷新与级联删除，
 * 并基于生成日志表实现每日按板块限流（每个板块 5 次，板块下各刷新操作类型最多 3 次）：
 * - 板块池：5 次/天（AUTO 自动生成 + MANUAL 刷新 + REGENERATE 重新生成合计）
 * - MANUAL 操作类型（刷新按钮）：最多 3 次/天
 * - REGENERATE 操作类型（清缓存强制重刷）：最多 3 次/天
 *
 * <p>AI 生成改为后台异步：接口立即返回 status=generating，前端轮询；
 * 避免 deepseek-reasoner 联网推理 + 重试超过 HTTP/代理超时。
 */
@Service
public class JobLearningResourceService {

    private static final Logger log = LoggerFactory.getLogger(JobLearningResourceService.class);

    private static final int MAX_RESOURCES_PER_JOB = 6;
    private static final int DAILY_RESOURCE_BOARD_QUOTA = 5;
    private static final int DAILY_MANUAL_CAP = 3;
    private static final int DAILY_REGENERATE_CAP = 3;
    /** 失败状态保留时长，供前端轮询读到失败原因 */
    private static final long FAILED_STATUS_TTL_MS = 60_000L;

    private final JobLearningResourceRepository resourceRepository;
    private final JobResourceGenerationLogRepository logRepository;
    private final JobFavoriteRepository jobFavoriteRepository;
    private final LearningProgressRepository progressRepository;
    private final JobLearningResourceClient client;
    private final AiJobGenerationGate aiJobGenerationGate;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService generationExecutor;

    /**
     * 岗位学习资源按 (userId:recommendationId) 维度的生成锁。
     * 仅保护「读缓存 / 启动任务 / 写库」短临界区，AI 调用在锁外执行。
     */
    private final Map<String, Object> perJobLocks = new ConcurrentHashMap<>();

    /** 进行中 / 刚失败的异步生成任务 */
    private final Map<String, GenerationJob> generationJobs = new ConcurrentHashMap<>();

    public JobLearningResourceService(
            JobLearningResourceRepository resourceRepository,
            JobResourceGenerationLogRepository logRepository,
            JobFavoriteRepository jobFavoriteRepository,
            LearningProgressRepository progressRepository,
            JobLearningResourceClient client,
            AiJobGenerationGate aiJobGenerationGate,
            PlatformTransactionManager transactionManager) {
        this.resourceRepository = resourceRepository;
        this.logRepository = logRepository;
        this.jobFavoriteRepository = jobFavoriteRepository;
        this.progressRepository = progressRepository;
        this.client = client;
        this.aiJobGenerationGate = aiJobGenerationGate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        AtomicInteger seq = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("job-resource-ai-" + seq.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        this.generationExecutor = new ThreadPoolExecutor(
                2, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @PreDestroy
    public void shutdown() {
        generationExecutor.shutdownNow();
    }

    @FunctionalInterface
    private interface JobResourceTask<T> {
        T run() throws Exception;
    }

    private <T> T executeWithJobLock(Long userId, Long recommendationId, JobResourceTask<T> task) throws Exception {
        String lockKey = userId + ":" + recommendationId;
        Object lock = perJobLocks.computeIfAbsent(lockKey, k -> new Object());
        synchronized (lock) {
            return task.run();
        }
    }

    /**
     * 获取学习资源（异步）：有缓存直接 ready；无缓存时启动后台生成并返回 generating。
     */
    public JobResourceAsyncResult getOrCreateResources(Long userId, Long recommendationId) throws Exception {
        return executeWithJobLock(userId, recommendationId,
                () -> getOrCreateResourcesInternal(userId, recommendationId));
    }

    private JobResourceAsyncResult getOrCreateResourcesInternal(Long userId, Long recommendationId) throws Exception {
        String lockKey = userId + ":" + recommendationId;
        List<JobLearningResourceDTO> cached = toDtoList(
                resourceRepository.findByUserIdAndRecommendationIdOrderBySortOrderAsc(userId, recommendationId));

        GenerationJob existingJob = generationJobs.get(lockKey);
        if (existingJob != null && existingJob.isGenerating()) {
            return JobResourceAsyncResult.generating(cached, "学习资源生成中，请稍候");
        }
        if (existingJob != null && existingJob.isFailed()) {
            // 主动再次 generate：清掉失败态允许立即重试；仅缓存查询才向客户端暴露 failed
            generationJobs.remove(lockKey, existingJob);
        }

        if (!cached.isEmpty()) {
            return JobResourceAsyncResult.ready(cached);
        }

        checkBoardQuota(userId);
        String jobTitle = resolveJobTitle(userId, recommendationId);
        startGenerationJob(userId, recommendationId, jobTitle, "AUTO", null);
        return JobResourceAsyncResult.generating(List.of(), "学习资源生成中，请稍候");
    }

    /**
     * 刷新学习资源（异步）：校验额度后启动后台生成，旧数据保留至成功替换。
     */
    public JobResourceAsyncResult refreshResources(Long userId, Long recommendationId) throws Exception {
        return executeWithJobLock(userId, recommendationId,
                () -> refreshResourcesInternal(userId, recommendationId));
    }

    private JobResourceAsyncResult refreshResourcesInternal(Long userId, Long recommendationId) throws Exception {
        String lockKey = userId + ":" + recommendationId;
        List<JobLearningResource> oldResources =
                resourceRepository.findByUserIdAndRecommendationIdOrderBySortOrderAsc(userId, recommendationId);
        List<JobLearningResourceDTO> cached = toDtoList(oldResources);

        GenerationJob existingJob = generationJobs.get(lockKey);
        if (existingJob != null && existingJob.isGenerating()) {
            return JobResourceAsyncResult.generating(cached, "学习资源生成中，请稍候");
        }

        checkManualCap(userId);
        checkBoardQuota(userId);
        String jobTitle = resolveJobTitle(userId, recommendationId);
        startGenerationJob(userId, recommendationId, jobTitle, "MANUAL", oldResources);
        return JobResourceAsyncResult.generating(cached, "学习资源刷新中，请稍候");
    }

    /**
     * 重新生成学习资源（异步）：先 AI 成功再替换旧数据；仅扣一次 REGENERATE。
     */
    public JobResourceAsyncResult regenerateResources(Long userId, Long recommendationId) throws Exception {
        return executeWithJobLock(userId, recommendationId,
                () -> regenerateResourcesInternal(userId, recommendationId));
    }

    private JobResourceAsyncResult regenerateResourcesInternal(Long userId, Long recommendationId) throws Exception {
        String lockKey = userId + ":" + recommendationId;
        List<JobLearningResource> oldResources =
                resourceRepository.findByUserIdAndRecommendationIdOrderBySortOrderAsc(userId, recommendationId);
        List<JobLearningResourceDTO> cached = toDtoList(oldResources);

        GenerationJob existingJob = generationJobs.get(lockKey);
        if (existingJob != null && existingJob.isGenerating()) {
            return JobResourceAsyncResult.generating(cached, "学习资源生成中，请稍候");
        }

        checkRegenerateCap(userId);
        checkBoardQuota(userId);
        String jobTitle = resolveJobTitle(userId, recommendationId);
        startGenerationJob(userId, recommendationId, jobTitle, "REGENERATE", oldResources);
        return JobResourceAsyncResult.generating(cached, "学习资源重新生成中，请稍候");
    }

    /**
     * @deprecated 保留兼容旧「清缓存」调用；内部改为异步重新生成。
     */
    @Deprecated
    public JobResourceAsyncResult clearResourceCache(Long userId, Long recommendationId) throws Exception {
        return regenerateResources(userId, recommendationId);
    }

    /** 取消收藏时级联删除该岗位的学习资源 */
    @Transactional
    public void deleteResourcesByJob(Long userId, Long recommendationId) {
        resourceRepository.deleteByUserIdAndRecommendationId(userId, recommendationId);
        generationJobs.remove(userId + ":" + recommendationId);
    }

    /**
     * 仅读取缓存与异步状态，不触发生成、不消耗额度。
     * 供首页聚合视图与前端轮询使用。
     */
    public JobResourceAsyncResult getCachedResources(Long userId, Long recommendationId) {
        String lockKey = userId + ":" + recommendationId;
        List<JobLearningResourceDTO> cached = toDtoList(
                resourceRepository.findByUserIdAndRecommendationIdOrderBySortOrderAsc(userId, recommendationId));
        GenerationJob job = generationJobs.get(lockKey);
        if (job != null && job.isGenerating()) {
            return JobResourceAsyncResult.generating(cached, "学习资源生成中，请稍候");
        }
        if (job != null && job.isFailed()) {
            if (job.isExpired(FAILED_STATUS_TTL_MS)) {
                generationJobs.remove(lockKey, job);
            } else {
                return JobResourceAsyncResult.failed(cached, job.errorMessage);
            }
        }
        if (!cached.isEmpty()) {
            return JobResourceAsyncResult.ready(cached);
        }
        return JobResourceAsyncResult.empty();
    }

    /** 查询用户今日的额度使用情况 */
    public Map<String, Object> getQuotaInfo(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long autoUsed = logRepository.countSuccessfulCalls(userId, todayStart);
        long manualUsed = logRepository.countManualCalls(userId, todayStart);
        long regenerateUsed = logRepository.countRegenerateCalls(userId, todayStart);
        Map<String, Object> quota = new LinkedHashMap<>();
        quota.put("autoUsed", autoUsed);
        quota.put("autoTotal", DAILY_RESOURCE_BOARD_QUOTA);
        quota.put("autoRemaining", Math.max(0, DAILY_RESOURCE_BOARD_QUOTA - autoUsed));
        quota.put("boardUsed", autoUsed);
        quota.put("boardTotal", DAILY_RESOURCE_BOARD_QUOTA);
        quota.put("boardRemaining", Math.max(0, DAILY_RESOURCE_BOARD_QUOTA - autoUsed));
        quota.put("manualUsed", manualUsed);
        quota.put("manualTotal", DAILY_MANUAL_CAP);
        quota.put("manualRemaining", Math.max(0, DAILY_MANUAL_CAP - manualUsed));
        quota.put("regenerateUsed", regenerateUsed);
        quota.put("regenerateTotal", DAILY_REGENERATE_CAP);
        quota.put("regenerateRemaining", Math.max(0, DAILY_REGENERATE_CAP - regenerateUsed));
        return quota;
    }

    private void startGenerationJob(
            Long userId,
            Long recommendationId,
            String jobTitle,
            String generationType,
            List<JobLearningResource> oldResources) {
        String lockKey = userId + ":" + recommendationId;
        GenerationJob job = new GenerationJob();
        generationJobs.put(lockKey, job);
        List<JobLearningResource> oldSnapshot = oldResources == null ? List.of() : List.copyOf(oldResources);

        generationExecutor.execute(() -> {
            try {
                List<JobLearningResourceClient.GeneratedLearningResource> generated =
                        aiJobGenerationGate.call("learning-resource:" + lockKey,
                                () -> client.generateResources(jobTitle));
                executeWithJobLock(userId, recommendationId, () -> {
                    transactionTemplate.execute(status -> {
                        if ("AUTO".equals(generationType)) {
                            List<JobLearningResource> saved =
                                    saveResources(userId, recommendationId, jobTitle, generated);
                            logGeneration(userId, recommendationId, "AUTO");
                            log.info("岗位学习资源异步生成完成 userId={} recommendationId={} count={}",
                                    userId, recommendationId, saved.size());
                        } else {
                            resourceRepository.deleteByUserIdAndRecommendationId(userId, recommendationId);
                            List<JobLearningResource> saved =
                                    saveResources(userId, recommendationId, jobTitle, generated);
                            migrateResourceProgress(userId, recommendationId, oldSnapshot, saved);
                            logGeneration(userId, recommendationId, generationType);
                            log.info("岗位学习资源异步{}完成 userId={} recommendationId={} count={}",
                                    generationType, userId, recommendationId, saved.size());
                        }
                        return null;
                    });
                    generationJobs.remove(lockKey, job);
                    return null;
                });
            } catch (Exception exception) {
                log.error("岗位学习资源异步生成失败 userId={} recommendationId={} type={}",
                        userId, recommendationId, generationType, exception);
                job.markFailed(friendlyError(exception));
            }
        });
    }

    private String friendlyError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "岗位学习资源生成失败，请稍后再试";
        }
        if (message.length() > 180) {
            return message.substring(0, 180);
        }
        return message;
    }

    private String resolveJobTitle(Long userId, Long recommendationId) {
        JobFavorite favorite = jobFavoriteRepository.findByUserIdAndRecommendationId(userId, recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("请先收藏该岗位后再获取学习资源"));
        return favorite.getJobTitle();
    }

    private void checkBoardQuota(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long used = logRepository.countSuccessfulCalls(userId, todayStart);
        if (used >= DAILY_RESOURCE_BOARD_QUOTA) {
            throw new RateLimitException("今日资源板块次数已用完（" + DAILY_RESOURCE_BOARD_QUOTA + " 次/天），请明天再试");
        }
    }

    private void checkManualCap(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long used = logRepository.countManualCalls(userId, todayStart);
        if (used >= DAILY_MANUAL_CAP) {
            throw new RateLimitException("今日刷新次数已用完（" + DAILY_MANUAL_CAP + " 次/天），请明天再试");
        }
    }

    private void checkRegenerateCap(Long userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long used = logRepository.countRegenerateCalls(userId, todayStart);
        if (used >= DAILY_REGENERATE_CAP) {
            throw new RateLimitException("今日重新生成次数已用完（" + DAILY_REGENERATE_CAP + " 次/天），请明天再试");
        }
    }

    private void migrateResourceProgress(
            Long userId,
            Long recommendationId,
            List<JobLearningResource> oldResources,
            List<JobLearningResource> newResources) {
        if (oldResources.isEmpty()) {
            return;
        }
        Map<String, String> oldTitleByKey = new LinkedHashMap<>();
        for (JobLearningResource resource : oldResources) {
            oldTitleByKey.put(String.valueOf(resource.getId()), resource.getTitle());
        }
        Map<String, Long> newIdByTitle = new LinkedHashMap<>();
        for (JobLearningResource resource : newResources) {
            newIdByTitle.putIfAbsent(resource.getTitle(), resource.getId());
        }
        migrateProgressByType(userId, recommendationId, LearningProgress.TYPE_RESOURCE, oldTitleByKey, newIdByTitle);
        migrateProgressByType(userId, recommendationId, LearningProgress.TYPE_RESOURCE_UNLOCK, oldTitleByKey, newIdByTitle);
    }

    private void migrateProgressByType(
            Long userId,
            Long recommendationId,
            String itemType,
            Map<String, String> oldTitleByKey,
            Map<String, Long> newIdByTitle) {
        Set<Long> migratedNewIds = new LinkedHashSet<>();
        List<LearningProgress> oldMarks = progressRepository.findByUserIdAndRecommendationIdAndItemType(
                userId, recommendationId, itemType);
        for (LearningProgress mark : oldMarks) {
            String oldTitle = oldTitleByKey.get(mark.getItemKey());
            if (oldTitle == null) {
                continue;
            }
            Long newResourceId = newIdByTitle.get(oldTitle);
            if (newResourceId != null && migratedNewIds.add(newResourceId)) {
                mark.setItemKey(String.valueOf(newResourceId));
                mark.setItemTitle(oldTitle);
                progressRepository.save(mark);
            } else {
                progressRepository.delete(mark);
            }
        }
    }

    private List<JobLearningResource> saveResources(
            Long userId,
            Long recommendationId,
            String jobTitle,
            List<JobLearningResourceClient.GeneratedLearningResource> generated) {
        if (generated == null || generated.isEmpty()) {
            throw new IllegalStateException("AI 未返回有效的学习资源");
        }
        resourceRepository.deleteByUserIdAndRecommendationId(userId, recommendationId);
        Map<String, Integer> firstIndexByTitle = new LinkedHashMap<>();
        for (int i = 0; i < generated.size(); i++) {
            firstIndexByTitle.putIfAbsent(generated.get(i).title(), i);
        }
        LocalDateTime now = LocalDateTime.now();
        List<JobLearningResource> entities = new ArrayList<>();
        int limit = Math.min(firstIndexByTitle.size(), MAX_RESOURCES_PER_JOB);
        int count = 0;
        for (Integer originalIndex : firstIndexByTitle.values()) {
            if (count >= limit) {
                break;
            }
            count++;
            JobLearningResourceClient.GeneratedLearningResource item = generated.get(originalIndex);
            JobLearningResource entity = new JobLearningResource();
            entity.setUserId(userId);
            entity.setRecommendationId(recommendationId);
            entity.setJobTitle(jobTitle);
            entity.setSortOrder(originalIndex + 1);
            entity.setTitle(item.title());
            entity.setDescription(item.description());
            entity.setGuide(item.guide());
            entity.setLink(item.link());
            entity.setContent(item.content());
            entity.setSource(client.source());
            entity.setModelName(client.modelName());
            entity.setGeneratedAt(now);
            entities.add(entity);
        }
        return resourceRepository.saveAll(entities);
    }

    private void logGeneration(Long userId, Long recommendationId, String generationType) {
        JobResourceGenerationLog logEntity = new JobResourceGenerationLog();
        logEntity.setUserId(userId);
        logEntity.setRecommendationId(recommendationId);
        logEntity.setGenerationType(generationType);
        logEntity.setSuccess(true);
        logEntity.setGeneratedAt(LocalDateTime.now());
        logRepository.save(logEntity);
    }

    private List<JobLearningResourceDTO> toDtoList(List<JobLearningResource> entities) {
        List<JobLearningResourceDTO> result = new ArrayList<>();
        for (JobLearningResource entity : entities) {
            JobLearningResourceDTO dto = new JobLearningResourceDTO();
            dto.setId(entity.getId());
            dto.setRecommendationId(entity.getRecommendationId());
            dto.setJobTitle(entity.getJobTitle());
            dto.setSortOrder(entity.getSortOrder());
            dto.setTitle(entity.getTitle());
            dto.setDescription(entity.getDescription());
            dto.setGuide(entity.getGuide());
            dto.setLink(entity.getLink());
            dto.setContent(entity.getContent());
            dto.setSource(entity.getSource());
            dto.setModelName(entity.getModelName());
            dto.setGeneratedAt(entity.getGeneratedAt());
            result.add(dto);
        }
        return result;
    }

    private static final class GenerationJob {
        private volatile String status = JobResourceAsyncResult.STATUS_GENERATING;
        private volatile String errorMessage;
        private volatile long finishedAtMs;

        private boolean isGenerating() {
            return JobResourceAsyncResult.STATUS_GENERATING.equals(status);
        }

        private boolean isFailed() {
            return JobResourceAsyncResult.STATUS_FAILED.equals(status);
        }

        private void markFailed(String message) {
            this.errorMessage = message;
            this.status = JobResourceAsyncResult.STATUS_FAILED;
            this.finishedAtMs = System.currentTimeMillis();
        }

        private boolean isExpired(long ttlMs) {
            return finishedAtMs > 0 && System.currentTimeMillis() - finishedAtMs > ttlMs;
        }
    }
}
