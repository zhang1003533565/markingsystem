package com.markingsystem.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateAnnouncementRequest {
    private String title;
    private String content;
    private String category;
    private String image;
    private String video;
    private String senderType;
    private String targetType;
    private List<String> targetGrades = new ArrayList<>();
    private List<String> targetClassNames = new ArrayList<>();
}
