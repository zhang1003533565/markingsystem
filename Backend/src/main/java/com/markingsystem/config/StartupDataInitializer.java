package com.markingsystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class StartupDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDataInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public StartupDataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAnnouncementSchema();
    }

    private void ensureAnnouncementSchema() {
        try {
            jdbcTemplate.execute("ALTER TABLE announcements MODIFY content LONGTEXT NOT NULL");
        } catch (Exception exception) {
            log.warn("Failed to expand announcements.content to LONGTEXT: {}", exception.getMessage());
        }
    }
}
