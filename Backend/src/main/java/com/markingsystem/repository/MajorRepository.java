package com.markingsystem.repository;

import com.markingsystem.entity.Major;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MajorRepository extends JpaRepository<Major, Long> {

    List<Major> findByCollegeId(Long collegeId);

    List<Major> findByStatus(String status);

    List<Major> findByCollegeIdAndStatus(Long collegeId, String status);

    List<Major> findByNameContaining(String name);
}
