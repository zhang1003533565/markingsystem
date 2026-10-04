package com.markingsystem.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    @JsonIgnore  // 密码不返回给前端
    private String password;
    
    private String nickname;
    
    @Column(name = "role_id", nullable = false)
    private Long roleId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Role role;
    
    @Column(name = "student_no")
    private String studentNo;
    
    @Column(name = "teacher_id")
    private Long teacherId;
    
    @Column(name = "class_name")
    private String className;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    private String email;

    private String phone;

    @JsonIgnore
    @Column(name = "SMTP")
    private String emailPassword;
    
    @Column(name = "password_reset_required")
    private Boolean passwordResetRequired;
    
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
