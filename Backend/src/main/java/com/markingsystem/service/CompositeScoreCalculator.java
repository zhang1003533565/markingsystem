package com.markingsystem.service;

import com.markingsystem.entity.CompositeScoreWeight;

final class CompositeScoreCalculator {

    private CompositeScoreCalculator() {
    }

    static double resolvePhysicalBaseScore(double realPhysicalBaseScore, boolean fullScoreEnabled) {
        return fullScoreEnabled ? 100D : realPhysicalBaseScore;
    }

    /** 权重系数：<=1 视为比例原样使用，>1 视为百分数除以 100。 */
    static double resolveFactor(Double value) {
        double safe = value == null ? 0D : value;
        return safe <= 1D ? safe : safe / 100D;
    }

    /**
     * 综测总分的六个原始分量（加权前）。
     * 与 {@code AdminCompositeScoreService.buildRow} 中喂给加权求和的口径完全一致：
     * 德育基础=德育均分，智育基础=智育科目均分，体育基础=体育科目均分(或满分规则)，
     * 三类附加=对应 scoreType 附加分求和。
     */
    record RawComponents(
            double moralBase,
            double intellectualBase,
            double physicalBase,
            double moralExtra,
            double intellectualExtra,
            double physicalExtra
    ) {
    }

    /** 加权后的六个分量与总分。 */
    static final class CompositeBreakdown {
        final double weightedMoralBase;
        final double weightedMoralExtra;
        final double weightedIntellectualBase;
        final double weightedIntellectualExtra;
        final double weightedPhysicalBase;
        final double weightedPhysicalExtra;
        final double totalScore;

        private CompositeBreakdown(
                double weightedMoralBase,
                double weightedMoralExtra,
                double weightedIntellectualBase,
                double weightedIntellectualExtra,
                double weightedPhysicalBase,
                double weightedPhysicalExtra
        ) {
            this.weightedMoralBase = weightedMoralBase;
            this.weightedMoralExtra = weightedMoralExtra;
            this.weightedIntellectualBase = weightedIntellectualBase;
            this.weightedIntellectualExtra = weightedIntellectualExtra;
            this.weightedPhysicalBase = weightedPhysicalBase;
            this.weightedPhysicalExtra = weightedPhysicalExtra;
            this.totalScore = weightedMoralBase + weightedMoralExtra
                    + weightedIntellectualBase + weightedIntellectualExtra
                    + weightedPhysicalBase + weightedPhysicalExtra;
        }
    }

    /**
     * 综测总分的唯一计算入口：由六个原始分量与权重配置算出加权分量与总分。
     * 正式刷新（buildRow）与成绩模拟器都必须走这里，保证口径不漂移。
     */
    static CompositeBreakdown computeBreakdown(RawComponents raw, CompositeScoreWeight weight) {
        return new CompositeBreakdown(
                raw.moralBase() * resolveFactor(weight.getMoralBaseWeight()),
                raw.moralExtra() * resolveFactor(weight.getMoralExtraWeight()),
                raw.intellectualBase() * resolveFactor(weight.getIntellectualBaseWeight()),
                raw.intellectualExtra() * resolveFactor(weight.getIntellectualExtraWeight()),
                raw.physicalBase() * resolveFactor(weight.getPhysicalBaseWeight()),
                raw.physicalExtra() * resolveFactor(weight.getPhysicalExtraWeight())
        );
    }
}
