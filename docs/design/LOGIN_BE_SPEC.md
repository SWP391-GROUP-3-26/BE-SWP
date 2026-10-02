# Backend Login Specification

## 1. Mục tiêu

Xây dựng API đăng nhập cho hệ thống Sports Center Management, cho phép người dùng đăng nhập bằng Username hoặc Email + Password và chỉ cho phép tài khoản hợp lệ, đang hoạt động và có Role hợp lệ truy cập hệ thống.

## 2. Database hiện tại

Bảng chính sử dụng cho đăng nhập:

- `User`
  - `User_ID`
  - `Role_ID`
  - `Username`
  - `FullName`
  - `Email`
  - `Password`
  - `Status`
- `Role`
  - `Role_ID`
  - `Role_Name`
  - `Description`

Database mẫu hiện có 4 role: `Admin`, `Receptionist`, `Coach`, `Member`.

> Dữ liệu mẫu hiện đang dùng mật khẩu thô `12345@` chỉ cho mục đích test. Khi triển khai BE thật phải đổi sang BCrypt hash.

> **Lưu ý về Username:** Database hiện tại phải có thêm cột `Username` trong bảng `User` nếu muốn đăng nhập bằng tên tài khoản. Cột này phải `NOT NULL` và `UNIQUE` đối với các tài khoản có đăng nhập.

Ví dụ SQL Server:

```sql
ALTER TABLE [User]
ADD Username VARCHAR(50) NOT NULL;

CREATE UNIQUE INDEX UX_User_Username
ON [User](Username);
```

Sau đó bổ sung Username cho dữ liệu mẫu, ví dụ `admin`, `receptionist`, `coach`, `member`.

## 3. API

### Endpoint

`POST /api/auth/login`

### Request

Người dùng có thể đăng nhập bằng **Username hoặc Email**. BE nhận một field chung là `identifier` để FE không cần biết người dùng đang dùng loại tài khoản nào.

```json
{
  "identifier": "member",
  "password": "12345@"
}
```

Hoặc:

```json
{
  "identifier": "member@gym.com",
  "password": "12345@"
}
```

## 4. Validation Request

### Identifier (Username hoặc Email)

- Không được null.
- Không được rỗng.
- Trim khoảng trắng đầu/cuối.
- Không vượt quá 255 ký tự.
- Nếu identifier chứa `@` thì xử lý như Email và chuyển về lowercase trước khi tìm tài khoản.
- Nếu không chứa `@` thì xử lý như Username và chuyển về lowercase trước khi tìm tài khoản, tùy policy của hệ thống.
- Username phải tuân thủ rule đã thống nhất của hệ thống, ví dụ chỉ cho phép chữ cái, số, dấu chấm hoặc dấu gạch dưới.
- Username phải unique. Email cũng phải unique.

### Password

- Không được null.
- Không được rỗng.
- Không trim để tránh thay đổi password do người dùng nhập.
- Độ dài tối thiểu đề xuất: 8 ký tự.
- Độ dài tối đa đề xuất: 72 ký tự nếu dùng BCrypt.

## 5. Quy trình Login

```text
Client
  ↓
POST /api/auth/login
  ↓
Validate request
  ↓
Normalize identifier
  ↓
Find User by username OR email
  ↓
User exists?
  ├── No  → Login failed
  └── Yes
       ↓
Check User.Status
       ├── Inactive/Blocked → Login failed
       └── Active
             ↓
       Check Role
             ├── Missing/invalid → Login failed
             └── Valid
                   ↓
       Verify BCrypt password
             ├── Wrong → Login failed
             └── Correct
                   ↓
               Generate JWT
                   ↓
               Return login response
```

## 6. Các ràng buộc tài khoản

### 6.1 Account không tồn tại

Không tiết lộ rằng email có tồn tại hay không.

Response đề xuất:

```json
{
  "success": false,
  "message": "Tên tài khoản, email hoặc mật khẩu không chính xác"
}
```

### 6.2 Account bị inactive/blocked

Không cho đăng nhập.

Không nên trả về thông tin quá chi tiết cho client nếu không cần thiết.

### 6.3 Account không có Role

Không cho đăng nhập vì hệ thống không xác định được quyền truy cập.

### 6.4 Password sai

Không cho đăng nhập.

## 7. Password Security

Không lưu password dạng plain text trong môi trường thực tế.

BE sử dụng:

```text
BCryptPasswordEncoder
```

Khi đăng ký hoặc đổi password:

```text
raw password
    ↓
BCrypt encode
    ↓
hash lưu vào DB
```

Khi login:

```text
raw password
    ↓
BCrypt.matches(rawPassword, storedHash)
```

Không tự hash password rồi so sánh chuỗi bằng `equals()`.

## 8. JWT

Sau khi login thành công, BE tạo JWT.

JWT nên chứa tối thiểu:

- `sub`: User_ID hoặc Username
- `username`: Username
- `email`: Email
- `role`: Role_Name
- `iat`: thời gian phát hành
- `exp`: thời gian hết hạn

Thời gian hết hạn đề xuất: 1 giờ.

Client gửi token ở các request cần authentication:

```http
Authorization: Bearer <JWT_TOKEN>
```

## 9. Response thành công

```json
{
  "success": true,
  "message": "Đăng nhập thành công",
  "data": {
    "accessToken": "JWT_TOKEN",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "userId": 4,
      "fullName": "Phạm Minh Anh",
      "username": "member",
      "email": "member@gym.com",
      "role": "Member",
      "status": "Active"
    }
  }
}
```

## 10. Response lỗi

### 400 - Validation

```json
{
  "success": false,
  "message": "Email không hợp lệ"
}
```

Hoặc:

```json
{
  "success": false,
  "message": "Password không được để trống"
}
```

### 401 - Login failed

```json
{
  "success": false,
  "message": "Tên tài khoản, email hoặc mật khẩu không chính xác"
}
```

### 403 - Account không được phép truy cập

```json
{
  "success": false,
  "message": "Tài khoản không được phép đăng nhập"
}
```

## 11. Chống brute-force / login spam

Đề xuất cho BE:

- Theo dõi số lần login thất bại.
- Ví dụ: tối đa 5 lần thất bại trong 15 phút cho một email/IP.
- Sau khi vượt ngưỡng, tạm khóa login trong một khoảng thời gian.
- Không trả response khác nhau để lộ email có tồn tại hay không.

Đây là security enhancement, có thể triển khai sau bản login cơ bản.

## 12. CORS

Chỉ cho phép FE domain được cấu hình gọi API.

Không bật:

```text
allowOrigin = *
```

cho môi trường production nếu API dùng authentication.

## 13. Architecture

```text
AuthController
      ↓
AuthService
      ↓
UserRepository
      ↓
SQL Server
```

Các component chính:

```text
controller/AuthController.java
service/AuthService.java
repository/UserRepository.java
repository/RoleRepository.java
entity/User.java
entity/Role.java
dto/LoginRequest.java
dto/LoginResponse.java
dto/UserResponse.java
security/JwtService.java
security/JwtAuthenticationFilter.java
config/SecurityConfig.java
exception/GlobalExceptionHandler.java
```

## 14. Repository cần có

```java
Optional<User> findByUsernameIgnoreCase(String username);
Optional<User> findByEmailIgnoreCase(String email);
```

Hoặc query tương đương nếu DB/configuration yêu cầu.

## 15. Service Login - pseudo code

```text
login(request):

1. Validate request
2. identifier = request.identifier.trim().toLowerCase()
3. Nếu identifier là email → tìm bằng email
4. Nếu identifier là username → tìm bằng username
5. user = repository.findByUsername(...) hoặc repository.findByEmail(...)
6. Nếu không tồn tại → 401
7. Nếu status != Active → 403/401 theo policy
8. Nếu role == null → không cho login
9. BCrypt.matches(password, user.password)
10. Nếu sai → 401
11. Generate JWT
12. Return token + user information
```

## 16. Không trả password về FE

Tuyệt đối không đưa field `password` vào JSON response.

Không trả trực tiếp entity `User` từ Controller nếu entity có field password.

Nên map sang DTO:

```java
UserResponse
```

## 17. Logout

Với JWT stateless, logout phía FE thường xóa token.

API logout có thể thêm sau:

`POST /api/auth/logout`

Nếu hệ thống yêu cầu revoke token thực sự thì cần cơ chế token blacklist/session management.

## 18. Test cases bắt buộc

| Case | Input | Expected |
|---|---|---|
| Valid login | đúng username + đúng password | 200 + JWT |
| Valid login | đúng email + đúng password | 200 + JWT |
| Wrong password | email đúng + password sai | 401 |
| Unknown identifier | username/email không tồn tại | 401 |
| Empty identifier | identifier rỗng | 400 |
| Invalid identifier | username/email sai format | 400 |
| Empty password | password rỗng | 400 |
| Inactive account | account inactive | không cho login |
| Missing role | role null | không cho login |
| Email uppercase | `MEMBER@GYM.COM` | xử lý theo normalize email |
| Username uppercase | `MEMBER` | xử lý theo normalize username |
| Identifier có space | ` member@gym.com ` | trim rồi xử lý |

## 19. Thứ tự code BE

### Step 1
Entity:

- `User`
- `Role`

### Step 2
Repository:

- `UserRepository`
- `RoleRepository`

### Step 3
DTO:

- `LoginRequest`
- `LoginResponse`
- `UserResponse`

### Step 4
Security:

- BCrypt
- JWT service
- JWT filter
- SecurityConfig

### Step 5
Service:

- `AuthService.login()`

### Step 6
Controller:

- `POST /api/auth/login`

### Step 7
Test bằng Swagger/Postman.

### Step 8
FE gọi API thật và lưu access token.

## 20. Definition of Done

Login được xem là hoàn thành khi:

- [ ] FE gửi identifier (username/email) + password đến BE.
- [ ] BE validate request.
- [ ] BE tìm được User trong SQL Server bằng Username hoặc Email.
- [ ] BE kiểm tra Status.
- [ ] BE kiểm tra Role.
- [ ] BE verify password bằng BCrypt.
- [ ] Login sai trả 401.
- [ ] Input sai trả 400.
- [ ] Login đúng trả JWT.
- [ ] Response không chứa password.
- [ ] API test thành công bằng Swagger/Postman.
- [ ] FE login thành công bằng API thật.
