package com.markingsystem.service;

import com.markingsystem.entity.Major;
import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.College;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.User;
import com.markingsystem.repository.AnnouncementRecipientRepository;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.CollegeRepository;
import com.markingsystem.repository.ExtraScoreApplicationRepository;
import com.markingsystem.repository.IntellectualEducationRepository;
import com.markingsystem.repository.MajorRepository;
import com.markingsystem.repository.MoralEducationRepository;
import com.markingsystem.repository.PhysicalEducationRepository;
import com.markingsystem.repository.StudentAnalysisRecordRepository;
import com.markingsystem.repository.StudentRepository;
import com.markingsystem.repository.SubjectScoreRAINRepository;
import com.markingsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MajorService {
    
    @Autowired
    private MajorRepository majorRepository;

    @Autowired
    private CollegeRepository collegeRepository;

    @Autowired
    private ClassInfoRepository classInfoRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private SubjectScoreRAINRepository subjectScoreRAINRepository;

    @Autowired
    private MoralEducationRepository moralEducationRepository;

    @Autowired
    private IntellectualEducationRepository intellectualEducationRepository;

    @Autowired
    private PhysicalEducationRepository physicalEducationRepository;

    @Autowired
    private ExtraScoreApplicationRepository extraScoreApplicationRepository;

    @Autowired
    private StudentAnalysisRecordRepository studentAnalysisRecordRepository;

    @Autowired
    private AnnouncementRecipientRepository announcementRecipientRepository;
    
    public List<Major> findAll() {
        return majorRepository.findAll();
    }

    public Map<String, Object> findPage(int page, int size, Long collegeId) {
        List<Major> rows = majorRepository.findAll().stream()
                .filter(major -> collegeId == null || collegeId.equals(major.getCollegeId()))
                .toList();
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int totalElements = rows.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", rows.subList(fromIndex, toIndex));
        result.put("totalElements", totalElements);
        result.put("totalPages", totalPages);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    public List<Map<String, Object>> findOptions() {
        Map<Long, String> collegeNameMap = collegeRepository.findAll().stream()
                .collect(Collectors.toMap(College::getId, College::getName, (left, right) -> left));
        return majorRepository.findAll().stream()
                .map(major -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", major.getId());
                    item.put("name", major.getName());
                    item.put("status", major.getStatus());
                    item.put("collegeId", major.getCollegeId());
                    item.put("collegeName", collegeNameMap.getOrDefault(major.getCollegeId(), ""));
                    return item;
                })
                .toList();
    }
    
    public Optional<Major> findById(Long id) {
        return majorRepository.findById(id);
    }

    public List<Major> findByCollegeId(Long collegeId) {
        return majorRepository.findByCollegeId(collegeId);
    }
    
    public List<Major> findByStatus(String status) {
        return majorRepository.findByStatus(status);
    }
    
    public List<Major> search(String keyword, Long collegeId) {
        List<Major> majors;
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            majors = majorRepository.findByNameContaining(keyword);
        } else {
            majors = majorRepository.findAll();
        }
        
        if (collegeId != null) {
            majors = majors.stream()
                    .filter(m -> m.getCollegeId().equals(collegeId))
                    .collect(Collectors.toList());
        }
        
        return majors;
    }
    
    public Major save(Major major) {
        if (major.getName() == null || major.getName().trim().isEmpty()) {
            throw new RuntimeException("专业名称不能为空");
        }
        if (major.getCollegeId() == null) {
            throw new RuntimeException("所属学院不能为空");
        }
        if (major.getStatus() == null || major.getStatus().isBlank()) {
            major.setStatus("active");
        }
        return majorRepository.save(major);
    }
    
    public Optional<Major> update(Long id, Major major) {
        return majorRepository.findById(id).map(existingMajor -> {
            if (major.getName() == null || major.getName().trim().isEmpty()) {
                throw new RuntimeException("专业名称不能为空");
            }
            if (major.getCollegeId() == null) {
                throw new RuntimeException("所属学院不能为空");
            }
            existingMajor.setName(major.getName());
            existingMajor.setCollegeId(major.getCollegeId());
            existingMajor.setStatus(
                    major.getStatus() == null || major.getStatus().isBlank()
                            ? existingMajor.getStatus()
                            : major.getStatus()
            );
            return majorRepository.save(existingMajor);
        });
    }
    
    public Map<String, Object> getDeleteImpact(Long id) {
        Major major = majorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("专业不存在"));

        List<ClassInfo> classes = classInfoRepository.findByMajorId(id);
        List<String> classNames = classes.stream()
                .map(ClassInfo::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        List<Student> students = classNames.isEmpty() ? List.of() : studentRepository.findByClassNameIn(classNames);
        List<User> users = classNames.isEmpty() ? List.of() : userRepository.findByClassNameIn(classNames);

        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("majorId", major.getId());
        impact.put("majorName", major.getName());
        impact.put("classCount", classes.size());
        impact.put("studentCount", students.size());
        impact.put("userCount", users.size());
        impact.put("classes", classes.stream().map(item -> Map.of(
                "id", item.getId(),
                "name", Optional.ofNullable(item.getName()).orElse("-"),
                "grade", Optional.ofNullable(item.getGrade()).orElse("-")
        )).collect(Collectors.toList()));
        impact.put("students", students.stream().limit(10).map(item -> Map.of(
                "id", item.getId(),
                "studentNo", Optional.ofNullable(item.getStudentNo()).orElse("-"),
                "name", Optional.ofNullable(item.getName()).orElse("-"),
                "className", Optional.ofNullable(item.getClassName()).orElse("-")
        )).collect(Collectors.toList()));
        impact.put("users", users.stream().limit(10).map(item -> Map.of(
                "id", item.getId(),
                "username", Optional.ofNullable(item.getUsername()).orElse("-"),
                "nickname", Optional.ofNullable(item.getNickname()).orElse("-"),
                "className", Optional.ofNullable(item.getClassName()).orElse("-"),
                "studentNo", Optional.ofNullable(item.getStudentNo()).orElse("-")
        )).collect(Collectors.toList()));
        return impact;
    }

    @Transactional
    public void deleteById(Long id, boolean force, boolean deleteUsers) {
        Major major = majorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("专业不存在"));

        List<ClassInfo> classes = classInfoRepository.findByMajorId(id);
        if (!force && !classes.isEmpty()) {
            throw new IllegalStateException("该专业下仍有班级数据，无法直接删除。请先处理关联班级，或使用强制删除。");
        }

        if (force && !classes.isEmpty()) {
            List<String> classNames = classes.stream()
                    .map(ClassInfo::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .toList();
            List<Student> students = classNames.isEmpty() ? List.of() : studentRepository.findByClassNameIn(classNames);
            List<User> users = classNames.isEmpty() ? List.of() : userRepository.findByClassNameIn(classNames);

            if (deleteUsers) {
                deleteBoundUsersAndStudents(students, users);
            } else {
                detachBoundUsersAndStudents(students, users);
            }

            classInfoRepository.deleteAll(classes);
        }

        majorRepository.delete(major);
    }

    private void detachBoundUsersAndStudents(List<Student> students, List<User> users) {
        for (Student student : students) {
            student.setClassName(null);
            student.setTeacherId(null);
        }
        if (!students.isEmpty()) {
            studentRepository.saveAll(students);
        }

        for (User user : users) {
            user.setClassName(null);
            user.setTeacherId(null);
        }
        if (!users.isEmpty()) {
            userRepository.saveAll(users);
        }
    }

    private void deleteBoundUsersAndStudents(List<Student> students, List<User> users) {
        List<Long> studentIds = students.stream()
                .map(Student::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<Long> userIds = users.stream()
                .map(User::getId)
                .filter(java.util.Objects::nonNull)
                .toList();

        if (!studentIds.isEmpty()) {
            announcementRecipientRepository.deleteByStudentIdIn(studentIds);
            extraScoreApplicationRepository.deleteByStudentIdIn(studentIds);
            studentAnalysisRecordRepository.deleteByStudentIdIn(studentIds);
            subjectScoreRAINRepository.deleteByStudentIdIn(studentIds);
            moralEducationRepository.deleteByStudentIdIn(studentIds);
            intellectualEducationRepository.deleteByStudentIdIn(studentIds);
            physicalEducationRepository.deleteByStudentIdIn(studentIds);
            studentRepository.deleteAll(students);
        }

        if (!userIds.isEmpty()) {
            subjectScoreRAINRepository.deleteByUserIdIn(userIds);
            userRoleService.removeAllRoles(userIds);
            userRepository.deleteAll(users);
        }
    }
    
    public Optional<Major> toggleStatus(Long id) {
        return majorRepository.findById(id).map(major -> {
            String newStatus = "active".equals(major.getStatus()) ? "inactive" : "active";
            major.setStatus(newStatus);
            return majorRepository.save(major);
        });
    }
}
