package com.markingsystem.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminAnalysisOverviewDTO {
    private Integer totalStudents;
    private Integer analyzedStudents;
    private Integer classCount;
    private List<TypeCount> typeDistribution = new ArrayList<>();
    private List<ClassDistribution> classDistributions = new ArrayList<>();

    @Data
    public static class TypeCount {
        private String type;
        private Integer count;
        private Double ratio;
    }

    @Data
    public static class ClassDistribution {
        private String className;
        private Integer totalStudents;
        private Integer analyzedStudents;
        private Integer graduateCount;
        private Integer civilCount;
        private Integer competitionCount;
        private String dominantType;
    }
}
