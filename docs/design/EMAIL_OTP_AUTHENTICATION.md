# Email OTP và khôi phục mật khẩu (BE)

## Phạm vi và dữ liệu

Chỉ thay đổi BE. Popup, nút điều hướng và màn hình do FE triển khai sau theo contract dưới đây.

- Member chưa xác thực email không nhận access token khi đăng nhập. Chỉ nhận một `verificationToken` ngẫu nhiên cho luồng email; token này không phải JWT và không truy cập được API nghiệp vụ.
- Admin/nhân viên giữ luồng đăng nhập, không bị chặn bởi `Email_Verified`.
- Tự đăng ký thường: kiểm tra dữ liệu như cũ, lưu yêu cầu tạm với mật khẩu đã băm; chỉ tạo User/Member Active sau OTP đúng. Không cấp JWT sau đăng ký.
- Lễ tân tạo Member qua API cũ: tạo ngay `LOCAL`, chưa xác thực; lần đăng nhập tiếp theo bắt buộc xác thực. Cả Member cũ chưa xác thực cũng dùng luồng này.
- Google mới: `GOOGLE`, email đã xác thực (handler kiểm tra `email_verified` từ Google). Google đăng nhập vào tài khoản LOCAL hiện có chứng minh quyền sở hữu email nhưng không đổi nguồn tạo tài khoản.
- Quên mật khẩu: chỉ tài khoản `LOCAL`, Active. GOOGLE được hướng dẫn đăng nhập Google; UNKNOWN phải được admin rà soát trước. Email không tồn tại trả 404 theo nghiệp vụ đã yêu cầu.
- Trạng thái xác thực email độc lập trạng thái Active/Inactive/Locked. OTP không tự mở khóa tài khoản. Luồng quên mật khẩu không thay thế luồng xác thực Member; Member chưa xác thực vẫn phải xác thực khi đăng nhập.

`User` thêm `Email_Verified`, `Auth_Provider` (LOCAL/GOOGLE/UNKNOWN), `Session_Version`. `Email_Otp_Challenge` lưu một dòng/email nhận chuẩn hóa, token/OTP đã băm, hạn dùng, số lần sai, lịch gửi gần nhất và thông tin đăng ký tạm. `Original_Email` giữ email tài khoản khi bắt đầu đổi để phát hiện thay đổi trong lúc chờ. Không lưu mật khẩu/OTP/token dạng rõ trong CSDL hoặc trả OTP qua API. DTO chứa bí mật có `toString` che dữ liệu để tránh log DEBUG của Spring MVC.

Email gửi dưới dạng HTML UTF-8: lời chào, khung OTP nền xám với mã màu xanh, thời hạn 5 phút, lưu ý bảo mật và chữ ký UNISPORT. Có phần văn bản thuần đi kèm cho ứng dụng email không hiển thị HTML. Luồng đặt lại mật khẩu dùng cùng bố cục nhưng nội dung ghi rõ mục đích đặt lại mật khẩu.

## Quy định và xử lý đồng thời

- OTP 6 chữ số, giữ cả số 0 đầu, hết hạn chính xác sau 300 giây.
- Sai tối đa 5 lần; lần thứ 5 khóa mã đó. Số lần sai vẫn được commit khi trả lỗi OTP, không bị transaction rollback mất bộ đếm.
- Gửi lại tối thiểu sau 60 giây, tối đa 5 thư/email trong cửa sổ trượt 60 phút, cộng chung mọi mục đích. Đăng nhập lại, tạo yêu cầu mới hoặc đổi mục đích không xóa giới hạn này.
- Mã mới vô hiệu mã cũ. REGISTER, VERIFY_EMAIL, RESET, VERIFY_CHANGE_EMAIL và CHANGE_EMAIL không dùng thay nhau. Mỗi email nhận chỉ có một luồng đang chờ; bắt đầu luồng mới vô hiệu token luồng cũ của email nhận đó.
- Token ngữ cảnh có hạn 15 phút để hoàn tất hoặc gửi lại OTP. OTP vẫn chỉ có hạn 5 phút. Khi token ngữ cảnh hết hạn, bắt đầu lại đăng ký/quên mật khẩu hoặc đăng nhập lại để lấy token mới.
- Xác nhận OTP quên mật khẩu trả `resetToken` ngẫu nhiên 256 bit, hạn 5 phút, dùng một lần, chỉ cho đổi mật khẩu của đúng User/email/phiên bản phiên đã xác thực.
- Giữ giới hạn gửi sau khi tiêu thụ OTP. Dọn dòng hết hạn mỗi giờ khi lần gửi cuối đã quá một giờ và mọi token đã hết hạn; xóa thông tin đăng ký tạm ngay khi thành công.
- Khóa dòng email khi gửi/kiểm tra, khóa User khi xác nhận hoặc đổi mật khẩu. Kiểm tra lại trùng dữ liệu trước khi tạo User; unique constraint vẫn bảo vệ email/username. Hai yêu cầu tạo dòng OTP đầu tiên đồng thời có thể trả 409 cho một yêu cầu, cần thử lại.
- SMTP lỗi trả 503 và rollback thay đổi OTP; không báo thành công giả. Nếu SMTP đã nhận thư nhưng transaction cuối cùng lỗi, mã có thể không dùng được; người dùng gửi lại sau khi thử lại yêu cầu.

## Contract API

Các API này không yêu cầu JWT. POST body là JSON; response có `success`, `message`, `data`.

| API | Body | Kết quả |
| --- | --- | --- |
| POST /api/auth/register | Các trường đăng ký hiện có | 202, Challenge; chưa tạo User |
| POST /api/auth/register/verify | VerifyRequest | 201, RegisterResponse hiện có; không JWT |
| POST /api/auth/register/resend | ContextRequest | 200, đã gửi lại |
| POST /api/auth/login | identifier, password | LoginResponse hoặc yêu cầu xác thực bên dưới |
| POST /api/auth/email-verification/request | ContextRequest từ đăng nhập đúng mật khẩu | 200, gửi OTP; cũng dùng để gửi lại |
| POST /api/auth/email-verification/verify | VerifyRequest | LoginResponse có JWT sau xác thực thành công |
| POST /api/auth/forgot-password | email | 200, Challenge |
| POST /api/auth/forgot-password/resend | ContextRequest | 200, đã gửi lại |
| POST /api/auth/forgot-password/verify | VerifyRequest | 200, resetToken và expiresAt |
| POST /api/auth/reset-password | email, resetToken, password, confirmPassword | 200, đăng nhập lại |

ContextRequest: `{"email":"member@example.com","verificationToken":"<token từ BE>"}`.

VerifyRequest thêm `"otp":"003291"` vào ContextRequest. Mã là chuỗi, không gửi số nguyên.

Challenge gồm `email`, `verificationToken`, `expiresAt` (hạn token ngữ cảnh), `otpExpiresInSeconds:300`, `resendAfterSeconds` (thời gian còn phải chờ).

Đăng nhập Member chưa xác thực trả HTTP 200 với:

```json
{
  "success": true,
  "message": "Vui lòng xác thực email trước khi sử dụng tài khoản",
  "data": {
    "accessToken": null,
    "tokenType": null,
    "expiresIn": 0,
    "user": null,
    "emailVerificationRequired": true,
    "emailVerification": {
      "email": "member@example.com",
      "verificationToken": "<token chỉ dùng cho xác thực email>",
      "expiresAt": "<ISO UTC>",
      "otpExpiresInSeconds": 300,
      "resendAfterSeconds": 0
    }
  }
}
```

FE dựa trên `emailVerificationRequired` để hiện thông báo/nút điều hướng, rồi gọi request OTP khi Member vào màn hình xác thực. Đăng nhập thường thành công vẫn có các trường cũ, bổ sung `emailVerificationRequired:false` và `emailVerification:null`. Các lỗi: 400 dữ liệu/mã không hợp lệ hoặc hết hạn; 403 tài khoản Google hoặc không Active; 404 email không tồn tại; 409 trùng dữ liệu/nguồn UNKNOWN; 429 giới hạn gửi; 503 SMTP.

## Tự đổi email

Chỉ nguồn LOCAL/UNKNOWN được tự đổi; GOOGLE giữ email Google. Xác thực email mới không tự chuyển UNKNOWN thành LOCAL. UNKNOWN vẫn phải qua admin rà soát trước khi dùng quên mật khẩu.

**Tại màn hình xác thực Member:** dùng bằng chứng đăng nhập đúng mật khẩu từ `/api/auth/login`, không cần JWT và không cần nhận OTP ở email cũ (hỗ trợ email giả/không còn dùng được).

Email nguồn lấy đúng từ response login; nếu dữ liệu cũ sai cả định dạng, BE vẫn cho lấy token sau khi đăng nhập đúng username/mật khẩu để chuyển sang email mới hợp lệ. Không gửi thư đến địa chỉ nguồn sai này. Email đích luôn phải hợp lệ.

| API | Body | Kết quả |
| --- | --- | --- |
| POST /api/auth/email-verification/change-email/request | email cũ, verificationToken từ login, newEmail | Challenge mới với email nhận mới |
| POST /api/auth/email-verification/change-email/resend | ContextRequest dùng email mới và token mới | Gửi lại OTP đến email mới |
| POST /api/auth/email-verification/change-email/verify | VerifyRequest dùng email mới và token mới | Đổi email, xác thực tài khoản, LoginResponse có JWT mới |

Ví dụ request đầu tiên:

```json
{
  "email": "fake@example.invalid",
  "verificationToken": "<token giới hạn từ đăng nhập đúng mật khẩu>",
  "newEmail": "real@example.com"
}
```

Sau khi gửi OTP thành công, token giới hạn ở email cũ bị tiêu thụ; tiếp tục bằng `data.email` và `data.verificationToken` của Challenge mới. Nếu SMTP lỗi, transaction rollback và token cũ vẫn dùng được. Luồng này chỉ dành cho Member chưa xác thực; Member đã xác thực phải dùng hồ sơ.

**Tại hồ sơ:** người dùng đã xác thực email gửi JWT hiện tại; không nhận User_ID từ body.

| API | Body | Kết quả |
| --- | --- | --- |
| POST /api/users/me/email-change/request | newEmail, currentPassword | Challenge; gửi OTP sau khi kiểm tra mật khẩu hiện tại |
| POST /api/users/me/email-change/resend | ContextRequest dùng email mới | Gửi lại OTP; chỉ chủ tài khoản đang đăng nhập |
| POST /api/users/me/email-change/verify | VerifyRequest dùng email mới | Đổi email, LoginResponse có JWT mới |

Ví dụ request hồ sơ (header `Authorization: Bearer <JWT hiện tại>`):

```json
{"newEmail":"real@example.com","currentPassword":"<mật khẩu hiện tại>"}
```

`GET /api/users/me` bổ sung `emailVerified`, `authProvider`, `canChangeEmail` để FE hiển thị chức năng phù hợp. `PUT /api/users/me` thông thường không đổi email; phải dùng luồng OTP riêng này. Xác nhận mật khẩu hiện tại trước khi đổi thông tin định danh phù hợp với hướng dẫn [OWASP về tái xác thực](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).

Quy tắc dùng chung:

- Email mới phải hợp lệ, khác email cũ (không phân biệt hoa/thường), chưa thuộc tài khoản khác. Kiểm tra lại khi xác nhận OTP; unique constraint là bước bảo vệ cuối cho yêu cầu đồng thời.
- Chưa xác nhận OTP thì không sửa User.Email, Email_Verified hoặc Session_Version. Hồ sơ và phiên hiện tại vẫn dùng email cũ. Yêu cầu đổi chưa xác nhận không giữ chỗ email.
- OTP đúng mới cập nhật email và đánh dấu đã xác thực, tăng Session_Version, đóng socket cũ sau commit và trả JWT mới. FE lưu JWT/user mới trong LoginResponse, cập nhật email hiển thị và kết nối lại chat. JWT cũ, OTP/token cũ của tài khoản đều mất hiệu lực qua đối chiếu phiên bản/email.
- Yêu cầu đang chờ gắn với User_ID, email gốc và phiên bản phiên. Đổi mật khẩu, quản lý đổi email hoặc tài khoản bị khóa trong thời gian chờ sẽ chặn xác nhận. Nếu có nhiều email đích đang chờ, lần xác nhận thành công đầu tiên vô hiệu các lần còn lại.
- Vẫn áp dụng 5 phút, 5 lần sai, 60 giây gửi lại và 5 thư trong cửa sổ trượt 60 phút cho từng email nhận, cộng chung các mục đích.
- Khóa các dòng email theo thứ tự ổn định rồi mới khóa User; cập nhật hồ sơ/avatar cũng khóa User để không ghi đè email hoặc phiên bản phiên vừa đổi.
- Lỗi thêm: 400 sai mật khẩu hiện tại, email không đổi, sai chủ token/trạng thái; 401 chưa đăng nhập ở hồ sơ; 403 GOOGLE; 409 email mới đã được dùng hoặc email tài khoản vừa đổi trong khi xử lý.

## Rà soát tài khoản cũ

Migration đọc audit log chỉ lần đầu thêm `Auth_Provider`. Nhận diện mẫu thông báo cố định và đúng User_ID của REGISTER_USER/CREATE_MEMBER/CREATE_USER, không dò email domain hay hash mật khẩu. Bằng chứng Google và LOCAL xung đột, thiếu hoặc không khớp mẫu => UNKNOWN. Google xác định chắc chắn được đánh dấu đã xác thực email; Member LOCAL/UNKNOWN cũ chưa xác thực phải xác thực khi đăng nhập.

Chỉ Admin có:

- GET `/api/admin/account-identities?page=0&size=20`: danh sách UNKNOWN, tối đa 100/trang.
- PUT `/api/admin/account-identities/{id}` với `{"provider":"LOCAL","reason":"Đã đối chiếu hồ sơ"}`: xác định LOCAL hoặc GOOGLE, lưu audit, vô hiệu phiên cũ. Không dùng API này để ghi đè nguồn đã xác định, không tự đánh dấu email đã xác thực.

Response quản lý người dùng thêm `emailVerified` và `authProvider`. Khi quản trị đổi email, email mất xác thực và các phiên cũ bị vô hiệu; token OTP gắn email cũ không dùng được.

## Vô hiệu phiên

JWT mới chứa claim `sv`; mỗi lần xác thực HTTP hoặc frame chat SEND/SUBSCRIBE, BE đối chiếu phiên bản, trạng thái, vai trò và điều kiện xác thực email với User trong CSDL. Reset mật khẩu tăng phiên bản và tiêu thụ resetToken trong cùng transaction. JWT cũ không có `sv` bị từ chối sau nâng cấp: mọi người dùng cần đăng nhập lại một lần.

Socket chat đang kết nối được đóng sau transaction đổi mật khẩu commit. Cơ chế theo dõi socket áp dụng cho instance ứng dụng hiện tại (project đang dùng simple broker); triển khai nhiều instance cần phát sự kiện thu hồi giữa các instance. API reset mật khẩu của quản lý cũng tăng phiên bản/đóng socket để không tạo đường vòng.

## Cấu hình và kiểm tra

1. Áp dụng `docs/database/migrations/20261110_email_otp_authentication.sql` trước khi chạy BE; nếu đã áp dụng bản OTP trước đó, chạy lại cùng file để thêm `Original_Email` và cập nhật CHECK cho hai mục đích đổi email, không tạo file migration rời. Chạy lại không backfill lại nguồn tài khoản đã có hoặc xóa dữ liệu/bộ đếm gửi. Xem danh sách UNKNOWN và rà soát bằng API Admin. Schema khởi tạo mới cũng đã bổ sung bảng/cột.
2. Điền cấu hình SMTP vào `.env` đang bị Git ignore theo mẫu `docs/config/email.env.example`; không gửi mật khẩu SMTP trong chat hoặc commit.
3. Khởi động BE, đăng ký một email thử thuộc quyền kiểm soát, lấy OTP thực từ hộp thư, xác nhận chỉ tạo User khi OTP đúng. Kiểm tra resend, mã cũ, sai 5 lần và hạn 5 phút.
4. Tạo Member qua lễ tân, đăng nhập lấy token xác thực giới hạn, kiểm tra chưa truy cập được API Member; xác thực rồi nhận JWT.
5. Quên mật khẩu LOCAL, kiểm tra không đổi được khi thiếu resetToken hoặc token đã dùng/hết hạn; sau reset, JWT cũ và socket cũ không còn sử dụng được. GOOGLE/UNKNOWN/Locked không được khôi phục bằng luồng này.
6. Member có email giả: đăng nhập lấy token giới hạn, yêu cầu đổi sang email thật, đọc OTP ở hộp thư mới rồi xác nhận. Kiểm tra email chỉ đổi sau xác nhận, token login cũ không gửi được OTP nữa, JWT mới truy cập được API Member.
7. Tại hồ sơ: gửi JWT và mật khẩu hiện tại để đổi email; thử mật khẩu sai, email trùng, OTP sai/hết hạn, xác nhận bằng tài khoản khác. Sau thành công, thử JWT cũ và token OTP cũ; chúng phải bị từ chối. GOOGLE không được tự đổi.

Unit/MockMvc và kiểm tra mapping/JPQL không thay thế kiểm tra SQL Server, SMTP thực, Google OAuth hoặc FE. Tham khảo cơ chế mail của [Spring Boot](https://docs.spring.io/spring-boot/reference/io/email.html) và nguyên tắc reset dùng một lần của [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).
