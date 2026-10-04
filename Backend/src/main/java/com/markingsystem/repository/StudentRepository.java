package com.markingsystem.repository;

import com.markingsystem.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    
    Optional<Student> findByStudentNo(String studentNo);
    
    boolean existsByStudentNo(String studentNo);
    
    List<Student> findByTeacherId(Long teacherId);
    
    List<Student> findByClassName(String className);

    List<Student> findByClassNameIn(List<String> classNames);

    Optional<Student> findByUserId(Long userId);

    List<Student> findByTeacherIdAndClassName(Long teacherId, String className);
}
