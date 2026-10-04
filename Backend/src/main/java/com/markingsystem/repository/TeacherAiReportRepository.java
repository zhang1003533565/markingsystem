package com.markingsystem.repository;

import com.markingsystem.entity.TeacherAiReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherAiReportRepository extends JpaRepository<TeacherAiReport, Long> {

    List<TeacherAiReport> findByTeacherIdAndReportTypeOrderByGeneratedAtDesc(Long teacherId, String reportType);

    List<TeacherAiReport> findByTeacherIdAndReportTypeAndClassNameOrderByGeneratedAtDesc(
            Long teacherId, String reportType, String className);

    Optional<TeacherAiReport> findByIdAndTeacherIdAndReportType(Long id, Long teacherId, String reportType);
}
