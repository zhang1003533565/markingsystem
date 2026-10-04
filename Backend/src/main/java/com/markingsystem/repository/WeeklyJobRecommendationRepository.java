package com.markingsystem.repository;

import com.markingsystem.entity.WeeklyJobRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeeklyJobRecommendationRepository extends JpaRepository<WeeklyJobRecommendation, Long> {

    List<WeeklyJobRecommendation> findByWeekStartDateOrderBySortOrderAsc(LocalDate weekStartDate);

    @Query("select max(item.weekStartDate) from WeeklyJobRecommendation item")
    Optional<LocalDate> findLatestWeekStartDate();

    @Transactional
    void deleteByWeekStartDate(LocalDate weekStartDate);

    /** 删除早于指定日期的所有周数据（保留最近 N 周） */
    @Transactional
    void deleteByWeekStartDateBefore(LocalDate date);
}
