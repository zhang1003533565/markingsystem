package com.markingsystem.service;

import com.markingsystem.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompositeScoreSnapshotScheduler {

    @Value("${composite-score.snapshot.cron:0 0 3 * * ?}")
    private String snapshotCron;

    @Autowired
    private AdminCompositeScoreService adminCompositeScoreService;

    @Autowired
    private AcademicYearService academicYearService;

    @Autowired
    private UserService userService;

    @Scheduled(cron = "${composite-score.snapshot.cron:0 0 3 * * ?}")
    public void refreshCompositeSnapshotsNightly() {
        String academicYear = academicYearService.findCurrent()
                .map(item -> item.getName() == null ? "" : item.getName().trim())
                .orElse("");
        if (academicYear.isBlank()) {
            return;
        }

        adminCompositeScoreService.refreshCompositeScores(
                academicYear,
                null,
                null,
                0L,
                "系统定时任务",
                "SYSTEM"
        );

        List<User> teachers = userService.findTeachers();
        for (User teacher : teachers) {
            Long teacherId = teacher.getTeacherId() != null ? teacher.getTeacherId() : teacher.getId();
            if (teacherId == null) {
                continue;
            }
            String updaterName = (teacher.getNickname() == null || teacher.getNickname().isBlank())
                    ? (teacher.getUsername() == null || teacher.getUsername().isBlank() ? "系统定时任务" : teacher.getUsername())
                    : teacher.getNickname();
            adminCompositeScoreService.refreshCompositeScores(
                    academicYear,
                    null,
                    teacherId,
                    0L,
                    updaterName + "（系统定时）",
                    "SYSTEM"
            );
        }
    }
}
