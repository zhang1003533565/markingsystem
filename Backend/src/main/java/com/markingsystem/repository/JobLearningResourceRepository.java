package com.markingsystem.repository;

import com.markingsystem.entity.JobLearningResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobLearningResourceRepository extends JpaRepository<JobLearningResource, Long> {

    /** 查询某个用户对某个收藏岗位的学习资源列表（按 sortOrder 升序） */
    List<JobLearningResource> findByUserIdAndRecommendationIdOrderBySortOrderAsc(Long userId, Long recommendationId);

    /** 查询某个用户对某个收藏岗位的学习资源（可能为空） */
    default Optional<JobLearningResource> findFirstByUserIdAndRecommendationId(Long userId, Long recommendationId) {
        List<JobLearningResource> list = findByUserIdAndRecommendationIdOrderBySortOrderAsc(userId, recommendationId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 删除某用户某岗位的所有学习资源（取消收藏时级联清理） */
    @Transactional
    void deleteByUserIdAndRecommendationId(Long userId, Long recommendationId);

    /** 统计某用户某岗位的资源数量 */
    long countByUserIdAndRecommendationId(Long userId, Long recommendationId);

    /** 删除某用户某岗位的旧资源（刷新时调用） */
    @Transactional
    void deleteByUserIdAndRecommendationIdAndIdNotIn(Long userId, Long recommendationId, List<Long> keepIds);
}
