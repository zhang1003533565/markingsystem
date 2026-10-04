-- =============================================
-- 完整重建数据库（解决乱码问题）
-- =============================================

-- 1. 删除旧数据库（如果存在）
DROP DATABASE IF EXISTS marking;

-- 2. 创建新数据库（使用 utf8mb4 字符集）
CREATE DATABASE marking 
    CHARACTER SET = utf8mb4 
    COLLATE = utf8mb4_unicode_ci;

-- 3. 使用新数据库
USE marking;

-- 4. 设置连接字符集
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 执行此脚本后，重启 Spring Boot 应用
-- application.yml 中的 spring.jpa.hibernate.ddl-auto=update 会自动创建表
-- 如需导入演示数据，请手动执行 db/data.sql
