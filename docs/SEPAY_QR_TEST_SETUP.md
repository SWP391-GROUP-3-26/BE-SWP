# Cài SePay QR cho Ví điện tử của Member

Luồng đã tích hợp: Member → Lớp học → Đăng ký lớp → Thanh toán trực tiếp → Ví điện tử → QR SePay.
Payment và đăng ký giữ trạng thái Pending trong 15 phút. Chỉ webhook hợp lệ mới xác nhận thanh toán.

## 1. Chuẩn bị

- Có tài khoản tại https://my.sepay.vn, máy tính chạy BE/FE và điện thoại có trình duyệt.
- Không cần cài app ngân hàng để mô phỏng. Không dùng tiền thật.
- Cài ngrok từ https://ngrok.com/download/windows, đăng ký tài khoản và làm bước kết nối authtoken trên dashboard ngrok. Authtoken ngrok khác với khóa webhook SePay.
- Không đưa authtoken hay khóa webhook lên Git hoặc nhập vào FE.

Sau khi cài ngrok, mở PowerShell và chạy lệnh sau, thay phần cuối bằng authtoken lấy từ dashboard ngrok:

```powershell
ngrok config add-authtoken TOKEN_TU_DASHBOARD_NGROK
```

## 2. Tạo tài khoản ngân hàng thử nghiệm

1. Đăng nhập https://my.sepay.vn trên máy tính hoặc điện thoại.
2. Vào **Test mode**, bật chế độ thử nghiệm; kiểm tra có banner Test mode.
3. Vào **Test mode → Tài khoản ngân hàng → Thêm tài khoản**.
4. Chọn **MBBank**, loại **Cá nhân** để test luồng tài khoản thường, không cần VA.
5. Lưu lại **số tài khoản giả lập** và **tên chủ tài khoản** do SePay tạo.

Hướng dẫn này dùng MBBank để tránh quy tắc VA/nội dung đặc thù ở một số ngân hàng khác.

## 3. Cấu hình BE

Nếu database cũ chưa có cột SePay, mở SSMS, chọn đúng `swp391_db` và chạy:

`docs/database/migrations/20261016_sepay_qr.sql`

Migration chỉ thêm cột nullable và unique index chống trùng mã giao dịch; có thể chạy lại.
Database tạo mới bằng `docs/database/swp391_db.sql` đã bao gồm thay đổi này.

Tạo một khóa ngẫu nhiên bằng PowerShell:

```powershell
[Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
```

Sao chép mẫu `docs/sepay.env.example` và thêm các dòng vào `.env` ở thư mục BE-SWP.
Thay số tài khoản, tên chủ tài khoản và khóa bằng giá trị của bạn:

```properties
SEPAY_ENABLED=true
SEPAY_TEST_MODE=true
SEPAY_BANK_CODE=MBBank
SEPAY_ACCOUNT_NUMBER=SO_TAI_KHOAN_TEST_CUA_BAN
SEPAY_ACCOUNT_NAME=TEN_CHU_TAI_KHOAN_TEST
SEPAY_WEBHOOK_API_KEY=KHOA_NGAU_NHIEN_VUA_TAO
```

Khóa phải ít nhất 32 ký tự. Không xóa cấu hình DB, JWT hoặc VNPay đang có trong `.env`.
`SEPAY_TEST_MODE=true` điều khiển nhãn thử nghiệm trên giao diện; chính tài khoản và webhook phải được tạo trong Test mode của SePay.
Ứng dụng không tự gọi API mô phỏng và không có nút tự đánh dấu Paid từ FE.

Khởi động lại BE để đọc `.env`:

```powershell
cd C:\FPT\FA26\SWP\BE-SWP
mvn spring-boot:run
```

Nếu Java mặc định khác JDK 25, đặt trước khi chạy Maven:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
$env:Path = $env:JAVA_HOME + '\bin;' + $env:Path
```

## 4. Mở đường dẫn webhook ra Internet

Mở một terminal khác và chạy:

```powershell
ngrok http 8080
```

Giữ terminal này mở. Ngrok hiển thị **Forwarding** có địa chỉ HTTPS, ví dụ:

```text
https://ten-cua-ban.ngrok-free.app
```

URL webhook cần dùng:

```text
https://ten-cua-ban.ngrok-free.app/api/payments/sepay/webhook
```

Thay domain ví dụ bằng Forwarding thực tế. Nếu domain thay đổi khi chạy lại ngrok, cập nhật webhook SePay.
SePay không gọi được `http://localhost:8080` trên máy tính của bạn.

## 5. Cấu hình mã thanh toán và webhook trong Test mode

1. Trong Test mode, mở **Cấu hình mã thanh toán** (có thể nằm trong Cấu hình Công ty/Cấu hình chung).
2. Thêm tiền tố **SWP**, hậu tố **số**, độ dài **1–10 chữ số** và bật cấu hình.
3. Vào **Test mode → Webhook → Thêm webhook**.
4. Tên: `SWP Member QR test`.
5. URL: URL HTTPS ở bước 4, có đủ `/api/payments/sepay/webhook`.
6. Sự kiện: **Có tiền vào**. Chọn tài khoản MBBank giả lập ở bước 2.
7. Định dạng: **JSON / application/json**.
8. Xác thực: **API Key**. Điền đúng giá trị `SEPAY_WEBHOOK_API_KEY`; không tự thêm chữ `Apikey` vào ô khóa.
9. Nếu dùng bộ lọc mã thanh toán, chọn tiền tố **SWP**. Lưu và bật webhook.

SePay sẽ gửi header `Authorization: Apikey <khóa>`. Đây là khóa webhook, không phải API Access Token của SePay.
Không cần tạo API Access Token cho cách mô phỏng thủ công này.

## 6. Tạo QR trên website Member

```powershell
cd C:\FPT\FA26\SWP\FE-SWP
npm install
npm run dev
```

1. Trên máy tính, mở http://localhost:5173 và đăng nhập Member.
2. Vào **Lớp học → Đăng ký lớp**, chọn lớp có chỗ trống và buổi đầu còn cách hơn 15 phút.
3. Chọn **Thanh toán trực tiếp → Ví điện tử → Xác nhận đăng ký**.
4. Trang thanh toán hiển thị QR, ngân hàng, tài khoản nhận, số tiền, nội dung dạng **SWP123** và hạn thanh toán.
5. Giữ trang này mở. Trạng thái lúc này phải là **Đang chờ xác nhận thanh toán**.

Mã SWP thay đổi theo payment mới; luôn lấy đúng mã và số tiền đang hiển thị.
Nếu đóng trang, vào Lớp đã đăng ký → **Tiếp tục thanh toán** để mở lại.

## 7. Quét ảo trên trình duyệt điện thoại

1. Trên điện thoại, mở https://my.sepay.vn và đăng nhập cùng tài khoản SePay.
2. Bật **Test mode**, vào **Mô phỏng tạo mã VietQR**.
3. Chọn đúng tài khoản MBBank đã cấu hình ở BE.
4. Nhập **đúng số tiền** và **nội dung SWP...** trên trang thanh toán Member ở máy tính.
5. Bấm **Mô phỏng chuyển khoản**.
6. SePay mở khung điện thoại/camera giả lập. Bấm xác nhận quét, kiểm tra số tiền và nội dung, rồi bấm **Chuyển khoản**.
7. Quay lại trang Member trên máy tính: sau webhook và lần kiểm tra trạng thái tiếp theo, trang hiện **Thanh toán và đăng ký thành công**. Mở Lịch tập để xem lớp.

Đây là quét ảo trong giao diện SePay trên trình duyệt điện thoại. Nó không dùng camera thật để đọc QR trên màn hình máy tính.
QR Test mode dùng tài khoản giả lập, nên app ngân hàng thật không thể chuyển tiền tới QR đó.

Nếu giao diện điện thoại khó thao tác, bật **Trang web cho máy tính** trong trình duyệt.
Cách khác: Test mode → Giao dịch → Mô phỏng, chọn Tiền vào và nhập cùng tài khoản/số tiền/nội dung; vẫn kiểm tra được luồng webhook.

## 8. Kiểm tra lỗi và các ca test

| Hiện tượng | Cách kiểm tra |
| --- | --- |
| Chọn Ví điện tử trả 503 | Kiểm tra đủ biến SEPAY, khóa ≥32 ký tự, rồi khởi động lại BE. |
| BE báo thiếu cột Sepay_Transaction_ID | Chạy migration ở bước 3 trên đúng database. |
| Có QR nhưng chưa thành công | Xem Test mode → Webhook → Lịch sử gửi. Kiểm tra ngrok còn chạy. |
| Webhook trả 401 | Khóa API Key trên SePay phải giống `.env`; không chọn Bearer/API Access Token. |
| Webhook trả 200 nhưng vẫn Pending | Kiểm tra tài khoản, ngân hàng MBBank, Tiền vào, mã SWP và số tiền khớp hoàn toàn. |
| Chuyển sau 15 phút / đã hủy lớp | Giao dịch đúng vẫn ghi nhận Paid nhưng cần trung tâm kiểm tra; không tự khôi phục chỗ. |
| Trang đã dừng kiểm tra | Bấm Kiểm tra lại hoặc mở lại Tiếp tục thanh toán. |

Các ca nên thử bằng payment mới: đúng tiền/mã; thiếu tiền; dư tiền; sai mã; sai tài khoản; gửi lại cùng webhook; hết hạn rồi mới chuyển.
Webhook trùng không xác nhận lớp nhiều lần. Thiếu/dư tiền không tự gộp hay tự xác nhận; cần đối soát thủ công.

## Tài liệu chính thức

- Test mode: https://developer.sepay.vn/vi/sepay-webhooks/test-mode/bat-dau-nhanh
- Tài khoản giả lập: https://developer.sepay.vn/vi/tien-ich-khac/test-mode/tai-khoan-ngan-hang
- Quét QR ảo: https://developer.sepay.vn/vi/tien-ich-khac/test-mode/mo-phong-tao-ma-vietqr
- Webhook / API Key / chống trùng: https://docs.sepay.vn/tich-hop-webhooks.html

Hướng dẫn này dành cho demo Test mode với MBBank. Khi chạy tiền thật cần cấu hình ngân hàng liên kết và webhook Live riêng, thay khóa, tắt nhãn Test mode và kiểm tra giao dịch thật.
