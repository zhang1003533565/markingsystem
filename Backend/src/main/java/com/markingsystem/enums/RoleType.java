package com.markingsystem.enums;

public enum RoleType {
    ADMIN("管理员"),
    STUDENT("学生"),
    TEACHER("教师"),
    CLASS_COMMITTEE("班委");
    
    private final String displayName;
    
    RoleType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public static RoleType fromString(String role) {
        for (RoleType type : RoleType.values()) {
            if (type.name().equalsIgnoreCase(role)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知角色类型: " + role);
    }
}
