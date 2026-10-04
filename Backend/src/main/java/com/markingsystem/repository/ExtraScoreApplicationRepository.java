package com.markingsystem.repository;

import com.markingsystem.entity.ExtraScoreApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ExtraScoreApplicationRepository extends JpaRepository<ExtraScoreApplication, Long> {
    void deleteByStudentIdIn(List<Long> studentIds);
    List<ExtraScoreApplication> findByStudentNoAndScoreTypeOrderByCreatedAtDesc(String studentNo, String scoreType);
    @Query("SELECT e FROM ExtraScoreApplication e WHERE e.studentNo = :studentNo AND UPPER(e.scoreType) = UPPER(:scoreType) ORDER BY e.createdAt DESC")
    List<ExtraScoreApplication> findByStudentNoAndScoreTypeCaseInsensitive(@Param("studentNo") String studentNo, @Param("scoreType") String scoreType);
    List<ExtraScoreApplication> findByStudentNoOrderByCreatedAtDesc(String studentNo);
    List<ExtraScoreApplication> findByStudentNoAndScoreTypeAndGroupNameAndStatus(String studentNo, String scoreType, String groupName, String status);
    List<ExtraScoreApplication> findByStudentNoAndStatus(String studentNo, String status);
    List<ExtraScoreApplication> findByStudentNoInAndStatus(List<String> studentNos, String status);
    List<ExtraScoreApplication> findByStudentNoAndSubmissionWindowId(String studentNo, Long submissionWindowId);
    List<ExtraScoreApplication> findByScoreTypeOrderByCreatedAtDesc(String scoreType);
    List<ExtraScoreApplication> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);
    List<ExtraScoreApplication> findByTeacherIdAndScoreTypeOrderByCreatedAtDesc(Long teacherId, String scoreType);
    List<ExtraScoreApplication> findByTeacherIdAndClassNameAndScoreTypeOrderByCreatedAtDesc(Long teacherId, String className, String scoreType);
    List<ExtraScoreApplication> findByTeacherIdAndScoreTypeAndStatusNotAndTemplateIdNot(Long teacherId, String scoreType, String status, Long templateId);
    List<ExtraScoreApplication> findByTeacherIdAndScoreTypeAndStatusInAndTemplateIdNot(Long teacherId, String scoreType, List<String> statuses, Long templateId);
    List<ExtraScoreApplication> findBySubmissionWindowIdIn(List<Long> submissionWindowIds);
    
    // Custom queries for advanced filtering
    @Query("SELECT e FROM ExtraScoreApplication e WHERE " +
           "(:scoreType IS NULL OR e.scoreType = :scoreType) AND " +
           "(:className IS NULL OR e.className = :className) AND " +
           "(:startDate IS NULL OR e.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR e.createdAt <= :endDate) AND " +
           "(:keyword IS NULL OR e.studentName LIKE %:keyword% OR e.studentNo LIKE %:keyword% OR e.itemName LIKE %:keyword%) " +
           "ORDER BY e.createdAt DESC")
    List<ExtraScoreApplication> findByFilters(@Param("scoreType") String scoreType,
                                               @Param("className") String className,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate,
                                               @Param("keyword") String keyword);
    
    @Query("SELECT e FROM ExtraScoreApplication e WHERE " +
           "e.teacherId = :teacherId AND " +
           "(:scoreType IS NULL OR e.scoreType = :scoreType) AND " +
           "(:className IS NULL OR e.className = :className) AND " +
           "(:startDate IS NULL OR e.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR e.createdAt <= :endDate) AND " +
           "(:keyword IS NULL OR e.studentName LIKE %:keyword% OR e.studentNo LIKE %:keyword% OR e.itemName LIKE %:keyword%) " +
           "ORDER BY e.createdAt DESC")
    List<ExtraScoreApplication> findByTeacherIdAndFilters(@Param("teacherId") Long teacherId,
                                                           @Param("scoreType") String scoreType,
                                                           @Param("className") String className,
                                                           @Param("startDate") LocalDateTime startDate,
                                                           @Param("endDate") LocalDateTime endDate,
                                                           @Param("keyword") String keyword);

    @Query("SELECT DISTINCT e.academicYear FROM ExtraScoreApplication e WHERE " +
           "(:teacherId IS NULL OR e.teacherId = :teacherId) AND " +
           "e.academicYear IS NOT NULL AND e.academicYear <> '' " +
           "ORDER BY e.academicYear DESC")
    List<String> findReviewAcademicYears(@Param("teacherId") Long teacherId);
}
