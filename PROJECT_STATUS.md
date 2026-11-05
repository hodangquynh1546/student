# EduTrack — trạng thái triển khai

Ngày bắt đầu: 2026-11-05. Dự án mới, tách khỏi Hong Beauty.

## Đã chốt
- Một hệ thống cho một giáo viên duy nhất; ba vai trò ADMIN_TEACHER, STUDENT, PARENT.
- Backend Java 21 / Spring Boot 3.5.x / PostgreSQL / Flyway; frontend React / TypeScript / Vite.
- Không đăng ký giáo viên công khai. Tài khoản Admin tạo từ biến môi trường lúc khởi tạo.
- Refresh token lưu dạng băm trong DB, rotation; cookie HttpOnly cho trình duyệt.
- Mọi truy vấn học sinh và phụ huynh phải kiểm tra quyền theo quan hệ lớp/liên kết đã xác minh.

## Đã triển khai và kiểm tra cục bộ
- Backend build thành công với Java 21/Spring Boot 3.5.16.
- Flyway V1–V2 chạy trên PostgreSQL 17; Hibernate `validate` đạt và ứng dụng khởi động.
- Đăng nhập Admin qua API trả JWT; tạo học sinh trạng thái chờ kích hoạt, tạo lớp, ghi danh và xem danh sách học sinh trong lớp đã kiểm tra HTTP thành công.
- API quản lý lớp hỗ trợ tạo, sửa, đổi trạng thái, xóa mềm, ghi danh và rời lớp; học sinh chỉ xem lớp của chính mình.
- Luồng liên kết phụ huynh: tạo tài khoản chờ kích hoạt nếu cần, gửi lời mời có hạn, phụ huynh đã đăng nhập xác nhận, giáo viên thu hồi, phụ huynh chỉ xem danh sách con đã liên kết.
- Ngân hàng câu hỏi: migration V3, tám loại câu hỏi, kiểm tra cấu trúc đáp án theo từng loại, API giáo viên tạo/xem/lọc/sửa/xóa mềm và khóa phiên bản khi cập nhật.
- Migration V4 tạo nền dữ liệu đề thi, bản chụp câu hỏi, giao bài, lượt làm, câu trả lời, điểm và lịch sử chấm. Chưa có API đề thi/làm bài.

## Đang làm
- Đề thi, giao bài, engine làm bài, quản lý phiên, giao diện và Docker; chuẩn hóa kiến trúc controller/service và email outbox.

## Chưa hoàn thành
- Ngân hàng 8 loại câu hỏi, đề thi, giao bài, engine làm bài, chấm điểm, AI Gemini, báo cáo và giao diện đầy đủ.
- Chưa có URL triển khai hoặc kiểm thử production.

## Kiểm thử gần nhất
- `mvn test`: đạt, nhưng hiện chưa có test tự động nên chỉ xác nhận biên dịch.
- Kiểm thử API thủ công trên PostgreSQL địa phương: login 200, tạo học sinh 201, tạo lớp 201, ghi danh 204, xem học sinh trong lớp 200.
- Kiểm thử API phụ huynh: lời mời 201, kích hoạt 204, đăng nhập 200, xác nhận liên kết 204, xem con 200, dùng lại mã bị từ chối 400, thu hồi 204, danh sách con về 0.
- Ngân hàng câu hỏi: tạo thành công cả 8 dạng qua API trên PostgreSQL, đáp án sai trả 400, khách ẩn danh trả 401, lọc trả 200, cập nhật với phiên bản cũ trả 409, xóa mềm trả 204.
- 11 bài kiểm thử tự động cho validation câu hỏi: 11 đạt, 0 lỗi.
- Flyway V4 áp dụng thành công trên PostgreSQL địa phương; ứng dụng khởi động với schema phiên bản 4.
- Chưa kiểm thử production, SMTP thực, vai trò Student và an toàn bảo mật toàn diện.
