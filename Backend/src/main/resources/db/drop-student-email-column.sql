-- =============================================================================
-- 邮箱字段迁移脚本
-- 说明：项目当前使用 users.email 保存学生邮箱；如果历史库仍有 students.email，
--      则先迁移到 users.email，再删除 students.email。
-- 执行时间：2026-05-12
-- =============================================================================

SET @student_email_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'students'
    AND COLUMN_NAME = 'email'
);

-- 仅当 students.email 存在时迁移历史邮箱数据。
SET @migrate_student_email_sql := IF(
  @student_email_exists > 0,
  'UPDATE users u
   INNER JOIN students s ON u.id = s.user_id
   SET u.email = s.email
   WHERE s.email IS NOT NULL AND s.email != ''''
     AND (u.email IS NULL OR u.email = '''')',
  'SELECT ''students.email 不存在，跳过邮箱迁移'' AS message'
);
PREPARE migrate_student_email_stmt FROM @migrate_student_email_sql;
EXECUTE migrate_student_email_stmt;
DEALLOCATE PREPARE migrate_student_email_stmt;

-- 仅当 students.email 存在时删除该列；不存在时不会报错。
SET @drop_student_email_sql := IF(
  @student_email_exists > 0,
  'ALTER TABLE students DROP COLUMN email',
  'SELECT ''students.email 不存在，跳过删除字段'' AS message'
);
PREPARE drop_student_email_stmt FROM @drop_student_email_sql;
EXECUTE drop_student_email_stmt;
DEALLOCATE PREPARE drop_student_email_stmt;

-- 验证：当前学生账号邮箱绑定情况。
SELECT
  COUNT(*) AS total_students,
  SUM(CASE WHEN u.email IS NOT NULL AND u.email != '' THEN 1 ELSE 0 END) AS bound_email_count,
  SUM(CASE WHEN u.email IS NULL OR u.email = '' THEN 1 ELSE 0 END) AS unbound_email_count
FROM students s
LEFT JOIN users u ON s.user_id = u.id;
