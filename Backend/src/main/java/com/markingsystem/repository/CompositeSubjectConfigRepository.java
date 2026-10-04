package com.markingsystem.repository;

import com.markingsystem.entity.CompositeSubjectConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompositeSubjectConfigRepository extends JpaRepository<CompositeSubjectConfig, Long> {

    List<CompositeSubjectConfig> findByAcademicYearAndMajorIdAndGradeOrderBySubjectNameAsc(String academicYear, Long majorId, String grade);

    List<CompositeSubjectConfig> findByAcademicYearAndClassNameOrderBySubjectNameAsc(String academicYear, String className);
}
