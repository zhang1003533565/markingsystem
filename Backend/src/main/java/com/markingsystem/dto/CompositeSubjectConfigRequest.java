package com.markingsystem.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompositeSubjectConfigRequest {

    private String academicYear;

    private Long majorId;

    private String grade;

    private String className;

    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private Long subjectId;
        private String category;
    }
}
