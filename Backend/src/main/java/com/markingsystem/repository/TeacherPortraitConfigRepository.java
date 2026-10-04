package com.markingsystem.repository;

import com.markingsystem.entity.TeacherPortraitConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeacherPortraitConfigRepository extends JpaRepository<TeacherPortraitConfig, Long> {
    Optional<TeacherPortraitConfig> findByTeacherId(Long teacherId);
}
