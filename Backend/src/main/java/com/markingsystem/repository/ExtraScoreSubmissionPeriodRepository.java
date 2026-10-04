package com.markingsystem.repository;

import com.markingsystem.entity.ExtraScoreSubmissionPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExtraScoreSubmissionPeriodRepository extends JpaRepository<ExtraScoreSubmissionPeriod, Long> {
    Optional<ExtraScoreSubmissionPeriod> findByBatchCode(String batchCode);
    List<ExtraScoreSubmissionPeriod> findByTeacherIdAndAcademicYearOrderByPeriodNoAsc(Long teacherId, String academicYear);
}
