package com.markingsystem.service;

final class CompositeScoreCalculator {

    private CompositeScoreCalculator() {
    }

    static double resolvePhysicalBaseScore(double realPhysicalBaseScore, boolean fullScoreEnabled) {
        return fullScoreEnabled ? 100D : realPhysicalBaseScore;
    }
}
