package com.markingsystem.repository;

import com.markingsystem.entity.JobTaskCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface JobTaskCardRepository extends JpaRepository<JobTaskCard, Long> {

    /** 查询某个用户对某个收藏岗位的学习任务卡（每个用户每岗位一张） */
    Optional<JobTaskCard> findByUserIdAndRecommendationId(Long userId, Long recommendationId);

    /** 删除某用户某岗位的任务卡（取消收藏时级联清理） */
    @Transactional
    void deleteByUserIdAndRecommendationId(Long userId, Long recommendationId);
}
