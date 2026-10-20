package com.markingsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "material_chain_files",
        indexes = {
                @Index(name = "idx_material_chain_code", columnList = "chain_code", unique = true),
                @Index(name = "idx_material_application_id", columnList = "application_id"),
                @Index(name = "idx_material_cert_no", columnList = "cert_no"),
                @Index(name = "idx_material_student_no", columnList = "student_no")
        }
)
public class MaterialChainFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "chain_code", nullable = false, length = 64, unique = true)
    private String chainCode;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type", length = 50)
    private String fileType;

    @Column(name = "cert_no", length = 100)
    private String certNo;

    @Column(name = "cert_name", length = 255)
    private String certName;

    @Column(name = "upload_stage", nullable = false, length = 30)
    private String uploadStage = "SUBMITTED";

    @Column(name = "tamper_risk", length = 20)
    private String tamperRisk = "LOW";

    @Column(name = "tamper_detail", columnDefinition = "TEXT")
    private String tamperDetail;

    @Column(name = "cert_duplicate_risk", length = 20)
    private String certDuplicateRisk = "CLEAN";

    @Column(name = "cert_duplicate_detail", length = 500)
    private String certDuplicateDetail;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo = 1;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "student_no", length = 50)
    private String studentNo;

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
