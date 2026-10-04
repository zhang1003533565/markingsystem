package com.markingsystem.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExtraScoreTemplateRequest {
    private String name;
    private String scoreType;
    private Double maxScore;
    private String description;
    private String proofRequirement;
    private String status;
    private List<GroupRequest> groups;

    @Data
    public static class GroupRequest {
        private Long id;
        private String groupName;
        private Double maxScore;
        private Boolean sameEventBestOnly;
        private Integer sortOrder;
        private List<ItemRequest> items;
    }

    @Data
    public static class ItemRequest {
        private Long id;
        private String itemName;
        private String description;
        private Integer maxAuthors;
        private Integer sameEventMaxCount;
        private String scoreDirection;
        private Integer sortOrder;
        private String status;
        private List<LevelRuleRequest> levelRules;
    }

    @Data
    public static class LevelRuleRequest {
        private Long id;
        private String levelName;
        private Double score;
        private Integer sortOrder;
        private List<AuthorRuleRequest> authorRules;
    }

    @Data
    public static class AuthorRuleRequest {
        private Long id;
        private String authorLabel;
        private String mode;
        private Double ratio;
        private Double fixedScore;
        private Integer sortOrder;
    }
}
