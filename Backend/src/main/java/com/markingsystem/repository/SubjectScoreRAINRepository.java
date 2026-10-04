package com.markingsystem.repository;

import com.markingsystem.entity.SubjectScoreRAIN;
import com.markingsystem.entity.SubjectRAIN;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectScoreRAINRepository extends JpaRepository<SubjectScoreRAIN, Long> {

    @Query("SELECT s FROM SubjectScoreRAIN s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.subject LEFT JOIN FETCH s.user")
    List<SubjectScoreRAIN> findAllWithDetails();

    @Query("SELECT s FROM SubjectScoreRAIN s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.subject LEFT JOIN FETCH s.user WHERE s.studentId IN :studentIds")
    List<SubjectScoreRAIN> findByStudentIdInWithDetails(@Param("studentIds") List<Long> studentIds);

    /**
     * 根据学生ID查询成绩，包含关联的学生和科目信息
     * @param studentId 学生ID
     * @return 成绩列表
     */
    @Query("SELECT s FROM SubjectScoreRAIN s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.subject LEFT JOIN FETCH s.user WHERE s.studentId = :studentId")
    List<SubjectScoreRAIN> findByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT s FROM SubjectScoreRAIN s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.subject LEFT JOIN FETCH s.user WHERE s.userId = :userId")
    List<SubjectScoreRAIN> findByUserId(@Param("userId") Long userId);

    @Query("SELECT s FROM SubjectScoreRAIN s LEFT JOIN FETCH s.student st LEFT JOIN FETCH s.subject LEFT JOIN FETCH s.user WHERE st.teacherId = :teacherId")
    List<SubjectScoreRAIN> findByStudentTeacherId(@Param("teacherId") Long teacherId);

    @Query("SELECT s FROM SubjectScoreRAIN s LEFT JOIN FETCH s.student st LEFT JOIN FETCH s.subject LEFT JOIN FETCH s.user WHERE st.className = :className")
    List<SubjectScoreRAIN> findByStudentClassName(@Param("className") String className);

    /**
     * 根据科目ID查询成绩，包含关联的学生和科目信息
     * @param subjectId 科目ID
     * @return 成绩列表
     */
    @Query("SELECT s FROM SubjectScoreRAIN s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.subject LEFT JOIN FETCH s.user WHERE s.subjectId = :subjectId")
    List<SubjectScoreRAIN> findBySubjectId(@Param("subjectId") Long subjectId);

    /**
     * 根据学期查询成绩，包含关联的学生和科目信息
     * @param semester 学期
     * @return 成绩列表
     */
    @Query("SELECT s FROM SubjectScoreRAIN s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.subject LEFT JOIN FETCH s.user WHERE s.semester = :semester")
    List<SubjectScoreRAIN> findBySemester(@Param("semester") String semester);

    /**
     * 根据学生ID和学期查询成绩，包含关联的学生和科目信息
     * @param studentId 学生ID
     * @param semester 学期
     * @return 成绩列表
     */
    @Query("SELECT s FROM SubjectScoreRAIN s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.subject LEFT JOIN FETCH s.user WHERE s.studentId = :studentId AND s.semester = :semester")
    List<SubjectScoreRAIN> findByStudentIdAndSemester(@Param("studentId") Long studentId, @Param("semester") String semester);

    /**
     * 根据科目ID和学期查询成绩，包含关联的学生和科目信息
     * @param subjectId 科目ID
     * @param semester 学期
     * @return 成绩列表
     */
    @Query("SELECT s FROM SubjectScoreRAIN s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.subject LEFT JOIN FETCH s.user WHERE s.subjectId = :subjectId AND s.semester = :semester")
    List<SubjectScoreRAIN> findBySubjectIdAndSemester(@Param("subjectId") Long subjectId, @Param("semester") String semester);

    /**
     * 根据学生ID、科目ID和学期查询成绩，包含关联的学生和科目信息
     * @param studentId 学生ID
     * @param subjectId 科目ID
     * @param semester 学期
     * @return 成绩对象
     */
    @Query("SELECT s FROM SubjectScoreRAIN s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.subject LEFT JOIN FETCH s.user WHERE s.studentId = :studentId AND s.subjectId = :subjectId AND s.semester = :semester")
    Optional<SubjectScoreRAIN> findByStudentIdAndSubjectIdAndSemester(@Param("studentId") Long studentId, @Param("subjectId") Long subjectId, @Param("semester") String semester);

    /**
     * 根据学生ID和科目ID查询成绩，包含关联的学生和科目信息
     * @param studentId 学生ID
     * @param subjectId 科目ID
     * @return 成绩列表
     */
    @Query("SELECT s FROM SubjectScoreRAIN s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.subject LEFT JOIN FETCH s.user WHERE s.studentId = :studentId AND s.subjectId = :subjectId")
    List<SubjectScoreRAIN> findByStudentIdAndSubjectId(@Param("studentId") Long studentId, @Param("subjectId") Long subjectId);

    @Query("SELECT s FROM SubjectScoreRAIN s WHERE s.id = :id")
    Optional<SubjectScoreRAIN> findPlainById(@Param("id") Long id);

    @Query("SELECT DISTINCT s.subject FROM SubjectScoreRAIN s WHERE s.subject IS NOT NULL AND s.semester LIKE CONCAT(:academicYear, '%') ORDER BY s.subject.subjectName ASC")
    List<SubjectRAIN> findDistinctSubjectsByAcademicYear(@Param("academicYear") String academicYear);

    @Query("""
            SELECT DISTINCT s.subject
            FROM SubjectScoreRAIN s
            JOIN s.student st
            JOIN ClassInfo c ON c.name = st.className
            WHERE s.subject IS NOT NULL
              AND s.semester LIKE CONCAT(:academicYear, '%')
              AND c.majorId = :majorId
              AND c.grade = :grade
            ORDER BY s.subject.subjectName ASC
            """)
    List<SubjectRAIN> findDistinctSubjectsByAcademicYearAndMajorIdAndGrade(
            @Param("academicYear") String academicYear,
            @Param("majorId") Long majorId,
            @Param("grade") String grade
    );

    @Query("""
            SELECT DISTINCT s.subject
            FROM SubjectScoreRAIN s
            JOIN s.student st
            WHERE s.subject IS NOT NULL
              AND s.semester LIKE CONCAT(:academicYear, '%')
              AND st.className = :className
            ORDER BY s.subject.subjectName ASC
            """)
    List<SubjectRAIN> findDistinctSubjectsByAcademicYearAndClassName(
            @Param("academicYear") String academicYear,
            @Param("className") String className
    );

    void deleteByStudentIdIn(List<Long> studentIds);

    void deleteByUserIdIn(List<Long> userIds);

    @Query("""
            SELECT COUNT(s)
            FROM SubjectScoreRAIN s
            WHERE s.semester = :semester
              AND s.studentId IN (
                  SELECT st.id FROM Student st WHERE st.className = :className
              )
              AND s.subjectId IN (
                  SELECT sub.id FROM SubjectRAIN sub WHERE sub.subjectName IN :subjectNames
              )
            """)
    long countByClassSemesterAndSubjectNames(
            @Param("className") String className,
            @Param("semester") String semester,
            @Param("subjectNames") List<String> subjectNames
    );

    @Modifying
    @Query("""
            DELETE FROM SubjectScoreRAIN s
            WHERE s.semester = :semester
              AND s.studentId IN (
                  SELECT st.id FROM Student st WHERE st.className = :className
              )
              AND s.subjectId IN (
                  SELECT sub.id FROM SubjectRAIN sub WHERE sub.subjectName IN :subjectNames
              )
            """)
    int deleteByClassSemesterAndSubjectNames(
            @Param("className") String className,
            @Param("semester") String semester,
            @Param("subjectNames") List<String> subjectNames
    );

}
