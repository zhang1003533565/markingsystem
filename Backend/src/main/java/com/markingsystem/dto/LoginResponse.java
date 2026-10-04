package com.markingsystem.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private Integer code;
    private String message;
    private UserInfo data;
    
    public LoginResponse() {}
    
    public LoginResponse(Integer code, String message, UserInfo data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String nickname;
        private String role;
        private Long teacherId;
        private String className;
        private String studentNo;
        private String avatarUrl;
        private String email;
        private String phone;
        private Boolean passwordResetRequired;
        private Boolean forceEmailBindingRequired;
        private String token;
        
        public UserInfo() {}
        
        public UserInfo(Long id, String username, String nickname, String role, Long teacherId, String className, String studentNo) {
            this.id = id;
            this.username = username;
            this.nickname = nickname;
            this.role = role;
            this.teacherId = teacherId;
            this.className = className;
            this.studentNo = studentNo;
        }
    }
    
    public static LoginResponse success(UserInfo userInfo) {
        return new LoginResponse(200, "登录成功", userInfo);
    }
    
    public static LoginResponse error(String message) {
        return new LoginResponse(401, message, null);
    }
}
