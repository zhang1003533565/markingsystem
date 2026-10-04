package com.markingsystem.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminDashboardPortraitOverviewDTO {
    private Integer analyzedStudentCount;
    private String topPortraitType;
    private List<ClassPortraitSummary> classPortraits = new ArrayList<>();
    private List<PortraitTrendItem> portraitTrends = new ArrayList<>();
    private List<OutstandingStudent> standoutStudents = new ArrayList<>();

    @Data
    public static class ClassPortraitSummary {
        private String className;
        private Integer studentCount;
        private Integer analyzedCount;
        private String dominantPortrait;
        private List<String> committeeNames = new ArrayList<>();
    }

    @Data
    public static class PortraitTrendItem {
        private String className;
        private Integer graduateCount;
        private Integer civilCount;
        private Integer competitionCount;
    }

    @Data
    public static class OutstandingStudent {
        private String studentNo;
        private String studentName;
        private String className;
        private String primaryType;
        private Integer confidence;
        private Double courseAverage;
        private String highlight;
    }
}
