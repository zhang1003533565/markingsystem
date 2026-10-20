package com.markingsystem.service;

import com.markingsystem.entity.CompositeScoreWeight;
import com.markingsystem.repository.CompositeScoreWeightRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompositeScoreWeightService {

    @Autowired
    private CompositeScoreWeightRepository compositeScoreWeightRepository;

    public CompositeScoreWeight getCurrentWeight() {
        return compositeScoreWeightRepository.findAll().stream()
                .findFirst()
                .orElseGet(this::createDefaultWeight);
    }

    @Transactional
    public CompositeScoreWeight save(CompositeScoreWeight input) {
        validate(input);
        CompositeScoreWeight existing = getCurrentWeight();
        existing.setMoralBaseWeight(input.getMoralBaseWeight());
        existing.setMoralExtraWeight(input.getMoralExtraWeight());
        existing.setIntellectualBaseWeight(input.getIntellectualBaseWeight());
        existing.setIntellectualExtraWeight(input.getIntellectualExtraWeight());
        existing.setPhysicalBaseWeight(input.getPhysicalBaseWeight());
        existing.setPhysicalExtraWeight(input.getPhysicalExtraWeight());
        int nextVersion = (existing.getVersion() == null ? 1 : existing.getVersion()) + 1;
        existing.setVersion(nextVersion);
        return compositeScoreWeightRepository.save(existing);
    }

    private CompositeScoreWeight createDefaultWeight() {
        return compositeScoreWeightRepository.save(new CompositeScoreWeight());
    }

    private void validate(CompositeScoreWeight weight) {
        if (weight == null) {
            throw new IllegalArgumentException("权重配置不能为空");
        }
        if (weight.getMoralBaseWeight() == null || weight.getMoralExtraWeight() == null
                || weight.getIntellectualBaseWeight() == null || weight.getIntellectualExtraWeight() == null
                || weight.getPhysicalBaseWeight() == null || weight.getPhysicalExtraWeight() == null) {
            throw new IllegalArgumentException("所有权重都必须填写");
        }
        if (weight.getMoralBaseWeight() < 0 || weight.getMoralExtraWeight() < 0
                || weight.getIntellectualBaseWeight() < 0 || weight.getIntellectualExtraWeight() < 0
                || weight.getPhysicalBaseWeight() < 0 || weight.getPhysicalExtraWeight() < 0) {
            throw new IllegalArgumentException("权重不能为负数");
        }
    }
}
