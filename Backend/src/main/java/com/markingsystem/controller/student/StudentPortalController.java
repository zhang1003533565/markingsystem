package com.markingsystem.controller.student;

import com.markingsystem.dto.StudentAnalysisPayloadDTO;
import com.markingsystem.dto.StudentAnalysisResultDTO;
import com.markingsystem.dto.StudentPortraitMatchResultDTO;
import com.markingsystem.entity.*;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.CollegeRepository;
import com.markingsystem.service.*;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.MajorRepository;
import com.markingsystem.repository.StudentAnalysisRecordRepository;
import com.markingsystem.repository.SubjectScoreRAINRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生端专属接口
 * 提供学生查询自己的成绩、个人信息等功能
 */
@RestController
@RequestMapping("/api/student")
public class StudentPortalController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private MoralEducationService moralEducationService;

    @Autowired
    private IntellectualEducationService intellectualEducationService;

    @Autowired
    private PhysicalEducationService physicalEducationService;

    @Autowired
    private UserService userService;

    @Autowired
    private SubjectScoreRAINRepository subjectScoreRAINRepository;

    @Autowired
    private ExtraScoreApplicationRepository extraScoreApplicationRepository;

    @Autowired
    private StudentAnalysisService studentAnalysisService;

    @Autowired
    private StudentPortraitMatchService studentPortraitMatchService;

    @Autowired
    private StudentAnalysisRecordRepository studentAnalysisRecordRepository;

    @Autowired
    private ClassInfoService classInfoService;

    @Autowired
    private ClassInfoRepository classInfoRepository;

    @Autowired
    private MajorRepository majorRepository;

    @Autowired
    private CollegeRepository collegeRepository;

    @Autowired
    private AcademicYearService academicYearService;

    @Autowired
    private ProofFileStorageService proofFileStorageService;

    @Autowired
    private StudentEmailBindingService studentEmailBindingService;

    @Autowired
    private AdminCompositeScoreService adminCompositeScoreService;

    /**
     * 获取当前登录学生的信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getStudentInfo(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {

        Map<String, Object> result = new HashMap<>();

        try {
            Optional<Student> studentOpt;

            if (userId != null && !userId.isEmpty()) {
                // 通过用户 ID 查找学生
                studentOpt = studentService.findByUserId(Long.parseLong(userId));
            } else if (studentNo != null && !studentNo.isEmpty()) {
                studentOpt = studentService.findByStudentNo(studentNo);
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "缺少用户标识"
                ));
            }

            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "code", 404,
                    "message", "未找到学生信息"
                ));
            }

            Student student = studentOpt.get();
            result.put("code", 200);
            result.put("data", buildStudentInfoPayload(student));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/profile/email/verification-code")
    public ResponseEntity<Map<String, Object>> sendEmailVerificationCode(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> data = studentEmailBindingService.sendVerificationCode(
                    parseUserId(userId),
                    stringValue(request.get("email"))
            );
            return ResponseEntity.ok(Map.of("code", 200, "message", "验证码已发送", "data", data));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", exception.getMessage()));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "验证码发送失败：" + exception.getMessage()));
        }
    }

    @GetMapping("/composite-score/snapshot")
    public ResponseEntity<Map<String, Object>> getCompositeScoreSnapshot(
            @RequestParam(required = false) String academicYear,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo) {
        Map<String, Object> result = new HashMap<>();

        try {
            Optional<Student> studentOpt = Optional.empty();
            if (userId != null && !userId.isBlank()) {
                studentOpt = studentService.findByUserId(Long.parseLong(userId));
            } else if (headerStudentNo != null && !headerStudentNo.isBlank()) {
                studentOpt = studentService.findByStudentNo(headerStudentNo);
            }

            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "未找到学生信息"
                ));
            }

            result.put("code", 200);
            result.put("data", adminCompositeScoreService
                    .getStudentSnapshot(studentOpt.get().getStudentNo(), academicYear)
                    .orElse(null));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询综测快照失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/composite-score/extra-details")
    public ResponseEntity<Map<String, Object>> getCompositeExtraScoreDetails(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String studentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo) {
        Map<String, Object> result = new HashMap<>();

        try {
            Optional<Student> studentOpt = resolveCurrentStudent(userId, headerStudentNo);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "未找到学生信息"
                ));
            }
            Student detailStudent = resolveVisibleCompositeDetailStudent(studentOpt.get(), studentNo);

            result.put("code", 200);
            result.put("data", adminCompositeScoreService.listExtraScoreDetails(
                    detailStudent.getStudentNo(),
                    academicYear,
                    category,
                    null
            ));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询附加分明细失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/composite-score/base-details")
    public ResponseEntity<Map<String, Object>> getCompositeBaseScoreDetails(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String studentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo) {
        Map<String, Object> result = new HashMap<>();

        try {
            Optional<Student> studentOpt = resolveCurrentStudent(userId, headerStudentNo);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "未找到学生信息"
                ));
            }
            Student detailStudent = resolveVisibleCompositeDetailStudent(studentOpt.get(), studentNo);

            result.put("code", 200);
            result.put("data", adminCompositeScoreService.listBaseScoreDetails(
                    detailStudent.getStudentNo(),
                    academicYear,
                    category,
                    null
            ));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询基础分明细失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/second-class-scores")
    public ResponseEntity<Map<String, Object>> getSecondClassScores(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false, defaultValue = "class") String scope,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo) {
        Map<String, Object> result = new HashMap<>();

        try {
            Optional<Student> studentOpt = resolveCurrentStudent(userId, headerStudentNo);

            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "未找到学生信息"
                ));
            }

            String normalizedYear = academicYear == null ? "" : academicYear.trim();
            Student currentStudent = studentOpt.get();
            String normalizedScope = scope == null ? "class" : scope.trim();
            String gradeScopeKey = inferGradeScopeKey(currentStudent);
            List<Student> visibleStudents;
            if ("self".equalsIgnoreCase(normalizedScope)) {
                visibleStudents = List.of(currentStudent);
            } else {
                visibleStudents = studentService.findAll().stream()
                        .filter(item -> "grade".equals(normalizedScope)
                                ? !gradeScopeKey.isBlank() && Objects.equals(inferGradeScopeKey(item), gradeScopeKey)
                                : Objects.equals(item.getClassName(), currentStudent.getClassName()))
                        .toList();
            }
            Map<Long, Student> studentMap = visibleStudents.stream()
                    .collect(Collectors.toMap(Student::getId, item -> item, (left, right) -> left));

            List<Map<String, Object>> rows = moralEducationService.findByStudentIdIn(new ArrayList<>(studentMap.keySet())).stream()
                    .filter(score -> score.getSecondClassScore() != null)
                    .filter(score -> normalizedYear.isBlank() || normalizedYear.equals(resolveMoralAcademicYear(score)))
                    .filter(score -> studentMap.containsKey(score.getStudentId()))
                    .sorted(Comparator.comparing(this::resolveMoralAcademicYear, Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(score -> {
                        Student rowStudent = studentMap.get(score.getStudentId());
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", score.getId());
                        row.put("studentNo", rowStudent == null ? null : rowStudent.getStudentNo());
                        row.put("studentName", rowStudent == null ? null : rowStudent.getName());
                        row.put("className", rowStudent == null ? null : rowStudent.getClassName());
                        row.put("academicYear", resolveMoralAcademicYear(score));
                        row.put("semester", score.getSemester());
                        row.put("secondClassScore", score.getSecondClassScore());
                        row.put("convertedMoralScore", score.getActivityScore());
                        row.put("moralTotalScore", score.getTotalScore());
                        row.put("updatedAt", score.getUpdatedAt());
                        return row;
                    })
                    .toList();

            result.put("code", 200);
            result.put("data", rows);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询二课成绩失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/profile/email/bind")
    public ResponseEntity<Map<String, Object>> bindEmail(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> data = studentEmailBindingService.bindEmail(
                    parseUserId(userId),
                    stringValue(request.get("email")),
                    stringValue(request.get("code"))
            );
            return ResponseEntity.ok(Map.of("code", 200, "message", "邮箱绑定成功", "data", data));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", exception.getMessage()));
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "邮箱绑定失败：" + exception.getMessage()));
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> buildStudentInfoPayload(Student student) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", student.getId());
        data.put("studentNo", student.getStudentNo());
        data.put("name", student.getName());
        data.put("className", student.getClassName());
        data.put("teacherId", student.getTeacherId());
        data.put("phone", student.getPhone());
        data.put("userId", student.getUserId());
        data.put("classCommittee", student.getClassCommittee());
        data.put("createdAt", student.getCreatedAt());
        data.put("updatedAt", student.getUpdatedAt());

        if (student.getClassName() == null || student.getClassName().isBlank()) {
            return data;
        }

        classInfoRepository.findByName(student.getClassName().trim()).ifPresent(classInfo -> {
            data.put("classId", classInfo.getId());
            data.put("grade", classInfo.getGrade());
            data.put("majorId", classInfo.getMajorId());
            data.put("collegeId", classInfo.getCollegeId());

            majorRepository.findById(classInfo.getMajorId()).ifPresent(major -> {
                data.put("majorName", major.getName());
                data.put("major", major.getName());
                Long collegeId = major.getCollegeId() != null ? major.getCollegeId() : classInfo.getCollegeId();
                if (collegeId != null) {
                    collegeRepository.findById(collegeId).ifPresent(college -> {
                        data.put("collegeName", college.getName());
                        data.put("college", college.getName());
                    });
                }
            });

            if (!data.containsKey("collegeName") && classInfo.getCollegeId() != null) {
                collegeRepository.findById(classInfo.getCollegeId()).ifPresent(college -> {
                    data.put("collegeName", college.getName());
                    data.put("college", college.getName());
                });
            }
        });

        return data;
    }

    /**
     * 获取我的成绩列表
     */
    @GetMapping("/scores")
    public ResponseEntity<Map<String, Object>> getMyScores(
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) String semester,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo) {

        Map<String, Object> result = new HashMap<>();

        try {
            Optional<Student> studentOpt;

            studentOpt = resolveCurrentStudent(userId, headerStudentNo);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "未找到当前登录学生"
                ));
            }

            if (studentOpt.isEmpty()) {
                result.put("code", 200);
                result.put("data", new ArrayList<>());
                return ResponseEntity.ok(result);
            }

            Student student = studentOpt.get();
            List<Map<String, Object>> scores = new ArrayList<>();

            // 查询德育成绩
            List<MoralEducation> moralScores = moralEducationService.findByStudentId(student.getId());
            for (MoralEducation moral : moralScores) {
                if (semester == null || semester.isEmpty() || semester.equals(moral.getSemester())) {
                    Map<String, Object> scoreMap = new HashMap<>();
                    scoreMap.put("id", moral.getId());
                    scoreMap.put("subjectName", "德育");
                    scoreMap.put("semester", moral.getSemester());
                    scoreMap.put("score", moral.getTotalScore());
                    scoreMap.put("detail", Map.of(
                        "activity", moral.getActivityScore(),
                        "bonus", moral.getBonusScore()
                    ));
                    scores.add(scoreMap);
                }
            }

            // 查询智育成绩
            List<IntellectualEducation> intellectualScores = intellectualEducationService.findByStudentId(student.getId());
            for (IntellectualEducation intellectual : intellectualScores) {
                if (semester == null || semester.isEmpty() || semester.equals(intellectual.getSemester())) {
                    Map<String, Object> scoreMap = new HashMap<>();
                    scoreMap.put("id", intellectual.getId());
                    scoreMap.put("subjectName", "智育");
                    scoreMap.put("semester", intellectual.getSemester());
                    scoreMap.put("score", intellectual.getTotalScore());
                    scoreMap.put("detail", Map.of(
                        "course", intellectual.getCourseScore(),
                        "bonus", intellectual.getBonusScore()
                    ));
                    scores.add(scoreMap);
                }
            }

            // 查询体育成绩
            List<PhysicalEducation> physicalScores = physicalEducationService.findByStudentId(student.getId());
            for (PhysicalEducation physical : physicalScores) {
                if (semester == null || semester.isEmpty() || semester.equals(physical.getSemester())) {
                    Map<String, Object> scoreMap = new HashMap<>();
                    scoreMap.put("id", physical.getId());
                    scoreMap.put("subjectName", "体育");
                    scoreMap.put("semester", physical.getSemester());
                    scoreMap.put("score", physical.getTotalScore());
                    scoreMap.put("detail", Map.of(
                        "raw", physical.getRawScore(),
                        "percentage", physical.getPercentage(),
                        "bonus", physical.getBonusScore()
                    ));
                    scores.add(scoreMap);
                }
            }

            result.put("code", 200);
            result.put("data", scores);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 获取成绩趋势（按学期）：德育 / 智育 / 体育分项与综合均分。
     */
    @GetMapping("/scores/trend/{studentId}")
    public ResponseEntity<Map<String, Object>> getScoreTrend(
            @PathVariable Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Optional<Student> currentStudent = studentService.findByUserId(parseUserId(userId) == null ? -1L : parseUserId(userId));
            if (currentStudent.isEmpty() || !Objects.equals(currentStudent.get().getId(), studentId)) {
                return ResponseEntity.status(403).body(Map.of("code", 403, "message", "只能查看自己的成绩趋势"));
            }

            Set<String> semesterSet = new LinkedHashSet<>();
            moralEducationService.findByStudentId(studentId).forEach(m -> {
                if (m.getSemester() != null && !m.getSemester().isBlank()) {
                    semesterSet.add(m.getSemester().trim());
                }
            });
            intellectualEducationService.findByStudentId(studentId).forEach(item -> {
                if (item.getSemester() != null && !item.getSemester().isBlank()) {
                    semesterSet.add(item.getSemester().trim());
                }
            });
            physicalEducationService.findByStudentId(studentId).forEach(item -> {
                if (item.getSemester() != null && !item.getSemester().isBlank()) {
                    semesterSet.add(item.getSemester().trim());
                }
            });

            List<String> semesters = new ArrayList<>(semesterSet);
            semesters.sort(String::compareTo);

            List<Double> trendData = new ArrayList<>();
            List<Map<String, Object>> points = new ArrayList<>();

            for (String semester : semesters) {
                Double moralScore = moralEducationService.findByStudentIdAndSemester(studentId, semester)
                        .map(MoralEducation::getTotalScore)
                        .orElse(null);
                Double intellectualScore = intellectualEducationService.findByStudentIdAndSemester(studentId, semester)
                        .map(IntellectualEducation::getTotalScore)
                        .orElse(null);
                Double physicalScore = physicalEducationService.findByStudentIdAndSemester(studentId, semester)
                        .map(PhysicalEducation::getTotalScore)
                        .orElse(null);

                List<Double> available = new ArrayList<>();
                if (moralScore != null) {
                    available.add(moralScore);
                }
                if (intellectualScore != null) {
                    available.add(intellectualScore);
                }
                if (physicalScore != null) {
                    available.add(physicalScore);
                }
                Double overall = available.isEmpty()
                        ? null
                        : round(available.stream().mapToDouble(Double::doubleValue).average().orElse(0D));

                Map<String, Object> point = new LinkedHashMap<>();
                point.put("semester", semester);
                point.put("overall", overall);
                point.put("moral", moralScore == null ? null : round(moralScore));
                point.put("intellectual", intellectualScore == null ? null : round(intellectualScore));
                point.put("physical", physicalScore == null ? null : round(physicalScore));
                points.add(point);
                trendData.add(overall == null ? 0D : overall);
            }

            // 有综测快照的学年与成绩快照同源合并，避免学期表只写了德育时曲线缺智育/体育
            points = adminCompositeScoreService.mergeSemesterTrendWithSnapshots(
                    currentStudent.get().getStudentNo(), points);
            semesters = points.stream()
                    .map(point -> String.valueOf(point.getOrDefault("semester", "")))
                    .filter(item -> !item.isBlank())
                    .collect(Collectors.toCollection(ArrayList::new));
            trendData = points.stream()
                    .map(point -> {
                        Object overall = point.get("overall");
                        return overall instanceof Number ? ((Number) overall).doubleValue() : 0D;
                    })
                    .collect(Collectors.toCollection(ArrayList::new));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("points", points);
            payload.put("averages", trendData);
            payload.put("semesters", semesters);

            result.put("code", 200);
            result.put("data", payload);
            result.put("semesters", semesters);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 获取班级统计信息（班委专用）
     */
    @GetMapping("/class/stats/{className}")
    public ResponseEntity<Map<String, Object>> getClassStatistics(
            @PathVariable String className,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Class-Name", required = false) String headerClassName) {
        Map<String, Object> result = new HashMap<>();

        try {
            Optional<Student> currentStudent = resolveCurrentStudent(userId, null);
            if (currentStudent.isEmpty() || !Objects.equals(
                    normalizeClassName(currentStudent.get().getClassName()),
                    normalizeClassName(className))) {
                return ResponseEntity.status(403).body(Map.of("code", 403, "message", "只能查看本班统计"));
            }
            // 获取班级所有学生
            List<Student> students = studentService.findByClassName(className);

            if (students.isEmpty()) {
                result.put("code", 200);
                result.put("data", Map.of(
                    "classAverage", 0,
                    "maxScore", 0,
                    "minScore", 0,
                    "passRate", 0,
                    "excellentRate", 0,
                    "totalStudents", 0
                ));
                return ResponseEntity.ok(result);
            }

            // 计算班级统计
            List<Double> allScores = new ArrayList<>();
            int passCount = 0;
            int excellentCount = 0;
            double maxScore = 0;
            double minScore = 100;

            for (Student student : students) {
                double studentAvg = 0;
                int scoreCount = 0;

                // 德育
                Optional<MoralEducation> moral = moralEducationService.findByStudentId(student.getId())
                    .stream().findFirst();
                if (moral.isPresent() && moral.get().getTotalScore() != null) {
                    studentAvg += moral.get().getTotalScore();
                    scoreCount++;
                }

                // 智育
                Optional<IntellectualEducation> intellectual = intellectualEducationService.findByStudentId(student.getId())
                    .stream().findFirst();
                if (intellectual.isPresent() && intellectual.get().getTotalScore() != null) {
                    studentAvg += intellectual.get().getTotalScore();
                    scoreCount++;
                }

                // 体育
                Optional<PhysicalEducation> physical = physicalEducationService.findByStudentId(student.getId())
                    .stream().findFirst();
                if (physical.isPresent() && physical.get().getTotalScore() != null) {
                    studentAvg += physical.get().getTotalScore();
                    scoreCount++;
                }

                if (scoreCount > 0) {
                    studentAvg /= scoreCount;
                    allScores.add(studentAvg);

                    if (studentAvg >= 60) passCount++;
                    if (studentAvg >= 90) excellentCount++;
                    if (studentAvg > maxScore) maxScore = studentAvg;
                    if (studentAvg < minScore) minScore = studentAvg;
                }
            }

            double classAverage = allScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);

            int totalStudents = students.size();
            double passRate = totalStudents > 0 ? (passCount * 100.0 / totalStudents) : 0;
            double excellentRate = totalStudents > 0 ? (excellentCount * 100.0 / totalStudents) : 0;

            result.put("code", 200);
            result.put("data", Map.of(
                "classAverage", String.format("%.1f", classAverage),
                "maxScore", String.format("%.1f", maxScore),
                "minScore", String.format("%.1f", minScore),
                "passRate", String.format("%.1f", passRate),
                "excellentRate", String.format("%.1f", excellentRate),
                "totalStudents", totalStudents
            ));
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/leaderboards/class")
    public ResponseEntity<Map<String, Object>> getClassLeaderboard(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false, defaultValue = "false") boolean includeExtraScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            String resolvedClassName = classId == null
                    ? (className == null ? "" : className.trim())
                    : classInfoService.findById(classId)
                        .map(ClassInfo::getName)
                        .orElse("");
            if (resolvedClassName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 400,
                        "message", "请提供有效班级"
                ));
            }
            Optional<Student> currentStudent = resolveCurrentStudent(userId, headerStudentNo);
            if (currentStudent.isEmpty() || !Objects.equals(
                    normalizeClassName(currentStudent.get().getClassName()),
                    normalizeClassName(resolvedClassName))) {
                return ResponseEntity.status(403).body(Map.of("code", 403, "message", "只能查看本班排行榜"));
            }
            List<Student> students = studentService.findByClassName(resolvedClassName);
            if (students.isEmpty()) {
                result.put("code", 200);
                result.put("data", buildPage(List.of(), page, size, headerStudentNo));
                return ResponseEntity.ok(result);
            }

            result.put("code", 200);
            result.put("data", buildPage(buildLeaderboard(students, year, semester, includeExtraScore), page, size, headerStudentNo));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/class/leaderboard/{className}")
    public ResponseEntity<Map<String, Object>> getLegacyClassLeaderboard(
            @PathVariable String className,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false, defaultValue = "false") boolean includeExtraScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return getClassLeaderboard(null, className, year, semester, includeExtraScore, page, size, headerStudentNo, userId);
    }

    @GetMapping("/leaderboards/grade")
    public ResponseEntity<Map<String, Object>> getGradeLeaderboard(
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false, defaultValue = "false") boolean includeExtraScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Optional<Student> studentOpt = resolveCurrentStudent(userId, headerStudentNo);
            String resolvedStudentNo = studentOpt.map(Student::getStudentNo).orElse(null);

            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "未找到学生信息"
                ));
            }

            String gradeScopeKey = inferGradeScopeKey(studentOpt.get());
            if (gradeScopeKey.isBlank()) {
                result.put("code", 200);
                result.put("data", buildPage(List.of(), page, size, resolvedStudentNo));
                return ResponseEntity.ok(result);
            }

            List<Student> gradeStudents = studentService.findAll().stream()
                    .filter(item -> Objects.equals(inferGradeScopeKey(item), gradeScopeKey))
                    .toList();

            result.put("code", 200);
            result.put("data", buildPage(buildLeaderboard(gradeStudents, year, semester, includeExtraScore), page, size, resolvedStudentNo));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/grade/leaderboard")
    public ResponseEntity<Map<String, Object>> getLegacyGradeLeaderboard(
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false, defaultValue = "false") boolean includeExtraScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return getGradeLeaderboard(studentNo, year, semester, includeExtraScore, page, size, headerStudentNo, userId);
    }

    @GetMapping("/home/summary")
    public ResponseEntity<Map<String, Object>> getHomeSummary(
            @RequestParam(required = false) String studentNo,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Optional<Student> studentOpt = resolveCurrentStudent(userId, headerStudentNo);

            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "未找到学生信息"
                ));
            }

            Student student = studentOpt.get();
            String gradeKey = inferGradeKey(student);
            String gradeScopeKey = inferGradeScopeKey(student);
            List<Student> gradeStudents = studentService.findAll().stream()
                    .filter(item -> Objects.equals(inferGradeScopeKey(item), gradeScopeKey))
                    .toList();

            List<Map<String, Object>> rankingRows = new ArrayList<>();
            for (Student item : gradeStudents) {
                double totalScore = calculateStudentOverallScore(item);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("studentNo", item.getStudentNo());
                row.put("studentName", item.getName());
                row.put("className", item.getClassName());
                row.put("totalScore", totalScore);
                rankingRows.add(row);
            }

            rankingRows.sort((left, right) -> Double.compare(
                    ((Number) right.get("totalScore")).doubleValue(),
                    ((Number) left.get("totalScore")).doubleValue()
            ));

            int currentRank = 0;
            for (int i = 0; i < rankingRows.size(); i++) {
                rankingRows.get(i).put("rank", i + 1);
                if (Objects.equals(rankingRows.get(i).get("studentNo"), student.getStudentNo())) {
                    currentRank = i + 1;
                }
            }

            double averageScore = rankingRows.stream()
                    .mapToDouble(item -> ((Number) item.get("totalScore")).doubleValue())
                    .average()
                    .orElse(0D);

            String teacherName = userService.findById(student.getTeacherId() == null ? -1L : student.getTeacherId())
                    .map(user -> Optional.ofNullable(user.getNickname()).filter(v -> !v.isBlank()).orElse(user.getUsername()))
                    .orElse("未分配老师");

            List<Map<String, Object>> teacherReports = studentAnalysisRecordRepository
                    .findByStudentNoOrderByGeneratedAtDesc(student.getStudentNo()).stream()
                    .limit(5)
                    .map(record -> {
                        Map<String, Object> report = new LinkedHashMap<>();
                        report.put("id", record.getId());
                        report.put("title", (record.getPrimaryType() == null || record.getPrimaryType().isBlank() ? "发展建议" : record.getPrimaryType()) + "专项报告");
                        report.put("summary", record.getSummary());
                        report.put("generatedAt", record.getGeneratedAt());
                        report.put("source", record.getSource());
                        report.put("modelName", record.getModelName());
                        report.put("teacherName", teacherName);
                        return report;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("gradeKey", gradeKey);
            data.put("gradeRank", currentRank);
            data.put("gradeStudentCount", rankingRows.size());
            data.put("gradeAverageScore", averageScore);
            data.put("teacherName", teacherName);
            data.put("teacherReports", teacherReports);
            data.put("topStudents", rankingRows.stream().limit(5).collect(Collectors.toList()));

            result.put("code", 200);
            result.put("data", data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "学生首页汇总查询失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private boolean matchesScope(String value, String year, String semester) {
        if (semester != null && !semester.isBlank()) {
            return semester.trim().equals(value);
        }
        if (year != null && !year.isBlank()) {
            return value != null && value.contains(year.trim());
        }
        return true;
    }

    /**
     * 更新学生信息
     */
    @PutMapping("/profile/{id}")
    public ResponseEntity<Map<String, Object>> updateStudent(
            @PathVariable Long id,
            @RequestBody Map<String, String> updates,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        Map<String, Object> result = new HashMap<>();

        try {
            Optional<Student> studentOpt = studentService.findById(id);
            if (studentOpt.isEmpty()) {
                result.put("code", 404);
                result.put("message", "学生不存在");
                return ResponseEntity.badRequest().body(result);
            }
            Optional<Student> currentStudent = resolveCurrentStudent(userId, null);
            if (currentStudent.isEmpty() || !Objects.equals(currentStudent.get().getId(), id)) {
                return ResponseEntity.status(403).body(Map.of("code", 403, "message", "只能修改自己的资料"));
            }

            Student student = studentOpt.get();

            // 允许更新的字段
            if (updates.containsKey("name")) {
                student.setName(updates.get("name"));
            }
            if (updates.containsKey("phone")) {
                student.setPhone(updates.get("phone"));
            }
            if (updates.containsKey("email")) {
                // 设置邮箱到关联的User，而非Student
                String email = updates.get("email");
                if (student.getUserId() != null) {
                    userService.findById(student.getUserId()).ifPresent(user -> {
                        user.setEmail(email);
                        userService.save(user);
                    });
                }
            }

            studentService.save(student);

            result.put("code", 200);
            result.put("message", "更新成功");
            result.put("data", student);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "更新失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/profile/avatar")
    public ResponseEntity<Map<String, Object>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Student-No", required = false) String studentNo) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 400,
                        "message", "请选择头像图片"
                ));
            }
            String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
            if (!contentType.equals("image/jpeg")
                    && !contentType.equals("image/png")
                    && !contentType.equals("image/webp")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 400,
                        "message", "头像仅支持 JPG、PNG、WEBP 图片"
                ));
            }
            if (file.getSize() > 5L * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 400,
                        "message", "头像图片不能超过 5MB"
                ));
            }

            Optional<Student> studentOpt = resolveStudentForProfile(userId, studentNo);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "未找到学生信息"
                ));
            }
            Student student = studentOpt.get();
            if (student.getUserId() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 400,
                        "message", "当前学生未绑定登录账号"
                ));
            }

            String avatarUrl = proofFileStorageService.upload(file, "avatars", resolveImageExtension(file.getOriginalFilename(), contentType));
            Optional<User> userOpt = userService.findById(student.getUserId());
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "未找到登录账号"
                ));
            }
            User user = userOpt.get();
            user.setAvatarUrl(avatarUrl);
            userService.save(user);

            result.put("code", 200);
            result.put("message", "头像上传成功");
            result.put("data", Map.of("avatarUrl", avatarUrl));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "头像上传失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/analysis/payload")
    public ResponseEntity<Map<String, Object>> getAnalysisPayload(
            @RequestParam(required = false) String studentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long parsedUserId = parseUserId(userId);
            Optional<StudentAnalysisPayloadDTO> payload = studentAnalysisService.buildPayload(studentNo, parsedUserId);
            if (payload.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "未找到学生画像数据"
                ));
            }

            result.put("code", 200);
            result.put("data", payload.get());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "画像数据查询失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private Optional<Student> resolveStudentForProfile(String userId, String studentNo) {
        Long parsedUserId = parseUserId(userId);
        if (parsedUserId != null) {
            Optional<Student> currentStudent = studentService.findByUserId(parsedUserId);
            if (currentStudent.isPresent()) {
                return currentStudent;
            }
        }
        return studentNo == null || studentNo.isBlank()
                ? Optional.empty()
                : studentService.findByStudentNo(studentNo);
    }

    private Optional<Student> resolveCurrentStudent(String userId, String studentNo) {
        Long parsedUserId = parseUserId(userId);
        if (parsedUserId != null) {
            Optional<Student> studentOpt = studentService.findByUserId(parsedUserId);
            if (studentOpt.isPresent()) {
                return studentOpt;
            }
        }
        if (studentNo != null && !studentNo.isBlank()) {
            return studentService.findByStudentNo(studentNo);
        }
        return Optional.empty();
    }

    private Student resolveVisibleCompositeDetailStudent(Student currentStudent, String targetStudentNo) {
        String normalizedTarget = targetStudentNo == null ? "" : targetStudentNo.trim();
        if (normalizedTarget.isBlank() || Objects.equals(normalizedTarget, currentStudent.getStudentNo())) {
            return currentStudent;
        }

        Student targetStudent = studentService.findByStudentNo(normalizedTarget)
                .orElseThrow(() -> new IllegalArgumentException("未找到要查看的学生"));
        boolean sameClass = Objects.equals(
                normalizeClassName(currentStudent.getClassName()),
                normalizeClassName(targetStudent.getClassName())
        );
        String currentGradeScope = inferGradeScopeKey(currentStudent);
        boolean sameGradeScope = !currentGradeScope.isBlank()
                && Objects.equals(currentGradeScope, inferGradeScopeKey(targetStudent));
        if (!sameClass && !sameGradeScope) {
            throw new IllegalArgumentException("只能查看同班或同年级榜单中的学生细则");
        }
        return targetStudent;
    }

    private String normalizeClassName(String className) {
        return className == null ? "" : className.trim();
    }

    private String resolveImageExtension(String filename, String contentType) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (name.endsWith(".png")) {
            return ".png";
        }
        if (name.endsWith(".webp")) {
            return ".webp";
        }
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        return ".jpg";
    }

    @GetMapping("/analysis/profile")
    public ResponseEntity<Map<String, Object>> getAnalysisProfile(
            @RequestParam(required = false) String studentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long parsedUserId = parseUserId(userId);
            Optional<StudentAnalysisResultDTO> analysis = studentAnalysisService.getLatestAnalysis(studentNo, parsedUserId);
            result.put("code", 200);
            result.put("data", analysis.orElse(null));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "学生画像分析失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/analysis/profile/history")
    public ResponseEntity<Map<String, Object>> getAnalysisHistory(
            @RequestParam(required = false) String studentNo,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String resolvedStudentNo = resolveCurrentStudent(userId, headerStudentNo)
                .map(Student::getStudentNo)
                .orElse("");
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", studentAnalysisService.getAnalysisHistory(resolvedStudentNo)
        ));
    }

    @GetMapping("/academic-years")
    public ResponseEntity<Map<String, Object>> getAcademicYears() {
        List<AcademicYear> years = academicYearService.findAll();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", years
        ));
    }

    @GetMapping("/analysis/portrait-match")
    public ResponseEntity<Map<String, Object>> getPortraitMatch(
            @RequestParam(required = false) String studentNo,
            @RequestHeader(value = "X-Student-No", required = false) String headerStudentNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String resolvedStudentNo = resolveCurrentStudent(userId, headerStudentNo)
                .map(Student::getStudentNo)
                .orElse("");

        try {
            Long parsedUserId = parseUserId(userId);
            Optional<StudentPortraitMatchResultDTO> result = studentPortraitMatchService.getPortraitMatch(resolvedStudentNo, parsedUserId);
            if (result.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 404,
                        "message", "未找到学生画像匹配度数据"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "data", result.get()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "学生画像匹配度分析失败：" + e.getMessage()
            ));
        }
    }

    @PostMapping("/password/change")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long parsedUserId = parseUserId(userId);
            if (parsedUserId == null) {
                result.put("code", 400);
                result.put("message", "用户未登录");
                return ResponseEntity.badRequest().body(result);
            }

            String oldPassword = request.get("oldPassword");
            String newPassword = request.get("newPassword");

            String error = userService.changePassword(parsedUserId, oldPassword, newPassword);
            if (error != null) {
                result.put("code", 400);
                result.put("message", error);
                return ResponseEntity.badRequest().body(result);
            }

            result.put("code", 200);
            result.put("message", "密码修改成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "密码修改失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return Long.parseLong(userId);
    }

    private String inferGradeKey(Student student) {
        if (student == null) {
            return "";
        }
        return resolveGradeFromClass(student.getClassName());
    }

    private String inferGradeScopeKey(Student student) {
        if (student == null || student.getClassName() == null || student.getClassName().isBlank()) {
            return "";
        }

        return classInfoService.findByName(student.getClassName().trim())
                .map(classInfo -> {
                    String grade = classInfo.getGrade() == null ? "" : classInfo.getGrade().trim();
                    Long majorId = classInfo.getMajorId();
                    return grade.isBlank() || majorId == null ? "" : grade + "|" + majorId;
                })
                .orElse("");
    }

    private String resolveGradeFromClass(String className) {
        if (className == null || className.isBlank()) {
            return "";
        }

        return classInfoService.findByName(className.trim())
                .map(ClassInfo::getGrade)
                .filter(Objects::nonNull)
                .map(String::trim)
                .orElse("");
    }

    private List<Map<String, Object>> buildLeaderboard(List<Student> students, String year, String semester, boolean includeExtraScore) {
        String academicYear = resolveLeaderboardAcademicYear(year, semester);
        if (academicYear.isBlank()) {
            return List.of();
        }

        Set<String> visibleStudentNos = students.stream()
                .map(Student::getStudentNo)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Map<String, Object>> leaderboard = adminCompositeScoreService
                .listCompositeScores(academicYear, null, null, null)
                .stream()
                .filter(row -> visibleStudentNos.contains(String.valueOf(row.get("studentNo"))))
                .map(this::toStudentLeaderboardRow)
                .collect(Collectors.toCollection(ArrayList::new));

        leaderboard.sort((left, right) -> Double.compare(
                ((Number) right.get("totalScore")).doubleValue(),
                ((Number) left.get("totalScore")).doubleValue()
        ));

        for (int index = 0; index < leaderboard.size(); index++) {
            leaderboard.get(index).put("rank", index + 1);
        }
        return leaderboard;
    }

    private String resolveLeaderboardAcademicYear(String year, String semester) {
        if (year != null && !year.isBlank()) {
            return year.trim();
        }
        return semester == null ? "" : semester.trim();
    }

    private Map<String, Object> toStudentLeaderboardRow(Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>(source);
        double moralBaseScore = resolveNumber(source.get("weightedMoralBaseScore"), source.get("moralBaseScore"));
        double moralExtraScore = resolveNumber(source.get("weightedMoralExtraScore"), source.get("moralExtraScore"));
        double intellectualBaseScore = resolveNumber(source.get("weightedIntellectualBaseScore"), source.get("intellectualBaseScore"));
        double intellectualExtraScore = resolveNumber(source.get("weightedIntellectualExtraScore"), source.get("intellectualExtraScore"));
        double physicalBaseScore = resolveNumber(source.get("weightedPhysicalBaseScore"), source.get("physicalBaseScore"));
        double physicalExtraScore = resolveNumber(source.get("weightedPhysicalExtraScore"), source.get("physicalExtraScore"));

        row.put("moralBaseScore", moralBaseScore);
        row.put("moralExtraScore", moralExtraScore);
        row.put("moralTotalScore", round(moralBaseScore + moralExtraScore));
        row.put("intellectualBaseScore", intellectualBaseScore);
        row.put("intellectualExtraScore", intellectualExtraScore);
        row.put("intellectualTotalScore", round(intellectualBaseScore + intellectualExtraScore));
        row.put("physicalBaseScore", physicalBaseScore);
        row.put("physicalExtraScore", physicalExtraScore);
        row.put("physicalTotalScore", round(physicalBaseScore + physicalExtraScore));
        row.put("totalScore", round(moralBaseScore + moralExtraScore
                + intellectualBaseScore + intellectualExtraScore
                + physicalBaseScore + physicalExtraScore));
        return row;
    }

    private double resolveNumber(Object preferred, Object fallback) {
        Object value = preferred != null ? preferred : fallback;
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Map<String, Object> buildPage(List<Map<String, Object>> rows, int page, int size, String currentStudentNo) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int total = rows == null ? 0 : rows.size();
        int fromIndex = Math.min(safePage * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);
        String normalizedStudentNo = currentStudentNo == null ? "" : currentStudentNo.trim();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows == null ? List.of() : rows.subList(fromIndex, toIndex));
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("current", normalizedStudentNo.isBlank() || rows == null
                ? null
                : rows.stream()
                    .filter(row -> normalizedStudentNo.equals(String.valueOf(row.get("studentNo"))))
                    .findFirst()
                    .orElse(null));
        return result;
    }

    private String resolveMoralAcademicYear(MoralEducation score) {
        if (score == null) {
            return "";
        }
        if (score.getAcademicYear() != null && !score.getAcademicYear().isBlank()) {
            return score.getAcademicYear();
        }
        return score.getSemester() == null ? "" : score.getSemester();
    }

    private double calculateStudentOverallScore(Student student) {
        if (student == null || student.getId() == null) {
            return 0D;
        }

        double courseAverage = subjectScoreRAINRepository.findByStudentId(student.getId()).stream()
                .map(SubjectScoreRAIN::getScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);
        if (courseAverage > 0) {
            return courseAverage;
        }

        List<Double> compositeScores = new ArrayList<>();
        moralEducationService.findByStudentId(student.getId()).stream()
                .map(MoralEducation::getTotalScore)
                .filter(Objects::nonNull)
                .forEach(compositeScores::add);
        intellectualEducationService.findByStudentId(student.getId()).stream()
                .map(IntellectualEducation::getTotalScore)
                .filter(Objects::nonNull)
                .forEach(compositeScores::add);
        physicalEducationService.findByStudentId(student.getId()).stream()
                .map(PhysicalEducation::getTotalScore)
                .filter(Objects::nonNull)
                .forEach(compositeScores::add);
        return compositeScores.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }
}
