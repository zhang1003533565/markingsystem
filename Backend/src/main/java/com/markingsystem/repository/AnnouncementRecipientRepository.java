package com.markingsystem.repository;

import com.markingsystem.entity.AnnouncementRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnnouncementRecipientRepository extends JpaRepository<AnnouncementRecipient, Long> {
    void deleteByStudentIdIn(List<Long> studentIds);

    List<AnnouncementRecipient> findByStudentNoOrderByCreatedAtDesc(String studentNo);

    List<AnnouncementRecipient> findByAnnouncementIdOrderByReadAscStudentNoAsc(Long announcementId);

    Optional<AnnouncementRecipient> findByAnnouncementIdAndStudentNo(Long announcementId, String studentNo);

    long countByAnnouncementIdAndReadTrue(Long announcementId);

    long countByAnnouncementIdAndReadFalse(Long announcementId);
}
