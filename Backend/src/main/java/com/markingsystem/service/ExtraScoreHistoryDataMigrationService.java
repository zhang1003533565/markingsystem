package com.markingsystem.service;

import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.ExtraScoreSubmissionPeriod;
import com.markingsystem.entity.ExtraScoreSubmissionWindow;
import com.markingsystem.repository.ExtraScoreApplicationEventRepository;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.ExtraScoreSubmissionPeriodRepository;
import com.markingsystem.repository.ExtraScoreSubmissionWindowRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExtraScoreHistoryDataMigrationService implements ApplicationRunner {

    private final ExtraScoreSubmissionWindowRepository windowRepository;
    private final ExtraScoreSubmissionPeriodRepository periodRepository;
    private final ExtraScoreApplicationRepository applicationRepository;
    private final ExtraScoreApplicationEventRepository eventRepository;
    private final ExtraScoreApplicationEventService eventService;

    public ExtraScoreHistoryDataMigrationService(
            ExtraScoreSubmissionWindowRepository windowRepository,
            ExtraScoreSubmissionPeriodRepository periodRepository,
            ExtraScoreApplicationRepository applicationRepository,
            ExtraScoreApplicationEventRepository eventRepository,
            ExtraScoreApplicationEventService eventService
    ) {
        this.windowRepository = windowRepository;
        this.periodRepository = periodRepository;
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.eventService = eventService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, ExtraScoreSubmissionPeriod> periodsByBatch = migratePeriods();
        migrateApplications(periodsByBatch);
    }

    private Map<String, ExtraScoreSubmissionPeriod> migratePeriods() {
        List<ExtraScoreSubmissionWindow> windows = windowRepository.findAll();
        Map<String, List<ExtraScoreSubmissionWindow>> windowsByScope = new LinkedHashMap<>();
        for (ExtraScoreSubmissionWindow window : windows) {
            String scope = window.getTeacherId() + "|" + safe(window.getAcademicYear());
            windowsByScope.computeIfAbsent(scope, ignored -> new ArrayList<>()).add(window);
        }

        Map<String, ExtraScoreSubmissionPeriod> periodsByBatch = new LinkedHashMap<>();
        for (List<ExtraScoreSubmissionWindow> scopeWindows : windowsByScope.values()) {
            Map<String, List<ExtraScoreSubmissionWindow>> grouped = new LinkedHashMap<>();
            for (ExtraScoreSubmissionWindow window : scopeWindows) {
                grouped.computeIfAbsent(resolveBatchCode(window), ignored -> new ArrayList<>()).add(window);
            }
            List<Map.Entry<String, List<ExtraScoreSubmissionWindow>>> batches = grouped.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> representative(entry.getValue()), windowOrder()))
                    .toList();
            for (int periodNo = 0; periodNo < batches.size(); periodNo++) {
                String batchCode = batches.get(periodNo).getKey();
                List<ExtraScoreSubmissionWindow> batchWindows = batches.get(periodNo).getValue();
                ExtraScoreSubmissionWindow first = representative(batchWindows);
                ExtraScoreSubmissionPeriod period = periodRepository.findByBatchCode(batchCode)
                        .orElseGet(ExtraScoreSubmissionPeriod::new);
                period.setBatchCode(batchCode);
                period.setAcademicYear(safe(first.getAcademicYear()));
                period.setTeacherId(first.getTeacherId());
                period.setPeriodNo(periodNo);
                period.setPeriodType(periodNo == 0 ? "annual" : "supplemental");
                period.setDisplayName(periodNo == 0 ? "年度主周期" : "补充周期 " + periodNo);
                period.setStartTime(first.getStartTime());
                period.setEndTime(first.getEndTime());
                period.setCreatedBy(first.getTeacherId());
                period.setStatus(resolvePeriodStatus(batchWindows));
                if ("settled".equals(period.getStatus()) && period.getSettledAt() == null) {
                    period.setSettledAt(first.getEndTime());
                }
                ExtraScoreSubmissionPeriod saved = periodRepository.save(period);
                periodsByBatch.put(batchCode, saved);
                List<ExtraScoreSubmissionWindow> changedWindows = new ArrayList<>();
                for (ExtraScoreSubmissionWindow window : batchWindows) {
                    if (!batchCode.equals(window.getBatchCode()) || !saved.getId().equals(window.getPeriodId())) {
                        window.setBatchCode(batchCode);
                        window.setPeriodId(saved.getId());
                        changedWindows.add(window);
                    }
                }
                if (!changedWindows.isEmpty()) {
                    windowRepository.saveAll(changedWindows);
                }
            }
        }
        return periodsByBatch;
    }

    private void migrateApplications(Map<String, ExtraScoreSubmissionPeriod> periodsByBatch) {
        List<ExtraScoreApplication> applications = applicationRepository.findAll();
        Map<Long, ExtraScoreSubmissionWindow> windowsById = new LinkedHashMap<>();
        windowRepository.findAll().forEach(window -> windowsById.put(window.getId(), window));
        List<ExtraScoreApplication> changed = new ArrayList<>();
        for (ExtraScoreApplication application : applications) {
            boolean dirty = false;
            if (application.getSubmissionPeriodId() == null) {
                ExtraScoreSubmissionPeriod period = null;
                ExtraScoreSubmissionWindow window = windowsById.get(application.getSubmissionWindowId());
                if (window != null && window.getPeriodId() != null) {
                    application.setSubmissionPeriodId(window.getPeriodId());
                    dirty = true;
                } else if (application.getSubmissionBatchCode() != null) {
                    period = periodsByBatch.get(application.getSubmissionBatchCode());
                    if (period != null) {
                        application.setSubmissionPeriodId(period.getId());
                        dirty = true;
                    }
                }
            }
            if (application.getCurrentRevisionNo() == null || application.getCurrentRevisionNo() < 1) {
                application.setCurrentRevisionNo(1);
                dirty = true;
            }
            if (dirty) {
                changed.add(application);
            }
        }
        if (!changed.isEmpty()) {
            applicationRepository.saveAll(changed);
        }
        for (ExtraScoreApplication application : applications) {
            if (application.getId() != null && !eventRepository.existsByApplicationId(application.getId())) {
                eventService.record(
                        application,
                        "MIGRATED",
                        null,
                        null,
                        "系统迁移",
                        "由历史申请数据生成",
                        true
                );
            }
        }
    }

    private String resolvePeriodStatus(List<ExtraScoreSubmissionWindow> windows) {
        LocalDateTime now = LocalDateTime.now();
        boolean enabled = windows.stream().anyMatch(window -> Boolean.TRUE.equals(window.getEnabled()));
        ExtraScoreSubmissionWindow first = representative(windows);
        if (!enabled || (first.getEndTime() != null && now.isAfter(first.getEndTime()))) {
            return "settled";
        }
        if (first.getStartTime() != null && now.isBefore(first.getStartTime())) {
            return "scheduled";
        }
        return "open";
    }

    private String resolveBatchCode(ExtraScoreSubmissionWindow window) {
        String batchCode = safe(window.getBatchCode()).trim();
        if (!batchCode.isBlank()) {
            return batchCode;
        }
        String legacyKey = window.getTeacherId()
                + "|" + safe(window.getAcademicYear())
                + "|" + String.valueOf(window.getStartTime())
                + "|" + String.valueOf(window.getEndTime());
        return "legacy-" + window.getTeacherId() + "-" + Integer.toUnsignedString(legacyKey.hashCode(), 36);
    }

    private ExtraScoreSubmissionWindow representative(List<ExtraScoreSubmissionWindow> windows) {
        return windows.stream().min(windowOrder()).orElseThrow();
    }

    private Comparator<ExtraScoreSubmissionWindow> windowOrder() {
        return Comparator.comparing(
                ExtraScoreSubmissionWindow::getStartTime,
                Comparator.nullsLast(Comparator.naturalOrder())
        ).thenComparing(
                ExtraScoreSubmissionWindow::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())
        ).thenComparing(
                ExtraScoreSubmissionWindow::getId,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
