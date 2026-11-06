# Dữ liệu mẫu để thử EduTrack

File [scripts/demo-data.sql](../scripts/demo-data.sql) tạo **2 lớp, 3 học sinh mẫu, 5 lượt ghi danh và 3 câu hỏi** trong database hiện có. Dữ liệu có tiền tố `MẪU`/`DEMO`, email thuộc miền `.invalid`, không chứa mật khẩu, token kích hoạt hoặc dữ liệu học sinh thật. Học sinh mẫu ở trạng thái chờ kích hoạt và **không thể đăng nhập**; chúng chỉ phục vụ kiểm tra giao diện quản lý. Script không tạo bài làm hay điểm giả.

## Chạy trên Neon của bạn

1. Đảm bảo backend đã chạy Flyway đến V10 và tài khoản giáo viên đã được tạo thành công.
2. Mở dự án của bạn tại Neon → **SQL Editor**. Chọn đúng database đang dùng cho EduTrack.
3. Mở [demo-data.sql](../scripts/demo-data.sql), sao chép toàn bộ nội dung vào SQL Editor rồi chạy một lần. Không cần gửi mật khẩu hoặc chuỗi kết nối cho người khác.
4. Kết quả cuối script phải là `demo_classes=2`, `demo_students=3`, `demo_enrollments=5`, `demo_questions=3`. Tải lại trang Quản lý lớp, Học sinh và Ngân hàng câu hỏi.

Script chạy trong một giao dịch, dùng mã cố định và `ON CONFLICT DO NOTHING` nên chạy lại không sinh bản ghi trùng. Nó không sửa hoặc xóa tài khoản/lớp hiện có. Vì email mẫu không nhận thư, hãy dùng chức năng thêm học sinh bình thường với email thật khi đã cấu hình SMTP hoạt động.

Chưa có quyền kết nối Neon của chủ từ môi trường phát triển này nên script **chưa được chạy trên database của chủ**. Chỉ chạy tại Neon SQL Editor sau khi đã xem lại nội dung file.
