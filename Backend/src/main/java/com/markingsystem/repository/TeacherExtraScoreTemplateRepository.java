package com.markingsystem.repository;

import com.markingsystem.entity.TeacherExtraScoreTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherExtraScoreTemplateRepository extends JpaRepository<TeacherExtraScoreTemplate, Long> {
    List<TeacherExtraScoreTemplate> findByTeacherId(Long teacherId);
    Optional<TeacherExtraScoreTemplate> findByTeacherIdAndScoreType(Long teacherId, String scoreType);
    void deleteByTeacherIdAndScoreType(Long teacherId, String scoreType);
}
