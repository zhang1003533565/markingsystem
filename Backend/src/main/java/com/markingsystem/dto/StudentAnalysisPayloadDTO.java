package com.markingsystem.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StudentAnalysisPayloadDTO {
    private Long studentId;
    private String studentNo;
    private String studentName;
    private String className;
    private Long teacherId;
    private BasicProfile basicProfile;
    private DimensionScores dimensionScores;
    private CourseStatistics courseStatistics;
    private ExtraScoreStatistics extraScoreStatistics;
    private List<SemesterSummary> semesterSummaries = new ArrayList<>();

    @Data
    public static class BasicProfile {
        private String phone;
        private String email;
    }

    @Data
    public static class DimensionScores {
        private Double moralAverage;
        private Double intellectualAverage;
        private Double physicalAverage;
    }

    @Data
    public static class CourseStatistics {
        private Double overallAverage;
        private Integer scoreCount;
        private Integer excellentCount;
        private Integer passCount;
        private List<SubjectStat> topSubjects = new ArrayList<>();
        private List<SubjectStat> weakSubjects = new ArrayList<>();
    }

    @Data
    public static class SubjectStat {
        private String subjectName;
        private Double averageScore;
        private Integer scoreCount;
    }

    @Data
    public static class ExtraScoreStatistics {
        private Integer approvedCount;
        private Double approvedTotalScore;
        private Integer competitionCount;
        private Integer researchCount;
        private Integer moralCount;
        private Integer physicalCount;
    }

    @Data
    public static class SemesterSummary {
        private String semester;
        private Double courseAverage;
        private Double moralScore;
        private Double intellectualScore;
        private Double physicalScore;
        private Double approvedExtraScore;
    }
}
