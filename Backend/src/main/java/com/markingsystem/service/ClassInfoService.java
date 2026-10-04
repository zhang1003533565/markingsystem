package com.markingsystem.service;

import com.markingsystem.entity.ClassInfo;
import com.markingsystem.entity.GradeInfo;
import com.markingsystem.entity.Major;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.GradeInfoRepository;
import com.markingsystem.repository.MajorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ClassInfoService {
    
    @Autowired
    private ClassInfoRepository classInfoRepository;

    @Autowired
    private MajorRepository majorRepository;

    @Autowired
    private GradeInfoRepository gradeInfoRepository;
    
    public List<ClassInfo> findAll() {
        return classInfoRepository.findAll();
    }

    public List<ClassInfo> findManagedByTeacherId(Long teacherId) {
        if (teacherId == null) {
            return List.of();
        }
        return classInfoRepository.findByTeacherId(teacherId);
    }
    
    public Optional<ClassInfo> findById(Long id) {
        return classInfoRepository.findById(id);
    }
    
    public Optional<ClassInfo> findByName(String name) {
        return classInfoRepository.findByName(name);
    }
    
    public List<ClassInfo> findByCollegeId(Long collegeId) {
        return classInfoRepository.findAll().stream()
                .filter(classInfo -> classInfo.getMajor() != null && collegeId.equals(classInfo.getMajor().getCollegeId()))
                .collect(Collectors.toList());
    }
    
    public List<ClassInfo> findByMajorId(Long majorId) {
        return classInfoRepository.findByMajorId(majorId);
    }
    
    public List<ClassInfo> findByGrade(String grade) {
        return classInfoRepository.findByGrade(grade);
    }
    
    public List<ClassInfo> search(String keyword, Long collegeId, Long majorId, String grade) {
        List<ClassInfo> classes;
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            classes = classInfoRepository.findByNameContaining(keyword);
        } else {
            classes = classInfoRepository.findAll();
        }
        
        if (collegeId != null) {
            classes = classes.stream()
                    .filter(c -> c.getMajor() != null && collegeId.equals(c.getMajor().getCollegeId()))
                    .collect(Collectors.toList());
        }
        
        if (majorId != null) {
            classes = classes.stream()
                    .filter(c -> c.getMajorId().equals(majorId))
                    .collect(Collectors.toList());
        }
        
        if (grade != null && !grade.trim().isEmpty()) {
            classes = classes.stream()
                    .filter(c -> grade.equals(c.getGrade()))
                    .collect(Collectors.toList());
        }
        
        return classes;
    }
    
    public ClassInfo save(ClassInfo classInfo) {
        if (classInfo.getName() == null || classInfo.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("班级名称不能为空");
        }
        if (classInfo.getMajorId() == null) {
            throw new IllegalArgumentException("所属专业不能为空");
        }
        if (classInfo.getTeacherId() == null) {
            throw new IllegalArgumentException("班主任不能为空");
        }
        String normalizedGrade = normalizeAndValidateGrade(classInfo.getGrade());
        String normalizedName = classInfo.getName().trim();
        if (classInfoRepository.existsByName(normalizedName)) {
            throw new IllegalArgumentException("班级名称已存在");
        }
        Major major = majorRepository.findById(classInfo.getMajorId())
                .orElseThrow(() -> new IllegalArgumentException("所属专业不存在"));
        classInfo.setName(normalizedName);
        classInfo.setGrade(normalizedGrade);
        classInfo.setCollegeId(major.getCollegeId());
        return classInfoRepository.save(classInfo);
    }
    
    public Optional<ClassInfo> update(Long id, ClassInfo classInfo) {
        return classInfoRepository.findById(id).map(existingClass -> {
            if (classInfo.getName() == null || classInfo.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("班级名称不能为空");
            }
            if (classInfo.getMajorId() == null) {
                throw new IllegalArgumentException("所属专业不能为空");
            }
            if (classInfo.getTeacherId() == null) {
                throw new IllegalArgumentException("班主任不能为空");
            }
            String normalizedGrade = normalizeAndValidateGrade(classInfo.getGrade());
            String normalizedName = classInfo.getName().trim();
            // 检查名称是否被其他班级使用
            Optional<ClassInfo> byName = classInfoRepository.findByName(normalizedName);
            if (byName.isPresent() && !byName.get().getId().equals(id)) {
                throw new IllegalArgumentException("班级名称已存在");
            }
            
            existingClass.setName(normalizedName);
            Major major = majorRepository.findById(classInfo.getMajorId())
                    .orElseThrow(() -> new IllegalArgumentException("所属专业不存在"));
            existingClass.setMajorId(classInfo.getMajorId());
            existingClass.setCollegeId(major.getCollegeId());
            existingClass.setGrade(normalizedGrade);
            existingClass.setTeacherId(classInfo.getTeacherId());
            existingClass.setStatus(classInfo.getStatus());
            return classInfoRepository.save(existingClass);
        });
    }
    
    public void deleteById(Long id) {
        classInfoRepository.deleteById(id);
    }
    
    public Optional<ClassInfo> toggleStatus(Long id) {
        return classInfoRepository.findById(id).map(classInfo -> {
            String newStatus = "active".equals(classInfo.getStatus()) ? "inactive" : "active";
            classInfo.setStatus(newStatus);
            return classInfoRepository.save(classInfo);
        });
    }

    private String normalizeAndValidateGrade(String gradeName) {
        if (gradeName == null || gradeName.trim().isEmpty()) {
            throw new IllegalArgumentException("年级不能为空");
        }
        String normalizedGrade = gradeName.trim();
        GradeInfo gradeInfo = gradeInfoRepository.findByName(normalizedGrade)
                .orElseThrow(() -> new IllegalArgumentException("年级不存在，请先在年级管理中维护"));
        if (!"active".equals(gradeInfo.getStatus())) {
            throw new IllegalArgumentException("所选年级已停用，请重新选择");
        }
        return normalizedGrade;
    }
}
