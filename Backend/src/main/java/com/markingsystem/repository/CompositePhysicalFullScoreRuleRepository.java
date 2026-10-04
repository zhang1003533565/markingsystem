package com.markingsystem.repository;

import com.markingsystem.entity.CompositePhysicalFullScoreRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompositePhysicalFullScoreRuleRepository
        extends JpaRepository<CompositePhysicalFullScoreRule, Long> {

    List<CompositePhysicalFullScoreRule> findByAcademicYearOrderByClassNameAsc(String academicYear);

    Optional<CompositePhysicalFullScoreRule> findByAcademicYearAndClassName(
            String academicYear,
            String className
    );
}
