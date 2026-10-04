package com.markingsystem.repository;

import com.markingsystem.entity.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

    List<AcademicYear> findByStatus(String status);

    Optional<AcademicYear> findByName(String name);

    Optional<AcademicYear> findByCurrentTrue();
}
