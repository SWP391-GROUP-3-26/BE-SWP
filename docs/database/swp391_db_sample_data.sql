/* =====================================================================
   swp391_db  -  DỮ LIỆU MẪU
   Chạy SAU khi đã chạy swp391_db.sql (đã tạo xong bảng).

   TÀI KHOẢN ĐĂNG NHẬP (đăng nhập bằng Username/Email, mật khẩu chung: 12345@)
   ---------------------------------------------------------------
   Role           Username       Email                    Mật khẩu
   Admin          admin          admin@gym.com            12345@
   Receptionist   receptionist   receptionist@gym.com     12345@
   Coach          coach          coach@gym.com            12345@
   Member         member         member@gym.com           12345@

   LƯU Ý:
   - Cột Password lưu BCrypt hash của mật khẩu 12345@ để khớp BE login.
   - 4 vai trò (Admin, Receptionist, Coach, Member) là giả định, nhóm
     chỉnh lại nếu khác.
   - Payment.Amount được hiểu là số tiền THỰC TRẢ sau khi trừ
     Discount_Amount.
   - CẢNH BÁO: phần 0 xoá toàn bộ dữ liệu của các bảng để script chạy
     lại được. Đừng chạy trên DB đã có dữ liệu thật.
   ===================================================================== */

USE swp391_db;
GO

/* ---------------------------------------------------------------------
   0. Xoá dữ liệu cũ (theo thứ tự ngược phụ thuộc) và reset IDENTITY
   --------------------------------------------------------------------- */
DELETE FROM Schedule;            DBCC CHECKIDENT ('Schedule', RESEED, 0);
DELETE FROM Activity_Log;        DBCC CHECKIDENT ('Activity_Log', RESEED, 0);
DELETE FROM Training_Result;     DBCC CHECKIDENT ('Training_Result', RESEED, 0);
DELETE FROM Attendance;          DBCC CHECKIDENT ('Attendance', RESEED, 0);
DELETE FROM Training_Plan;       DBCC CHECKIDENT ('Training_Plan', RESEED, 0);
DELETE FROM Payment;             DBCC CHECKIDENT ('Payment', RESEED, 0);
DELETE FROM Voucher;             DBCC CHECKIDENT ('Voucher', RESEED, 0);
DELETE FROM Class_Booking;       DBCC CHECKIDENT ('Class_Booking', RESEED, 0);
DELETE FROM Class_Session;       DBCC CHECKIDENT ('Class_Session', RESEED, 0);
DELETE FROM Class;               DBCC CHECKIDENT ('Class', RESEED, 0);
DELETE FROM Room;                DBCC CHECKIDENT ('Room', RESEED, 0);
DELETE FROM Subject;             DBCC CHECKIDENT ('Subject', RESEED, 0);
DELETE FROM Member_Subscription; DBCC CHECKIDENT ('Member_Subscription', RESEED, 0);
DELETE FROM Membership_Package;  DBCC CHECKIDENT ('Membership_Package', RESEED, 0);
DELETE FROM [User];              DBCC CHECKIDENT ('[User]', RESEED, 0);
DELETE FROM Role;                DBCC CHECKIDENT ('Role', RESEED, 0);
GO

/* ---------------------------------------------------------------------
   1. Role
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Role ON;
INSERT INTO Role (Role_ID, Role_Name, Description) VALUES
(1, N'Admin',        N'Quản trị hệ thống'),
(2, N'Receptionist', N'Lễ tân: đăng ký gói, thu tiền, hỗ trợ hội viên'),
(3, N'Coach',        N'Huấn luyện viên: dạy lớp, lập kế hoạch và đánh giá kết quả tập'),
(4, N'Member',       N'Hội viên phòng tập');
SET IDENTITY_INSERT Role OFF;
GO

/* ---------------------------------------------------------------------
   2. [User]  (4 tài khoản, mỗi role 1 tài khoản, mật khẩu chung 12345@)
   --------------------------------------------------------------------- */
DECLARE @DefaultPassword VARCHAR(255) = '$2a$10$HCZe8IHqoiwYaP8e2gnra.B4.ENayRlCCLaQGPflzHnx6DREzgowy';

SET IDENTITY_INSERT [User] ON;
INSERT INTO [User] (User_ID, Role_ID, FullName, Username, Phone, Email, DOB, Gender, Address, Avatar_URL, Password, Status) VALUES
(1, 1, N'Nguyễn Văn Quản',  'admin',        '0900000001', 'admin@gym.com',        '1990-01-15', N'Male',   N'12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',      'https://example.com/avatars/admin.png',        @DefaultPassword, N'Active'),
(2, 2, N'Trần Thị Lan',     'receptionist', '0900000002', 'receptionist@gym.com', '1998-05-20', N'Female', N'45 Lê Lợi, Quận 1, TP. Hồ Chí Minh',          'https://example.com/avatars/receptionist.png', @DefaultPassword, N'Active'),
(3, 3, N'Lê Hoàng Nam',     'coach',        '0900000003', 'coach@gym.com',        '1993-09-10', N'Male',   N'78 Võ Văn Tần, Quận 3, TP. Hồ Chí Minh',      'https://example.com/avatars/coach.png',        @DefaultPassword, N'Active'),
(4, 4, N'Phạm Minh Anh',    'member',       '0900000004', 'member@gym.com',       '2001-03-25', N'Female', N'23 Điện Biên Phủ, Bình Thạnh, TP. Hồ Chí Minh', 'https://example.com/avatars/member.png',       @DefaultPassword, N'Active');
SET IDENTITY_INSERT [User] OFF;
GO

/* ---------------------------------------------------------------------
   3. Membership_Package   (Duration: số ngày)
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Membership_Package ON;
INSERT INTO Membership_Package (Package_ID, Name, Description, Price, Duration, Included_Classes, Benefits, Term_Conditions, Status) VALUES
(1, N'Basic 1 tháng',    N'Gói cơ bản dành cho người mới bắt đầu',            500000.00,   30,   8,
    N'Sử dụng phòng tập tự do; 8 buổi lớp nhóm',
    N'Không hoàn tiền; không bảo lưu', N'Active'),
(2, N'Standard 3 tháng', N'Gói tiêu chuẩn tiết kiệm hơn so với gói tháng',   1300000.00,  90,  30,
    N'Phòng tập tự do; 30 buổi lớp nhóm; 1 buổi tư vấn dinh dưỡng',
    N'Được bảo lưu tối đa 7 ngày', N'Active'),
(3, N'Premium 12 tháng', N'Gói cao cấp cho hội viên gắn bó lâu dài',          4500000.00, 365, 150,
    N'Phòng tập tự do; 150 buổi lớp nhóm; 4 buổi huấn luyện cá nhân',
    N'Được bảo lưu tối đa 30 ngày; được chuyển nhượng 1 lần', N'Active');
SET IDENTITY_INSERT Membership_Package OFF;
GO

/* ---------------------------------------------------------------------
   4. Member_Subscription  (hội viên User_ID = 4)
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Member_Subscription ON;
INSERT INTO Member_Subscription (Subscription_ID, User_ID, Package_ID, Start_Date, End_Date, Status) VALUES
(1, 4, 1, '2026-06-01', '2026-06-30', N'Expired'),
(2, 4, 2, '2026-09-01', '2026-11-30', N'Active');
SET IDENTITY_INSERT Member_Subscription OFF;
GO

/* ---------------------------------------------------------------------
   5. Subject
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Subject ON;
INSERT INTO Subject (Subject_ID, Name, Description) VALUES
(1, N'Yoga',   N'Yoga cơ bản giúp tăng độ dẻo dai và thư giãn'),
(2, N'Boxing', N'Boxing cơ bản: kỹ thuật đấm và di chuyển'),
(3, N'Zumba',  N'Nhảy aerobic kết hợp nhạc Latin, đốt mỡ hiệu quả'),
(4, N'HIIT',   N'Tập cường độ cao ngắt quãng, giảm mỡ và tăng sức bền');
SET IDENTITY_INSERT Subject OFF;
GO

/* ---------------------------------------------------------------------
   6. Room
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Room ON;
INSERT INTO Room (Room_ID, Name, Max_Capacity) VALUES
(1, N'Phòng Yoga',   20),
(2, N'Phòng Võ',     15),
(3, N'Phòng Đa năng', 30);
SET IDENTITY_INSERT Room OFF;
GO

/* ---------------------------------------------------------------------
   7. Class  (Coach_ID = 3; Max_Capacity không vượt sức chứa phòng)
      Date/Time = buổi học đầu tiên của lớp
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Class ON;
INSERT INTO Class (Class_ID, Subject_ID, Coach_ID, Room_ID, Name, Max_Capacity, Status, Date, Time) VALUES
(1, 1, 3, 1, N'Yoga buổi sáng',    15, N'Open', '2026-09-28', '07:00:00'),
(2, 2, 3, 2, N'Boxing cơ bản',     12, N'Open', '2026-09-29', '18:00:00'),
(3, 3, 3, 3, N'Zumba buổi tối',    25, N'Open', '2026-10-01', '19:00:00'),
(4, 4, 3, 3, N'HIIT giảm mỡ',      20, N'Open', '2026-10-08', '17:30:00');
SET IDENTITY_INSERT Class OFF;
GO

/* ---------------------------------------------------------------------
   8. Class_Session  (4 buổi đã học + 4 buổi sắp tới; hôm nay 02/10/2026)
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Class_Session ON;
INSERT INTO Class_Session (Session_ID, Class_ID, Date, Start_Time, End_Time, Status) VALUES
(1, 1, '2026-09-28', '07:00:00', '08:00:00', N'Completed'),
(2, 1, '2026-09-30', '07:00:00', '08:00:00', N'Completed'),
(3, 2, '2026-09-29', '18:00:00', '19:00:00', N'Completed'),
(4, 3, '2026-10-01', '19:00:00', '20:00:00', N'Completed'),
(5, 1, '2026-10-05', '07:00:00', '08:00:00', N'Scheduled'),
(6, 2, '2026-10-06', '18:00:00', '19:00:00', N'Scheduled'),
(7, 3, '2026-10-07', '19:00:00', '20:00:00', N'Scheduled'),
(8, 4, '2026-10-08', '17:30:00', '18:30:00', N'Scheduled');
SET IDENTITY_INSERT Class_Session OFF;
GO

/* ---------------------------------------------------------------------
   9. Class_Booking  (hội viên User_ID = 4)
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Class_Booking ON;
INSERT INTO Class_Booking (Booking_ID, User_ID, Session_ID, Booking_Datetime, Status) VALUES
(1, 4, 1, '2026-09-25 09:00:00', N'Completed'),
(2, 4, 2, '2026-09-26 10:30:00', N'Completed'),
(3, 4, 3, '2026-09-27 08:15:00', N'Completed'),
(4, 4, 4, '2026-09-28 20:00:00', N'Completed'),
(5, 4, 5, '2026-10-01 21:00:00', N'Booked'),
(6, 4, 6, '2026-10-02 08:00:00', N'Booked'),
(7, 4, 7, '2026-10-02 08:05:00', N'Cancelled');
SET IDENTITY_INSERT Class_Booking OFF;
GO

/* ---------------------------------------------------------------------
   10. Voucher
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Voucher ON;
INSERT INTO Voucher (Voucher_ID, Code, Discount_Type, Discount_Value, Start_Date, End_Date) VALUES
(1, 'WELCOME10',   N'Percent', 10.00,    '2026-01-01', '2026-12-31'),
(2, 'SUMMER50K',   N'Fixed',   50000.00, '2026-06-01', '2026-08-31'),
(3, 'NEWMEMBER20', N'Percent', 20.00,    '2026-10-01', '2026-10-31');
SET IDENTITY_INSERT Voucher OFF;
GO

/* ---------------------------------------------------------------------
   11. Payment
       Receptionist_ID = 2 khi thu tại quầy, NULL khi thanh toán online.
       Amount = số tiền thực trả (đã trừ Discount_Amount).
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Payment ON;
INSERT INTO Payment (Payment_ID, Subscription_ID, Receptionist_ID, Voucher_ID, Booking_ID, Payment_Type, Amount, Method, Payment_Date, Status, Discount_Amount) VALUES
-- Thanh toán gói hội viên (tại quầy)
(1, 1, 2, NULL, NULL, N'Subscription',  500000.00, N'Cash',          '2026-06-01 09:00:00', N'Paid',    0.00),
(2, 2, 2, 1,    NULL, N'Subscription', 1170000.00, N'Bank_Transfer', '2026-09-01 10:00:00', N'Paid',    130000.00),
-- Thanh toán lẻ cho buổi đặt lớp (online)
(3, NULL, NULL, NULL, 4, N'Class_Booking', 80000.00, N'E_Wallet',     '2026-09-28 20:05:00', N'Paid',    0.00),
(4, NULL, NULL, NULL, 6, N'Class_Booking', 80000.00, N'Card',         '2026-10-02 08:02:00', N'Pending', 0.00);
SET IDENTITY_INSERT Payment OFF;
GO

/* ---------------------------------------------------------------------
   12. Training_Plan  (Coach_ID = 3, Member_ID = 4)
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Training_Plan ON;
INSERT INTO Training_Plan (Plan_ID, Coach_ID, Member_ID, Goal, Description, Start_Date, End_Date) VALUES
(1, 3, 4, N'Giảm 5kg trong 3 tháng',
    N'Tập 3-4 buổi/tuần kết hợp yoga, zumba và HIIT; theo dõi cân nặng mỗi tuần.',
    '2026-09-01', '2026-11-30'),
(2, 3, 4, N'Cải thiện độ linh hoạt',
    N'Tập yoga 2 buổi/tuần, tập trung giãn cơ lưng và chân.',
    '2026-06-01', '2026-08-31');
SET IDENTITY_INSERT Training_Plan OFF;
GO

/* ---------------------------------------------------------------------
   13. Attendance  (các buổi đã học của hội viên User_ID = 4)
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Attendance ON;
INSERT INTO Attendance (Attendance_ID, Session_ID, User_ID, Status, Note) VALUES
(1, 1, 4, N'Present', NULL),
(2, 2, 4, N'Present', NULL),
(3, 3, 4, N'Late',    N'Đến muộn 10 phút'),
(4, 4, 4, N'Present', NULL);
SET IDENTITY_INSERT Attendance OFF;
GO

/* ---------------------------------------------------------------------
   14. Training_Result  (Coach_ID = 3 đánh giá hội viên User_ID = 4)
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Training_Result ON;
INSERT INTO Training_Result (Result_ID, Session_ID, User_ID, Coach_ID, Metric, Coach_Feedback, Date) VALUES
(1, 1, 4, 3, N'Cân nặng: 62.5 kg; Nhịp tim nghỉ: 72 bpm',
    N'Giữ nhịp thở tốt, cần tập thêm độ dẻo vùng lưng.', '2026-09-28'),
(2, 3, 4, 3, N'Đấm bao 3 hiệp, trung bình 45 cú/phút',
    N'Kỹ thuật đấm thẳng ổn, cần giữ guard cao hơn.', '2026-09-29'),
(3, 4, 4, 3, N'Năng lượng tiêu hao ước tính: 350 kcal',
    N'Tham gia nhiệt tình, bắt nhịp nhanh.', '2026-10-01');
SET IDENTITY_INSERT Training_Result OFF;
GO

/* ---------------------------------------------------------------------
   15. Activity_Log
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Activity_Log ON;
INSERT INTO Activity_Log (Log_ID, User_ID, Action_Type, Target_Entity, Description, Created_At) VALUES
(1, 1, N'CREATE_CLASS',     N'Class',            N'Admin tạo lớp "Yoga buổi sáng"',                         '2026-09-20 09:00:00.000'),
(2, 2, N'CREATE_PAYMENT',   N'Payment',          N'Lễ tân ghi nhận thanh toán gói Standard 3 tháng',         '2026-09-01 10:00:00.000'),
(3, 3, N'CREATE_PLAN',      N'Training_Plan',    N'Huấn luyện viên tạo kế hoạch "Giảm 5kg trong 3 tháng"',   '2026-09-01 14:30:00.000'),
(4, 4, N'LOGIN',            N'User',             N'Hội viên đăng nhập hệ thống',                             '2026-09-25 08:55:00.000'),
(5, 4, N'BOOK_CLASS',       N'Class_Booking',    N'Hội viên đặt lớp Yoga buổi sáng ngày 28/09/2026',          '2026-09-25 09:00:00.000'),
(6, 4, N'CANCEL_BOOKING',   N'Class_Booking',    N'Hội viên huỷ đặt lớp Zumba buổi tối ngày 07/10/2026',      '2026-10-02 08:10:00.000');
SET IDENTITY_INSERT Activity_Log OFF;
GO

/* ---------------------------------------------------------------------
   16. Schedule  (lịch dạy của huấn luyện viên User_ID = 3)
   --------------------------------------------------------------------- */
SET IDENTITY_INSERT Schedule ON;
INSERT INTO Schedule (Schedule_ID, Subject_ID, Coach_ID, Schedule_Date, Start_Time, End_Time, Status) VALUES
(1, 1, 3, '2026-09-28', '07:00:00', '08:00:00', N'Completed'),
(2, 1, 3, '2026-10-05', '07:00:00', '08:00:00', N'Scheduled'),
(3, 2, 3, '2026-10-06', '18:00:00', '19:00:00', N'Scheduled'),
(4, 3, 3, '2026-10-07', '19:00:00', '20:00:00', N'Scheduled'),
(5, 4, 3, '2026-10-08', '17:30:00', '18:30:00', N'Scheduled');
SET IDENTITY_INSERT Schedule OFF;
GO

/* ---------------------------------------------------------------------
   17. Kiểm tra nhanh
   --------------------------------------------------------------------- */
SELECT r.Role_Name, u.Email, u.FullName, u.Status
FROM [User] u
JOIN Role r ON r.Role_ID = u.Role_ID
ORDER BY r.Role_ID;
GO
