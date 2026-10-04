package com.markingsystem.repository;

import com.markingsystem.entity.TeacherPortraitRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherPortraitRuleRepository extends JpaRepository<TeacherPortraitRule, Long> {
    List<TeacherPortraitRule> findByTeacherId(Long teacherId);
    void deleteByTeacherId(Long teacherId);
}
