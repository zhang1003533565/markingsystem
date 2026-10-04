package com.markingsystem.repository;

import com.markingsystem.entity.ExtraScoreTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtraScoreTemplateRepository extends JpaRepository<ExtraScoreTemplate, Long> {
    List<ExtraScoreTemplate> findAllByOrderByScoreTypeAscIdAsc();
    List<ExtraScoreTemplate> findByStatusOrderByScoreTypeAscIdAsc(String status);
}
