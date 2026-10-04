package com.markingsystem.repository;

import com.markingsystem.entity.LearningProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearningProgressRepository extends JpaRepository<LearningProgress, Long> {

    /** 查询某用户对某岗位的全部进度记录 */
    List<LearningProgress> findByUserIdAndRecommendationId(Long userId, Long recommendationId);

    /** 查询某用户对某岗位某类型的进度记录 */
    List<LearningProgress> findByUserIdAndRecommendationIdAndItemType(Long userId, Long recommendationId, String itemType);

    /** 查询某用户某类型的进度记录（如全部每日打卡） */
    List<LearningProgress> findByUserIdAndItemType(Long userId, String itemType);

    /** 查询某用户某岗位某类型某键的进度记录（用于标记完成 / 取消） */
    Optional<LearningProgress> findByUserIdAndRecommendationIdAndItemTypeAndItemKey(
            Long userId, Long recommendationId, String itemType, String itemKey);

    /** 统计某用户某岗位某类型已完成数量 */
    long countByUserIdAndRecommendationIdAndItemTypeAndCompletedTrue(Long userId, Long recommendationId, String itemType);

    /** 统计某用户某类型已完成数量 */
    long countByUserIdAndItemTypeAndCompletedTrue(Long userId, String itemType);

    /** 取消收藏时级联删除该岗位的全部进度记录 */
    @Transactional
    void deleteByUserIdAndRecommendationId(Long userId, Long recommendationId);

    /** 删除某用户某岗位某类型的进度记录（如刷新任务卡时清空 TASK_STEP） */
    @Transactional
    void deleteByUserIdAndRecommendationIdAndItemType(Long userId, Long recommendationId, String itemType);
}
