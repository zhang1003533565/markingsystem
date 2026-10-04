package com.markingsystem.service;

import com.markingsystem.entity.AcademicYear;
import com.markingsystem.repository.AcademicYearRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AcademicYearService {

    @Autowired
    private AcademicYearRepository academicYearRepository;

    public List<AcademicYear> findAll() {
        return academicYearRepository.findAll().stream()
                .sorted(Comparator.comparing(AcademicYear::getName).reversed())
                .toList();
    }

    public List<AcademicYear> findActive() {
        return academicYearRepository.findByStatus("active").stream()
                .sorted(Comparator.comparing(AcademicYear::getName).reversed())
                .toList();
    }

    public Optional<AcademicYear> findById(Long id) {
        return academicYearRepository.findById(id);
    }

    public Optional<AcademicYear> findCurrent() {
        return academicYearRepository.findByCurrentTrue();
    }

    public AcademicYear save(AcademicYear academicYear) {
        validate(academicYear.getName());
        academicYear.setName(academicYear.getName().trim());
        if (academicYearRepository.findByName(academicYear.getName()).isPresent()) {
            throw new IllegalArgumentException("该学年已存在，请勿重复新增");
        }
        if (academicYear.getStatus() == null || academicYear.getStatus().isBlank()) {
            academicYear.setStatus("active");
        }
        if (Boolean.TRUE.equals(academicYear.getCurrent())) {
            clearCurrentFlag();
            academicYear.setStatus("active");
        } else if (academicYearRepository.findByCurrentTrue().isEmpty()) {
            academicYear.setCurrent(true);
            academicYear.setStatus("active");
        }
        return academicYearRepository.save(academicYear);
    }

    public Optional<AcademicYear> update(Long id, AcademicYear academicYear) {
        return academicYearRepository.findById(id).map(existing -> {
            validate(academicYear.getName());
            String nextName = academicYear.getName().trim();
            academicYearRepository.findByName(nextName)
                    .filter(item -> !item.getId().equals(id))
                    .ifPresent(item -> {
                        throw new IllegalArgumentException("该学年已存在，请勿重复保存");
                    });
            existing.setName(nextName);
            if (academicYear.getStatus() != null && !academicYear.getStatus().isBlank()) {
                if (Boolean.TRUE.equals(existing.getCurrent()) && "inactive".equals(academicYear.getStatus())) {
                    throw new RuntimeException("当前学年不能停用");
                }
                existing.setStatus(academicYear.getStatus());
            }
            if (Boolean.TRUE.equals(academicYear.getCurrent())) {
                clearCurrentFlag();
                existing.setCurrent(true);
                existing.setStatus("active");
            } else if (Boolean.FALSE.equals(academicYear.getCurrent()) && Boolean.TRUE.equals(existing.getCurrent())) {
                throw new RuntimeException("当前学年不能取消，请先设置其他学年为当前学年");
            }
            return academicYearRepository.save(existing);
        });
    }

    public void deleteById(Long id) {
        academicYearRepository.findById(id).ifPresent(item -> {
            if (Boolean.TRUE.equals(item.getCurrent())) {
                throw new RuntimeException("当前学年不能删除");
            }
            academicYearRepository.deleteById(id);
        });
    }

    public Optional<AcademicYear> toggleStatus(Long id) {
        return academicYearRepository.findById(id).map(item -> {
            if (Boolean.TRUE.equals(item.getCurrent()) && "active".equals(item.getStatus())) {
                throw new RuntimeException("当前学年不能停用");
            }
            item.setStatus("active".equals(item.getStatus()) ? "inactive" : "active");
            return academicYearRepository.save(item);
        });
    }

    public Optional<AcademicYear> setCurrent(Long id) {
        return academicYearRepository.findById(id).map(item -> {
            clearCurrentFlag();
            item.setCurrent(true);
            item.setStatus("active");
            return academicYearRepository.save(item);
        });
    }

    private void clearCurrentFlag() {
        academicYearRepository.findByCurrentTrue().ifPresent(current -> {
            current.setCurrent(false);
            academicYearRepository.save(current);
        });
    }

    private void validate(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("学年名称不能为空");
        }
        if (!name.trim().matches("\\d{4}-\\d{4}")) {
            throw new IllegalArgumentException("学年名称格式应为 2026-2027");
        }
        String[] parts = name.trim().split("-");
        int start = Integer.parseInt(parts[0]);
        int end = Integer.parseInt(parts[1]);
        if (end != start + 1) {
            throw new IllegalArgumentException("学年结束年份应为开始年份的下一年");
        }
    }
}
