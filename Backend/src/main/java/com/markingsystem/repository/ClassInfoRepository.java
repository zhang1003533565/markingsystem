package com.markingsystem.repository;

import com.markingsystem.entity.ClassInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassInfoRepository extends JpaRepository<ClassInfo, Long> {
    
    Optional<ClassInfo> findByName(String name);
    
    List<ClassInfo> findByMajorId(Long majorId);
    
    List<ClassInfo> findByGrade(String grade);

    List<ClassInfo> findByTeacherId(Long teacherId);
    
    List<ClassInfo> findByStatus(String status);
    
    List<ClassInfo> findByNameContaining(String name);
    
    boolean existsByName(String name);
}
