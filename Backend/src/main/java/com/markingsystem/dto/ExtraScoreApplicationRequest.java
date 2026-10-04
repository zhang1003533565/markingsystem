package com.markingsystem.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExtraScoreApplicationRequest {
    private String scoreType;
    private Long templateId;
    private Long itemId;
    private Long levelRuleId;
    private Long authorRuleId;
    private String certFileName;
    private String certFileUrl;
    private List<String> certFileNames;
    private List<String> certFileUrls;
    private String certName;
    private String detailDesc;
    private String awardTime;
    private String proofText;
    private List<String> proofImages;
}
