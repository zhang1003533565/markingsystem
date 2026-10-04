package com.markingsystem.repository;

import com.markingsystem.entity.JobResourceGenerationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface JobResourceGenerationLogRepository extends JpaRepository<JobResourceGenerationLog, Long> {

    /** 统计某用户在指定时间段内学习资源生成的总调用次数（AUTO + MANUAL + REGENERATE，排除任务卡 TASK_CARD / TASK_REFRESH） */
    @Query("select count(l) from JobResourceGenerationLog l " +
            "where l.userId = :userId and l.generatedAt >= :startTime and l.success = true " +
            "and l.generationType <> 'TASK_CARD' and l.generationType <> 'TASK_REFRESH'")
    long countSuccessfulCalls(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    /** 统计某用户在指定时间段内手动刷新次数 */
    @Query("select count(l) from JobResourceGenerationLog l " +
            "where l.userId = :userId and l.generatedAt >= :startTime " +
            "and l.generationType = 'MANUAL' and l.success = true")
    long countManualCalls(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    /** 统计某用户在指定时间段内学习任务卡生成次数（TASK_CARD，与学习资源额度相互独立） */
    @Query("select count(l) from JobResourceGenerationLog l " +
            "where l.userId = :userId and l.generatedAt >= :startTime " +
            "and l.generationType = 'TASK_CARD' and l.success = true")
    long countTaskCardCalls(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    /** 统计某用户在指定时间段内「重新生成」（清理缓存强制重刷）次数 */
    @Query("select count(l) from JobResourceGenerationLog l " +
            "where l.userId = :userId and l.generatedAt >= :startTime " +
            "and l.generationType = 'REGENERATE' and l.success = true")
    long countRegenerateCalls(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    /** 统计某用户某板块在指定时间段内学习任务卡板块总次数（TASK_CARD 生成 + TASK_REFRESH 刷新，板块池 5 次/天） */
    @Query("select count(l) from JobResourceGenerationLog l " +
            "where l.userId = :userId and l.generatedAt >= :startTime and l.success = true " +
            "and (l.generationType = 'TASK_CARD' or l.generationType = 'TASK_REFRESH')")
    long countTaskCardBoardCalls(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);

    /** 统计某用户某板块在指定时间段内学习任务卡「刷新」次数（TASK_REFRESH 独立 3 次/天，跨岗位合计） */
    @Query("select count(l) from JobResourceGenerationLog l " +
            "where l.userId = :userId and l.generatedAt >= :startTime " +
            "and l.generationType = 'TASK_REFRESH' and l.success = true")
    long countTaskRefreshCalls(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);
}
