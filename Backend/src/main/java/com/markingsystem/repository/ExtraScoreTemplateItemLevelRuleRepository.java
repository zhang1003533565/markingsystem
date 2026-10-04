package com.markingsystem.repository;

import com.markingsystem.entity.ExtraScoreTemplateItemLevelRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExtraScoreTemplateItemLevelRuleRepository extends JpaRepository<ExtraScoreTemplateItemLevelRule, Long> {
    List<ExtraScoreTemplateItemLevelRule> findByItemIdInOrderBySortOrderAscIdAsc(Collection<Long> itemIds);
    void deleteByItemIdIn(Collection<Long> itemIds);
}
