package com.markingsystem.dto;

import lombok.Data;

@Data
public class ScoreImportDTO {
    
    private String studentNo;
    private String name;
    private String className;
    
    // 德育
    private Double activityScore;
    private Double moralBonusScore;
    
    // 智育
    private Double courseScore;
    private Double intellectualBonusScore;
    
    // 体育
    private Double physicalRawScore;
    private Double physicalPercentage;
    private Double physicalBonusScore;
    
    // 学期
    private String semester;
}
