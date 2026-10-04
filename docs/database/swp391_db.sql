/* =====================================================================
   swp391_db  -  SQL Server DDL
   Nguồn: logical-erd-sqlserver.json (16 bảng, 104 cột, 23 quan hệ FK)
   Ghi chú:
   - Tên bảng/cột giữ nguyên theo ERD. [User] là từ khoá của SQL Server
     nên luôn viết trong ngoặc vuông (hoặc đổi tên thành Users).
   - Khoá chính: INT IDENTITY(1,1) (riêng Activity_Log.Log_ID là BIGINT).
   - Mọi FK dùng ON DELETE NO ACTION / ON UPDATE NO ACTION.
   - Cần SQL Server 2016 trở lên (DROP TABLE IF EXISTS).

   CÁC ASSUMPTION CHƯA ĐƯỢC NHÓM DUYỆT (không có trong ERD gốc):
   [A1] IDENTITY(1,1) cho khoá chính.
   [A2] NULL / NOT NULL cho từng cột.
   [A3] UNIQUE: Username, Email, Phone (cho phép trống), Role_Name, Package.Name,
        Subject.Name, Room.Name, Voucher.Code,
        (User_ID, Session_ID) ở Class_Booking và Attendance.
   [A4] CHECK danh sách giá trị: Status, Gender, Discount_Type,
        Payment_Type, Method.
   [A5] DEFAULT cho Status và các cột ngày/giờ.
   [A6] Payment: đúng 1 target, khớp Payment_Type (xem CK_Payment_Target).
   [A7] Precision: TIME(0), DATETIME2(0), Activity_Log.Created_At DATETIME2(3).
   [A8] Kiểm tra vai trò Coach/Member/Receptionist: chưa enforce trong DB.
   [A9] Nguồn lịch học chính: chưa chốt (Class / Class_Session / Schedule).
   ===================================================================== */

IF DB_ID(N'swp391_db') IS NULL
    CREATE DATABASE swp391_db;
GO

USE swp391_db;
GO

/* ---------------------------------------------------------------------
   0. Xoá bảng cũ (theo thứ tự ngược phụ thuộc) để chạy lại script được.
      CẢNH BÁO: lệnh này xoá toàn bộ dữ liệu của các bảng.
   --------------------------------------------------------------------- */
DROP TABLE IF EXISTS Schedule;
DROP TABLE IF EXISTS Activity_Log;
DROP TABLE IF EXISTS Training_Result;
DROP TABLE IF EXISTS Attendance;
DROP TABLE IF EXISTS Training_Plan;
DROP TABLE IF EXISTS Payment;
DROP TABLE IF EXISTS Voucher;
DROP TABLE IF EXISTS Class_Booking;
DROP TABLE IF EXISTS Class_Session;
DROP TABLE IF EXISTS Class;
DROP TABLE IF EXISTS Room;
DROP TABLE IF EXISTS Subject;
DROP TABLE IF EXISTS Member_Subscription;
DROP TABLE IF EXISTS Membership_Package;
DROP TABLE IF EXISTS [User];
DROP TABLE IF EXISTS Role;
GO

/* =====================================================================
   1. TABLES
   ===================================================================== */

/* ---------- Role ---------- */
CREATE TABLE Role (
    Role_ID     INT IDENTITY(1,1) NOT NULL,
    Role_Name   NVARCHAR(50)      NOT NULL,
    Description NVARCHAR(255)     NULL,
    CONSTRAINT PK_Role PRIMARY KEY (Role_ID),
    CONSTRAINT UQ_Role_Name UNIQUE (Role_Name)
);
GO

/* ---------- User ---------- */
CREATE TABLE [User] (
    User_ID    INT IDENTITY(1,1) NOT NULL,
    Role_ID    INT               NOT NULL,
    FullName   NVARCHAR(100)     NOT NULL,
    Username   VARCHAR(50)       NOT NULL,
    Phone      VARCHAR(15)       NULL,
    Email      VARCHAR(100)      NOT NULL,
    DOB        DATE              NULL,
    Gender     NVARCHAR(10)      NULL,
    Address    NVARCHAR(255)     NULL,
    Avatar_URL VARCHAR(255)      NULL,
    Password   VARCHAR(255)      NOT NULL,   -- lưu chuỗi đã băm, không lưu mật khẩu thô
    Status     NVARCHAR(20)      NOT NULL CONSTRAINT DF_User_Status DEFAULT N'Active',
    CONSTRAINT PK_User PRIMARY KEY (User_ID),
    CONSTRAINT UQ_User_Username UNIQUE (Username),
    CONSTRAINT UQ_User_Email UNIQUE (Email),
    CONSTRAINT FK_User_Role FOREIGN KEY (Role_ID)
        REFERENCES Role (Role_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_User_Email  CHECK (Email LIKE '%_@_%._%'),
    CONSTRAINT CK_User_Username CHECK (Username NOT LIKE '%[^a-zA-Z0-9._]%'),
    CONSTRAINT CK_User_Gender CHECK (Gender IS NULL OR Gender IN (N'Male', N'Female', N'Other')),
    CONSTRAINT CK_User_Status CHECK (Status IN (N'Active', N'Inactive', N'Locked'))
);
GO

-- Phone có thể để trống, nhưng nếu có thì không được trùng
CREATE UNIQUE INDEX UX_User_Phone ON [User] (Phone) WHERE Phone IS NOT NULL;
GO

/* ---------- Membership_Package ---------- */
CREATE TABLE Membership_Package (
    Package_ID       INT IDENTITY(1,1) NOT NULL,
    Name             NVARCHAR(100)     NOT NULL,
    Description      NVARCHAR(MAX)     NULL,
    Price            DECIMAL(12,2)     NOT NULL,
    Duration         INT               NOT NULL,   -- số ngày
    Included_Classes INT               NOT NULL CONSTRAINT DF_Package_Classes DEFAULT 0,   -- số buổi học
    Benefits         NVARCHAR(MAX)     NULL,
    Term_Conditions  NVARCHAR(MAX)     NULL,
    Status           NVARCHAR(20)      NOT NULL CONSTRAINT DF_Package_Status DEFAULT N'Active',
    CONSTRAINT PK_Membership_Package PRIMARY KEY (Package_ID),
    CONSTRAINT UQ_Package_Name UNIQUE (Name),
    CONSTRAINT CK_Package_Price    CHECK (Price >= 0),
    CONSTRAINT CK_Package_Duration CHECK (Duration > 0),
    CONSTRAINT CK_Package_Classes  CHECK (Included_Classes >= 0),
    CONSTRAINT CK_Package_Status   CHECK (Status IN (N'Active', N'Inactive'))
);
GO

/* ---------- Member_Subscription ---------- */
CREATE TABLE Member_Subscription (
    Subscription_ID INT IDENTITY(1,1) NOT NULL,
    User_ID         INT               NOT NULL,
    Package_ID      INT               NOT NULL,
    Start_Date      DATE              NOT NULL,
    End_Date        DATE              NOT NULL,
    Status          NVARCHAR(20)      NOT NULL CONSTRAINT DF_Subscription_Status DEFAULT N'Active',
    CONSTRAINT PK_Member_Subscription PRIMARY KEY (Subscription_ID),
    CONSTRAINT FK_Subscription_User FOREIGN KEY (User_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Subscription_Package FOREIGN KEY (Package_ID)
        REFERENCES Membership_Package (Package_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_Subscription_Dates  CHECK (End_Date >= Start_Date),
    CONSTRAINT CK_Subscription_Status CHECK (Status IN (N'Pending', N'Active', N'Expired', N'Cancelled'))
);
GO

/* ---------- Subject ---------- */
CREATE TABLE Subject (
    Subject_ID  INT IDENTITY(1,1) NOT NULL,
    Name        NVARCHAR(100)     NOT NULL,
    Description NVARCHAR(MAX)     NULL,
    Category    NVARCHAR(50)      NULL,
    CONSTRAINT PK_Subject PRIMARY KEY (Subject_ID),
    CONSTRAINT UQ_Subject_Name UNIQUE (Name)
);
GO

/* ---------- Room ---------- */
CREATE TABLE Room (
    Room_ID      INT IDENTITY(1,1) NOT NULL,
    Name         NVARCHAR(50)      NOT NULL,
    Max_Capacity INT               NOT NULL,
    CONSTRAINT PK_Room PRIMARY KEY (Room_ID),
    CONSTRAINT UQ_Room_Name UNIQUE (Name),
    CONSTRAINT CK_Room_Capacity CHECK (Max_Capacity > 0)
);
GO

/* ---------- Class ---------- */
-- Coach_ID trỏ về [User]; việc đúng vai trò Coach cần kiểm tra ở tầng ứng dụng hoặc trigger.
-- Date/Time cho phép NULL vì thời gian thực tế nằm ở Class_Session (xem ghi chú cuối file).
CREATE TABLE Class (
    Class_ID     INT IDENTITY(1,1) NOT NULL,
    Subject_ID   INT               NOT NULL,
    Coach_ID     INT               NOT NULL,
    Room_ID      INT               NOT NULL,
    Name         NVARCHAR(100)     NOT NULL,
    Max_Capacity INT               NOT NULL,
    Status       NVARCHAR(20)      NOT NULL CONSTRAINT DF_Class_Status DEFAULT N'Open',
    Date         DATE              NULL,
    Time         TIME(0)           NULL,
    CONSTRAINT PK_Class PRIMARY KEY (Class_ID),
    CONSTRAINT FK_Class_Subject FOREIGN KEY (Subject_ID)
        REFERENCES Subject (Subject_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Class_Coach FOREIGN KEY (Coach_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Class_Room FOREIGN KEY (Room_ID)
        REFERENCES Room (Room_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_Class_Capacity CHECK (Max_Capacity > 0),
    CONSTRAINT CK_Class_Status   CHECK (Status IN (N'Open', N'Closed', N'Cancelled'))
);
GO

/* ---------- Class_Session ---------- */
CREATE TABLE Class_Session (
    Session_ID INT IDENTITY(1,1) NOT NULL,
    Class_ID   INT               NOT NULL,
    Date       DATE              NOT NULL,
    Start_Time TIME(0)           NOT NULL,
    End_Time   TIME(0)           NOT NULL,
    Status     NVARCHAR(20)      NOT NULL CONSTRAINT DF_Session_Status DEFAULT N'Scheduled',
    CONSTRAINT PK_Class_Session PRIMARY KEY (Session_ID),
    CONSTRAINT FK_Session_Class FOREIGN KEY (Class_ID)
        REFERENCES Class (Class_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT UQ_Session_Class_Slot UNIQUE (Class_ID, Date, Start_Time),
    CONSTRAINT CK_Session_Time   CHECK (End_Time > Start_Time),
    CONSTRAINT CK_Session_Status CHECK (Status IN (N'Scheduled', N'Completed', N'Cancelled'))
);
GO

/* ---------- Class_Booking ---------- */
CREATE TABLE Class_Booking (
    Booking_ID       INT IDENTITY(1,1) NOT NULL,
    User_ID          INT               NOT NULL,
    Session_ID       INT               NOT NULL,
    Booking_Datetime DATETIME2(0)      NOT NULL CONSTRAINT DF_Booking_Datetime DEFAULT SYSDATETIME(),
    Status           NVARCHAR(20)      NOT NULL CONSTRAINT DF_Booking_Status DEFAULT N'Booked',
    CONSTRAINT PK_Class_Booking PRIMARY KEY (Booking_ID),
    CONSTRAINT FK_Booking_User FOREIGN KEY (User_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Booking_Session FOREIGN KEY (Session_ID)
        REFERENCES Class_Session (Session_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT UQ_Booking_User_Session UNIQUE (User_ID, Session_ID),   -- 1 người chỉ đặt 1 lần cho mỗi buổi
    CONSTRAINT CK_Booking_Status CHECK (Status IN (N'Booked', N'Cancelled', N'Completed'))
);
GO

/* ---------- Voucher ---------- */
CREATE TABLE Voucher (
    Voucher_ID     INT IDENTITY(1,1) NOT NULL,
    Code           VARCHAR(50)       NOT NULL,
    Discount_Type  NVARCHAR(20)      NOT NULL,
    Discount_Value DECIMAL(12,2)     NOT NULL,
    Start_Date     DATE              NOT NULL,
    End_Date       DATE              NOT NULL,
    CONSTRAINT PK_Voucher PRIMARY KEY (Voucher_ID),
    CONSTRAINT UQ_Voucher_Code UNIQUE (Code),
    CONSTRAINT CK_Voucher_Type  CHECK (Discount_Type IN (N'Percent', N'Fixed')),
    CONSTRAINT CK_Voucher_Value CHECK (Discount_Value > 0),
    CONSTRAINT CK_Voucher_Percent CHECK (Discount_Type <> N'Percent' OR Discount_Value <= 100),
    CONSTRAINT CK_Voucher_Dates CHECK (End_Date >= Start_Date)
);
GO

/* ---------- Payment ---------- */
-- Mỗi Payment thuộc đúng 1 loại target, khớp với Payment_Type:
--   Payment_Type = 'Subscription'  -> chỉ Subscription_ID có giá trị, Booking_ID phải NULL
--   Payment_Type = 'Class_Booking' -> chỉ Booking_ID có giá trị, Subscription_ID phải NULL
-- Voucher_ID: tuỳ chọn. Receptionist_ID: NULL nếu thanh toán online (không qua lễ tân).
CREATE TABLE Payment (
    Payment_ID      INT IDENTITY(1,1) NOT NULL,
    Subscription_ID INT               NULL,
    Receptionist_ID INT               NULL,
    Voucher_ID      INT               NULL,
    Booking_ID      INT               NULL,
    Payment_Type    NVARCHAR(30)      NOT NULL,
    Amount          DECIMAL(12,2)     NOT NULL,
    Method          NVARCHAR(30)      NOT NULL,
    Payment_Date    DATETIME2(0)      NOT NULL CONSTRAINT DF_Payment_Date DEFAULT SYSDATETIME(),
    Status          NVARCHAR(20)      NOT NULL CONSTRAINT DF_Payment_Status DEFAULT N'Pending',
    Discount_Amount DECIMAL(12,2)     NOT NULL CONSTRAINT DF_Payment_Discount DEFAULT 0,
    CONSTRAINT PK_Payment PRIMARY KEY (Payment_ID),
    CONSTRAINT FK_Payment_Subscription FOREIGN KEY (Subscription_ID)
        REFERENCES Member_Subscription (Subscription_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Payment_Receptionist FOREIGN KEY (Receptionist_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Payment_Voucher FOREIGN KEY (Voucher_ID)
        REFERENCES Voucher (Voucher_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Payment_Booking FOREIGN KEY (Booking_ID)
        REFERENCES Class_Booking (Booking_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_Payment_Type     CHECK (Payment_Type IN (N'Subscription', N'Class_Booking')),
    CONSTRAINT CK_Payment_Target   CHECK (
        (Payment_Type = N'Subscription'  AND Subscription_ID IS NOT NULL AND Booking_ID IS NULL)
     OR (Payment_Type = N'Class_Booking' AND Booking_ID IS NOT NULL AND Subscription_ID IS NULL)
    ),
    CONSTRAINT CK_Payment_Amount   CHECK (Amount >= 0),
    CONSTRAINT CK_Payment_Method   CHECK (Method IN (N'Cash', N'Card', N'Bank_Transfer', N'E_Wallet')),
    CONSTRAINT CK_Payment_Status   CHECK (Status IN (N'Pending', N'Paid', N'Failed', N'Refunded')),
    CONSTRAINT CK_Payment_Discount CHECK (Discount_Amount >= 0)
);
GO

/* ---------- Training_Plan ---------- */
CREATE TABLE Training_Plan (
    Plan_ID     INT IDENTITY(1,1) NOT NULL,
    Coach_ID    INT               NOT NULL,
    Member_ID   INT               NOT NULL,
    Goal        NVARCHAR(255)     NOT NULL,
    Description NVARCHAR(MAX)     NULL,
    Start_Date  DATE              NOT NULL,
    End_Date    DATE              NULL,
    CONSTRAINT PK_Training_Plan PRIMARY KEY (Plan_ID),
    CONSTRAINT FK_Plan_Coach FOREIGN KEY (Coach_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Plan_Member FOREIGN KEY (Member_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_Plan_Dates  CHECK (End_Date IS NULL OR End_Date >= Start_Date),
    CONSTRAINT CK_Plan_People CHECK (Coach_ID <> Member_ID)
);
GO

/* ---------- Attendance ---------- */
CREATE TABLE Attendance (
    Attendance_ID INT IDENTITY(1,1) NOT NULL,
    Session_ID    INT               NOT NULL,
    User_ID       INT               NOT NULL,
    Status        NVARCHAR(20)      NOT NULL,
    Note          NVARCHAR(255)     NULL,
    CONSTRAINT PK_Attendance PRIMARY KEY (Attendance_ID),
    CONSTRAINT FK_Attendance_Session FOREIGN KEY (Session_ID)
        REFERENCES Class_Session (Session_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Attendance_User FOREIGN KEY (User_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT UQ_Attendance_Session_User UNIQUE (Session_ID, User_ID),
    CONSTRAINT CK_Attendance_Status CHECK (Status IN (N'Present', N'Absent', N'Late', N'Excused'))
);
GO

/* ---------- Training_Result ---------- */
CREATE TABLE Training_Result (
    Result_ID      INT IDENTITY(1,1) NOT NULL,
    Session_ID     INT               NOT NULL,
    User_ID        INT               NOT NULL,
    Coach_ID       INT               NOT NULL,
    Metric         NVARCHAR(255)     NULL,
    Coach_Feedback NVARCHAR(MAX)     NULL,
    Date           DATE              NOT NULL CONSTRAINT DF_Result_Date DEFAULT CAST(GETDATE() AS DATE),
    CONSTRAINT PK_Training_Result PRIMARY KEY (Result_ID),
    CONSTRAINT FK_Result_Session FOREIGN KEY (Session_ID)
        REFERENCES Class_Session (Session_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Result_User FOREIGN KEY (User_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Result_Coach FOREIGN KEY (Coach_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_Result_People CHECK (User_ID <> Coach_ID)
);
GO

/* ---------- Activity_Log ---------- */
CREATE TABLE Activity_Log (
    Log_ID        BIGINT IDENTITY(1,1) NOT NULL,
    User_ID       INT                  NOT NULL,
    Action_Type   NVARCHAR(50)         NOT NULL,
    Target_Entity NVARCHAR(100)        NULL,
    Description   NVARCHAR(MAX)        NULL,
    Created_At    DATETIME2(3)         NOT NULL CONSTRAINT DF_Log_Created DEFAULT SYSDATETIME(),
    CONSTRAINT PK_Activity_Log PRIMARY KEY (Log_ID),
    CONSTRAINT FK_Log_User FOREIGN KEY (User_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION
);
GO

/* ---------- Schedule ---------- */
CREATE TABLE Schedule (
    Schedule_ID   INT IDENTITY(1,1) NOT NULL,
    Subject_ID    INT               NOT NULL,
    Coach_ID      INT               NOT NULL,
    Schedule_Date DATE              NOT NULL,
    Start_Time    TIME(0)           NOT NULL,
    End_Time      TIME(0)           NOT NULL,
    Status        NVARCHAR(20)      NOT NULL CONSTRAINT DF_Schedule_Status DEFAULT N'Scheduled',
    CONSTRAINT PK_Schedule PRIMARY KEY (Schedule_ID),
    CONSTRAINT FK_Schedule_Subject FOREIGN KEY (Subject_ID)
        REFERENCES Subject (Subject_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Schedule_Coach FOREIGN KEY (Coach_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_Schedule_Time   CHECK (End_Time > Start_Time),
    CONSTRAINT CK_Schedule_Status CHECK (Status IN (N'Scheduled', N'Completed', N'Cancelled'))
);
GO

/* =====================================================================
   2. INDEXES cho các cột FK (SQL Server không tự tạo index cho FK)
   ===================================================================== */
CREATE INDEX IX_User_Role              ON [User] (Role_ID);
CREATE INDEX IX_Subscription_User      ON Member_Subscription (User_ID);
CREATE INDEX IX_Subscription_Package   ON Member_Subscription (Package_ID);
CREATE INDEX IX_Class_Subject          ON Class (Subject_ID);
CREATE INDEX IX_Class_Coach            ON Class (Coach_ID);
CREATE INDEX IX_Class_Room             ON Class (Room_ID);
CREATE INDEX IX_Booking_Session        ON Class_Booking (Session_ID);
CREATE INDEX IX_Payment_Subscription   ON Payment (Subscription_ID);
CREATE INDEX IX_Payment_Receptionist   ON Payment (Receptionist_ID);
CREATE INDEX IX_Payment_Voucher        ON Payment (Voucher_ID);
CREATE INDEX IX_Payment_Booking        ON Payment (Booking_ID);
CREATE INDEX IX_Plan_Coach             ON Training_Plan (Coach_ID);
CREATE INDEX IX_Plan_Member            ON Training_Plan (Member_ID);
CREATE INDEX IX_Attendance_User        ON Attendance (User_ID);
CREATE INDEX IX_Result_Session         ON Training_Result (Session_ID);
CREATE INDEX IX_Result_User            ON Training_Result (User_ID);
CREATE INDEX IX_Result_Coach           ON Training_Result (Coach_ID);
CREATE INDEX IX_Log_User               ON Activity_Log (User_ID);
CREATE INDEX IX_Schedule_Subject       ON Schedule (Subject_ID);
CREATE INDEX IX_Schedule_Coach         ON Schedule (Coach_ID);
GO

/* =====================================================================
   3. GHI CHÚ NGHIỆP VỤ CHƯA ENFORCE TRONG DB
   - Coach_ID / Member_ID / Receptionist_ID cùng trỏ về [User]; DB chưa
     kiểm tra đúng Role. Cần trigger hoặc kiểm tra ở tầng ứng dụng.
   - Lịch học đang lưu ở 3 nơi: Class (Date, Time), Class_Session và
     Schedule. Nên chọn 1 nguồn chính (thường là Class_Session) rồi bỏ
     hoặc đổi vai trò các bảng/cột còn lại.
   - Class.Max_Capacity chưa được so với Room.Max_Capacity.
   ===================================================================== */
