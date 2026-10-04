package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "announcements")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "publisher_id", nullable = false)
    private Long publisherId;

    @Column(name = "publisher_name", nullable = false, length = 50)
    private String publisherName;

    @Column(name = "publisher_role", nullable = false, length = 40)
    private String publisherRole;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(name = "class_name", length = 50)
    private String className;

    @Column(name = "image", columnDefinition = "LONGTEXT")
    private String image;

    @Column(name = "video", columnDefinition = "LONGTEXT")
    private String video;

    @Column(name = "recipient_count", nullable = false)
    private Integer recipientCount = 0;

    @Column(name = "read_count", nullable = false)
    private Integer readCount = 0;

    @Column(name = "unread_count", nullable = false)
    private Integer unreadCount = 0;

    @Column(name = "remind_count", nullable = false)
    private Integer remindCount = 0;

    @Column(name = "last_reminded_at")
    private LocalDateTime lastRemindedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
