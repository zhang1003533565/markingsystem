package com.markingsystem.dto;

/**
 * 发展助手 - 个性化资源推荐 DTO
 */
public class DevelopmentResourceDTO {

    private Long id;
    private String title;
    private String description;
    private String guide;

    public DevelopmentResourceDTO() {
    }

    public DevelopmentResourceDTO(Long id, String title, String description, String guide) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.guide = guide;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGuide() {
        return guide;
    }

    public void setGuide(String guide) {
        this.guide = guide;
    }
}
