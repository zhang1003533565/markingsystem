package com.markingsystem.repository;

import com.markingsystem.entity.ExtraScoreTemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExtraScoreTemplateItemRepository extends JpaRepository<ExtraScoreTemplateItem, Long> {
    List<ExtraScoreTemplateItem> findByTemplateIdOrderBySortOrderAscIdAsc(Long templateId);
    List<ExtraScoreTemplateItem> findByGroupIdInOrderBySortOrderAscIdAsc(Collection<Long> groupIds);
    void deleteByTemplateId(Long templateId);
}
