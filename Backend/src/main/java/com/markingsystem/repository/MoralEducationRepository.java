package com.markingsystem.repository;

import com.markingsystem.entity.MoralEducation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MoralEducationRepository extends JpaRepository<MoralEducation, Long> {
    
    List<MoralEducation> findByStudentId(Long studentId);

    List<MoralEducation> findByStudentIdIn(List<Long> studentIds);

    void deleteByStudentIdIn(List<Long> studentIds);
    
    Optional<MoralEducation> findByStudentIdAndSemester(Long studentId, String semester);
    
    boolean existsByStudentIdAndSemester(Long studentId, String semester);

    Optional<MoralEducation> findByStudentIdAndAcademicYear(Long studentId, String academicYear);

    boolean existsByStudentIdAndAcademicYear(Long studentId, String academicYear);

    @Query("SELECT m FROM MoralEducation m LEFT JOIN FETCH m.student s " +
            "WHERE m.academicYear = :academicYear AND m.secondClassScore IS NOT NULL " +
            "ORDER BY s.className ASC, s.studentNo ASC")
    List<MoralEducation> findSecondClassRecordsByAcademicYear(@Param("academicYear") String academicYear);

    @Query("SELECT m FROM MoralEducation m LEFT JOIN FETCH m.student s " +
            "WHERE m.academicYear = :academicYear AND m.secondClassScore IS NOT NULL AND s.teacherId = :teacherId " +
            "ORDER BY s.className ASC, s.studentNo ASC")
    List<MoralEducation> findSecondClassRecordsByAcademicYearAndTeacherId(
            @Param("academicYear") String academicYear,
            @Param("teacherId") Long teacherId);
}
