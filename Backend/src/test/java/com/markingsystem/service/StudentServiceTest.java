package com.markingsystem.service;

import com.markingsystem.entity.Role;
import com.markingsystem.entity.Student;
import com.markingsystem.entity.User;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.CollegeRepository;
import com.markingsystem.repository.MajorRepository;
import com.markingsystem.repository.RoleRepository;
import com.markingsystem.repository.StudentRepository;
import com.markingsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StudentServiceTest {

    @Test
    void rejectsDuplicateStudentNumberWithReadableReason() throws Exception {
        StudentService service = new StudentService();
        Student existing = student(1L, "20230001", "已有学生", 11L);
        Student request = student(null, " 20230001 ", "新学生", null);

        setField(service, "studentRepository", proxy(
                StudentRepository.class,
                Map.of("findByStudentNo", Optional.of(existing))
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.save(request)
        );

        assertEquals("学号 “20230001” 已存在，对应学生为“已有学生”，请勿重复创建", exception.getMessage());
    }

    @Test
    void filtersNormalStudentsWhenCommitteeFilterRequestsNormalStudent() throws Exception {
        StudentService service = new StudentService();
        Student committee = student(1L, "20230001", "班委学生", 11L);
        Student normal = student(2L, "20230002", "普通学生", 12L);
        Role committeeRole = new Role();
        committeeRole.setId(6L);
        committeeRole.setName("CLASS_COMMITTEE");
        User committeeUser = new User();
        committeeUser.setId(11L);
        committeeUser.setRoleId(6L);
        committeeUser.setStudentNo("20230001");

        setField(service, "studentRepository", proxy(
                StudentRepository.class,
                Map.of("findAll", List.of(committee, normal))
        ));
        setField(service, "classInfoRepository", proxy(ClassInfoRepository.class, Map.of("findAll", List.of())));
        setField(service, "majorRepository", proxy(MajorRepository.class, Map.of("findAll", List.of())));
        setField(service, "collegeRepository", proxy(CollegeRepository.class, Map.of("findAll", List.of())));
        setField(service, "userRepository", proxy(
                UserRepository.class,
                Map.of(
                        "findByRoleId", List.of(committeeUser),
                        "findAllById", List.of()
                )
        ));
        setField(service, "roleRepository", proxy(
                RoleRepository.class,
                Map.of("findByName", Optional.of(committeeRole))
        ));

        Page<Map<String, Object>> rows = service.findStudentListRowsWithFilter(
                null,
                null,
                null,
                null,
                null,
                "NORMAL_STUDENT",
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(1, rows.getTotalElements());
        assertEquals("20230002", rows.getContent().get(0).get("studentNo"));
        assertEquals(false, rows.getContent().get(0).get("classCommittee"));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Map<String, Object> responses) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (responses.containsKey(method.getName())) {
                        return responses.get(method.getName());
                    }
                    if ("toString".equals(method.getName())) {
                        return type.getSimpleName() + "TestProxy";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Student student(Long id, String studentNo, String name, Long userId) {
        Student student = new Student();
        student.setId(id);
        student.setStudentNo(studentNo);
        student.setName(name);
        student.setClassName("计科231");
        student.setTeacherId(3L);
        student.setUserId(userId);
        return student;
    }
}
