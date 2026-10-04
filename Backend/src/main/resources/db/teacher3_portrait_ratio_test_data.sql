-- 王老师画像比例测试数据
-- 依赖：Backend/src/main/resources/db/data.sql 已执行，存在 teacher3 / 王老师、计科2401、通信2401。
-- 用途：给王老师新增 7 个学生，并写入画像分析记录，便于直接查看类型比例。

SET @admin_id := (SELECT id FROM users WHERE username = 'admin' LIMIT 1);
SET @teacher3_id := (SELECT id FROM users WHERE username = 'teacher3' LIMIT 1);

-- 如果曾在 Windows PowerShell 里用管道执行本脚本，中文可能被传成问号；这里清理这些错误测试行。
DELETE FROM extra_score_templates WHERE name LIKE '%?%';
DELETE FROM subject_rain WHERE subject_name LIKE '%?%';
UPDATE students
SET user_id = NULL
WHERE student_no IN ('2025101', '2025102', '2025103', '2025104', '2025105', '2025106', '2025107');
DELETE FROM user_roles
WHERE user_id IN (
  SELECT id FROM users
  WHERE username IN (
    'wang_ratio_2025101',
    'wang_ratio_2025102',
    'wang_ratio_2025103',
    'wang_ratio_2025104',
    'wang_ratio_2025105',
    'wang_ratio_2025106',
    'wang_ratio_2025107'
  )
);
DELETE FROM users
WHERE username IN (
  'wang_ratio_2025101',
  'wang_ratio_2025102',
  'wang_ratio_2025103',
  'wang_ratio_2025104',
  'wang_ratio_2025105',
  'wang_ratio_2025106',
  'wang_ratio_2025107'
);

-- 1) 画像测试会用到的科目
INSERT IGNORE INTO subject_rain (subject_name, category, deleted) VALUES
('高等数学', 'SUBJECT', 0),
('大学英语', 'SUBJECT', 0),
('数据结构', 'SUBJECT', 0),
('线性代数', 'SUBJECT', 0),
('计算机组成原理', 'SUBJECT', 0),
('软件工程', 'SUBJECT', 0),
('Java程序设计', 'SUBJECT', 0),
('思想道德与法治', 'SUBJECT', 0),
('大学体育', 'SUBJECT', 0),
('计算机网络', 'SUBJECT', 0),
('项目管理', 'SUBJECT', 0);

SET @sub_math := (SELECT id FROM subject_rain WHERE subject_name = '高等数学' LIMIT 1);
SET @sub_english := (SELECT id FROM subject_rain WHERE subject_name = '大学英语' LIMIT 1);
SET @sub_data := (SELECT id FROM subject_rain WHERE subject_name = '数据结构' LIMIT 1);
SET @sub_linear := (SELECT id FROM subject_rain WHERE subject_name = '线性代数' LIMIT 1);
SET @sub_comp := (SELECT id FROM subject_rain WHERE subject_name = '计算机组成原理' LIMIT 1);
SET @sub_soft := (SELECT id FROM subject_rain WHERE subject_name = '软件工程' LIMIT 1);
SET @sub_java := (SELECT id FROM subject_rain WHERE subject_name = 'Java程序设计' LIMIT 1);
SET @sub_ideo := (SELECT id FROM subject_rain WHERE subject_name = '思想道德与法治' LIMIT 1);
SET @sub_pe := (SELECT id FROM subject_rain WHERE subject_name = '大学体育' LIMIT 1);
SET @sub_net := (SELECT id FROM subject_rain WHERE subject_name = '计算机网络' LIMIT 1);
SET @sub_pm := (SELECT id FROM subject_rain WHERE subject_name = '项目管理' LIMIT 1);

-- 2) 附加分模板
INSERT INTO extra_score_templates (name, score_type, max_score, description, proof_requirement, creator_id, status, created_at, updated_at)
SELECT seed.name, seed.score_type, seed.max_score, seed.description, seed.proof_requirement, @admin_id, 'active', NOW(), NOW()
FROM (
  SELECT '王老师学术科研测试模板' AS name, 'INTELLECTUAL' AS score_type, 20 AS max_score, '王老师画像比例测试：学术科研' AS description, '测试数据' AS proof_requirement
  UNION ALL SELECT '王老师竞赛测试模板', 'INTELLECTUAL', 20, '王老师画像比例测试：竞赛', '测试数据'
  UNION ALL SELECT '王老师技术项目测试模板', 'INTELLECTUAL', 20, '王老师画像比例测试：技术项目', '测试数据'
  UNION ALL SELECT '王老师就业实践测试模板', 'INTELLECTUAL', 20, '王老师画像比例测试：就业实践', '测试数据'
  UNION ALL SELECT '王老师班委志愿测试模板', 'MORAL', 20, '王老师画像比例测试：班委志愿', '测试数据'
  UNION ALL SELECT '王老师体育综合测试模板', 'PHYSICAL', 20, '王老师画像比例测试：体育综合', '测试数据'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM extra_score_templates t WHERE t.name = seed.name
);

SET @tpl_academic := (SELECT id FROM extra_score_templates WHERE name = '王老师学术科研测试模板' LIMIT 1);
SET @tpl_competition := (SELECT id FROM extra_score_templates WHERE name = '王老师竞赛测试模板' LIMIT 1);
SET @tpl_technical := (SELECT id FROM extra_score_templates WHERE name = '王老师技术项目测试模板' LIMIT 1);
SET @tpl_employment := (SELECT id FROM extra_score_templates WHERE name = '王老师就业实践测试模板' LIMIT 1);
SET @tpl_service := (SELECT id FROM extra_score_templates WHERE name = '王老师班委志愿测试模板' LIMIT 1);
SET @tpl_sport := (SELECT id FROM extra_score_templates WHERE name = '王老师体育综合测试模板' LIMIT 1);

-- 3) 学生档案，全部关联到王老师。这里不创建登录账号，测试画像比例只保留 students 一组。
INSERT INTO students (student_no, name, class_name, teacher_id, phone, user_id, created_at, updated_at) VALUES
('2025101', '王测全优', '计科2401', @teacher3_id, '13951010001', NULL, NOW(), NOW()),
('2025102', '王测学术', '计科2401', @teacher3_id, '13951010002', NULL, NOW(), NOW()),
('2025103', '王测竞赛', '计科2401', @teacher3_id, '13951010003', NULL, NOW(), NOW()),
('2025104', '王测技术', '通信2401', @teacher3_id, '13951010004', NULL, NOW(), NOW()),
('2025105', '王测就业', '通信2401', @teacher3_id, '13951010005', NULL, NOW(), NOW()),
('2025106', '王测考公', '通信2401', @teacher3_id, '13951010006', NULL, NOW(), NOW()),
('2025107', '王测潜力', '通信2401', @teacher3_id, '13951010007', NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  class_name = VALUES(class_name),
  teacher_id = VALUES(teacher_id),
  phone = VALUES(phone),
  user_id = VALUES(user_id),
  updated_at = NOW();

SET @s1 := (SELECT id FROM students WHERE student_no = '2025101' LIMIT 1);
SET @s2 := (SELECT id FROM students WHERE student_no = '2025102' LIMIT 1);
SET @s3 := (SELECT id FROM students WHERE student_no = '2025103' LIMIT 1);
SET @s4 := (SELECT id FROM students WHERE student_no = '2025104' LIMIT 1);
SET @s5 := (SELECT id FROM students WHERE student_no = '2025105' LIMIT 1);
SET @s6 := (SELECT id FROM students WHERE student_no = '2025106' LIMIT 1);
SET @s7 := (SELECT id FROM students WHERE student_no = '2025107' LIMIT 1);

-- 重跑脚本时，清理这 7 个测试学生的画像相关数据，避免重复累计。
DELETE FROM subject_score_rain WHERE student_id IN (@s1, @s2, @s3, @s4, @s5, @s6, @s7);
DELETE FROM moral_education WHERE student_id IN (@s1, @s2, @s3, @s4, @s5, @s6, @s7);
DELETE FROM intellectual_education WHERE student_id IN (@s1, @s2, @s3, @s4, @s5, @s6, @s7);
DELETE FROM physical_education WHERE student_id IN (@s1, @s2, @s3, @s4, @s5, @s6, @s7);
DELETE FROM extra_score_applications WHERE student_id IN (@s1, @s2, @s3, @s4, @s5, @s6, @s7);
DELETE FROM student_analysis_records WHERE student_no IN ('2025101', '2025102', '2025103', '2025104', '2025105', '2025106', '2025107');

-- 5) 三维成绩
INSERT INTO moral_education (student_id, activity_score, second_class_score, bonus_score, total_score, semester, academic_year, created_at, updated_at) VALUES
(@s1, 58, 18, 32, 90, '2025-2026-1', '2025-2026', NOW(), NOW()),
(@s2, 44, 12, 18, 62, '2025-2026-1', '2025-2026', NOW(), NOW()),
(@s3, 42, 10, 16, 58, '2025-2026-1', '2025-2026', NOW(), NOW()),
(@s4, 40,  8, 15, 55, '2025-2026-1', '2025-2026', NOW(), NOW()),
(@s5, 45, 14, 18, 63, '2025-2026-1', '2025-2026', NOW(), NOW()),
(@s6, 60, 20, 30, 90, '2025-2026-1', '2025-2026', NOW(), NOW()),
(@s7, 38,  8, 12, 50, '2025-2026-1', '2025-2026', NOW(), NOW());

INSERT INTO intellectual_education (student_id, course_score, bonus_score, total_score, semester, created_at, updated_at) VALUES
(@s1, 94, 4, 98, '2025-2026-1', NOW(), NOW()),
(@s2, 98, 2, 100, '2025-2026-1', NOW(), NOW()),
(@s3, 86, 3, 89, '2025-2026-1', NOW(), NOW()),
(@s4, 94, 2, 96, '2025-2026-1', NOW(), NOW()),
(@s5, 84, 2, 86, '2025-2026-1', NOW(), NOW()),
(@s6, 74, 1, 75, '2025-2026-1', NOW(), NOW()),
(@s7, 72, 0, 72, '2025-2026-1', NOW(), NOW());

INSERT INTO physical_education (student_id, raw_score, percentage, bonus_score, total_score, semester, created_at, updated_at) VALUES
(@s1, 92, 1.00, 4, 96, '2025-2026-1', NOW(), NOW()),
(@s2, 84, 1.00, 2, 86, '2025-2026-1', NOW(), NOW()),
(@s3, 80, 1.00, 1, 81, '2025-2026-1', NOW(), NOW()),
(@s4, 78, 1.00, 1, 79, '2025-2026-1', NOW(), NOW()),
(@s5, 78, 1.00, 1, 79, '2025-2026-1', NOW(), NOW()),
(@s6, 88, 1.00, 4, 92, '2025-2026-1', NOW(), NOW()),
(@s7, 74, 1.00, 2, 76, '2025-2026-1', NOW(), NOW());

-- 6) 两学期课程成绩，便于潜力型体现增长
INSERT INTO subject_score_rain (student_id, user_id, subject_id, score, semester, created_at, updated_at) VALUES
(@s1, @teacher3_id, @sub_math, 94, '2024-2025-1', NOW(), NOW()), (@s1, @teacher3_id, @sub_english, 93, '2024-2025-1', NOW(), NOW()), (@s1, @teacher3_id, @sub_data, 95, '2024-2025-1', NOW(), NOW()),
(@s1, @teacher3_id, @sub_math, 96, '2025-2026-1', NOW(), NOW()), (@s1, @teacher3_id, @sub_english, 95, '2025-2026-1', NOW(), NOW()), (@s1, @teacher3_id, @sub_data, 96, '2025-2026-1', NOW(), NOW()),
(@s2, @teacher3_id, @sub_math, 98, '2024-2025-1', NOW(), NOW()), (@s2, @teacher3_id, @sub_linear, 97, '2024-2025-1', NOW(), NOW()), (@s2, @teacher3_id, @sub_comp, 96, '2024-2025-1', NOW(), NOW()),
(@s2, @teacher3_id, @sub_math, 99, '2025-2026-1', NOW(), NOW()), (@s2, @teacher3_id, @sub_linear, 98, '2025-2026-1', NOW(), NOW()), (@s2, @teacher3_id, @sub_comp, 97, '2025-2026-1', NOW(), NOW()),
(@s3, @teacher3_id, @sub_english, 85, '2024-2025-1', NOW(), NOW()), (@s3, @teacher3_id, @sub_data, 84, '2024-2025-1', NOW(), NOW()), (@s3, @teacher3_id, @sub_soft, 83, '2024-2025-1', NOW(), NOW()),
(@s3, @teacher3_id, @sub_english, 87, '2025-2026-1', NOW(), NOW()), (@s3, @teacher3_id, @sub_data, 86, '2025-2026-1', NOW(), NOW()), (@s3, @teacher3_id, @sub_soft, 85, '2025-2026-1', NOW(), NOW()),
(@s4, @teacher3_id, @sub_data, 92, '2024-2025-1', NOW(), NOW()), (@s4, @teacher3_id, @sub_java, 95, '2024-2025-1', NOW(), NOW()), (@s4, @teacher3_id, @sub_soft, 93, '2024-2025-1', NOW(), NOW()),
(@s4, @teacher3_id, @sub_data, 94, '2025-2026-1', NOW(), NOW()), (@s4, @teacher3_id, @sub_java, 96, '2025-2026-1', NOW(), NOW()), (@s4, @teacher3_id, @sub_soft, 95, '2025-2026-1', NOW(), NOW()),
(@s5, @teacher3_id, @sub_english, 80, '2024-2025-1', NOW(), NOW()), (@s5, @teacher3_id, @sub_pm, 83, '2024-2025-1', NOW(), NOW()), (@s5, @teacher3_id, @sub_net, 81, '2024-2025-1', NOW(), NOW()),
(@s5, @teacher3_id, @sub_english, 82, '2025-2026-1', NOW(), NOW()), (@s5, @teacher3_id, @sub_pm, 84, '2025-2026-1', NOW(), NOW()), (@s5, @teacher3_id, @sub_net, 83, '2025-2026-1', NOW(), NOW()),
(@s6, @teacher3_id, @sub_ideo, 82, '2024-2025-1', NOW(), NOW()), (@s6, @teacher3_id, @sub_pe, 88, '2024-2025-1', NOW(), NOW()), (@s6, @teacher3_id, @sub_english, 72, '2024-2025-1', NOW(), NOW()),
(@s6, @teacher3_id, @sub_ideo, 85, '2025-2026-1', NOW(), NOW()), (@s6, @teacher3_id, @sub_pe, 90, '2025-2026-1', NOW(), NOW()), (@s6, @teacher3_id, @sub_english, 74, '2025-2026-1', NOW(), NOW()),
(@s7, @teacher3_id, @sub_math, 64, '2024-2025-1', NOW(), NOW()), (@s7, @teacher3_id, @sub_english, 66, '2024-2025-1', NOW(), NOW()), (@s7, @teacher3_id, @sub_data, 70, '2024-2025-1', NOW(), NOW()),
(@s7, @teacher3_id, @sub_math, 75, '2025-2026-1', NOW(), NOW()), (@s7, @teacher3_id, @sub_english, 77, '2025-2026-1', NOW(), NOW()), (@s7, @teacher3_id, @sub_data, 83, '2025-2026-1', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  score = VALUES(score),
  updated_at = NOW();

-- 7) 附加分申请，关键词用于 7 类画像匹配
INSERT INTO extra_score_applications (
  student_id, student_no, student_name, class_name, teacher_id, academic_year,
  score_type, template_id, template_name, group_name, item_name, score_direction,
  base_score, expected_score, detail_desc, proof_text, status, review_stage,
  committee_status, teacher_status, created_at, updated_at
) VALUES
(@s1, '2025101', '王测全优', '计科2401', @teacher3_id, '2025-2026', 'MORAL', @tpl_service, '王老师班委志愿测试模板', '志愿服务', '校内志愿', 'add', 5, 5, '志愿服务、三好学生、奖学金', '测试证明', 'approved', 'teacher_approved', 'approved', 'approved', NOW(), NOW()),
(@s1, '2025101', '王测全优', '计科2401', @teacher3_id, '2025-2026', 'PHYSICAL', @tpl_sport, '王老师体育综合测试模板', '体育赛事', '校运会', 'add', 4, 4, '运动会表现优秀', '测试证明', 'approved', 'teacher_approved', 'approved', 'approved', NOW(), NOW()),
(@s2, '2025102', '王测学术', '计科2401', @teacher3_id, '2025-2026', 'INTELLECTUAL', @tpl_academic, '王老师学术科研测试模板', '科研训练', '论文科研', 'add', 8, 8, '论文、科研训练、学术项目', '测试证明', 'approved', 'teacher_approved', 'approved', 'approved', NOW(), NOW()),
(@s3, '2025103', '王测竞赛', '计科2401', @teacher3_id, '2025-2026', 'INTELLECTUAL', @tpl_competition, '王老师竞赛测试模板', '挑战杯', '省赛一等奖', 'add', 8, 8, '挑战杯竞赛一等奖', '测试证明', 'approved', 'teacher_approved', 'approved', 'approved', NOW(), NOW()),
(@s3, '2025103', '王测竞赛', '计科2401', @teacher3_id, '2025-2026', 'INTELLECTUAL', @tpl_competition, '王老师竞赛测试模板', '互联网+', '创新创业比赛', 'add', 7, 7, '互联网+创新创业比赛', '测试证明', 'approved', 'teacher_approved', 'approved', 'approved', NOW(), NOW()),
(@s4, '2025104', '王测技术', '通信2401', @teacher3_id, '2025-2026', 'INTELLECTUAL', @tpl_technical, '王老师技术项目测试模板', '程序设计', '蓝桥杯', 'add', 8, 8, 'Java、Python、程序设计、软件工程项目', '测试证明', 'approved', 'teacher_approved', 'approved', 'approved', NOW(), NOW()),
(@s5, '2025105', '王测就业', '通信2401', @teacher3_id, '2025-2026', 'INTELLECTUAL', @tpl_employment, '王老师就业实践测试模板', '实习', '企业实习', 'add', 6, 6, '企业实习、项目、证书、岗位实践', '测试证明', 'approved', 'teacher_approved', 'approved', 'approved', NOW(), NOW()),
(@s6, '2025106', '王测考公', '通信2401', @teacher3_id, '2025-2026', 'MORAL', @tpl_service, '王老师班委志愿测试模板', '班委', '班长', 'add', 5, 5, '班委、党员、志愿服务、基层实践', '测试证明', 'approved', 'teacher_approved', 'approved', 'approved', NOW(), NOW()),
(@s6, '2025106', '王测考公', '通信2401', @teacher3_id, '2025-2026', 'MORAL', @tpl_service, '王老师班委志愿测试模板', '基层服务', '社区服务', 'add', 5, 5, '基层、社区、纪律、服务', '测试证明', 'approved', 'teacher_approved', 'approved', 'approved', NOW(), NOW()),
(@s7, '2025107', '王测潜力', '通信2401', @teacher3_id, '2025-2026', 'INTELLECTUAL', @tpl_academic, '王老师学术科研测试模板', '学习进步', '进步奖', 'add', 3, 3, '学习进步明显，发展潜力突出', '测试证明', 'approved', 'teacher_approved', 'approved', 'approved', NOW(), NOW());

-- 8) 总览页用的分析记录。王老师视角下会出现 3/3/1 的已分析类型分布：
-- 考研 3 人、竞赛型人才 3 人、考公 1 人。
INSERT INTO student_analysis_records (
  student_id, student_no, student_name, class_name, teacher_id,
  primary_type, secondary_types, confidence, summary, reasons, suggestions,
  feature_snapshot, source, model_name, payload_snapshot, generated_at, created_at
) VALUES
(@s1, '2025101', '王测全优', '计科2401', @teacher3_id, '考研', '["竞赛型人才","考公"]', 88, '测试数据：综合均衡，偏向考研准备。', '["王老师比例测试：全面优秀型样本。"]', '["保持均衡发展。"]', '{"seed":"teacher3-ratio","portrait":"全面优秀型"}', 'sql-seed', 'manual', '{}', NOW(), NOW()),
(@s2, '2025102', '王测学术', '计科2401', @teacher3_id, '考研', '["竞赛型人才","考公"]', 94, '测试数据：学术成绩突出，偏向考研。', '["王老师比例测试：学术升学型样本。"]', '["继续强化科研和课程能力。"]', '{"seed":"teacher3-ratio","portrait":"学术升学型"}', 'sql-seed', 'manual', '{}', NOW(), NOW()),
(@s3, '2025103', '王测竞赛', '计科2401', @teacher3_id, '竞赛型人才', '["考研","考公"]', 91, '测试数据：竞赛信号突出。', '["王老师比例测试：复合竞赛型样本。"]', '["继续参加高质量竞赛。"]', '{"seed":"teacher3-ratio","portrait":"复合竞赛型"}', 'sql-seed', 'manual', '{}', NOW(), NOW()),
(@s4, '2025104', '王测技术', '通信2401', @teacher3_id, '竞赛型人才', '["考研","考公"]', 89, '测试数据：技术课程和项目突出。', '["王老师比例测试：技术专长型样本。"]', '["沉淀项目作品和竞赛成果。"]', '{"seed":"teacher3-ratio","portrait":"技术专长型"}', 'sql-seed', 'manual', '{}', NOW(), NOW()),
(@s5, '2025105', '王测就业', '通信2401', @teacher3_id, '竞赛型人才', '["考研","考公"]', 84, '测试数据：实践、项目、证书信号突出。', '["王老师比例测试：就业实践型样本。"]', '["补充实习与项目材料。"]', '{"seed":"teacher3-ratio","portrait":"就业实践型"}', 'sql-seed', 'manual', '{}', NOW(), NOW()),
(@s6, '2025106', '王测考公', '通信2401', @teacher3_id, '考公', '["考研","竞赛型人才"]', 92, '测试数据：德育、班委、基层服务突出。', '["王老师比例测试：稳定考公型样本。"]', '["保持稳定执行和基层实践。"]', '{"seed":"teacher3-ratio","portrait":"稳定考公型"}', 'sql-seed', 'manual', '{}', NOW(), NOW()),
(@s7, '2025107', '王测潜力', '通信2401', @teacher3_id, '考研', '["竞赛型人才","考公"]', 78, '测试数据：近期进步明显，偏向继续升学培养。', '["王老师比例测试：发展潜力型样本。"]', '["继续观察学习增长曲线。"]', '{"seed":"teacher3-ratio","portrait":"发展潜力型"}', 'sql-seed', 'manual', '{}', NOW(), NOW());
