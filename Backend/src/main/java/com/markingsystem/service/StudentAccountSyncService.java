package com.markingsystem.service;

import com.markingsystem.entity.Role;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.User;
import com.markingsystem.repository.RoleRepository;
import com.markingsystem.repository.StudentRepository;
import com.markingsystem.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class StudentAccountSyncService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleService userRoleService;
    private final PasswordService passwordService;

    public StudentAccountSyncService(
            StudentRepository studentRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleService userRoleService,
            PasswordService passwordService
    ) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleService = userRoleService;
        this.passwordService = passwordService;
    }

    @PostConstruct
    @Transactional
    public void syncStudentAccounts() {
        Role studentRole = getStudentRole();

        for (User existingUser : userRepository.findAll()) {
            if (existingUser.getRoleId() != null && userRoleService.findByUserId(existingUser.getId()).stream()
                    .noneMatch(userRole -> existingUser.getRoleId().equals(userRole.getRoleId()))) {
                userRoleService.assignRole(existingUser.getId(), existingUser.getRoleId());
            }
        }

        List<Student> students = studentRepository.findAll();
        for (Student student : students) {
            if (student.getStudentNo() == null || student.getStudentNo().isBlank()) {
                continue;
            }

            User user = userRepository.findByUsername(student.getStudentNo())
                    .orElseGet(() -> createStudentUser(
                            student,
                            studentRole,
                            student.getStudentNo(),
                            "123456",
                            student.getName()
                    ));

            boolean changed = false;
            if (!studentRole.getId().equals(user.getRoleId())) {
                user.setRoleId(studentRole.getId());
                changed = true;
            }
            if (!student.getStudentNo().equals(user.getStudentNo())) {
                user.setStudentNo(student.getStudentNo());
                changed = true;
            }
            if (student.getTeacherId() == null) {
                if (user.getTeacherId() != null) {
                    user.setTeacherId(null);
                    changed = true;
                }
            } else if (!student.getTeacherId().equals(user.getTeacherId())) {
                user.setTeacherId(student.getTeacherId());
                changed = true;
            }
            if (student.getClassName() == null) {
                if (user.getClassName() != null) {
                    user.setClassName(null);
                    changed = true;
                }
            } else if (!student.getClassName().equals(user.getClassName())) {
                user.setClassName(student.getClassName());
                changed = true;
            }
            if (student.getName() != null && !student.getName().equals(user.getNickname())) {
                user.setNickname(student.getName());
                changed = true;
            }
            if (shouldRequireInitialPasswordChange(user, "123456")) {
                user.setPasswordResetRequired(true);
                changed = true;
            }
            if (changed) {
                user = userRepository.save(user);
            }

            ensurePrimaryAndStudentRole(user, studentRole);

            if (student.getUserId() == null || !student.getUserId().equals(user.getId())) {
                student.setUserId(user.getId());
                studentRepository.save(student);
            }
        }
    }

    @Transactional
    public User ensureStudentAccount(Student student, String username, String rawPassword, String nickname) {
        if (student == null || student.getStudentNo() == null || student.getStudentNo().isBlank()) {
            throw new IllegalArgumentException("学生学号不能为空");
        }

        Role studentRole = getStudentRole();
        String finalUsername = normalizeOrDefault(username, student.getStudentNo());
        String finalPassword = normalizeOrDefault(rawPassword, "123456");
        String finalNickname = normalizeOrDefault(nickname, student.getName());

        Optional<User> userByStudentRef = Optional.empty();
        if (student.getUserId() != null) {
            userByStudentRef = userRepository.findById(student.getUserId());
        }

        User user = userByStudentRef
                .or(() -> userRepository.findByUsername(finalUsername))
                .orElseGet(() -> createStudentUser(student, studentRole, finalUsername, finalPassword, finalNickname));
        final Long currentUserId = user.getId();

        boolean changed = false;
        if (!finalUsername.equals(user.getUsername())) {
            final String usernameToCheck = finalUsername;
            userRepository.findByUsername(finalUsername)
                    .filter(existing -> !existing.getId().equals(currentUserId))
                    .ifPresent(existing -> {
                        throw new IllegalStateException("账号 " + usernameToCheck + " 已存在，无法重复创建");
                    });
            user.setUsername(finalUsername);
            changed = true;
        }
        if (!studentRole.getId().equals(user.getRoleId())) {
            user.setRoleId(studentRole.getId());
            changed = true;
        }
        if (!student.getStudentNo().equals(user.getStudentNo())) {
            user.setStudentNo(student.getStudentNo());
            changed = true;
        }
        if (student.getTeacherId() == null) {
            if (user.getTeacherId() != null) {
                user.setTeacherId(null);
                changed = true;
            }
        } else if (!student.getTeacherId().equals(user.getTeacherId())) {
            user.setTeacherId(student.getTeacherId());
            changed = true;
        }
        if (student.getClassName() == null) {
            if (user.getClassName() != null) {
                user.setClassName(null);
                changed = true;
            }
        } else if (!student.getClassName().equals(user.getClassName())) {
            user.setClassName(student.getClassName());
            changed = true;
        }
        if (finalNickname != null && !finalNickname.equals(user.getNickname())) {
            user.setNickname(finalNickname);
            changed = true;
        }
        if (shouldRequireInitialPasswordChange(user, finalPassword)) {
            user.setPasswordResetRequired(true);
            changed = true;
        }
        if (!userByStudentRef.isPresent() && !userRepository.findByUsername(finalUsername).isPresent()) {
            user.setPassword(passwordService.encode(finalPassword));
            user.setPasswordResetRequired(true);
            changed = true;
        }

        if (changed) {
            user = userRepository.save(user);
        }

        ensurePrimaryAndStudentRole(user, studentRole);

        if (student.getUserId() == null || !student.getUserId().equals(user.getId())) {
            student.setUserId(user.getId());
            studentRepository.save(student);
        }

        return user;
    }

    private User createStudentUser(Student student, Role studentRole, String username, String password, String nickname) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordService.encode(password));
        user.setNickname(nickname);
        user.setRoleId(studentRole.getId());
        user.setTeacherId(student.getTeacherId());
        user.setClassName(student.getClassName());
        user.setStudentNo(student.getStudentNo());
        user.setPasswordResetRequired(true);
        return userRepository.save(user);
    }

    private Role getStudentRole() {
        return roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new IllegalStateException("STUDENT 角色不存在"));
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    boolean shouldRequireInitialPasswordChange(User user, String initialPassword) {
        return user != null
                && !Boolean.TRUE.equals(user.getPasswordResetRequired())
                && initialPassword != null
                && !initialPassword.isBlank()
                && passwordService.matches(initialPassword, user.getPassword());
    }

    private void ensurePrimaryAndStudentRole(User user, Role studentRole) {
        if (!userRoleService.hasRole(user.getId(), com.markingsystem.enums.RoleType.STUDENT)) {
            userRoleService.assignRole(user.getId(), studentRole.getId());
        }
    }
}
