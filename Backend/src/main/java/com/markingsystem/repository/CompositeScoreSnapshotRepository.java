package com.markingsystem.repository;

import com.markingsystem.entity.CompositeScoreSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompositeScoreSnapshotRepository extends JpaRepository<CompositeScoreSnapshot, Long> {

    List<CompositeScoreSnapshot> findByAcademicYearOrderByRankNoAscStudentNoAsc(String academicYear);

    List<CompositeScoreSnapshot> findByAcademicYearAndClassNameOrderByRankNoAscStudentNoAsc(
            String academicYear,
            String className
    );

    @Query("""
            SELECT snapshot FROM CompositeScoreSnapshot snapshot
            WHERE snapshot.studentNo = :studentNo
            ORDER BY snapshot.academicYear DESC, snapshot.generatedAt DESC
            """)
    List<CompositeScoreSnapshot> findLatestByStudentNo(@Param("studentNo") String studentNo);

    Optional<CompositeScoreSnapshot> findFirstByAcademicYearAndStudentNoOrderByGeneratedAtDesc(
            String academicYear,
            String studentNo
    );

    void deleteByAcademicYearAndStudentNoIn(
            String academicYear,
            Collection<String> studentNos
    );
}
