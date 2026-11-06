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
- Flyway V1–V10 chạy trên PostgreSQL 17 trong các bài kiểm thử tích hợp và ứng dụng khởi động từ schema mới với Hibernate `validate`.
- Đăng nhập Admin qua API trả JWT; tạo học sinh trạng thái chờ kích hoạt, tạo lớp, ghi danh và xem danh sách học sinh trong lớp đã kiểm tra HTTP thành công.
- API quản lý lớp hỗ trợ tạo, sửa, đổi trạng thái, xóa mềm, ghi danh và rời lớp; học sinh chỉ xem lớp của chính mình.
- Luồng liên kết phụ huynh: tạo tài khoản chờ kích hoạt nếu cần, gửi lời mời có hạn, phụ huynh đã đăng nhập xác nhận, giáo viên thu hồi, phụ huynh chỉ xem danh sách con đã liên kết.
- Ngân hàng câu hỏi: migration V3, tám loại câu hỏi, kiểm tra cấu trúc đáp án theo từng loại, API giáo viên tạo/xem/lọc/sửa/xóa mềm và khóa phiên bản khi cập nhật.
- Migration V4 tạo dữ liệu đề thi, bản chụp câu hỏi, giao bài, lượt làm, câu trả lời, điểm và lịch sử chấm. Migration V5 thêm trạng thái chấm tự động; cả hai đã chạy trên PostgreSQL thật.
- API giáo viên tạo đề nháp, thêm bản chụp câu hỏi, xuất bản, đóng đề và giao cho lớp/học sinh.
- Exam Builder cho phép giáo viên tạo câu hỏi ngay trong đề, đổi thứ tự bằng kéo thả hoặc nút, sửa điểm, xóa câu hỏi, xem trước và cập nhật cài đặt đề nháp với kiểm tra phiên bản. Kiểm thử backend trên PostgreSQL và build/test frontend đạt; chưa kiểm thử toàn luồng bằng trình duyệt với backend đang chạy.
- API học sinh liệt kê bài được giao, bắt đầu/tiếp tục lượt làm, lưu và đánh dấu đáp án, nộp bài có khóa tránh nộp đồng thời và mã idempotency, xem kết quả khi được phép. Đáp án đúng và rubric không được trả về API học sinh.
- Chấm tự động cho 7 dạng khách quan; tự luận để chờ giáo viên chấm. Tác vụ định kỳ tự nộp và chấm phần đáp án đã lưu khi hết giờ.
- Cấu hình chấm `gradingPolicy` theo câu hỏi: đúng hoàn toàn hoặc điểm từng phần cho nhiều lựa chọn, ghép cặp, sắp xếp, điền ô; quy tắc phân biệt chữ hoa và chuẩn hóa khoảng trắng cho đáp án chữ. Cấu hình được chụp vào đề khi giáo viên thêm câu hỏi.
- API và giao diện giáo viên xem hàng chờ chấm, xem đáp án/rubric, nhập điểm và nhận xét, lưu lịch sử sửa điểm, công bố kết quả. Luồng API đã kiểm tra trên PostgreSQL; bài tự luận có gợi ý AI vẫn phải được giáo viên xác nhận trước khi công bố.
- API và giao diện phụ huynh xem kết quả đã công bố và điểm/nhận xét từng câu được thêm; API yêu cầu liên kết còn hiệu lực cho mỗi lần xem. Đã build giao diện và kiểm tra HTTP trên PostgreSQL: đúng con 200, con khác 404, sau thu hồi 404.
- Ba dashboard giáo viên, học sinh, phụ huynh có API đọc dữ liệu thực và giao diện Recharts; phụ huynh chỉ nhận dữ liệu từ kết quả đã công bố của con liên kết. Đã build giao diện; API giáo viên và học sinh trả 200 trên PostgreSQL, API phụ huynh chưa kiểm tra HTTP trực tiếp.
- API lưu đáp án kiểm tra cấu trúc theo 8 loại câu hỏi và ID lựa chọn theo bản chụp trong đề, giới hạn độ dài, từ chối trường lạ hoặc phần tử trùng. Kiểm thử tự động đạt; HTTP trên PostgreSQL: lựa chọn ngoài đề 400, lựa chọn hợp lệ 200, trường lạ 400.
- Adapter Gemini Interactions API dùng `GEMINI_API_KEY` và `GEMINI_MODEL` từ ENV, không giữ tương tác phía nhà cung cấp. Luồng đề xuất chấm tự luận nhận ảnh/PDF đã lưu, kiểm tra JSON và giới hạn điểm, lưu lịch sử đánh giá/model/thời gian qua V7; giáo viên vẫn phải xác nhận điểm. Chưa kiểm thử với khóa API thật.
- Giao diện học sinh đã nối API thật: danh sách bài, làm 8 dạng câu hỏi, tự lưu, chuyển câu, đánh dấu, đồng hồ theo thời gian máy chủ, nộp bài và xem trạng thái.
- Giáo viên có thể import học sinh từ CSV/XLSX/XLS (tối đa 2 MB và 500 dòng), nhận báo lỗi theo dòng; tài khoản tạo qua cùng quy trình kích hoạt và liên kết phụ huynh. Giao diện import dùng API thật và có mẫu CSV.
- API xếp hạng tính từ điểm đã công bố, tỷ lệ hoàn thành và tiến bộ; giáo viên xem danh sách lớp, học sinh/phụ huynh chỉ nhận chỉ số của chính mình/con đã liên kết. Có kiểm thử PostgreSQL cho quyền và bài giao trùng.
- API báo cáo tuần/tháng lưu snapshot và có lịch tự tổng hợp kỳ vừa kết thúc; phụ huynh chỉ xem con đã liên kết và điểm đã công bố. Chưa có xuất PDF/Excel.
- API thông báo trong ứng dụng hỗ trợ danh sách theo người dùng, đếm chưa đọc, đánh dấu một/tất cả, giáo viên tạo thông báo. Chưa nối tự động với bài giao hoặc email.
- Thông báo trong ứng dụng đã được nối vào sự kiện giao bài và công bố điểm, tránh tạo trùng khi công bố lặp; chưa gửi email cho các sự kiện này.
- Email outbox V10 ghi cùng giao dịch với sự kiện giao bài/công bố điểm, gửi sau commit, kiểm tra lại quyền người nhận, thử lại có giới hạn và hủy nếu quan hệ không còn hiệu lực. Kiểm thử PostgreSQL đạt; chưa thử với SMTP thật.
- Báo cáo đã có xuất PDF/XLSX từ snapshot thật, kiểm tra quyền tải theo từng vai trò và font tiếng Việt đóng gói cùng ứng dụng. Giao diện có nút tải; 7 bài kiểm thử chuyên biệt đạt.
- API trợ lý AI cho học sinh và sinh bản nháp câu hỏi cho giáo viên đã thêm. Học sinh chỉ được gửi ngữ cảnh bài của mình đã công bố và kết thúc; đang siết thêm lệnh cấm dùng AI khi có lượt thi đang làm. Chưa kiểm thử với API key thật.
- AI trợ lý đã chặn yêu cầu khi học sinh có lượt thi `IN_PROGRESS`, kể cả câu hỏi không gắn lượt làm; 7 bài kiểm thử service AI đạt. Giao diện học sinh hỏi AI và giáo viên duyệt/sửa/lưu bản nháp vào ngân hàng đã build, 6 bài kiểm thử frontend đạt.
- Token kích hoạt/đặt lại mật khẩu/liên kết phụ huynh được khóa bản ghi khi dùng; kiểm thử đồng thời trên PostgreSQL xác nhận hai yêu cầu dùng cùng token đặt lại không cùng thành công.
- Làm mới và đăng xuất qua cookie kiểm tra Origin theo allowlist hoặc Fetch Metadata `same-origin` khi thiếu Origin; từ chối khi cả hai thiếu. 5 bài kiểm thử riêng đạt sau build sạch; toàn bộ backend đã chạy lại đạt.
- Cả ba vai trò có API và màn hình tự sửa họ tên, số điện thoại, ngày sinh; không cho đổi email/role qua form. Màn hình đổi mật khẩu yêu cầu đăng nhập lại. Kiểm thử backend và frontend liên quan, build frontend đạt; avatar vẫn chưa triển khai.
- Bộ tài liệu kiến trúc và hợp đồng API đã có trong `docs/`; cấu hình Docker Compose đã viết, chưa chạy container được vì môi trường hiện thiếu Docker.
- Khung avatar–tên–vai trò đã được tách thành component mở trang Hồ sơ; giao diện Hồ sơ được bố trí lại. Script dữ liệu mẫu có 2 lớp, 3 học sinh giả, 5 lượt ghi danh và 3 câu hỏi; chạy hai lần trên schema PostgreSQL tạm vẫn đúng số lượng, chưa chạy trên Neon của chủ vì không có kết nối được cấp.
- Blueprint `render.yaml` đã chuyển sang hai web service gói Free theo yêu cầu. Backend phải công khai vì Free không hỗ trợ nhận kết nối mạng riêng; dữ liệu upload trên ổ cục bộ sẽ mất khi khởi động lại, và SMTP phải dùng cổng được Render Free cho phép. Đây chỉ là cấu hình đánh giá với dữ liệu thử; chưa tạo dịch vụ Render hay Neon và chưa chạy container thực tế.

## Đang làm
- Hoàn thiện xác thực, bảo mật và các module còn thiếu. Giới hạn số yêu cầu vào endpoint xác thực nhạy cảm đã được thêm ở mức tiến trình; cần kho chia sẻ nếu chạy nhiều backend.
- Tải và hiển thị tệp bài tự luận: migration V6, API phân quyền, lớp lưu trữ tệp có interface thay thế và giao diện đã viết. HTTP trên PostgreSQL: tải PNG 201, tệp giả 400, học sinh khác xem 404, giáo viên xem 200; phụ huynh trước công bố 404, sau công bố 200, sau thu hồi liên kết 404.

## Chưa hoàn thành
- Email nhắc hạn, upload avatar, một số màn hình/quy trình quản trị và giao diện đầy đủ theo đặc tả; Gemini và SMTP chưa được thử với credentials thật.
- Có integration test tự động dùng PostgreSQL thật cho phân quyền và tệp bài làm; Testcontainers chưa chạy trong môi trường này do thiếu Docker. Chưa kiểm tra Docker Compose vì môi trường thiếu Docker.
- Chưa có URL triển khai hoặc kiểm thử production.
- Mã nguồn đã được đẩy lên nhánh `main` của repository GitHub. Chủ repository đã thêm khóa deploy có quyền ghi; khóa riêng nằm ngoài Git. Chưa tạo dịch vụ Render hoặc cơ sở dữ liệu Neon.

## Kiểm thử gần nhất
- `mvn test -DforkCount=0` với PostgreSQL test sau thay đổi Exam Builder, AI, action token, email outbox, bảo vệ refresh/logout và hồ sơ cá nhân: 102 bài kiểm thử backend đạt, 0 lỗi/0 bỏ qua, Flyway V1–V10. `npm run build` đạt; `npm test` đạt 10/10 bài frontend. Chưa kiểm thử trình duyệt end-to-end hoặc Docker Compose.
- HTTP smoke trên schema PostgreSQL riêng: login 200, import CSV 200 (1 tạo/1 lỗi theo dòng), tạo lớp 201, ghi danh 204, ranking 200, tạo báo cáo 200, tải PDF 200 với chữ ký `%PDF`, tạo thông báo 201, AI chưa cấu hình 503. Schema thử đã xóa sau khi kiểm tra.
- Sau 84 bài kiểm thử backend, nhóm targeted gồm phân quyền kết quả/tệp, xuất báo cáo PostgreSQL, email HTML, AI service và token đồng thời đều đạt. Toàn bộ suite cần chạy lại sau phần Exam Builder đang triển khai.
- Frontend `npm test`: 2 bài kiểm thử trang kết quả phụ huynh đạt. `npm audit`: 0 lỗ hổng báo cáo sau cập nhật Vitest; build TypeScript đạt.
- Kiểm thử API thủ công trên PostgreSQL địa phương: login 200, tạo học sinh 201, tạo lớp 201, ghi danh 204, xem học sinh trong lớp 200.
- Kiểm thử API phụ huynh: lời mời 201, kích hoạt 204, đăng nhập 200, xác nhận liên kết 204, xem con 200, dùng lại mã bị từ chối 400, thu hồi 204, danh sách con về 0.
- Ngân hàng câu hỏi: tạo thành công cả 8 dạng qua API trên PostgreSQL, đáp án sai trả 400, khách ẩn danh trả 401, lọc trả 200, cập nhật với phiên bản cũ trả 409, xóa mềm trả 204.
- 11 bài kiểm thử tự động cho validation câu hỏi: 11 đạt, 0 lỗi.
- Flyway V4 áp dụng thành công trên PostgreSQL địa phương; ứng dụng khởi động với schema phiên bản 4.
- API đề thi trên PostgreSQL: tạo nháp 201, thêm câu hỏi 201, xuất bản 200, giao lớp 201, sửa đề đã xuất bản bị từ chối 409.
- 30/30 bài kiểm thử backend đạt với Java 21. Bộ mới gồm chấm 7 dạng khách quan, điểm từng phần, chuẩn hóa đáp án, từ chối đáp án thiếu/trùng và kiểm tra không lộ đáp án trong nội dung học sinh.
- Frontend `npm run build` đạt sau khi nối giao diện làm bài với API thật.
- Frontend `npm run build` đạt sau khi thêm màn hình kết quả phụ huynh; TypeScript không báo lỗi.
- Flyway V5 chạy trên PostgreSQL 17; kiểm thử HTTP thực tế: tạo/giao đề, lưu/nộp/idempotency, chặn học sinh khác, chặn sửa sau nộp, chặn xem điểm trước công bố, chấm tay, công bố, điểm cuối 4.5. Bài quá hạn được tự nộp/chấm đáp án đã lưu. Mã reset cũ bị vô hiệu khi tạo mã mới; mã không hợp lệ trả 400.
- API phụ huynh trên PostgreSQL: đăng nhập 200, liệt kê 2 kết quả đã công bố 200, xem chi tiết 2 câu 200, con không liên kết 404, sau khi thu hồi liên kết trả 404.
- API lưu đáp án trên PostgreSQL sau khi thêm validator: lựa chọn không thuộc câu hỏi 400, đáp án hợp lệ 200, payload thêm `answerKey` 400.
- Chưa kiểm thử production, SMTP/Gemini thực, Docker Compose và an toàn bảo mật toàn diện.
