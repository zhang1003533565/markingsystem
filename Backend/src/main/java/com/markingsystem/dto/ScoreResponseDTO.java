package com.markingsystem.dto;

import lombok.Data;

@Data
public class ScoreResponseDTO {
    
    private Long studentId;
    private String studentNo;
    private String name;
    private String className;
    private String semester;
    
    // 德育
    private Double activityScore;
    private Double moralBonusScore;
    private Double moralTotalScore;
    
    // 智育
    private Double courseScore;
    private Double intellectualBonusScore;
    private Double intellectualTotalScore;
    
    // 体育
    private Double physicalRawScore;
    private Double physicalPercentage;
    private Double physicalBonusScore;
    private Double physicalTotalScore;
}
