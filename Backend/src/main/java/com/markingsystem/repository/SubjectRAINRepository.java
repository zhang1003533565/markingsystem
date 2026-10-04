package com.markingsystem.repository;

import com.markingsystem.entity.SubjectRAIN;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRAINRepository extends JpaRepository<SubjectRAIN, Long> {

    /**
     * 根据科目名称查询科目
     * @param subjectName 科目名称
     * @return 科目对象
     */
    Optional<SubjectRAIN> findBySubjectName(String subjectName);

    /**
     * 检查科目名称是否存在
     * @param subjectName 科目名称
     * @return 是否存在
     */
    boolean existsBySubjectName(String subjectName);

    /**
     * 查询所有未删除的科目
     * @return 未删除的科目列表
     */
    @Query("SELECT s FROM SubjectRAIN s WHERE s.deleted = false ORDER BY s.subjectName ASC")
    List<SubjectRAIN> findAllActive();

    /**
     * 查询所有已删除的科目
     * @return 已删除的科目列表
     */
    @Query("SELECT s FROM SubjectRAIN s WHERE s.deleted = true ORDER BY s.deletedAt DESC")
    List<SubjectRAIN> findAllDeleted();

    /**
     * 查询所有科目（包括已删除）
     * @return 所有科目列表
     */
    @Query("SELECT s FROM SubjectRAIN s ORDER BY s.deleted ASC, s.subjectName ASC")
    List<SubjectRAIN> findAllIncludingDeleted();

    /**
     * 根据分类查询未删除的科目
     * @param category 分类
     * @return 未删除的科目列表
     */
    @Query("SELECT s FROM SubjectRAIN s WHERE s.category = :category AND s.deleted = false ORDER BY s.subjectName ASC")
    List<SubjectRAIN> findByCategoryAndDeletedFalse(SubjectRAIN.Category category);

    /**
     * 根据科目名称查询未删除的科目
     * @param subjectName 科目名称
     * @return 科目对象
     */
    Optional<SubjectRAIN> findBySubjectNameAndDeletedFalse(String subjectName);

    /**
     * 检查未删除的科目名称是否存在
     * @param subjectName 科目名称
     * @return 是否存在
     */
    boolean existsBySubjectNameAndDeletedFalse(String subjectName);

}
