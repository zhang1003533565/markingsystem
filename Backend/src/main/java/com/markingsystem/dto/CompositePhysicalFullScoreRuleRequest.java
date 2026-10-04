package com.markingsystem.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompositePhysicalFullScoreRuleRequest {

    private String academicYear;

    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private String className;
        private Boolean fullScoreEnabled;
    }
}
