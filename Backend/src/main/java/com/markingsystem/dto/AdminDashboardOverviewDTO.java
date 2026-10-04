package com.markingsystem.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminDashboardOverviewDTO {
    private Integer managedClassCount;
    private Integer managedStudentCount;
    private List<ClassSummary> managedClasses = new ArrayList<>();

    @Data
    public static class ClassSummary {
        private String className;
        private Integer studentCount;
    }
}
