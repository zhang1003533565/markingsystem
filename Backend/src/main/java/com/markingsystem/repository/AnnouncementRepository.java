package com.markingsystem.repository;

import com.markingsystem.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findByPublisherIdOrderByCreatedAtDesc(Long publisherId);
}
