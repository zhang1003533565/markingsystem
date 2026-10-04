package com.markingsystem.service;

import com.markingsystem.entity.Subject;
import com.markingsystem.repository.SubjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SubjectService {
    
    @Autowired
    private SubjectRepository subjectRepository;
    
    public List<Subject> findAll() {
        return subjectRepository.findAll();
    }
    
    public Optional<Subject> findById(Long id) {
        return subjectRepository.findById(id);
    }
    
    public Optional<Subject> findBySubjectCode(String subjectCode) {
        return subjectRepository.findBySubjectCode(subjectCode);
    }
    
    public Optional<Subject> findBySubjectName(String subjectName) {
        return subjectRepository.findBySubjectName(subjectName);
    }
    
    @Transactional
    public Subject save(Subject subject) {
        return subjectRepository.save(subject);
    }
    
    @Transactional
    public Subject createSubject(String subjectCode, String subjectName, String subjectType, Double maxScore, Boolean isRequired, Integer displayOrder, String description) {
        Subject subject = new Subject();
        subject.setSubjectCode(subjectCode);
        subject.setSubjectName(subjectName);
        subject.setSubjectType(subjectType);
        subject.setMaxScore(maxScore);
        subject.setIsRequired(isRequired);
        subject.setDisplayOrder(displayOrder);
        subject.setDescription(description);
        return subjectRepository.save(subject);
    }
    
    @Transactional
    public Optional<Subject> updateSubject(Long id, Subject subject) {
        return subjectRepository.findById(id).map(existingSubject -> {
            existingSubject.setSubjectCode(subject.getSubjectCode());
            existingSubject.setSubjectName(subject.getSubjectName());
            existingSubject.setSubjectType(subject.getSubjectType());
            existingSubject.setMaxScore(subject.getMaxScore());
            existingSubject.setIsRequired(subject.getIsRequired());
            existingSubject.setDisplayOrder(subject.getDisplayOrder());
            existingSubject.setDescription(subject.getDescription());
            existingSubject.setStatus(subject.getStatus());
            return subjectRepository.save(existingSubject);
        });
    }
    
    @Transactional
    public void deleteById(Long id) {
        subjectRepository.deleteById(id);
    }
    
    @Transactional
    public void batchDelete(List<Long> ids) {
        subjectRepository.deleteAllById(ids);
    }
    
    public boolean existsBySubjectCode(String subjectCode) {
        return subjectRepository.existsBySubjectCode(subjectCode);
    }
    
    public boolean existsBySubjectName(String subjectName) {
        return subjectRepository.existsBySubjectName(subjectName);
    }
    
    public List<Subject> findBySubjectType(String subjectType) {
        return subjectRepository.findBySubjectType(subjectType);
    }
    
    public List<Subject> findByStatus(String status) {
        return subjectRepository.findByStatus(status);
    }
    
    public List<Subject> findBySubjectTypeAndStatus(String subjectType, String status) {
        return subjectRepository.findBySubjectTypeAndStatus(subjectType, status);
    }
    
    @Transactional
    public Optional<Subject> updateStatus(Long id, String status) {
        return subjectRepository.findById(id).map(subject -> {
            subject.setStatus(status);
            return subjectRepository.save(subject);
        });
    }
}