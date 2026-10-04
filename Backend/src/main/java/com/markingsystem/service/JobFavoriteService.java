package com.markingsystem.service;

import com.markingsystem.dto.WeeklyJobRecommendationDTO;
import com.markingsystem.entity.JobFavorite;
import com.markingsystem.entity.WeeklyJobRecommendation;
import com.markingsystem.repository.JobFavoriteRepository;
import com.markingsystem.repository.WeeklyJobRecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JobFavoriteService {

    /** 单个用户最多可收藏的岗位数量 */
    public static final int MAX_FAVORITES_PER_USER = 6;

    private final JobFavoriteRepository jobFavoriteRepository;
    private final WeeklyJobRecommendationRepository weeklyJobRecommendationRepository;
    private final JobLearningResourceService jobLearningResourceService;
    private final JobTaskCardService jobTaskCardService;
    private final LearningProgressService learningProgressService;

    public JobFavoriteService(
            JobFavoriteRepository jobFavoriteRepository,
            WeeklyJobRecommendationRepository weeklyJobRecommendationRepository,
            JobLearningResourceService jobLearningResourceService,
            JobTaskCardService jobTaskCardService,
            LearningProgressService learningProgressService) {
        this.jobFavoriteRepository = jobFavoriteRepository;
        this.weeklyJobRecommendationRepository = weeklyJobRecommendationRepository;
        this.jobLearningResourceService = jobLearningResourceService;
        this.jobTaskCardService = jobTaskCardService;
        this.learningProgressService = learningProgressService;
    }

    @Transactional
    public JobFavorite addFavorite(Long userId, Long recommendationId) {
        if (jobFavoriteRepository.existsByUserIdAndRecommendationId(userId, recommendationId)) {
            return jobFavoriteRepository.findByUserIdAndRecommendationId(userId, recommendationId).orElse(null);
        }
        if (countFavorites(userId) >= MAX_FAVORITES_PER_USER) {
            throw new IllegalStateException("最多只能收藏 " + MAX_FAVORITES_PER_USER + " 个岗位，请先取消部分收藏");
        }
        WeeklyJobRecommendation job = weeklyJobRecommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("岗位推荐不存在: " + recommendationId));
        JobFavorite favorite = new JobFavorite();
        favorite.setUserId(userId);
        favorite.setRecommendationId(recommendationId);
        favorite.setJobTitle(job.getJobTitle());
        favorite.setSalary(job.getSalary());
        favorite.setSkills(job.getSkills());
        favorite.setRecruitmentLink(job.getRecruitmentLink());
        return jobFavoriteRepository.save(favorite);
    }

    @Transactional
    public void removeFavorite(Long userId, Long recommendationId) {
        jobFavoriteRepository.deleteByUserIdAndRecommendationId(userId, recommendationId);
        // 级联删除该岗位的学习资源与学习任务卡，避免残留数据
        jobLearningResourceService.deleteResourcesByJob(userId, recommendationId);
        jobTaskCardService.deleteTaskCardByJob(userId, recommendationId);
        // 级联删除该岗位的学习进度/打卡记录
        learningProgressService.deleteProgressByJob(userId, recommendationId);
    }

    /** 统计用户当前收藏岗位数量 */
    public long countFavorites(Long userId) {
        return jobFavoriteRepository.countByUserId(userId);
    }

    public Set<Long> getFavoriteRecommendationIds(Long userId) {
        return jobFavoriteRepository.findByUserId(userId).stream()
                .map(JobFavorite::getRecommendationId)
                .collect(Collectors.toSet());
    }

    public List<WeeklyJobRecommendationDTO> listFavoriteJobs(Long userId) {
        List<JobFavorite> favorites = jobFavoriteRepository.findByUserId(userId);
        if (favorites.isEmpty()) {
            return List.of();
        }

        List<WeeklyJobRecommendationDTO> result = new ArrayList<>();
        for (JobFavorite favorite : favorites) {
            WeeklyJobRecommendationDTO dto = new WeeklyJobRecommendationDTO();
            dto.setId(favorite.getRecommendationId());
            dto.setJobTitle(favorite.getJobTitle());
            dto.setSalary(favorite.getSalary());
            dto.setSkills(favorite.getSkills());
            dto.setRecruitmentLink(favorite.getRecruitmentLink());
            result.add(dto);
        }

        return result;
    }
}
