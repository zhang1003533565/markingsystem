package com.markingsystem.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeScoreCalculatorTest {

    @Test
    void usesRealPhysicalBaseScoreWhenFullScoreSwitchIsOff() {
        assertEquals(82.5D, CompositeScoreCalculator.resolvePhysicalBaseScore(82.5D, false));
    }

    @Test
    void usesOneHundredAsPhysicalBaseScoreWhenFullScoreSwitchIsOn() {
        assertEquals(100D, CompositeScoreCalculator.resolvePhysicalBaseScore(82.5D, true));
    }
}
