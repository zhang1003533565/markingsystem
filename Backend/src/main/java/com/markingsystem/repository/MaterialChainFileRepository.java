package com.markingsystem.repository;

import com.markingsystem.entity.MaterialChainFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaterialChainFileRepository extends JpaRepository<MaterialChainFile, Long> {

    Optional<MaterialChainFile> findByChainCode(String chainCode);

    List<MaterialChainFile> findByApplicationId(Long applicationId);

    List<MaterialChainFile> findByStudentNo(String studentNo);

    Page<MaterialChainFile> findByCertNoAndCertDuplicateRisk(String certNo, String risk, Pageable pageable);

    List<MaterialChainFile> findByCertNo(String certNo);

    @Query("SELECT m FROM MaterialChainFile m WHERE m.certNo = :certNo AND m.certDuplicateRisk = 'CLEAN' AND m.revisionNo = (SELECT MAX(m2.revisionNo) FROM MaterialChainFile m2 WHERE m2.studentNo = m.studentNo AND m2.certNo = m.certNo)")
    List<MaterialChainFile> findActiveByCertNo(@Param("certNo") String certNo);

    long countByCertNo(String certNo);

    long countByCertNoAndStudentNoNot(String certNo, String studentNo);

    List<MaterialChainFile> findByCertNoAndStudentNoNot(String certNo, String studentNo);

    @Query("SELECT m.studentNo FROM MaterialChainFile m WHERE m.certNo = :certNo AND m.revisionNo = (SELECT MAX(m2.revisionNo) FROM MaterialChainFile m2 WHERE m2.studentNo = m.studentNo AND m2.certNo = m.certNo)")
    List<String> findStudentNosByCertNo(@Param("certNo") String certNo);
}
