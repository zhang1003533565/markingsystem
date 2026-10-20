package com.markingsystem.service;

import com.markingsystem.dto.ScoreReverseRequest;
import com.markingsystem.dto.ScoreSimulateRequest;
import com.markingsystem.entity.CompositeScoreSnapshot;
import com.markingsystem.entity.CompositeScoreWeight;
import com.markingsystem.entity.ExtraScoreApplication;
import com.markingsystem.entity.MoralEducation;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.SubjectRAIN;
import com.markingsystem.entity.SubjectScoreRAIN;
import com.markingsystem.repository.CompositeScoreSnapshotRepository;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.MoralEducationRepository;
import com.markingsystem.repository.SubjectScoreRAINRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreSimulationServiceTest {

    private static final String YEAR = "2024-2025";
    private static final String CLASS_NAME = "计科2301";

    @Mock
    private StudentService studentService;
    @Mock
    private CompositeScoreWeightService compositeScoreWeightService;
    @Mock
    private CompositeSubjectConfigService compositeSubjectConfigService;
    @Mock
    private CompositePhysicalFullScoreRuleService compositePhysicalFullScoreRuleService;
    @Mock
    private SubjectScoreRAINRepository subjectScoreRAINRepository;
    @Mock
    private MoralEducationRepository moralEducationRepository;
    @Mock
    private ExtraScoreApplicationRepository extraScoreApplicationRepository;
    @Mock
    private ExtraScoreEffectiveScoreService effectiveScoreService;
    @Mock
    private CompositeScoreSnapshotRepository compositeScoreSnapshotRepository;

    @InjectMocks
    private ScoreSimulationService service;

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private double num(Map<String, Object> m, String key) {
        return ((Number) m.get(key)).doubleValue();
    }

    @BeforeEach
    void setUp() {
        Student student = new Student();
        student.setId(1L);
        student.setStudentNo("S001");
        student.setClassName(CLASS_NAME);
        when(studentService.findByStudentNo("S001")).thenReturn(Optional.of(student));

        // 权重全部为 1（factor=1），便于手工核算。
        when(compositeScoreWeightService.getCurrentWeight()).thenReturn(new CompositeScoreWeight());
        when(compositeSubjectConfigService.getCategoryMapByClassName(anyString(), anyString())).thenReturn(Map.of(
                10L, "INTELLECTUAL",
                11L, "INTELLECTUAL",
                12L, "PHYSICAL"
        ));
        when(compositePhysicalFullScoreRuleService.isFullScoreEnabled(anyString(), anyString())).thenReturn(false);

        // 三门课程：数学80、英语90（智育），体育70。
        when(subjectScoreRAINRepository.findByStudentIdInWithDetails(anyList())).thenReturn(List.of(
                subject(10L, "数学", 80D),
                subject(11L, "英语", 90D),
                subject(12L, "体育", 70D)
        ));

        // 德育均分 88。
        MoralEducation moral = new MoralEducation();
        moral.setStudentId(1L);
        moral.setTotalScore(88D);
        moral.setAcademicYear(YEAR);
        when(moralEducationRepository.findByStudentIdIn(anyList())).thenReturn(List.of(moral));

        // 一条已通过的智育附加分：+5。
        ExtraScoreApplication application = new ExtraScoreApplication();
        application.setId(1L);
        application.setStudentNo("S001");
        application.setScoreType("INTELLECTUAL");
        application.setAcademicYear(YEAR);
        when(extraScoreApplicationRepository.findByStudentNoInAndStatus(anyList(), anyString()))
                .thenReturn(List.of(application));
        when(effectiveScoreService.resolveEffectiveSignedScores(any())).thenReturn(Map.of(1L, 5D));

        // 班级快照：三名同学（不含本人），总分 260 / 250 / 200。
        when(compositeScoreSnapshotRepository.findByAcademicYearAndClassNameOrderByRankNoAscStudentNoAsc(YEAR, CLASS_NAME))
                .thenReturn(List.of(
                        peer("P1", 260D),
                        peer("P2", 250D),
                        peer("P3", 200D)
                ));
    }

    @Test
    void baselineTotalAndRankMatchManualFormula() {
        ScoreSimulateRequest request = new ScoreSimulateRequest();
        request.setAcademicYear(YEAR);

        Map<String, Object> result = service.simulate("S001", request);
        Map<String, Object> before = map(result.get("before"));

        // 88 + (80+90)/2 + 70 + 5 = 248
        assertEquals(248D, num(before, "totalScore"), 0.001);
        // 高于本人的同学：260、250 → 第 3 名；班级规模 3+1=4
        assertEquals(3D, ((Number) before.get("rank")).doubleValue(), 0.001);
        assertEquals(4, before.get("classSize"));
    }

    @Test
    void raisingCourseImprovesTotalAndRank() {
        ScoreSimulateRequest request = new ScoreSimulateRequest();
        request.setAcademicYear(YEAR);
        ScoreSimulateRequest.Adjustment adjustment = new ScoreSimulateRequest.Adjustment();
        adjustment.setType("COURSE");
        adjustment.setSubjectId(10L);
        adjustment.setNewScore(100D);
        request.setAdjustments(List.of(adjustment));

        Map<String, Object> result = service.simulate("S001", request);
        Map<String, Object> after = map(result.get("after"));

        // 88 + (100+90)/2 + 70 + 5 = 258；高于本人：260 → 第 2 名
        assertEquals(258D, num(after, "totalScore"), 0.001);
        assertEquals(2D, ((Number) after.get("rank")).doubleValue(), 0.001);
        assertEquals(10D, num(result, "delta"), 0.001);
    }

    @Test
    void reverseExtraSolvesNeededScoreToEnterTopPercent() {
        ScoreReverseRequest request = new ScoreReverseRequest();
        request.setAcademicYear(YEAR);
        request.setTargetPercentile(25D); // 班级前 25% → 目标第 1 名
        request.setSolveFor("EXTRA");
        request.setCategory("INTELLECTUAL");

        Map<String, Object> result = service.reverse("S001", request);

        assertEquals(1, result.get("targetRank"));
        assertEquals(260D, num(result, "thresholdTotal"), 0.001);
        // 需超过 260：248 → 260.01，附加分权重 factor=1，需 +12.01
        assertEquals(12.01D, num(result, "neededExtra"), 0.001);
        assertTrue((Boolean) result.get("feasible"));
        Map<String, Object> projected = map(result.get("projectedAfter"));
        assertEquals(1D, ((Number) projected.get("rank")).doubleValue(), 0.001);
    }

    @Test
    void reverseCourseFlagsInfeasibleWhenExceedsFullScore() {
        ScoreReverseRequest request = new ScoreReverseRequest();
        request.setAcademicYear(YEAR);
        request.setTargetPercentile(25D); // 目标第 1 名，需超过 260
        request.setSolveFor("COURSE");
        request.setSubjectId(10L);

        Map<String, Object> result = service.reverse("S001", request);

        // 需 Δscore = gap*count/factor = 12.01*2/1 = 24.02 → 80+24.02=104.02 > 100
        assertFalse((Boolean) result.get("feasible"));
        assertTrue((Boolean) result.get("cappedAt100"));
        assertEquals(100D, num(result, "achievableScore"), 0.001);
        // 满分也只能到 258 → 第 2 名
        assertEquals(2D, ((Number) map(result.get("projectedAfter")).get("rank")).doubleValue(), 0.001);
    }

    private SubjectScoreRAIN subject(Long subjectId, String name, Double score) {
        SubjectScoreRAIN scoreRAIN = new SubjectScoreRAIN();
        scoreRAIN.setStudentId(1L);
        scoreRAIN.setSubjectId(subjectId);
        scoreRAIN.setScore(score);
        scoreRAIN.setSemester(YEAR + "-1");
        SubjectRAIN subjectRAIN = new SubjectRAIN();
        subjectRAIN.setId(subjectId);
        subjectRAIN.setSubjectName(name);
        scoreRAIN.setSubject(subjectRAIN);
        return scoreRAIN;
    }

    private CompositeScoreSnapshot peer(String studentNo, double totalScore) {
        CompositeScoreSnapshot snapshot = new CompositeScoreSnapshot();
        snapshot.setAcademicYear(YEAR);
        snapshot.setClassName(CLASS_NAME);
        snapshot.setStudentNo(studentNo);
        snapshot.setTotalScore(totalScore);
        return snapshot;
    }
}
