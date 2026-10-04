package com.markingsystem.repository;

import com.markingsystem.entity.StudentAnalysisRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentAnalysisRecordRepository extends JpaRepository<StudentAnalysisRecord, Long> {
    void deleteByStudentIdIn(List<Long> studentIds);
    Optional<StudentAnalysisRecord> findTopByStudentNoOrderByGeneratedAtDesc(String studentNo);
    List<StudentAnalysisRecord> findByStudentNoOrderByGeneratedAtDesc(String studentNo);
    List<StudentAnalysisRecord> findByStudentNoInOrderByGeneratedAtDesc(List<String> studentNos);
}
