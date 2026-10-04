-- 初始化数据
-- 执行顺序：roles -> users -> colleges -> majors -> classes -> students -> academic_years -> subjects

-- 1. 角色数据
INSERT IGNORE INTO roles (name, description, created_at, updated_at) VALUES
('ADMIN', '管理员', NOW(), NOW()),
('TEACHER', '教师', NOW(), NOW()),
('STUDENT', '学生', NOW(), NOW()),
('CLASS_COMMITTEE', '班委', NOW(), NOW());

-- 2. 用户数据
INSERT INTO users (username, password, nickname, role_id, created_at, updated_at)
SELECT 'admin', '$2a$10$hW7J4jJgUS/ZJjNcShCy..d3VGEQtaZkBT2iexOjX9jQvJ1Qsfl8O', '系统管理员', id, NOW(), NOW()
FROM roles
WHERE name = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

INSERT INTO users (username, password, nickname, role_id, created_at, updated_at)
SELECT 'teacher1', '$2a$10$hW7J4jJgUS/ZJjNcShCy..d3VGEQtaZkBT2iexOjX9jQvJ1Qsfl8O', '张老师', id, NOW(), NOW()
FROM roles
WHERE name = 'TEACHER'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'teacher1');

INSERT INTO users (username, password, nickname, role_id, created_at, updated_at)
SELECT 'teacher2', '$2a$10$hW7J4jJgUS/ZJjNcShCy..d3VGEQtaZkBT2iexOjX9jQvJ1Qsfl8O', '李老师', id, NOW(), NOW()
FROM roles
WHERE name = 'TEACHER'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'teacher2');

INSERT INTO users (username, password, nickname, role_id, created_at, updated_at)
SELECT 'teacher3', '$2a$10$hW7J4jJgUS/ZJjNcShCy..d3VGEQtaZkBT2iexOjX9jQvJ1Qsfl8O', '王老师', id, NOW(), NOW()
FROM roles
WHERE name = 'TEACHER'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'teacher3');

-- 3. 学院数据
INSERT IGNORE INTO colleges (name, status, created_at, updated_at) VALUES
('计算机学院', 'active', NOW(), NOW()),
('电子工程学院', 'active', NOW(), NOW()),
('机械工程学院', 'active', NOW(), NOW()),
('经济管理学院', 'active', NOW(), NOW()),
('外国语学院', 'inactive', NOW(), NOW());

-- 4. 专业数据
INSERT IGNORE INTO majors (name, college_id, status, created_at, updated_at) VALUES
('软件工程', 1, 'active', NOW(), NOW()),
('计算机科学与技术', 1, 'active', NOW(), NOW()),
('网络工程', 1, 'active', NOW(), NOW()),
('电子信息工程', 2, 'active', NOW(), NOW()),
('通信工程', 2, 'active', NOW(), NOW()),
('机械设计制造及其自动化', 3, 'active', NOW(), NOW()),
('工商管理', 4, 'active', NOW(), NOW()),
('会计学', 4, 'active', NOW(), NOW());

-- 5. 班级数据
INSERT INTO classes (name, major_id, college_id, grade, teacher_id, status, created_at, updated_at)
SELECT '软工2401', 1, 1, '2024', id, 'active', NOW(), NOW()
FROM users
WHERE username = 'teacher1'
  AND NOT EXISTS (SELECT 1 FROM classes WHERE name = '软工2401');

INSERT INTO classes (name, major_id, college_id, grade, teacher_id, status, created_at, updated_at)
SELECT '软工2402', 1, 1, '2024', id, 'active', NOW(), NOW()
FROM users
WHERE username = 'teacher2'
  AND NOT EXISTS (SELECT 1 FROM classes WHERE name = '软工2402');

INSERT INTO classes (name, major_id, college_id, grade, teacher_id, status, created_at, updated_at)
SELECT '计科2401', 2, 1, '2024', id, 'active', NOW(), NOW()
FROM users
WHERE username = 'teacher3'
  AND NOT EXISTS (SELECT 1 FROM classes WHERE name = '计科2401');

INSERT INTO classes (name, major_id, college_id, grade, teacher_id, status, created_at, updated_at)
SELECT '网工2401', 3, 1, '2024', id, 'active', NOW(), NOW()
FROM users
WHERE username = 'teacher1'
  AND NOT EXISTS (SELECT 1 FROM classes WHERE name = '网工2401');

INSERT INTO classes (name, major_id, college_id, grade, teacher_id, status, created_at, updated_at)
SELECT '电信2401', 4, 2, '2024', id, 'active', NOW(), NOW()
FROM users
WHERE username = 'teacher2'
  AND NOT EXISTS (SELECT 1 FROM classes WHERE name = '电信2401');

INSERT INTO classes (name, major_id, college_id, grade, teacher_id, status, created_at, updated_at)
SELECT '通信2401', 5, 2, '2024', id, 'active', NOW(), NOW()
FROM users
WHERE username = 'teacher3'
  AND NOT EXISTS (SELECT 1 FROM classes WHERE name = '通信2401');

INSERT INTO classes (name, major_id, college_id, grade, teacher_id, status, created_at, updated_at)
SELECT '工商2401', 7, 4, '2024', id, 'active', NOW(), NOW()
FROM users
WHERE username = 'teacher1'
  AND NOT EXISTS (SELECT 1 FROM classes WHERE name = '工商2401');

INSERT INTO classes (name, major_id, college_id, grade, teacher_id, status, created_at, updated_at)
SELECT '会计2401', 8, 4, '2024', id, 'active', NOW(), NOW()
FROM users
WHERE username = 'teacher2'
  AND NOT EXISTS (SELECT 1 FROM classes WHERE name = '会计2401');

-- 6. 学生数据
INSERT INTO students (student_no, name, class_name, teacher_id, created_at, updated_at)
SELECT '2024001', '张三', '软工2401', id, NOW(), NOW()
FROM users
WHERE username = 'teacher1'
  AND NOT EXISTS (SELECT 1 FROM students WHERE student_no = '2024001');

INSERT INTO students (student_no, name, class_name, teacher_id, created_at, updated_at)
SELECT '2024002', '李四', '软工2401', id, NOW(), NOW()
FROM users
WHERE username = 'teacher1'
  AND NOT EXISTS (SELECT 1 FROM students WHERE student_no = '2024002');

INSERT INTO students (student_no, name, class_name, teacher_id, created_at, updated_at)
SELECT '2024003', '王五', '软工2401', id, NOW(), NOW()
FROM users
WHERE username = 'teacher1'
  AND NOT EXISTS (SELECT 1 FROM students WHERE student_no = '2024003');

INSERT INTO students (student_no, name, class_name, teacher_id, created_at, updated_at)
SELECT '2024004', '赵六', '软工2402', id, NOW(), NOW()
FROM users
WHERE username = 'teacher2'
  AND NOT EXISTS (SELECT 1 FROM students WHERE student_no = '2024004');

INSERT INTO students (student_no, name, class_name, teacher_id, created_at, updated_at)
SELECT '2024005', '钱七', '软工2402', id, NOW(), NOW()
FROM users
WHERE username = 'teacher2'
  AND NOT EXISTS (SELECT 1 FROM students WHERE student_no = '2024005');

INSERT INTO students (student_no, name, class_name, teacher_id, created_at, updated_at)
SELECT '2024006', '孙八', '计科2401', id, NOW(), NOW()
FROM users
WHERE username = 'teacher3'
  AND NOT EXISTS (SELECT 1 FROM students WHERE student_no = '2024006');

INSERT INTO students (student_no, name, class_name, teacher_id, created_at, updated_at)
SELECT '2024007', '周九', '计科2401', id, NOW(), NOW()
FROM users
WHERE username = 'teacher3'
  AND NOT EXISTS (SELECT 1 FROM students WHERE student_no = '2024007');

INSERT INTO students (student_no, name, class_name, teacher_id, created_at, updated_at)
SELECT '2024008', '吴十', '网工2401', id, NOW(), NOW()
FROM users
WHERE username = 'teacher1'
  AND NOT EXISTS (SELECT 1 FROM students WHERE student_no = '2024008');

INSERT INTO students (student_no, name, class_name, teacher_id, created_at, updated_at)
SELECT '2024009', '郑十一', '电信2401', id, NOW(), NOW()
FROM users
WHERE username = 'teacher2'
  AND NOT EXISTS (SELECT 1 FROM students WHERE student_no = '2024009');

INSERT INTO students (student_no, name, class_name, teacher_id, created_at, updated_at)
SELECT '2024010', '陈十二', '通信2401', id, NOW(), NOW()
FROM users
WHERE username = 'teacher3'
  AND NOT EXISTS (SELECT 1 FROM students WHERE student_no = '2024010');

INSERT INTO students (student_no, name, class_name, teacher_id, created_at, updated_at)
SELECT '2024011', '刘一', '工商2401', id, NOW(), NOW()
FROM users
WHERE username = 'teacher1'
  AND NOT EXISTS (SELECT 1 FROM students WHERE student_no = '2024011');

INSERT INTO students (student_no, name, class_name, teacher_id, created_at, updated_at)
SELECT '2024012', '陈二', '会计2401', id, NOW(), NOW()
FROM users
WHERE username = 'teacher2'
  AND NOT EXISTS (SELECT 1 FROM students WHERE student_no = '2024012');

-- 7. 成绩导入时的非科目字段
INSERT IGNORE INTO academic_years (name, status, created_at, updated_at) VALUES
('2027-2028', 'active', NOW(), NOW()),
('2026-2027', 'active', NOW(), NOW()),
('2025-2026', 'active', NOW(), NOW()),
('2024-2025', 'inactive', NOW(), NOW());

-- 8. 成绩导入时的非科目字段
INSERT IGNORE INTO subject_rain (subject_name, category) VALUES
('学号', 'NON_SUBJECT'),
('姓名', 'NON_SUBJECT'),
('班级', 'NON_SUBJECT'),
('学院', 'NON_SUBJECT'),
('专业', 'NON_SUBJECT'),
('账号', 'NON_SUBJECT'),
('密码', 'NON_SUBJECT');

-- 9. 示例科目
INSERT IGNORE INTO subject_rain (subject_name, category) VALUES
('高等数学', 'SUBJECT'),
('大学英语', 'SUBJECT'),
('数据结构', 'SUBJECT'),
('计算机网络', 'SUBJECT'),
('数据库原理', 'SUBJECT'),
('操作系统', 'SUBJECT'),
('软件工程', 'SUBJECT'),
('Java程序设计', 'SUBJECT');

-- 10. 附加分模板
INSERT INTO extra_score_templates (name, score_type, max_score, description, proof_requirement, creator_id, status, created_at, updated_at)
SELECT '德育通用模板', 'MORAL', 20, '适用于志愿服务、社会实践、思想道德表现等德育附加分申请。', '需上传证书或活动证明，并补充文字说明。', u.id, 'active', NOW(), NOW()
FROM users u WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM extra_score_templates WHERE name = '德育通用模板');

INSERT INTO extra_score_templates (name, score_type, max_score, description, proof_requirement, creator_id, status, created_at, updated_at)
SELECT '智育竞赛模板', 'INTELLECTUAL', 20, '适用于竞赛获奖、学术成果、科研立项等智育附加分申请。', '需上传获奖证书、成绩单或成果证明。', u.id, 'active', NOW(), NOW()
FROM users u WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM extra_score_templates WHERE name = '智育竞赛模板');

INSERT INTO extra_score_templates (name, score_type, max_score, description, proof_requirement, creator_id, status, created_at, updated_at)
SELECT '体育赛事模板', 'PHYSICAL', 20, '适用于校运会、体育赛事获奖、体能竞赛等体育附加分申请。', '需上传赛事证明、证书或现场图片。', u.id, 'active', NOW(), NOW()
FROM users u WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM extra_score_templates WHERE name = '体育赛事模板');

-- 11. 老师模板选择示例
INSERT INTO teacher_extra_score_templates (teacher_id, score_type, template_id, created_at, updated_at)
SELECT u.id, 'MORAL', t.id, NOW(), NOW()
FROM users u
JOIN extra_score_templates t ON t.name = '德育通用模板'
WHERE u.username = 'teacher1'
  AND NOT EXISTS (
    SELECT 1 FROM teacher_extra_score_templates s
    WHERE s.teacher_id = u.id AND s.score_type = 'MORAL'
  );

INSERT INTO teacher_extra_score_templates (teacher_id, score_type, template_id, created_at, updated_at)
SELECT u.id, 'INTELLECTUAL', t.id, NOW(), NOW()
FROM users u
JOIN extra_score_templates t ON t.name = '智育竞赛模板'
WHERE u.username = 'teacher1'
  AND NOT EXISTS (
    SELECT 1 FROM teacher_extra_score_templates s
    WHERE s.teacher_id = u.id AND s.score_type = 'INTELLECTUAL'
  );

INSERT INTO teacher_extra_score_templates (teacher_id, score_type, template_id, created_at, updated_at)
SELECT u.id, 'PHYSICAL', t.id, NOW(), NOW()
FROM users u
JOIN extra_score_templates t ON t.name = '体育赛事模板'
WHERE u.username = 'teacher1'
  AND NOT EXISTS (
    SELECT 1 FROM teacher_extra_score_templates s
    WHERE s.teacher_id = u.id AND s.score_type = 'PHYSICAL'
  );
