package com.markingsystem.repository;

import com.markingsystem.entity.ExtraScoreTemplateItemAuthorRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExtraScoreTemplateItemAuthorRuleRepository extends JpaRepository<ExtraScoreTemplateItemAuthorRule, Long> {
    List<ExtraScoreTemplateItemAuthorRule> findByItemIdInOrderBySortOrderAscIdAsc(Collection<Long> itemIds);
    List<ExtraScoreTemplateItemAuthorRule> findByLevelRuleIdInOrderBySortOrderAscIdAsc(Collection<Long> levelRuleIds);
    void deleteByItemIdIn(Collection<Long> itemIds);
    void deleteByLevelRuleIdIn(Collection<Long> levelRuleIds);
}
