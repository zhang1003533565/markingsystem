package com.markingsystem.repository;

import com.markingsystem.entity.SecondClassScoreRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecondClassScoreRuleRepository extends JpaRepository<SecondClassScoreRule, Long> {

    List<SecondClassScoreRule> findAllByOrderBySortOrderAscMinScoreAscIdAsc();
}
