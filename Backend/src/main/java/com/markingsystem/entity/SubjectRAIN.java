package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "subject_rain")
@Data
public class SubjectRAIN {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_name", nullable = false, unique = true)
    private String subjectName;

    @Column(name = "category", nullable = false)
    @Enumerated(EnumType.STRING)
    private Category category = Category.SUBJECT; // 默认是科目项

    // 软删除字段
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false; // 默认未删除

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // 删除时间

    public enum Category {
        SUBJECT, // 科目项
        NON_SUBJECT // 无关项
    }

}
