# SWP391 - SereneDesk Fitness & Sports

## 1. Giới thiệu

Đây là project SWP391 - hệ thống quản lý trung tâm thể thao/fitness SereneDesk.

Project được tách thành 2 repository:

- `BE-SWP`: Backend Spring Boot + SQL Server
- `FE-SWP`: Frontend React + Vite

Hướng dẫn cấu hình và quét QR thử nghiệm cho Ví điện tử Member: [SePay QR Test Mode](docs/SEPAY_QR_TEST_SETUP.md).

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

## Member package payments

Apply `docs/database/migrations/20261026_member_package_online_payment.sql` before restarting the updated backend. This adds nullable renewal metadata to `Payment`; existing rows are preserved.

Member `POST /api/member/packages/register` and `POST /api/member/packages/renewal` accept `Card` (VNPay) or `E_Wallet` (SePay QR), not cash. They return `data.paymentId`, `data.paymentStatus = Pending` and `data.paymentUrl` for VNPay. Read `/api/member/payments/{paymentId}` for QR details and payment status. Prices, discounts and sport selections are checked on the server.

An immediate renewal replaces its original membership only after a valid paid callback. A paid `AFTER_CURRENT` renewal stays pending until its start date. Unpaid subscriptions are excluded from automatic activation. Late payments are marked for review without replacing the original membership.

Rollback-only SQL Server checks: set `MEMBER_PACKAGE_DB_CHECK=true` and run `MemberPackageOnlineSqlServerTest`. The tests sign local VNPay callback data and simulate authenticated SePay webhooks; they do not transfer funds.

## Training plans

Run `docs/database/migrations/20261030_training_plans.sql` against an existing database before starting the updated backend (`ddl-auto=validate`). The migration is repeatable and preserves existing plans and their `Description`. Legacy plans without a class association remain in the database and are excluded from the new class-scoped screens; they are not automatically assigned to a guessed class. Fresh installations use the updated `swp391_db.sql`.

For shared class training plans, also apply `docs/database/migrations/20261101_class_training_plans.sql` before restarting. Existing personal plans and results are unchanged. Set `scope` to `CLASS` and omit `memberId` to create a shared plan; the default scope is `INDIVIDUAL`. One assigned class plan and one assigned personal plan may coexist for a student in the same class. Recipients are captured from active members with confirmed, non-cancelled bookings when the plan is first published. Later content edits preserve that roster and all student results.

Coach can preview recipients with `GET /api/coach/training-plans/eligible-members?classId=...`, explicitly add newly enrolled members with `POST /api/coach/training-plans/{id}/recipients` (`version`), and save private feedback with `PATCH /api/coach/training-plans/{id}/recipients/{memberId}/feedback` (`coachFeedback`, `version`). Class details show each student's progress, read state and results. Member endpoints return only the caller's results, private feedback and read state. Unenrolled members retain readable history but cannot record new results; completed/cancelled plans are read-only. Publishing a class plan with no eligible members is rejected. Exercises apply throughout the plan start/end date range within the class term; no per-exercise date is required or used to restrict result entry. The legacy Training_Date column is preserved for existing data, with new exercises defaulting to the plan start date.

Coach: `/coach/training-plans` or the member roster in class details. Create a draft, set the plan date range and add exercises with targets/instructions, then assign it. Only the current class coach can manage a plan. Only active members with confirmed bookings in an active, unexpired class can receive a plan; each member has at most one assigned plan per class.

Member: `/member/training-plans`, with new/updated plan notices on `/member`. Opening a plan acknowledges the revision actually opened; future coach updates become unread again. Members record exercise completion/results during the plan period. Coaches retain member results when editing and cannot remove exercises that have results. Completed/cancelled plans, ended classes and cancelled enrollments retain readable history and stop new result entry.

API roots: `/api/coach/training-plans` (list, detail, create, update, review) and `/api/member/training-plans` (list, detail, read acknowledgement, exercise results). Drafts are private to the coach. Mutations include the last returned `version` to reject stale writes. Notifications are persisted plan revision/read revision pairs, with one current notice per plan; no email or push service is required.

### Session results and student evaluations

Apply `docs/database/migrations/20261101_session_evaluations.sql` before restarting the backend. It extends the existing `Training_Result` table without deleting old results and enforces one evaluation per session/student. The script stops if legacy duplicate session/student rows need review. New evaluations record training content, results, progress (`INITIAL`, `IMPROVING`, `STEADY`, `NEEDS_IMPROVEMENT`), coach feedback, optional next steps, update time and version. They are independent of training plans.

Coach opens class details, selects a session, saves attendance, then uses **Kết quả & đánh giá sau buổi học**. Only the assigned active coach may save after the session ends and only for enrolled students with saved `Present` attendance. The editor shows up to three prior evaluations of that student in the same class. Evaluations can be edited with the last returned version; creating one uses a null/omitted version. Withdrawals preserve the row but hide it from Member. Changing an evaluated student to `Absent` requires withdrawal first; an existing withdrawn evaluation can be corrected and republished while the session remains eligible.

Coach API: `GET /api/coach/classes/{classId}/sessions/{sessionId}/evaluations`, `PUT .../evaluations/{memberId}`, and `POST .../evaluations/{memberId}/withdraw` (`version`). Member API: `GET /api/member/session-evaluations` returns only that member's published session IDs, and `GET /api/member/session-evaluations/{sessionId}` returns their own evaluation. The Member timetable adds a small **Xem đánh giá** button to reviewed sessions and opens a read-only dialog. Failed evaluation loading does not hide the timetable. Set `SESSION_EVALUATION_DB_CHECK=true` to run `SessionEvaluationSqlServerTest`; all fixtures and changes roll back. Unit checks: `SessionEvaluationServiceTest`, `CoachAttendanceServiceTest`; frontend: `tests/sessionEvaluations.test.mjs` and `tests/memberAttendance.test.mjs`.

### Package sessions plus a class payment supplement

- Member class confirmation returns the permitted package/payment split. A package with some remaining sessions can use `MIXED`: the first chronological sessions use the package and the remaining sessions use direct payment.
- Submit `paymentMethod: "MIXED"`, `paymentChannel: "Card"` or `"E_Wallet"`, plus the displayed `packageSessions` and `paymentAmount`. The backend recalculates the split under the member/class locks and rejects changed quotes with HTTP 409.
- Only the missing sessions are charged. All sessions stay Pending until the authenticated VNPay/SePay callback confirms payment; unexpired Pending package rows reserve quota across classes and receptionist registrations. Failures, cancellations and expired holds release quota. Duplicate or late callbacks cannot spend it again.
- Pending class registrations expose the payment id, original deadline and held package sessions. Members can reopen the payment result page; `POST /api/member/payments/{id}/resume` checks ownership and current registration/payment state and returns a VNPay link for the same transaction. QR payments reopen the same QR. Resuming never extends the deadline or creates another booking/payment.
- Existing PACKAGE and DIRECT registrations remain available. Mixed payments require a valid package covering the sport and the entire class term; cash payments and packages with no remaining sessions cannot use MIXED.
- Cancellation releases package-funded rows; the cash refund is calculated only from direct-funded rows. No new database columns or migration are required.

## Class cancellations, refunds and member receipts

Apply `docs/database/migrations/20261030_class_refunds.sql`, then `docs/database/migrations/20261030_refund_counter_codes.sql` before starting the updated backend. Both are repeatable and preserve old payments/refunds. Existing receipt names are captured from the available class/package data at migration time. Exact paid times for old payments are left unknown; new cash and successful gateway payments record them.

- Member: `/member/classes` shows a cancellation preview; `/member/payment-history` lists class/package receipts with status, service and date filters. In refund details, choose cash collection at the counter to generate a private pickup code. Cash is the only supported new refund method. The code remains stable across reloads/repeated confirmations and is only returned in that owner's active refund detail, never staff responses, lists or notifications. Dashboard notices stay unread until that refund revision is opened.
- Center Manager: `/center-manager/refunds` reviews `PENDING` requests, approves an amount or rejects with a reason. Amount adjustments require a reason and cannot exceed the original paid amount. The same screen can open a request for old paid class transactions marked for review and containing cancelled bookings, including late payments after cancellation. Their reconstructed session counts must be checked before approval.
- Receptionist: `/receptionist/refunds` shows requests; only `APPROVED` cash requests with a generated pickup code can be confirmed as `PAID`. The receptionist must manually enter the code provided by the member. Incorrect codes are rejected without changing payment/refund status; redeemed requests cannot be paid again. A cash receipt reference is generated by the server after success, rather than entered by staff. The original payment stays `Paid` after a refund. The application does not transfer funds online.

Pickup codes use 16 cryptographically random characters (80 bits), formatted `HT-XXXX-XXXX-XXXX-XXXX`, with a filtered unique database index covering all issued codes. Generation retries known collisions; the unique index also rejects concurrent duplicates. The entered code is checked against the selected request; letter case, spaces and hyphens do not affect matching. Old unpaid bank/cash requests must be confirmed by the member as counter collection to obtain a code. Previously paid bank refunds remain readable history.

Policy: cancel only future, unstarted bookings in the same registration; retain past bookings. Cash refund estimate = net amount actually paid after discount × cancelled future sessions / all purchased sessions, rounded down to whole VND. Package-funded bookings return unused package sessions and do not create cash refunds. The confirmation API rechecks the preview under the existing class lock. One refund per payment plus row locks/version checks prevents duplicate requests or duplicate payout recording. Each request retains its original session/amount snapshot and audit trail.

API roots: `/api/member/payment-history`, `/api/member/refunds`, `/api/center-manager/refunds`, `/api/receptionist/refunds`. Cancellation preview: `GET /api/member/classes/bookings/{id}/cancel-preview`; confirmation accepts `{ estimatedRefund, cancelSessions }`. The equivalent receptionist routes live under `/api/receptionist/registrations/members/{memberId}/classes/bookings/{id}`. Counter enrollment: `PUT /api/member/refunds/{id}/recipient` accepts `{ method: "CASH", version }`. Counter payout: `POST /api/receptionist/refunds/{id}/payout` accepts `{ pickupCode, note, version }`. New refund writes require the last returned `version`.

Checks: run `RefundServiceTest` and the existing class/payment service tests. Set `REFUND_DB_CHECK=true` to enable `RefundSqlServerTest` (all fixtures and mutations roll back; no real transfers). Frontend `tests/refunds.browser.mjs` uses an installed Playwright package (or `PLAYWRIGHT_MODULE` override) and temporary browser profiles with mocked APIs.
