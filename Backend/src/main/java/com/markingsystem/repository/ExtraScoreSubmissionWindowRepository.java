package com.markingsystem.repository;

import com.markingsystem.entity.ExtraScoreSubmissionWindow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExtraScoreSubmissionWindowRepository extends JpaRepository<ExtraScoreSubmissionWindow, Long> {

    Optional<ExtraScoreSubmissionWindow> findByAcademicYearAndClassName(String academicYear, String className);

    List<ExtraScoreSubmissionWindow> findByAcademicYearOrderByCreatedAtDescClassNameAsc(String academicYear);

    List<ExtraScoreSubmissionWindow> findByTeacherIdAndAcademicYearOrderByClassNameAsc(Long teacherId, String academicYear);

    List<ExtraScoreSubmissionWindow> findByTeacherIdAndAcademicYearOrderByCreatedAtDescClassNameAsc(Long teacherId, String academicYear);

    List<ExtraScoreSubmissionWindow> findByAcademicYearAndClassNameOrderByCreatedAtDesc(String academicYear, String className);

    List<ExtraScoreSubmissionWindow> findByClassNameOrderByAcademicYearDescCreatedAtDesc(String className);

    List<ExtraScoreSubmissionWindow> findByEnabledTrueAndEndTimeBefore(LocalDateTime endTime);
}
