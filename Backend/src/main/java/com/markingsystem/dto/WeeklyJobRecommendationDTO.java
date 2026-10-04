package com.markingsystem.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class WeeklyJobRecommendationDTO {
    private Long id;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private Integer sortOrder;
    private String jobTitle;
    private String salary;
    private String skills;
    private String recruitmentLink;
    private String source;
    private String modelName;
    private LocalDateTime generatedAt;
}
