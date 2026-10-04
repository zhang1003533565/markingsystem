package com.markingsystem.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class RequestUserContext {

    private final Long userId;
    private final String role;
    private final Long teacherId;
    private final String className;
    private final String studentNo;

    public RequestUserContext(Long userId, String role, Long teacherId, String className, String studentNo) {
        this.userId = userId;
        this.role = role;
        this.teacherId = teacherId;
        this.className = className;
        this.studentNo = studentNo;
    }

    public static RequestUserContext fromHeaders(String userId, String role, String teacherId, String className, String studentNo) {
        return new RequestUserContext(parseLong(userId), normalize(role), parseLong(teacherId), normalize(className), normalize(studentNo));
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.contains("%")) {
            try {
                normalized = URLDecoder.decode(normalized, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed percent-encoding and keep the original value.
            }
        }
        return normalized;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    public boolean isTeacher() {
        return "TEACHER".equalsIgnoreCase(role);
    }

    public boolean isClassCommittee() {
        return "CLASS_COMMITTEE".equalsIgnoreCase(role);
    }

    public boolean isStudent() {
        return "STUDENT".equalsIgnoreCase(role);
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public String getClassName() {
        return className;
    }

    public String getStudentNo() {
        return studentNo;
    }
}
