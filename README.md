# SWP391 - SereneDesk Fitness & Sports

## 1. Giới thiệu

Đây là project SWP391 - hệ thống quản lý trung tâm thể thao/fitness SereneDesk.

Project được tách thành 2 repository:

- `BE-SWP`: Backend Spring Boot + SQL Server
- `FE-SWP`: Frontend React + Vite

## 2. Công nghệ sử dụng

### Backend

- Java JDK 25
- Spring Boot 4.1.1
- Maven
- Spring Web
- Spring Data JPA
- Spring Security
- JWT
- Validation
- Lombok
- Microsoft SQL Server

### Frontend

- Node.js 22.x
- React
- Vite
- JavaScript
- React Router
- Axios
- Bootstrap
- CSS

## 3. Yêu cầu môi trường

Trước khi chạy project, máy cần có:

- JDK 25
- Node.js 22.x
- npm
- Microsoft SQL Server
- SQL Server Management Studio (SSMS) hoặc công cụ tương đương
- Git

Kiểm tra phiên bản:

```bash
java -version
node -v
npm -v
git --version
```

## 4. Cấu trúc project

```text
SWP391/
├── BE-SWP/
└── FE-SWP/
```

## 5. Chuẩn bị Database

Backend sử dụng Microsoft SQL Server.

Database hiện tại của project là:

```text
swp391_db
```

### 5.1. Tạo database

Nếu database chưa tồn tại, sử dụng SQL script trong Backend:

```text
BE-SWP/docs/sql/
chạy 
+ swp391_db_sample_data.sql
+ swp391_db.sql

Mở script bằng SQL Server Management Studio và chạy bằng SQL Server.

> Lưu ý: kiểm tra lại tên database, username/password và quyền của SQL Server trước khi chạy script.

### 5.2. Cấu hình Backend kết nối SQL Server

Mở file cấu hình Spring Boot, thường là:

```text
BE-SWP/src/main/resources/application.properties
```

hoặc:

```text
BE-SWP/src/main/resources/application.yml
```

Điền thông tin SQL Server của máy local theo cấu hình project hiện tại.

Không commit password database hoặc secret thật lên GitHub.

## 6. Chạy Backend

Mở Terminal tại thư mục:

```text
BE-SWP
```

### Windows

```powershell
mvnw.cmd spring-boot:run
```

Nếu chạy thành công, Backend sẽ chạy tại:

```text
http://localhost:8080
```

Có thể kiểm tra log và tìm dòng tương tự:

```text
Started BeSwpApplication
```

## 7. Chạy Frontend

Mở Terminal thứ hai tại thư mục:

```text
FE-SWP
```

Cài dependency lần đầu:

```bash
npm install
```

Sau đó chạy:

```bash
npm run dev
```

Frontend thường chạy tại:

```text
http://localhost:5173
```

Mở trình duyệt và truy cập:

```text
http://localhost:5173
```

## 8. Thứ tự chạy project

Mỗi lần làm việc với project, nên chạy theo thứ tự:

### Terminal 1 - Backend

```powershell
cd BE-SWP
mvnw.cmd spring-boot:run
```

### Terminal 2 - Frontend

```powershell
cd FE-SWP
npm run dev
```

Sau đó mở:

```text
http://localhost:5173
```

## 9. Authentication

Frontend hiện tích hợp với Backend Authentication.

Login sử dụng:

```http
POST http://localhost:8080/api/auth/login
```

Request:

```json
{
  "email": "...",
  "password": "..."
}
```

Backend trả response có cấu trúc dạng:

```json
{
  "success": true,
  "message": "Dang nhap thanh cong",
  "data": {
    "accessToken": "...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "userId": 7,
      "fullName": "...",
      "username": "...",
      "email": "...",
      "role": "Member",
      "status": "Active"
    }
  }
}
```

Frontend sử dụng thông tin authentication để xác định user và điều hướng theo role.

## 10. Các route chính của Frontend

Public:

```text
/
/login
/register
```

Protected dashboard:

```text
/member
/receptionist
/coach
/center-manager
```

User chưa đăng nhập không được truy cập các trang protected.

## 11. Register

Register được thực hiện thông qua Backend API hiện tại.

Tài khoản đăng ký mới phải tuân theo validation của Backend.

Role của tài khoản đăng ký phải do Backend quyết định theo logic hiện tại; Frontend không cho người dùng tự chọn role quản trị hoặc nhân viên.

Sau khi đăng ký thành công, user có thể quay về trang Login để đăng nhập.

## 12. Kiểm tra nhanh sau khi chạy

### Backend

Kiểm tra:

- Backend start thành công
- Không có lỗi kết nối SQL Server
- Port `8080` đang hoạt động

### Frontend

Kiểm tra:

- Home mở được
- Login mở được
- Register mở được
- Login thành công với tài khoản hợp lệ
- User được đưa tới đúng dashboard theo role
- Logout đưa user về Home
- User chưa đăng nhập không truy cập được protected route

## 13. Nếu Login/Register bị lỗi

Mở Developer Tools trong trình duyệt:

```text
F12 → Network
```

Kiểm tra request đến:

```text
http://localhost:8080/api/auth/
```

Đồng thời kiểm tra:

```text
F12 → Console
```

Nếu lỗi liên quan CORS, kiểm tra cấu hình Backend cho phép Frontend dev server:

```text
http://localhost:5173
```

## 14. Lỗi thường gặp

### Port 8080 đang được sử dụng

Kiểm tra process đang sử dụng port 8080 hoặc dừng application Backend cũ trước khi chạy lại.

### Frontend báo không kết nối được Backend

Đảm bảo Backend đang chạy trước và truy cập được:

```text
http://localhost:8080
```

### Lỗi database connection

Kiểm tra:

- SQL Server đang chạy
- Database `swp391_db` tồn tại
- Username/password đúng
- JDBC URL đúng
- SQL Server cho phép kết nối từ application

### `npm install` lỗi

Kiểm tra Node.js:

```bash
node -v
npm -v
```

Sau đó thử lại:

```bash
npm install
```

### Frontend không cập nhật code mới

Thử:

```bash
npm run dev
```

và refresh trình duyệt.

## 15. Git workflow cho team

Không code trực tiếp trên `main` nếu team đang sử dụng workflow branch.

Tạo branch cho feature:

```bash
git checkout develop
git pull origin develop
git checkout -b feature/ten-feature
```

Sau khi code:

```bash
git status
git add .
git commit -m "feat: ten feature"
git push -u origin feature/ten-feature
```

Sau đó tạo Pull Request vào `develop` theo workflow của team.

## 16. Không commit secret

Không commit các thông tin nhạy cảm như:

```text
Database password
JWT secret
API key
Access token
Private key
```

Sử dụng file môi trường hoặc cấu hình local phù hợp với project.

## 17. Tóm tắt chạy nhanh

Nếu đã setup database và cấu hình Backend rồi:

### Backend

```powershell
cd BE-SWP
mvnw.cmd spring-boot:run
```

### Frontend

Terminal mới:

```powershell
cd FE-SWP
npm install
npm run dev
```

Mở:

```text
http://localhost:5173
```

---

## 18. Lưu ý cho thành viên mới

Lần đầu clone project:

1. Clone cả `BE-SWP` và `FE-SWP`.
2. Cài JDK 25 và Node.js 22.x.
3. Setup SQL Server và database `swp391_db`.
4. Cấu hình thông tin database local trong Backend.
5. Chạy Backend.
6. Chạy Frontend.
7. Kiểm tra Login/Register trước khi bắt đầu phát triển feature mới.

Nếu gặp lỗi, gửi cho team:

- lỗi trong Terminal Backend
- lỗi trong Terminal Frontend
- lỗi Console trình duyệt
- request/response trong Network nếu lỗi API

Không gửi password, JWT token hoặc API key lên group chat/GitHub.
