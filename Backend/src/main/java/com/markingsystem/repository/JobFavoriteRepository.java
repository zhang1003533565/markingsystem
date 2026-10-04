package com.markingsystem.repository;

import com.markingsystem.entity.JobFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobFavoriteRepository extends JpaRepository<JobFavorite, Long> {

    List<JobFavorite> findByUserId(Long userId);

    Optional<JobFavorite> findByUserIdAndRecommendationId(Long userId, Long recommendationId);

    boolean existsByUserIdAndRecommendationId(Long userId, Long recommendationId);

    /** 统计某用户的收藏岗位数量（用于收藏上限校验） */
    long countByUserId(Long userId);

    @Transactional
    void deleteByUserIdAndRecommendationId(Long userId, Long recommendationId);
}
