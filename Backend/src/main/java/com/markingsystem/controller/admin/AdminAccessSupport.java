package com.markingsystem.controller.admin;

import com.markingsystem.util.RequestUserContext;

final class AdminAccessSupport {

    private AdminAccessSupport() {
    }

    static RequestUserContext requireAdmin(String userId, String role, String teacherId, String className, String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        if (!context.isAdmin()) {
            throw new IllegalArgumentException("仅系统管理员可访问该接口");
        }
        return context;
    }

    static RequestUserContext requireAdminOrTeacher(String userId, String role, String teacherId, String className, String studentNo) {
        RequestUserContext context = RequestUserContext.fromHeaders(userId, role, teacherId, className, studentNo);
        if (!context.isAdmin() && !context.isTeacher()) {
            throw new IllegalArgumentException("无权访问该接口");
        }
        return context;
    }
}
