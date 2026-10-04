package com.markingsystem.service;

import com.markingsystem.entity.College;
import com.markingsystem.entity.Major;
import com.markingsystem.repository.CollegeRepository;
import com.markingsystem.repository.MajorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CollegeService {
    
    @Autowired
    private CollegeRepository collegeRepository;

    @Autowired
    private MajorRepository majorRepository;

    @Autowired
    private MajorService majorService;
    
    public List<College> findAll() {
        return collegeRepository.findAll();
    }
    
    public Optional<College> findById(Long id) {
        return collegeRepository.findById(id);
    }

    public List<College> findByStatus(String status) {
        return collegeRepository.findByStatus(status);
    }

    public List<College> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return collegeRepository.findAll();
        }
        return collegeRepository.findByNameContaining(keyword);
    }

    public College save(College college) {
        if (college.getName() == null || college.getName().trim().isEmpty()) {
            throw new RuntimeException("学院名称不能为空");
        }
        if (college.getStatus() == null || college.getStatus().isBlank()) {
            college.setStatus("active");
        }
        return collegeRepository.save(college);
    }

    public Optional<College> update(Long id, College college) {
        return collegeRepository.findById(id).map(existingCollege -> {
            if (college.getName() == null || college.getName().trim().isEmpty()) {
                throw new RuntimeException("学院名称不能为空");
            }

            existingCollege.setName(college.getName());
            existingCollege.setStatus(
                    college.getStatus() == null || college.getStatus().isBlank()
                            ? existingCollege.getStatus()
                            : college.getStatus()
            );
            return collegeRepository.save(existingCollege);
        });
    }
    
    public Map<String, Object> getDeleteImpact(Long id) {
        College college = collegeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("学院不存在"));

        List<Major> majors = majorRepository.findByCollegeId(id);
        List<Map<String, Object>> majorImpacts = new ArrayList<>();
        int classCount = 0;
        int studentCount = 0;
        int userCount = 0;
        List<Map<String, Object>> classes = new ArrayList<>();
        List<Map<String, Object>> students = new ArrayList<>();
        List<Map<String, Object>> users = new ArrayList<>();

        for (Major major : majors) {
            Map<String, Object> impact = majorService.getDeleteImpact(major.getId());
            majorImpacts.add(Map.of(
                    "id", major.getId(),
                    "name", major.getName(),
                    "classCount", impact.get("classCount"),
                    "studentCount", impact.get("studentCount"),
                    "userCount", impact.get("userCount")
            ));
            classCount += ((Number) impact.get("classCount")).intValue();
            studentCount += ((Number) impact.get("studentCount")).intValue();
            userCount += ((Number) impact.get("userCount")).intValue();
            classes.addAll((List<Map<String, Object>>) impact.get("classes"));
            students.addAll((List<Map<String, Object>>) impact.get("students"));
            users.addAll((List<Map<String, Object>>) impact.get("users"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collegeId", college.getId());
        result.put("collegeName", college.getName());
        result.put("majorCount", majors.size());
        result.put("classCount", classCount);
        result.put("studentCount", studentCount);
        result.put("userCount", userCount);
        result.put("majors", majorImpacts);
        result.put("classes", classes.stream().limit(20).toList());
        result.put("students", students.stream().limit(10).toList());
        result.put("users", users.stream().limit(10).toList());
        return result;
    }

    @Transactional
    public void deleteById(Long id, boolean force, boolean deleteUsers) {
        College college = collegeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("学院不存在"));

        List<Major> majors = majorRepository.findByCollegeId(id);
        if (!force && !majors.isEmpty()) {
            throw new IllegalStateException("该学院下仍有专业数据，无法直接删除。请先处理关联专业，或使用强制删除。");
        }

        if (force && !majors.isEmpty()) {
            for (Major major : majors) {
                majorService.deleteById(major.getId(), true, deleteUsers);
            }
        }

        collegeRepository.delete(college);
    }
    
    public Optional<College> toggleStatus(Long id) {
        return collegeRepository.findById(id).map(college -> {
            String newStatus = "active".equals(college.getStatus()) ? "inactive" : "active";
            college.setStatus(newStatus);
            return collegeRepository.save(college);
        });
    }
}
