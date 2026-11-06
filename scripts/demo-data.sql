-- EduTrack evaluation data. Run manually in Neon SQL Editor after Flyway V1–V10.
-- Uses reserved .invalid addresses. No passwords, activation tokens, or parent links.
-- Safe to rerun: fixed identifiers and ON CONFLICT DO NOTHING.
BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN_TEACHER') THEN
    RAISE EXCEPTION 'Create the teacher account through the application before adding sample data';
  END IF;
END;
$$;

INSERT INTO classrooms
  (id, code, name, description, subject, grade_level, academic_year, status)
VALUES
  ('9d399c00-1b47-4e25-9c56-000000000101', 'DEMO-TOAN-8A', 'MẪU · Toán 8A', 'Lớp mẫu để kiểm tra giao diện và ghi danh.', 'Toán', '8', '2026-2027', 'ACTIVE'),
  ('9d399c00-1b47-4e25-9c56-000000000102', 'DEMO-VAN-8A', 'MẪU · Ngữ văn 8A', 'Lớp mẫu để kiểm tra danh sách học sinh.', 'Ngữ văn', '8', '2026-2027', 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO users (id, email, full_name, role, status)
VALUES
  ('9d399c00-1b47-4e25-9c56-000000000201', 'hoc-sinh-mau-01@example.invalid', 'Học sinh Mẫu 01', 'STUDENT', 'PENDING_ACTIVATION'),
  ('9d399c00-1b47-4e25-9c56-000000000202', 'hoc-sinh-mau-02@example.invalid', 'Học sinh Mẫu 02', 'STUDENT', 'PENDING_ACTIVATION'),
  ('9d399c00-1b47-4e25-9c56-000000000203', 'hoc-sinh-mau-03@example.invalid', 'Học sinh Mẫu 03', 'STUDENT', 'PENDING_ACTIVATION')
ON CONFLICT DO NOTHING;

INSERT INTO student_enrollments (classroom_id, student_id, enrolled_by)
SELECT c.id, u.id, admin.id
FROM (VALUES
  ('9d399c00-1b47-4e25-9c56-000000000101'::uuid, '9d399c00-1b47-4e25-9c56-000000000201'::uuid),
  ('9d399c00-1b47-4e25-9c56-000000000101'::uuid, '9d399c00-1b47-4e25-9c56-000000000202'::uuid),
  ('9d399c00-1b47-4e25-9c56-000000000101'::uuid, '9d399c00-1b47-4e25-9c56-000000000203'::uuid),
  ('9d399c00-1b47-4e25-9c56-000000000102'::uuid, '9d399c00-1b47-4e25-9c56-000000000201'::uuid),
  ('9d399c00-1b47-4e25-9c56-000000000102'::uuid, '9d399c00-1b47-4e25-9c56-000000000202'::uuid)
) AS sample(class_id, student_id)
JOIN classrooms c ON c.id = sample.class_id
JOIN users u ON u.id = sample.student_id AND u.role = 'STUDENT'
CROSS JOIN (SELECT id FROM users WHERE role = 'ADMIN_TEACHER' LIMIT 1) admin
ON CONFLICT DO NOTHING;

INSERT INTO questions
  (id, type, subject, grade_level, topic, difficulty, body, content, answer_key, explanation, default_points, created_by)
SELECT sample.id, sample.type, sample.subject, '8', sample.topic, sample.difficulty,
       sample.body, sample.content::jsonb, sample.answer_key::jsonb,
       sample.explanation, sample.default_points, admin.id
FROM (VALUES
  ('9d399c00-1b47-4e25-9c56-000000000301'::uuid, 'SINGLE_CHOICE', 'Toán', 'Phương trình bậc nhất', 'EASY',
   'MẪU · Nghiệm của phương trình 2x + 3 = 7 là bao nhiêu?',
   '{"options":[{"id":"A","text":"x = 1"},{"id":"B","text":"x = 2"},{"id":"C","text":"x = 3"}]}',
   '{"correctId":"B"}', 'Chuyển 3 sang vế phải rồi chia cả hai vế cho 2.', 1.00),
  ('9d399c00-1b47-4e25-9c56-000000000302'::uuid, 'TRUE_FALSE', 'Toán', 'Hình học phẳng', 'EASY',
   'MẪU · Tổng ba góc của một tam giác bằng 180 độ.',
   '{}', '{"correct":true}', 'Đây là tính chất tổng ba góc của tam giác.', 1.00),
  ('9d399c00-1b47-4e25-9c56-000000000303'::uuid, 'SHORT_ANSWER', 'Ngữ văn', 'Từ loại', 'MEDIUM',
   'MẪU · Từ loại dùng để chỉ người, sự vật và hiện tượng là gì?',
   '{}', '{"acceptedAnswers":["danh từ"],"caseSensitive":false}',
   'Danh từ chỉ người, sự vật, hiện tượng và khái niệm.', 1.00)
) AS sample(id,type,subject,topic,difficulty,body,content,answer_key,explanation,default_points)
CROSS JOIN (SELECT id FROM users WHERE role = 'ADMIN_TEACHER' LIMIT 1) admin
ON CONFLICT DO NOTHING;

COMMIT;

SELECT
  (SELECT count(*) FROM classrooms WHERE code LIKE 'DEMO-%') AS demo_classes,
  (SELECT count(*) FROM users WHERE email LIKE 'hoc-sinh-mau-%@example.invalid') AS demo_students,
  (SELECT count(*) FROM student_enrollments WHERE classroom_id IN
    ('9d399c00-1b47-4e25-9c56-000000000101','9d399c00-1b47-4e25-9c56-000000000102')) AS demo_enrollments,
  (SELECT count(*) FROM questions WHERE id IN
    ('9d399c00-1b47-4e25-9c56-000000000301','9d399c00-1b47-4e25-9c56-000000000302','9d399c00-1b47-4e25-9c56-000000000303')) AS demo_questions;
