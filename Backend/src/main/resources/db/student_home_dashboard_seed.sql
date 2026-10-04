-- 学生首页展示数据：多学期德智体趋势 + 审核中附加分 + AI 画像雷达 + 我的公告
-- 用法：先改下方学号列表（默认覆盖截图中总分约 140.3 的账号），再执行本脚本。
-- 可重复执行：会先清理本脚本写入的同学期/同批次/公告种子再插入。

SET NAMES utf8mb4;
SET collation_connection = utf8mb4_unicode_ci;

-- ========== 目标学号（按需增删）==========
DROP TEMPORARY TABLE IF EXISTS tmp_home_seed_students;
CREATE TEMPORARY TABLE tmp_home_seed_students (
  student_no VARCHAR(50) COLLATE utf8mb4_unicode_ci PRIMARY KEY
);
INSERT INTO tmp_home_seed_students (student_no) VALUES
  ('20203090128'),  -- 张泽升，综测约 140.3
  ('20233090415');  -- 史佳浩，综测约 140.3

SET @tpl_moral := (SELECT id FROM extra_score_templates WHERE name = '德育通用模板' LIMIT 1);
SET @tpl_intel := (SELECT id FROM extra_score_templates WHERE name = '智育竞赛模板' LIMIT 1);
SET @tpl_phys  := (SELECT id FROM extra_score_templates WHERE name = '体育赛事模板' LIMIT 1);
SET @batch_code := 'HOME-SEED-2025-2026-1';
SET @period_label := '2025-2026 第1批次';
SET @period_start := '2026-08-25 00:00:00';
SET @period_end := '2026-09-30 23:59:59';

-- ========== 1) 清理旧种子 ==========
DELETE m FROM moral_education m
JOIN students s ON s.id = m.student_id
JOIN tmp_home_seed_students t ON t.student_no = s.student_no
WHERE m.semester IN (
  '2023-2024-1', '2023-2024-2',
  '2024-2025-1', '2024-2025-2',
  '2025-2026-1'
);

DELETE i FROM intellectual_education i
JOIN students s ON s.id = i.student_id
JOIN tmp_home_seed_students t ON t.student_no = s.student_no
WHERE i.semester IN (
  '2023-2024-1', '2023-2024-2',
  '2024-2025-1', '2024-2025-2',
  '2025-2026-1'
);

DELETE p FROM physical_education p
JOIN students s ON s.id = p.student_id
JOIN tmp_home_seed_students t ON t.student_no = s.student_no
WHERE p.semester IN (
  '2023-2024-1', '2023-2024-2',
  '2024-2025-1', '2024-2025-2',
  '2025-2026-1'
);

DELETE e FROM extra_score_applications e
JOIN tmp_home_seed_students t ON t.student_no = e.student_no
WHERE e.submission_batch_code = @batch_code
   OR e.detail_desc LIKE '【首页种子】%';

DELETE a FROM student_analysis_records a
JOIN tmp_home_seed_students t ON t.student_no = a.student_no
WHERE a.source = 'home-dashboard-seed';

-- ========== 2) 多学期德智体（成绩趋势折线）==========
INSERT INTO moral_education (
  student_id, activity_score, second_class_score, bonus_score, total_score,
  semester, academic_year, created_at, updated_at
)
SELECT s.id, v.activity_score, v.second_class_score, v.bonus_score, v.total_score,
       v.semester, v.academic_year, NOW(), NOW()
FROM students s
JOIN tmp_home_seed_students t ON t.student_no = s.student_no
JOIN (
  SELECT '2023-2024-1' AS semester, '2023-2024' AS academic_year, 52 AS activity_score, 12 AS second_class_score, 10 AS bonus_score, 74 AS total_score
  UNION ALL SELECT '2023-2024-2', '2023-2024', 54, 13, 11, 78
  UNION ALL SELECT '2024-2025-1', '2024-2025', 56, 14, 12, 82
  UNION ALL SELECT '2024-2025-2', '2024-2025', 58, 15, 13, 86
  UNION ALL SELECT '2025-2026-1', '2025-2026', 60, 16, 14, 90
) v;

INSERT INTO intellectual_education (
  student_id, course_score, bonus_score, total_score, semester, created_at, updated_at
)
SELECT s.id, v.course_score, v.bonus_score, v.total_score, v.semester, NOW(), NOW()
FROM students s
JOIN tmp_home_seed_students t ON t.student_no = s.student_no
JOIN (
  SELECT '2023-2024-1' AS semester, 72 AS course_score, 1 AS bonus_score, 73 AS total_score
  UNION ALL SELECT '2023-2024-2', 75, 2, 77
  UNION ALL SELECT '2024-2025-1', 78, 2, 80
  UNION ALL SELECT '2024-2025-2', 81, 3, 84
  UNION ALL SELECT '2025-2026-1', 84, 3, 87
) v;

INSERT INTO physical_education (
  student_id, raw_score, percentage, bonus_score, total_score, semester, created_at, updated_at
)
SELECT s.id, v.raw_score, 1.00, v.bonus_score, v.total_score, v.semester, NOW(), NOW()
FROM students s
JOIN tmp_home_seed_students t ON t.student_no = s.student_no
JOIN (
  SELECT '2023-2024-1' AS semester, 76 AS raw_score, 2 AS bonus_score, 78 AS total_score
  UNION ALL SELECT '2023-2024-2', 79, 2, 81
  UNION ALL SELECT '2024-2025-1', 82, 3, 85
  UNION ALL SELECT '2024-2025-2', 84, 3, 87
  UNION ALL SELECT '2025-2026-1', 86, 4, 90
) v;

-- ========== 3) 审核中附加分（首页进度条）==========
INSERT INTO extra_score_applications (
  student_id, student_no, student_name, class_name, teacher_id, academic_year,
  submission_batch_code, submission_period_label, submission_start_time, submission_end_time,
  score_type, template_id, template_name, group_name, item_name, score_direction,
  base_score, expected_score, detail_desc, proof_text,
  status, review_stage, committee_status, teacher_status,
  submission_type, current_revision_no, created_at, updated_at
)
SELECT
  s.id,
  s.student_no,
  s.name,
  s.class_name,
  COALESCE(s.teacher_id, 4),
  '2025-2026',
  @batch_code,
  @period_label,
  @period_start,
  @period_end,
  v.score_type,
  v.template_id,
  v.template_name,
  v.group_name,
  v.item_name,
  'add',
  v.base_score,
  v.expected_score,
  v.detail_desc,
  '首页展示用证明材料',
  'pending',
  'committee_pending',
  NULL,
  NULL,
  'normal',
  1,
  '2026-09-01 14:28:00',
  NOW()
FROM students s
JOIN tmp_home_seed_students t ON t.student_no = s.student_no
JOIN (
  SELECT
    'MORAL' AS score_type,
    @tpl_moral AS template_id,
    '德育通用模板' AS template_name,
    '志愿服务' AS group_name,
    '校内志愿' AS item_name,
    5 AS base_score,
    5 AS expected_score,
    '【首页种子】参加学院志愿活动' AS detail_desc
  UNION ALL SELECT
    'INTELLECTUAL', @tpl_intel, '智育竞赛模板', '学科竞赛', '蓝桥杯', 8, 8,
    '【首页种子】蓝桥杯省赛报名材料'
  UNION ALL SELECT
    'PHYSICAL', @tpl_phys, '体育赛事模板', '体育赛事', '校运会', 4, 4,
    '【首页种子】校运会参赛证明'
) v
WHERE @tpl_moral IS NOT NULL AND @tpl_intel IS NOT NULL AND @tpl_phys IS NOT NULL;

-- ========== 4) AI 画像 + 八维雷达（无记录则插入；有记录则补雷达）==========
INSERT INTO student_analysis_records (
  student_id, student_no, student_name, class_name, teacher_id,
  primary_type, secondary_types, confidence, summary, reasons, suggestions,
  feature_snapshot, source, model_name, payload_snapshot, generated_at, created_at
)
SELECT
  s.id,
  s.student_no,
  s.name,
  s.class_name,
  COALESCE(s.teacher_id, 4),
  '考研',
  '["竞赛型人才","就业实践型"]',
  82,
  '课程基础尚可，近期德智体与附加分表现持续改善，适合继续夯实学业并补齐竞赛/项目作品。',
  '["近五学期德智体成绩整体上行。","附加分已覆盖德育、智育、体育三类申请。","课程与综测数据足以支撑考研方向培养。"]',
  '["保持每周固定学习节奏，优先巩固核心专业课。","选择 1-2 个高质量竞赛或项目持续打磨。"]',
  JSON_OBJECT(
    'seed', 'home-dashboard',
    'radarDimensions', JSON_OBJECT(
      '思想品德', 78,
      '学业成绩', 74,
      '科研创新', 62,
      '实践服务', 70,
      '文体素养', 76,
      '技能素养', 72,
      '综合素质', 73,
      '发展潜力', 80
    )
  ),
  'home-dashboard-seed',
  'manual',
  '{}',
  NOW(),
  NOW()
FROM students s
JOIN tmp_home_seed_students t ON t.student_no = s.student_no
WHERE NOT EXISTS (
  SELECT 1 FROM student_analysis_records a WHERE a.student_no = s.student_no
);

-- 已有画像但缺少雷达时补上
UPDATE student_analysis_records a
JOIN tmp_home_seed_students t ON t.student_no = a.student_no
SET a.feature_snapshot = JSON_SET(
  COALESCE(a.feature_snapshot, '{}'),
  '$.radarDimensions',
  JSON_OBJECT(
    '思想品德', 78,
    '学业成绩', 74,
    '科研创新', 62,
    '实践服务', 70,
    '文体素养', 76,
    '技能素养', 72,
    '综合素质', 73,
    '发展潜力', 80
  )
)
WHERE JSON_EXTRACT(a.feature_snapshot, '$.radarDimensions') IS NULL
   OR JSON_LENGTH(JSON_EXTRACT(a.feature_snapshot, '$.radarDimensions')) = 0;

-- ========== 5) 我的公告（announcements + recipients）==========
SET @publisher_id := (SELECT id FROM users WHERE username = 'teacher3' LIMIT 1);
SET @publisher_name := COALESCE((SELECT nickname FROM users WHERE id = @publisher_id), '王老师');
SET @seed_recipient_count := (
  SELECT COUNT(*) FROM students s
  JOIN tmp_home_seed_students t ON t.student_no = s.student_no
);

-- 清理旧首页公告种子（按固定标题识别，可重复执行）
DELETE r FROM announcement_recipients r
JOIN announcements a ON a.id = r.announcement_id
WHERE a.title IN (
  '综测成绩已更新，请及时核对',
  '附加分申请进入班委初审',
  '本学期综测材料提交安排',
  '【首页种子】综测成绩已更新，请及时核对',
  '【首页种子】附加分申请进入班委初审',
  '【首页种子】本学期综测材料提交安排'
);

DELETE FROM announcements
WHERE title IN (
  '综测成绩已更新，请及时核对',
  '附加分申请进入班委初审',
  '本学期综测材料提交安排',
  '【首页种子】综测成绩已更新，请及时核对',
  '【首页种子】附加分申请进入班委初审',
  '【首页种子】本学期综测材料提交安排'
);

INSERT INTO announcements (
  title, content, category,
  publisher_id, publisher_name, publisher_role, teacher_id, class_name,
  recipient_count, read_count, unread_count, remind_count,
  created_at, updated_at
)
SELECT
  v.title,
  v.content,
  v.category,
  @publisher_id,
  @publisher_name,
  'TEACHER',
  @publisher_id,
  '计科2401',
  @seed_recipient_count,
  0,
  @seed_recipient_count,
  0,
  v.created_at,
  NOW()
FROM (
  SELECT
    '综测成绩已更新，请及时核对' AS title,
    '<p>2025-2026 学年综测快照已生成，德育/智育/体育分项与综合分可在首页成绩卡与成绩趋势中查看。如有异议请在本周内联系辅导员。</p>' AS content,
    'COLLEGE_NOTICE' AS category,
    '2026-09-03 09:20:00' AS created_at
  UNION ALL SELECT
    '附加分申请进入班委初审',
    '<p>你本周期提交的附加分材料已进入班委初审阶段，可在「附加分审核进度」查看当前节点。请保持证明材料完整，便于老师终审。</p>',
    'COMMITTEE_NOTICE',
    '2026-09-02 15:40:00'
  UNION ALL SELECT
    '本学期综测材料提交安排',
    '<p>学院通知：本学期综测附加分开放提交至 2026-09-30。请按德育、智育、体育分类上传证明，逾期将无法补交本批次材料。</p>',
    'COLLEGE_NOTICE',
    '2026-09-01 10:05:00'
) v
WHERE @publisher_id IS NOT NULL
  AND @seed_recipient_count > 0;

INSERT INTO announcement_recipients (
  announcement_id, student_id, student_no, student_name, teacher_id, class_name,
  is_read, read_at, remind_count, created_at, updated_at
)
SELECT
  a.id,
  s.id,
  s.student_no,
  s.name,
  COALESCE(s.teacher_id, @publisher_id),
  s.class_name,
  0,
  NULL,
  0,
  a.created_at,
  NOW()
FROM announcements a
JOIN students s
JOIN tmp_home_seed_students t ON t.student_no = s.student_no
WHERE a.title IN (
  '综测成绩已更新，请及时核对',
  '附加分申请进入班委初审',
  '本学期综测材料提交安排'
)
  AND @publisher_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM announcement_recipients r
    WHERE r.announcement_id = a.id AND r.student_no = s.student_no
  );

-- ========== 校验 ==========
SELECT
  s.student_no,
  s.name,
  (SELECT COUNT(*) FROM moral_education m WHERE m.student_id = s.id) AS moral_terms,
  (SELECT COUNT(*) FROM intellectual_education i WHERE i.student_id = s.id) AS intel_terms,
  (SELECT COUNT(*) FROM physical_education p WHERE p.student_id = s.id) AS phys_terms,
  (SELECT COUNT(*) FROM extra_score_applications e
     WHERE e.student_no = s.student_no AND e.submission_batch_code = @batch_code) AS pending_extra,
  (SELECT COUNT(*) FROM student_analysis_records a WHERE a.student_no = s.student_no) AS analysis_n,
  (SELECT COUNT(*) FROM announcement_recipients r WHERE r.student_no = s.student_no) AS inbox_n
FROM students s
JOIN tmp_home_seed_students t ON t.student_no = s.student_no;
