package com.markingsystem.service;

import com.markingsystem.entity.Student;
import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.Major;
import com.markingsystem.entity.College;
import com.markingsystem.entity.Role;
import com.markingsystem.entity.User;
import com.markingsystem.enums.RoleType;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.CollegeRepository;
import com.markingsystem.repository.MajorRepository;
import com.markingsystem.repository.RoleRepository;
import com.markingsystem.repository.StudentRepository;
import com.markingsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class StudentService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ClassInfoRepository classInfoRepository;

    @Autowired
    private MajorRepository majorRepository;

    @Autowired
    private CollegeRepository collegeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private StudentAccountSyncService studentAccountSyncService;

    @Autowired
    private PasswordService passwordService;

    // 全局学生缓存（用于导入时的跨请求缓存）
    private final Map<String, Student> globalStudentCache = new ConcurrentHashMap<>();
    
    public List<Student> findAll() {
        return applyCommitteeFlag(studentRepository.findAll());
    }
    
    public Optional<Student> findById(Long id) {
        return studentRepository.findById(id);
    }
    
    public Optional<Student> findByStudentNo(String studentNo) {
        return studentRepository.findByStudentNo(studentNo);
    }
    
    @Transactional
    public Student save(Student student) {
        validateStudentForSave(student, student == null ? null : student.getId());
        ClassInfo classInfo = resolveClassInfo(student.getClassName());
        student.setTeacherId(classInfo.getTeacherId());

        Student saved = studentRepository.save(student);
        return syncStudentAccount(saved, Boolean.TRUE.equals(student.getClassCommittee()));
    }
    
    @Transactional
    public Student createStudent(String studentNo, String name, String className) {
        Student student = new Student();
        student.setStudentNo(studentNo);
        student.setName(name);
        student.setClassName(className);
        return studentRepository.save(student);
    }
    
    @Transactional
    public Optional<Student> updateStudent(Long id, String name, String className) {
        return studentRepository.findById(id).map(student -> {
            student.setName(name);
            student.setClassName(className);
            return studentRepository.save(student);
        });
    }
    
    /**
     * 更新学生完整信息
     */
    @Transactional
    public Optional<Student> updateStudent(Long id, Student student) {
        return studentRepository.findById(id).map(existingStudent -> {
            validateStudentForSave(student, id);
            ClassInfo classInfo = resolveClassInfo(student.getClassName());
            existingStudent.setStudentNo(student.getStudentNo());
            existingStudent.setName(student.getName());
            existingStudent.setClassName(student.getClassName());
            existingStudent.setTeacherId(classInfo.getTeacherId());
            Student saved = studentRepository.save(existingStudent);
            return syncStudentAccount(saved, Boolean.TRUE.equals(student.getClassCommittee()));
        });
    }
    
    @Transactional
    public void deleteById(Long id) {
        studentRepository.deleteById(id);
    }

    @Transactional
    public void deleteById(Long id, Long teacherId) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("学生不存在"));
        if (teacherId != null && !teacherId.equals(student.getTeacherId())) {
            throw new IllegalArgumentException("仅可删除自己负责的学生");
        }
        studentRepository.deleteById(id);
    }

    @Transactional
    public String resetStudentPasswordToAccount(Long id, Long teacherId) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("学生不存在"));
        if (teacherId != null && !teacherId.equals(student.getTeacherId())) {
            throw new IllegalArgumentException("仅可重置自己负责学生的密码");
        }

        User user = Optional.ofNullable(student.getUserId())
                .flatMap(userRepository::findById)
                .or(() -> userRepository.findByStudentNo(student.getStudentNo()))
                .or(() -> userRepository.findByUsername(student.getStudentNo()))
                .orElseThrow(() -> new IllegalArgumentException("该学生未绑定登录账号，无法重置密码"));
        String account = user.getUsername() == null ? "" : user.getUsername().trim();
        if (account.isBlank()) {
            throw new IllegalArgumentException("学生账号为空，无法重置密码");
        }

        user.setPassword(passwordService.encode(account));
        user.setPasswordResetRequired(true);
        userRepository.save(user);
        return account;
    }

    /**
     * 获取或创建学生（带缓存）
     * 用于成绩导入时自动创建不存在的学生
     * @param studentNo 学号
     * @param name 姓名
     * @param className 班级
     * @param localCache 本地缓存（请求级缓存，优先于全局缓存）
     * @return 学生对象
     */
    @Transactional
    public Student getOrCreateStudent(String studentNo, String name, String className, Long teacherId, Map<String, Student> localCache) {
        // 1. 先查本地缓存（请求级）
        if (localCache != null && localCache.containsKey(studentNo)) {
            Student cachedStudent = localCache.get(studentNo);
            // 更新姓名和班级（保持最新）
            if (name != null && !name.isEmpty() && !name.equals(cachedStudent.getName())) {
                cachedStudent.setName(name);
            }
            if (className != null && !className.isEmpty() && !className.equals(cachedStudent.getClassName())) {
                cachedStudent.setClassName(className);
            }
            if (teacherId != null && !teacherId.equals(cachedStudent.getTeacherId())) {
                cachedStudent.setTeacherId(teacherId);
            }
            return cachedStudent;
        }

        // 2. 再查全局缓存
        Student student = globalStudentCache.get(studentNo);
        if (student != null) {
            // 更新姓名和班级（保持最新）
            if (name != null && !name.isEmpty() && !name.equals(student.getName())) {
                student.setName(name);
            }
            if (className != null && !className.isEmpty() && !className.equals(student.getClassName())) {
                student.setClassName(className);
            }
            if (teacherId != null && !teacherId.equals(student.getTeacherId())) {
                student.setTeacherId(teacherId);
            }
            // 同步到本地缓存
            if (localCache != null) {
                localCache.put(studentNo, student);
            }
            return student;
        }

        // 3. 查数据库
        Optional<Student> existing = studentRepository.findByStudentNo(studentNo);
        if (existing.isPresent()) {
            student = existing.get();
            // 更新姓名和班级（保持最新）
            if (name != null && !name.isEmpty()) {
                student.setName(name);
            }
            if (className != null && !className.isEmpty()) {
                student.setClassName(className);
            }
            if (teacherId != null) {
                student.setTeacherId(teacherId);
            }
            student = studentRepository.save(student);
        } else {
            // 4. 创建新学生
            student = new Student();
            student.setStudentNo(studentNo);
            student.setName(name != null ? name : "");
            student.setClassName(className != null ? className : "");
            student.setTeacherId(teacherId);
            student = studentRepository.save(student);
        }

        // 5. 更新缓存
        globalStudentCache.put(studentNo, student);
        if (localCache != null) {
            localCache.put(studentNo, student);
        }

        return student;
    }

    /**
     * 清除全局学生缓存
     * 建议在批量导入完成后调用，释放内存
     */
    public void clearGlobalStudentCache() {
        globalStudentCache.clear();
    }

    /**
     * 获取全局缓存中的学生数量
     * @return 缓存数量
     */
    public int getGlobalCacheSize() {
        return globalStudentCache.size();
    }
    
    /**
     * 批量删除学生
     */
    @Transactional
    public void batchDelete(List<Long> ids) {
        studentRepository.deleteAllById(ids);
    }
    
    public boolean existsByStudentNo(String studentNo) {
        return studentRepository.existsByStudentNo(studentNo);
    }
    
    public List<Student> findByTeacherId(Long teacherId) {
        return applyCommitteeFlag(studentRepository.findByTeacherId(teacherId));
    }

    public List<Student> findManagedByTeacherId(Long teacherId) {
        if (teacherId == null) {
            return List.of();
        }

        Map<Long, Student> managedStudents = new LinkedHashMap<>();
        studentRepository.findByTeacherId(teacherId)
                .forEach(student -> managedStudents.put(student.getId(), student));

        List<String> classNames = classInfoRepository.findByTeacherId(teacherId).stream()
                .map(ClassInfo::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        if (!classNames.isEmpty()) {
            studentRepository.findByClassNameIn(classNames)
                    .forEach(student -> managedStudents.put(student.getId(), student));
        }

        return List.copyOf(managedStudents.values());
    }
    
    public List<Student> findByClassName(String className) {
        return applyCommitteeFlag(studentRepository.findByClassName(className));
    }

    public Optional<Student> findByUserId(Long userId) {
        return studentRepository.findByUserId(userId);
    }

    public List<Student> findByTeacherIdAndClassName(Long teacherId, String className) {
        return applyCommitteeFlag(studentRepository.findByTeacherIdAndClassName(teacherId, className));
    }

    public List<Student> findManagedByTeacherIdAndClassName(Long teacherId, String className) {
        if (className == null || className.isBlank()) {
            return findManagedByTeacherId(teacherId);
        }
        return findManagedByTeacherId(teacherId).stream()
                .filter(student -> className.equals(student.getClassName()))
                .toList();
    }

    @Transactional
    public Optional<Student> assignTeacher(Long studentId, Long teacherId) {
        return studentRepository.findById(studentId).map(student -> {
            student.setTeacherId(teacherId);
            return studentRepository.save(student);
        });
    }
    
    /**
     * 带筛选条件的分页查询
     */
    public Page<Student> findStudentsWithFilter(String keyword, String college, String grade, Long teacherId, Pageable pageable) {
        return findStudentsWithFilter(keyword, college, null, grade, null, "ALL", teacherId, pageable);
    }

    public Page<Student> findStudentsWithFilter(String keyword, String college, String grade, boolean committeeOnly, Long teacherId, Pageable pageable) {
        return findStudentsWithFilter(keyword, college, null, grade, null, committeeOnly ? "CLASS_COMMITTEE" : "ALL", teacherId, pageable);
    }

    public Page<Student> findStudentsWithFilter(String keyword, String college, String grade, String className, boolean committeeOnly, Long teacherId, Pageable pageable) {
        return findStudentsWithFilter(keyword, college, null, grade, className, committeeOnly ? "CLASS_COMMITTEE" : "ALL", teacherId, pageable);
    }

    public Page<Student> findStudentsWithFilter(String keyword, String college, String major, String grade, String className, boolean committeeOnly, Long teacherId, Pageable pageable) {
        return findStudentsWithFilter(keyword, college, major, grade, className, committeeOnly ? "CLASS_COMMITTEE" : "ALL", teacherId, pageable);
    }

    public Page<Student> findStudentsWithFilter(String keyword, String college, String major, String grade, String className, String committeeStatus, Long teacherId, Pageable pageable) {
        try {
            List<Student> allStudents = teacherId != null
                    ? findManagedByTeacherId(teacherId)
                    : studentRepository.findAll();
            allStudents = applyCommitteeFlag(allStudents);
            String normalizedCommitteeStatus = normalizeCommitteeStatus(committeeStatus);

            Map<String, ClassInfo> classInfoMap = classInfoRepository.findAll().stream()
                    .filter(classInfo -> classInfo.getName() != null && !classInfo.getName().isBlank())
                    .collect(Collectors.toMap(ClassInfo::getName, classInfo -> classInfo, (left, right) -> left));
            Map<Long, Major> majorMap = majorRepository.findAll().stream()
                    .collect(Collectors.toMap(Major::getId, majorEntity -> majorEntity, (left, right) -> left));
            Map<Long, College> collegeMap = collegeRepository.findAll().stream()
                    .collect(Collectors.toMap(College::getId, item -> item, (left, right) -> left));
            
            // 关键词筛选（学号或姓名）
            if (keyword != null && !keyword.trim().isEmpty()) {
                final String keywordLower = keyword.toLowerCase();
                allStudents = allStudents.stream()
                        .filter(s -> {
                            String studentNo = s.getStudentNo() != null ? s.getStudentNo().toLowerCase() : "";
                            String name = s.getName() != null ? s.getName().toLowerCase() : "";
                            String classNameValue = s.getClassName() != null ? s.getClassName().toLowerCase() : "";
                            return studentNo.contains(keywordLower) || name.contains(keywordLower) || classNameValue.contains(keywordLower);
                        })
                        .collect(Collectors.toList());
            }
            
            if (college != null && !college.trim().isEmpty()) {
                final String expectedCollege = college.trim();
                allStudents = allStudents.stream()
                        .filter(student -> {
                            ClassInfo classInfo = classInfoMap.get(student.getClassName());
                            if (classInfo == null || classInfo.getMajorId() == null) {
                                return false;
                            }
                            Major majorEntity = majorMap.get(classInfo.getMajorId());
                            if (majorEntity == null || majorEntity.getCollegeId() == null) {
                                return false;
                            }
                            College collegeInfo = collegeMap.get(majorEntity.getCollegeId());
                            return collegeInfo != null && expectedCollege.equals(collegeInfo.getName());
                        })
                        .collect(Collectors.toList());
            }

            if (major != null && !major.trim().isEmpty()) {
                final String expectedMajor = major.trim();
                allStudents = allStudents.stream()
                        .filter(student -> {
                            ClassInfo classInfo = classInfoMap.get(student.getClassName());
                            if (classInfo == null || classInfo.getMajorId() == null) {
                                return false;
                            }
                            Major majorInfo = majorMap.get(classInfo.getMajorId());
                            return majorInfo != null && expectedMajor.equals(majorInfo.getName());
                        })
                        .collect(Collectors.toList());
            }

            if (grade != null && !grade.trim().isEmpty()) {
                final String expectedGrade = grade.trim();
                allStudents = allStudents.stream()
                        .filter(student -> {
                            ClassInfo classInfo = classInfoMap.get(student.getClassName());
                            return classInfo != null && expectedGrade.equals(classInfo.getGrade());
                        })
                        .collect(Collectors.toList());
            }

            if (className != null && !className.trim().isEmpty()) {
                final String expectedClassName = className.trim();
                allStudents = allStudents.stream()
                        .filter(student -> expectedClassName.equals(student.getClassName()))
                        .collect(Collectors.toList());
            }

            if ("CLASS_COMMITTEE".equals(normalizedCommitteeStatus)) {
                allStudents = allStudents.stream()
                        .filter(student -> Boolean.TRUE.equals(student.getClassCommittee()))
                        .collect(Collectors.toList());
            } else if ("NORMAL_STUDENT".equals(normalizedCommitteeStatus)) {
                allStudents = allStudents.stream()
                        .filter(student -> !Boolean.TRUE.equals(student.getClassCommittee()))
                        .collect(Collectors.toList());
            }
            
            // 手动分页
            int start = (int) pageable.getOffset();
            int total = allStudents.size();
            
            if (start >= total) {
                return new PageImpl<>(new java.util.ArrayList<>(), pageable, total);
            }
            
            int end = Math.min(start + pageable.getPageSize(), total);
            List<Student> pageContent = allStudents.subList(start, end);
            return new PageImpl<>(pageContent, pageable, total);
        } catch (Exception e) {
            // 发生异常时返回空分页
            System.err.println("查询学生列表失败: " + e.getMessage());
            e.printStackTrace();
            return new PageImpl<>(new java.util.ArrayList<>(), pageable, 0);
        }
    }

    public Page<Map<String, Object>> findStudentListRowsWithFilter(String keyword, String college, String major, String grade, String className, boolean committeeOnly, Long teacherId, Pageable pageable) {
        return findStudentListRowsWithFilter(keyword, college, major, grade, className, committeeOnly ? "CLASS_COMMITTEE" : "ALL", teacherId, pageable);
    }

    public Page<Map<String, Object>> findStudentListRowsWithFilter(String keyword, String college, String major, String grade, String className, String committeeStatus, Long teacherId, Pageable pageable) {
        Page<Student> students = findStudentsWithFilter(keyword, college, major, grade, className, committeeStatus, teacherId, pageable);
        Set<Long> teacherIds = students.getContent().stream()
                .map(Student::getTeacherId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        // 注意: teacher_id 可能为 NULL, 不可变 Map.of().get(null) 会抛 NPE, 空表用 Collections.emptyMap() 并对 null 键短路
        Map<Long, User> teacherMap = teacherIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : userRepository.findAllById(teacherIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        List<Map<String, Object>> rows = students.getContent().stream()
                .map(student -> toStudentListRow(student,
                        student.getTeacherId() == null ? null : teacherMap.get(student.getTeacherId())))
                .toList();
        return new PageImpl<>(rows, pageable, students.getTotalElements());
    }

    private String normalizeCommitteeStatus(String committeeStatus) {
        String normalized = committeeStatus == null ? "" : committeeStatus.trim().toUpperCase();
        return switch (normalized) {
            case "CLASS_COMMITTEE", "COMMITTEE", "TRUE" -> "CLASS_COMMITTEE";
            case "NORMAL_STUDENT", "STUDENT", "FALSE" -> "NORMAL_STUDENT";
            default -> "ALL";
        };
    }

    private Map<String, Object> toStudentListRow(Student student, User teacher) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", student.getId());
        row.put("studentNo", student.getStudentNo());
        row.put("name", student.getName());
        row.put("className", student.getClassName());
        row.put("teacherName", resolveDisplayName(teacher));
        row.put("classCommittee", Boolean.TRUE.equals(student.getClassCommittee()));
        return row;
    }

    private String resolveDisplayName(User user) {
        if (user == null) {
            return "";
        }
        String nickname = user.getNickname() == null ? "" : user.getNickname().trim();
        if (!nickname.isBlank()) {
            return nickname;
        }
        return user.getUsername() == null ? "" : user.getUsername().trim();
    }

    private List<Student> applyCommitteeFlag(List<Student> students) {
        if (students == null || students.isEmpty()) {
            return List.of();
        }

        Optional<Role> committeeRole = roleRepository.findByName("CLASS_COMMITTEE");
        if (committeeRole.isEmpty()) {
            students.forEach(student -> student.setClassCommittee(false));
            return students;
        }

        List<User> committeeUsers = userRepository.findByRoleId(committeeRole.get().getId());
        Set<Long> committeeUserIds = committeeUsers.stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        Set<String> committeeStudentNos = committeeUsers.stream()
                .map(User::getStudentNo)
                .filter(studentNo -> studentNo != null && !studentNo.isBlank())
                .collect(Collectors.toSet());

        students.forEach(student -> {
            boolean isCommittee = (student.getUserId() != null && committeeUserIds.contains(student.getUserId()))
                    || (student.getStudentNo() != null && committeeStudentNos.contains(student.getStudentNo()));
            student.setClassCommittee(isCommittee);
        });
        return students;
    }

    private ClassInfo resolveClassInfo(String className) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("班级不能为空");
        }
        return classInfoRepository.findByName(className.trim())
                .orElseThrow(() -> new IllegalArgumentException("班级不存在: " + className));
    }

    private void validateStudentForSave(Student student, Long currentStudentId) {
        if (student == null) {
            throw new IllegalArgumentException("学生信息不能为空");
        }

        String studentNo = student.getStudentNo() == null ? "" : student.getStudentNo().trim();
        String name = student.getName() == null ? "" : student.getName().trim();
        String className = student.getClassName() == null ? "" : student.getClassName().trim();
        if (studentNo.isBlank()) {
            throw new IllegalArgumentException("学号不能为空");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("学生姓名不能为空");
        }
        if (className.isBlank()) {
            throw new IllegalArgumentException("班级不能为空");
        }

        studentRepository.findByStudentNo(studentNo)
                .filter(existing -> currentStudentId == null || !existing.getId().equals(currentStudentId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "学号 “" + studentNo + "” 已存在，对应学生为“" + existing.getName() + "”，请勿重复创建"
                    );
                });

        student.setStudentNo(studentNo);
        student.setName(name);
        student.setClassName(className);
    }

    private Student syncStudentAccount(Student student, boolean classCommittee) {
        User user = studentAccountSyncService.ensureStudentAccount(
                student,
                student.getStudentNo(),
                "123456",
                student.getName()
        );
        syncPrimaryRole(user, classCommittee);

        if (student.getUserId() == null || !student.getUserId().equals(user.getId())) {
            student.setUserId(user.getId());
            student = studentRepository.save(student);
        }
        student.setClassCommittee(classCommittee);
        return student;
    }

    private void syncPrimaryRole(User user, boolean classCommittee) {
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new IllegalStateException("STUDENT 角色不存在"));
        Role committeeRole = roleRepository.findByName("CLASS_COMMITTEE")
                .orElseThrow(() -> new IllegalStateException("CLASS_COMMITTEE 角色不存在"));

        boolean changed = false;
        Long targetRoleId = classCommittee ? committeeRole.getId() : studentRole.getId();
        if (!targetRoleId.equals(user.getRoleId())) {
            user.setRoleId(targetRoleId);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
        }

        if (!userRoleService.hasRole(user.getId(), RoleType.STUDENT)) {
            userRoleService.assignRoleByType(user.getId(), RoleType.STUDENT);
        }

        if (classCommittee) {
            if (!userRoleService.hasRole(user.getId(), RoleType.CLASS_COMMITTEE)) {
                userRoleService.assignRoleByType(user.getId(), RoleType.CLASS_COMMITTEE);
            }
        } else if (userRoleService.hasRole(user.getId(), RoleType.CLASS_COMMITTEE)) {
            userRoleService.removeRoleByType(user.getId(), RoleType.CLASS_COMMITTEE);
        }
    }
}
