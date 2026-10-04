package com.markingsystem.service;

import com.markingsystem.entity.SecondClassScoreRule;
import com.markingsystem.repository.SecondClassScoreRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SecondClassScoreRuleService {

    @Autowired
    private SecondClassScoreRuleRepository secondClassScoreRuleRepository;

    public List<SecondClassScoreRule> findAll() {
        List<SecondClassScoreRule> rules = secondClassScoreRuleRepository.findAllByOrderBySortOrderAscMinScoreAscIdAsc();
        if (!rules.isEmpty()) {
            return rules;
        }
        return buildDefaultRules();
    }

    @Transactional
    public List<SecondClassScoreRule> replaceAll(List<SecondClassScoreRule> rules) {
        List<SecondClassScoreRule> normalizedRules = normalize(rules);
        secondClassScoreRuleRepository.deleteAllInBatch();
        return secondClassScoreRuleRepository.saveAll(normalizedRules).stream()
                .sorted(Comparator.comparing(SecondClassScoreRule::getSortOrder)
                        .thenComparing(SecondClassScoreRule::getMinScore)
                        .thenComparing(rule -> rule.getId() == null ? 0L : rule.getId()))
                .toList();
    }

    private List<SecondClassScoreRule> normalize(List<SecondClassScoreRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("换算规则不能为空");
        }
        List<SecondClassScoreRule> normalized = new ArrayList<>();
        for (int index = 0; index < rules.size(); index++) {
            SecondClassScoreRule source = rules.get(index);
            if (source == null) {
                continue;
            }
            if (source.getMinScore() == null) {
                throw new IllegalArgumentException("最小二课分不能为空");
            }
            if (source.getMoralScore() == null) {
                throw new IllegalArgumentException("换算德育分不能为空");
            }
            if (source.getMaxScore() != null && source.getMaxScore() < source.getMinScore()) {
                throw new IllegalArgumentException("最大二课分不能小于最小二课分");
            }
            SecondClassScoreRule item = new SecondClassScoreRule();
            item.setLabel((source.getLabel() == null || source.getLabel().isBlank()) ? buildLabel(source) : source.getLabel().trim());
            item.setMinScore(source.getMinScore());
            item.setMaxScore(source.getMaxScore());
            item.setMoralScore(source.getMoralScore());
            item.setSortOrder(source.getSortOrder() == null ? index + 1 : source.getSortOrder());
            normalized.add(item);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("换算规则不能为空");
        }
        return normalized;
    }

    private String buildLabel(SecondClassScoreRule rule) {
        if (rule.getMaxScore() == null) {
            return trimZero(rule.getMinScore()) + "及以上";
        }
        if (Double.compare(rule.getMinScore(), rule.getMaxScore()) == 0) {
            return trimZero(rule.getMinScore());
        }
        return trimZero(rule.getMinScore()) + "-" + trimZero(rule.getMaxScore());
    }

    private String trimZero(Double value) {
        if (value == null) {
            return "";
        }
        if (Math.abs(value - Math.rint(value)) < 0.000001d) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.valueOf(value);
    }

    private List<SecondClassScoreRule> buildDefaultRules() {
        List<SecondClassScoreRule> defaults = new ArrayList<>();
        double secondClassScore = 0D;
        double moralScore = 13D;
        int order = 1;
        while (secondClassScore < 2D) {
            SecondClassScoreRule item = new SecondClassScoreRule();
            item.setLabel(trimZero(secondClassScore));
            item.setMinScore(secondClassScore);
            item.setMaxScore(secondClassScore);
            item.setMoralScore(moralScore);
            item.setSortOrder(order++);
            defaults.add(item);
            secondClassScore = Math.round((secondClassScore + 0.1D) * 10D) / 10D;
            moralScore = Math.round((moralScore + 0.2D) * 10D) / 10D;
        }
        SecondClassScoreRule last = new SecondClassScoreRule();
        last.setLabel("2及以上");
        last.setMinScore(2D);
        last.setMaxScore(null);
        last.setMoralScore(17D);
        last.setSortOrder(order);
        defaults.add(last);
        return defaults;
    }
}
