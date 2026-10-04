package com.markingsystem.service;

import com.markingsystem.entity.GradeInfo;
import com.markingsystem.repository.ClassInfoRepository;
import com.markingsystem.repository.GradeInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class GradeInfoService {

    @Autowired
    private GradeInfoRepository gradeInfoRepository;

    @Autowired
    private ClassInfoRepository classInfoRepository;

    public List<GradeInfo> findAll() {
        return gradeInfoRepository.findAll().stream()
                .sorted(Comparator.comparing(GradeInfo::getName).reversed())
                .toList();
    }

    public List<GradeInfo> findActive() {
        return gradeInfoRepository.findByStatus("active").stream()
                .sorted(Comparator.comparing(GradeInfo::getName).reversed())
                .toList();
    }

    public GradeInfo save(GradeInfo gradeInfo) {
        validate(gradeInfo.getName(), null);
        gradeInfo.setName(gradeInfo.getName().trim());
        if (gradeInfo.getStatus() == null || gradeInfo.getStatus().isBlank()) {
          gradeInfo.setStatus("active");
        }
        return gradeInfoRepository.save(gradeInfo);
    }

    public Optional<GradeInfo> update(Long id, GradeInfo gradeInfo) {
        return gradeInfoRepository.findById(id).map(existing -> {
            validate(gradeInfo.getName(), id);
            existing.setName(gradeInfo.getName().trim());
            if (gradeInfo.getStatus() != null && !gradeInfo.getStatus().isBlank()) {
                existing.setStatus(gradeInfo.getStatus());
            }
            return gradeInfoRepository.save(existing);
        });
    }

    public void deleteById(Long id) {
        GradeInfo grade = gradeInfoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("年级不存在"));
        boolean usedByClass = classInfoRepository.findAll().stream()
                .anyMatch(item -> grade.getName().equals(item.getGrade()));
        if (usedByClass) {
            throw new IllegalArgumentException("该年级已被班级使用，不能删除");
        }
        gradeInfoRepository.deleteById(id);
    }

    public Optional<GradeInfo> toggleStatus(Long id) {
        return gradeInfoRepository.findById(id).map(item -> {
            item.setStatus("active".equals(item.getStatus()) ? "inactive" : "active");
            return gradeInfoRepository.save(item);
        });
    }

    private void validate(String name, Long currentId) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("年级名称不能为空");
        }
        gradeInfoRepository.findByName(name.trim()).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new IllegalArgumentException("年级名称已存在");
            }
        });
    }
}
