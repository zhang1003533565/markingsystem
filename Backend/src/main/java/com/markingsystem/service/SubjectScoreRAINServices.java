package com.markingsystem.service;

import com.markingsystem.entity.SubjectScoreRAIN;
import com.markingsystem.repository.SubjectScoreRAINRepository;
import com.markingsystem.util.RequestUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SubjectScoreRAINServices {

    @Autowired
    private SubjectScoreRAINRepository subjectScoreRAINRepository;

    /**
     * 加载指定学期的已有成绩
     * @param semester 学期
     * @return 成绩映射（key: userId_subjectId，兼容旧数据时回退到 studentId_subjectId）
     */
    public Map<String, SubjectScoreRAIN> loadExistingScores(String semester) {
        List<SubjectScoreRAIN> scores = subjectScoreRAINRepository.findBySemester(semester);
        Map<String, SubjectScoreRAIN> scoreMap = new HashMap<>();
        for (SubjectScoreRAIN score : scores) {
            String ownerKey = score.getUserId() != null
                    ? "U_" + score.getUserId() + "_" + score.getSubjectId()
                    : "S_" + score.getStudentId() + "_" + score.getSubjectId();
            scoreMap.put(ownerKey, score);

            // 兼容旧数据或旧逻辑，保留 student 维度键，避免历史记录无法命中
            String studentKey = "S_" + score.getStudentId() + "_" + score.getSubjectId();
            scoreMap.putIfAbsent(studentKey, score);
        }
        return scoreMap;
    }

    /**
     * 批量保存成绩
     * @param scores 成绩列表
     */
    @Transactional
    public void batchSave(List<SubjectScoreRAIN> scores) {
        if (scores == null || scores.isEmpty()) {
            return;
        }
        subjectScoreRAINRepository.saveAll(scores);
    }

    /**
     * 根据学生ID查询成绩
     * @param studentId 学生ID
     * @return 成绩列表
     */
    public List<SubjectScoreRAIN> getScoresByStudentId(Long studentId) {
        return subjectScoreRAINRepository.findByStudentId(studentId);
    }

    public List<SubjectScoreRAIN> getScoresByUserId(Long userId) {
        return subjectScoreRAINRepository.findByUserId(userId);
    }

    public List<SubjectScoreRAIN> getVisibleScores(RequestUserContext context) {
        if (context == null) {
            return Collections.emptyList();
        }
        if (context.isAdmin()) {
            return subjectScoreRAINRepository.findAllWithDetails();
        }
        if (context.isTeacher()) {
            Long teacherId = context.getTeacherId() != null ? context.getTeacherId() : context.getUserId();
            if (teacherId == null) {
                return Collections.emptyList();
            }
            return subjectScoreRAINRepository.findByStudentTeacherId(teacherId);
        }
        if (context.isClassCommittee()) {
            if (context.getClassName() == null || context.getClassName().isBlank()) {
                return Collections.emptyList();
            }
            return subjectScoreRAINRepository.findByStudentClassName(context.getClassName());
        }
        if (context.isStudent()) {
            if (context.getUserId() != null) {
                return subjectScoreRAINRepository.findByUserId(context.getUserId());
            }
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    public Map<String, Object> getVisibleScoreSummary(
            RequestUserContext context,
            String academicYear,
            String className,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        String yearFilter = academicYear == null ? "" : academicYear.trim();
        String classFilter = className == null ? "" : className.trim();

        List<SubjectScoreRAIN> filteredScores = getVisibleScores(context).stream()
                .filter(score -> yearFilter.isBlank() || yearFilter.equals(getAcademicYearFromSemester(score.getSemester())))
                .filter(score -> classFilter.isBlank() || classFilter.equals(getClassName(score)))
                .toList();

        Set<String> subjects = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Map<String, Object>> studentRows = new LinkedHashMap<>();

        for (SubjectScoreRAIN score : filteredScores) {
            String studentNo = getStudentNo(score);
            if (studentNo.isBlank()) {
                continue;
            }
            String subjectName = getSubjectName(score);
            if (!subjectName.isBlank()) {
                subjects.add(subjectName);
            }

            Map<String, Object> row = studentRows.computeIfAbsent(studentNo, key -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("studentNo", studentNo);
                item.put("studentName", getStudentName(score));
                item.put("className", getClassName(score));
                item.put("scoresBySubject", new LinkedHashMap<String, Double>());
                return item;
            });

            if (!subjectName.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Double> scoresBySubject = (Map<String, Double>) row.get("scoresBySubject");
                scoresBySubject.put(subjectName, score.getScore());
            }
        }

        List<Map<String, Object>> rows = studentRows.values().stream()
                .sorted(Comparator.comparing(row -> String.valueOf(row.get("studentNo"))))
                .toList();
        int totalElements = rows.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows.subList(fromIndex, toIndex));
        result.put("subjects", new ArrayList<>(subjects));
        result.put("totalElements", totalElements);
        result.put("totalPages", totalPages);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    private String getAcademicYearFromSemester(String semester) {
        if (semester == null || semester.isBlank()) {
            return "";
        }
        String value = semester.trim();
        return value.matches(".+-[12]$") ? value.substring(0, value.length() - 2) : value;
    }

    private String getSubjectName(SubjectScoreRAIN score) {
        if (score.getSubject() != null && score.getSubject().getSubjectName() != null) {
            return score.getSubject().getSubjectName();
        }
        return score.getSubjectId() == null ? "" : "科目" + score.getSubjectId();
    }

    private String getStudentNo(SubjectScoreRAIN score) {
        if (score.getStudent() != null && score.getStudent().getStudentNo() != null) {
            return score.getStudent().getStudentNo();
        }
        if (score.getUser() != null && score.getUser().getStudentNo() != null) {
            return score.getUser().getStudentNo();
        }
        return "";
    }

    private String getStudentName(SubjectScoreRAIN score) {
        if (score.getStudent() != null && score.getStudent().getName() != null) {
            return score.getStudent().getName();
        }
        if (score.getUser() != null && score.getUser().getNickname() != null) {
            return score.getUser().getNickname();
        }
        return "-";
    }

    private String getClassName(SubjectScoreRAIN score) {
        if (score.getStudent() != null && score.getStudent().getClassName() != null) {
            return score.getStudent().getClassName();
        }
        if (score.getUser() != null && score.getUser().getClassName() != null) {
            return score.getUser().getClassName();
        }
        return "-";
    }

    /**
     * 根据科目ID查询成绩
     * @param subjectId 科目ID
     * @return 成绩列表
     */
    public List<SubjectScoreRAIN> getScoresBySubjectId(Long subjectId) {
        return subjectScoreRAINRepository.findBySubjectId(subjectId);
    }

    /**
     * 根据学期查询成绩
     * @param semester 学期
     * @return 成绩列表
     */
    public List<SubjectScoreRAIN> getScoresBySemester(String semester) {
        return subjectScoreRAINRepository.findBySemester(semester);
    }

    /**
     * 根据学生ID和学期查询成绩
     * @param studentId 学生ID
     * @param semester 学期
     * @return 成绩列表
     */
    public List<SubjectScoreRAIN> getScoresByStudentIdAndSemester(Long studentId, String semester) {
        return subjectScoreRAINRepository.findByStudentIdAndSemester(studentId, semester);
    }

    /**
     * 根据科目ID和学期查询成绩
     * @param subjectId 科目ID
     * @param semester 学期
     * @return 成绩列表
     */
    public List<SubjectScoreRAIN> getScoresBySubjectIdAndSemester(Long subjectId, String semester) {
        return subjectScoreRAINRepository.findBySubjectIdAndSemester(subjectId, semester);
    }

    /**
     * 根据学生ID、科目ID和学期查询成绩
     * @param studentId 学生ID
     * @param subjectId 科目ID
     * @param semester 学期
     * @return 成绩对象
     */
    public Optional<SubjectScoreRAIN> getScoreByStudentIdAndSubjectIdAndSemester(Long studentId, Long subjectId, String semester) {
        return subjectScoreRAINRepository.findByStudentIdAndSubjectIdAndSemester(studentId, subjectId, semester);
    }

    /**
     * 根据学生ID和科目ID查询成绩
     * @param studentId 学生ID
     * @param subjectId 科目ID
     * @return 成绩列表
     */
    public List<SubjectScoreRAIN> getScoresByStudentIdAndSubjectId(Long studentId, Long subjectId) {
        return subjectScoreRAINRepository.findByStudentIdAndSubjectId(studentId, subjectId);
    }

    /**
     * 删除指定学生的所有成绩
     * @param studentId 学生ID
     */
    @Transactional
    public void deleteScoresByStudentId(Long studentId) {
        List<SubjectScoreRAIN> scores = subjectScoreRAINRepository.findByStudentId(studentId);
        if (!scores.isEmpty()) {
            subjectScoreRAINRepository.deleteAll(scores);
        }
    }

    /**
     * 删除指定科目的所有成绩
     * @param subjectId 科目ID
     */
    @Transactional
    public void deleteScoresBySubjectId(Long subjectId) {
        List<SubjectScoreRAIN> scores = subjectScoreRAINRepository.findBySubjectId(subjectId);
        if (!scores.isEmpty()) {
            subjectScoreRAINRepository.deleteAll(scores);
        }
    }

    /**
     * 删除指定学期的所有成绩
     * @param semester 学期
     */
    @Transactional
    public void deleteScoresBySemester(String semester) {
        List<SubjectScoreRAIN> scores = subjectScoreRAINRepository.findBySemester(semester);
        if (!scores.isEmpty()) {
            subjectScoreRAINRepository.deleteAll(scores);
        }
    }

    public Optional<SubjectScoreRAIN> findPlainById(Long id) {
        return subjectScoreRAINRepository.findPlainById(id);
    }

    @Transactional
    public void saveSingle(SubjectScoreRAIN score) {
        subjectScoreRAINRepository.save(score);
    }

    @Transactional
    public void deleteById(Long id) {
        subjectScoreRAINRepository.deleteById(id);
    }

    public Map<String, Object> previewClassSemesterSubjectCleanup(String className, String semester, List<String> subjectNames) {
        List<String> normalizedSubjectNames = normalizeSubjectNames(subjectNames);
        validateCleanupParams(className, semester, normalizedSubjectNames);
        long count = subjectScoreRAINRepository.countByClassSemesterAndSubjectNames(className.trim(), semester.trim(), normalizedSubjectNames);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("className", className.trim());
        result.put("semester", semester.trim());
        result.put("subjectNames", normalizedSubjectNames);
        result.put("matchedCount", count);
        return result;
    }

    @Transactional
    public Map<String, Object> deleteClassSemesterSubjectScores(String className, String semester, List<String> subjectNames) {
        List<String> normalizedSubjectNames = normalizeSubjectNames(subjectNames);
        validateCleanupParams(className, semester, normalizedSubjectNames);
        int deletedCount = subjectScoreRAINRepository.deleteByClassSemesterAndSubjectNames(className.trim(), semester.trim(), normalizedSubjectNames);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("className", className.trim());
        result.put("semester", semester.trim());
        result.put("subjectNames", normalizedSubjectNames);
        result.put("deletedCount", deletedCount);
        return result;
    }

    private void validateCleanupParams(String className, String semester, List<String> subjectNames) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("请选择要清理的班级");
        }
        if (semester == null || semester.isBlank()) {
            throw new IllegalArgumentException("请选择要清理的学期");
        }
        if (subjectNames == null || subjectNames.isEmpty()) {
            throw new IllegalArgumentException("请填写要清理的科目名称");
        }
    }

    private List<String> normalizeSubjectNames(List<String> subjectNames) {
        if (subjectNames == null) {
            return List.of();
        }
        return subjectNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

}
