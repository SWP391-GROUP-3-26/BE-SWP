# Backend Register Specification

## 1. Mục tiêu

Xây dựng API đăng ký tài khoản cho hệ thống Sports Center Management.

API Register chỉ được phép tạo **tài khoản Member**. Client không được tự chọn Role khi đăng ký.

Các thông tin đăng ký phải được validate trước khi tạo tài khoản, password phải tuân thủ cùng policy với Login và được lưu dưới dạng BCrypt hash.

## 2. Database sử dụng

Bảng chính:

- `User`
  - `User_ID`
  - `Role_ID`
  - `Username`
  - `FullName`
  - `Phone`
  - `Email`
  - `DOB`
  - `Gender`
  - `Address`
  - `Avatar_URL`
  - `Password`
  - `Status`
- `Role`
  - `Role_ID`
  - `Role_Name`
  - `Description`

Database có các role:

- `Admin`
- `Receptionist`
- `Coach`
- `Member`

Register API chỉ được phép tạo account với:

```text
Role = Member
```

## 3. API

### Endpoint

`POST /api/auth/register`

### Request

Ví dụ:

```json
{
  "username": "member01",
  "fullName": "Nguyen Van A",
  "email": "member01@gmail.com",
  "phone": "0912345678",
  "password": "Password@123",
  "confirmPassword": "Password@123"
}
```

Không nhận các field sau từ FE:

```text
roleId
roleName
status
```

BE tự quyết định Role và Status khi tạo account.

## 4. Validation Request

### 4.1 Username

- Không được null.
- Không được rỗng.
- Trim khoảng trắng đầu/cuối.
- Không vượt quá 50 ký tự.
- Chỉ cho phép các ký tự theo rule Username của hệ thống, ví dụ:
  - chữ cái `a-z`, `A-Z`
  - số `0-9`
  - dấu chấm `.`, dấu gạch dưới `_`
- Không được trùng Username đã tồn tại.

### 4.2 FullName

- Không được null.
- Không được rỗng.
- Trim khoảng trắng đầu/cuối.
- Không vượt quá giới hạn của database.

### 4.3 Email

- Không được null.
- Không được rỗng.
- Trim khoảng trắng đầu/cuối.
- Chuyển về lowercase trước khi kiểm tra/tìm kiếm.
- Phải đúng format email hợp lệ.
- Không được trùng Email đã tồn tại.

### 4.4 Phone

- Không được null.
- Không được rỗng.
- Chuẩn hóa về dạng số điện thoại Việt Nam trước khi validate.
- Chỉ chấp nhận số điện thoại thuộc các dải đầu số của:
  - Viettel
  - MobiFone
  - VinaPhone
- Sau chuẩn hóa phải có định dạng mobile number hợp lệ tại Việt Nam.
- Không được trùng Phone đã tồn tại.

> Lưu ý: kiểm tra đầu số chỉ xác định được dải số được cấp cho nhà mạng. Do có Mobile Number Portability (MNP), đầu số không đảm bảo 100% nhà mạng đang khai thác số tại thời điểm hiện tại. Muốn xác minh nhà mạng thực tế cần dịch vụ tra cứu/OTP phù hợp.

### 4.5 Password

Password phải sử dụng **cùng policy với Login**:

- Không được null.
- Không được rỗng.
- Không trim nội dung password.
- Độ dài tối thiểu: 8 ký tự.
- Độ dài tối đa: 72 ký tự nếu sử dụng BCrypt.

Password được khuyến nghị có:

- ít nhất 1 chữ hoa
- ít nhất 1 chữ thường
- ít nhất 1 chữ số
- ít nhất 1 ký tự đặc biệt

Nếu hệ thống đã chốt rule cụ thể khác, FE và BE phải dùng cùng một rule.

### 4.6 Confirm Password

- Không được null.
- Không được rỗng.
- Phải giống chính xác `password`.

```text
password == confirmPassword
```

## 5. Business Rules

### Rule 1 - Chỉ tạo Member

FE **không được gửi Role** để yêu cầu tạo tài khoản khác.

BE luôn lấy Role `Member` từ database.

Pseudo flow:

```text
Find Role where Role_Name = 'Member'
        ↓
Use Member Role_ID
        ↓
Create User
```

### Rule 2 - Không tin Role từ FE

Không sử dụng dữ liệu như:

```json
{
  "roleId": 1
}
```

hoặc:

```json
{
  "roleName": "Admin"
}
```

để quyết định quyền của account.

Nếu client cố gửi các field này, BE có thể bỏ qua hoặc reject request tùy policy API, nhưng tuyệt đối không dùng chúng để cấp quyền.

### Rule 3 - Status mặc định

Sau khi đăng ký thành công, BE tự gán trạng thái mặc định, ví dụ:

```text
Status = Active
```

Nếu sau này hệ thống có Email Verification hoặc OTP thì có thể dùng:

```text
Status = Pending
```

và chỉ chuyển sang `Active` sau khi xác minh thành công.

### Rule 4 - Unique

Các giá trị sau phải unique:

- Username
- Email
- Phone

Việc kiểm tra phải thực hiện ở BE và database nên có `UNIQUE CONSTRAINT/INDEX` để tránh race condition.

## 6. Quy trình Register

```text
Client
  ↓
POST /api/auth/register
  ↓
Validate request
  ↓
Normalize username/email/phone
  ↓
Validate phone number
  ↓
Validate password + confirmPassword
  ↓
Check Username exists?
  ├── Yes → Register failed
  └── No
       ↓
Check Email exists?
  ├── Yes → Register failed
  └── No
       ↓
Check Phone exists?
  ├── Yes → Register failed
  └── No
       ↓
Find Role = Member
       ↓
BCrypt encode password
       ↓
Create User
       ↓
Set Role = Member
       ↓
Set default Status
       ↓
Save User
       ↓
Return register response
```

## 7. Password Security

Không lưu password dạng plain text.

Khi register:

```text
raw password
    ↓
BCryptPasswordEncoder.encode()
    ↓
stored hash
    ↓
Database
```

Không gửi password hoặc password hash trong response.

Ví dụ:

```java
String encodedPassword = passwordEncoder.encode(request.getPassword());
```

## 8. N-Layer Architecture

Register API tuân theo kiến trúc N-Layer + Spring Boot:

```text
FE React
   ↓
RegisterController
   ↓
RegisterService
   ↓
UserRepository / RoleRepository
   ↓
JPA / Hibernate
   ↓
SQL Server
```

### Controller

Nhận HTTP request và trả HTTP response.

```text
POST /api/auth/register
```

### Service

Chứa business logic:

- validate business rules
- check duplicate Username/Email/Phone
- lấy Role Member
- BCrypt password
- tạo User

### Repository

Truy vấn database:

```text
existsByUsername(...)
existsByEmail(...)
existsByPhone(...)
findRoleByRoleName("Member")
save(...)
```

### Entity

Mapping các bảng:

```text
User
Role
```

### DTO

Nên có:

```text
RegisterRequest
RegisterResponse
```

Không trả trực tiếp entity `User` ra FE.

## 9. Success Response

HTTP `201 Created`:

```json
{
  "success": true,
  "message": "Đăng ký tài khoản thành công",
  "data": {
    "userId": 10,
    "username": "member01",
    "fullName": "Nguyen Van A",
    "email": "member01@gmail.com",
    "phone": "0912345678",
    "role": "Member",
    "status": "Active"
  }
}
```

Không trả:

```text
password
password hash
```

## 10. Error Response

### 400 - Validation Error

Ví dụ:

```json
{
  "success": false,
  "message": "Password không hợp lệ"
}
```

Hoặc:

```json
{
  "success": false,
  "message": "Số điện thoại không thuộc nhà mạng được hỗ trợ"
}
```

### 409 - Duplicate Data

Ví dụ:

```json
{
  "success": false,
  "message": "Username đã tồn tại"
}
```

Các trường hợp:

```text
Username đã tồn tại
Email đã tồn tại
Phone đã tồn tại
```

### 500 - Server Error

Lỗi hệ thống hoặc database không được trả nguyên stack trace cho FE.

## 11. Database Constraint đề xuất

Nên đảm bảo database cũng có unique constraint/index:

```sql
CREATE UNIQUE INDEX UX_User_Username
ON [User](Username);

CREATE UNIQUE INDEX UX_User_Email
ON [User](Email);

CREATE UNIQUE INDEX UX_User_Phone
ON [User](Phone);
```

Tên index có thể thay đổi theo convention của project.

## 12. Security Requirements

- Không cho FE tự chọn Role.
- Không lưu password plain text.
- Không trả password/hash về FE.
- Validate toàn bộ dữ liệu ở BE, không chỉ dựa vào FE.
- Không log password hoặc password hash.
- Không leak thông tin nhạy cảm trong error response.
- Có thể bổ sung rate limit/CAPTCHA/Email OTP nếu hệ thống yêu cầu chống abuse.

## 13. Test Cases

### Valid

- Username hợp lệ + email hợp lệ + phone Viettel/MobiFone/VinaPhone + password hợp lệ → tạo Member thành công.
- Đăng ký nhiều account khác nhau với dữ liệu unique → thành công.

### Invalid Username

- Username rỗng.
- Username quá dài.
- Username chứa ký tự không cho phép.
- Username đã tồn tại.

### Invalid Email

- Email rỗng.
- Email sai format.
- Email đã tồn tại.

### Invalid Phone

- Phone rỗng.
- Phone sai format.
- Phone không thuộc dải đầu số được hỗ trợ.
- Phone đã tồn tại.

### Invalid Password

- Password rỗng.
- Password dưới 8 ký tự.
- Password vượt quá 72 ký tự khi sử dụng BCrypt.
- Password không đạt complexity rule đã thống nhất.
- `confirmPassword` khác `password`.

### Role Security

- FE không gửi Role → vẫn tạo Member.
- FE gửi `roleId = Admin` → không được tạo Admin.
- FE gửi `roleName = Coach` → không được tạo Coach.
- FE cố sửa Role trong request → Role vẫn phải được BE quyết định là Member.

## 14. Definition of Done

Register được xem là hoàn thành khi:

- API `POST /api/auth/register` hoạt động.
- Request được validate ở BE.
- Username/Email/Phone unique.
- Phone chỉ chấp nhận các dải đầu số theo policy.
- Password được hash bằng BCrypt.
- Account tạo ra luôn có Role = Member.
- FE không thể dùng API Register để tạo Admin/Receptionist/Coach.
- Không trả password/hash về client.
- Có HTTP status và error response rõ ràng.
- Có unit/integration tests cho các rule quan trọng.
