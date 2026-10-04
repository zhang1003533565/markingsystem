package com.markingsystem.repository;

import com.markingsystem.entity.ExtraScoreTemplateGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtraScoreTemplateGroupRepository extends JpaRepository<ExtraScoreTemplateGroup, Long> {
    List<ExtraScoreTemplateGroup> findByTemplateIdOrderBySortOrderAscIdAsc(Long templateId);
    void deleteByTemplateId(Long templateId);
}
