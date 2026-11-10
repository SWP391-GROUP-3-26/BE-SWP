/* =====================================================================
   swp391_db  -  SQL Server DDL
   Nguồn: logical-erd-sqlserver.json và các migrations đến 20261014.
   Chạy file này, sau đó swp391_db_sample_data.sql; không cần chạy migrations.
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
        (Registration_Code, Session_ID) ở Class_Booking khi có mã đăng ký,
        (User_ID, Session_ID) ở Attendance.
   [A4] CHECK danh sách giá trị: Status, Gender, Discount_Type,
        Payment_Type, Method.
   [A5] DEFAULT cho Status và các cột ngày/giờ.
   [A6] Payment: đúng 1 target, khớp Payment_Type (xem CK_Payment_Target).
   [A7] Precision: TIME(0), DATETIME2(0), Activity_Log.Created_At DATETIME2(3).
   [A8] Kiểm tra vai trò Coach/Member/Receptionist: chưa enforce trong DB.
   [A9] Nguồn lịch học chính: chưa chốt (Class / Class_Session / Schedule).
   ===================================================================== */

-- Các SET options bắt buộc cho filtered indexes, kể cả khi chạy bằng sqlcmd.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
GO

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
DROP TABLE IF EXISTS Training_Exercise_Result;
DROP TABLE IF EXISTS Training_Plan_Recipient;
DROP TABLE IF EXISTS Training_Exercise;
DROP TABLE IF EXISTS Training_Plan;
DROP TABLE IF EXISTS Refund_Session;
DROP TABLE IF EXISTS Refund_Request;
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
    Email_Verified BIT NOT NULL CONSTRAINT DF_User_Email_Verified DEFAULT 0,
    Auth_Provider NVARCHAR(10) NOT NULL CONSTRAINT DF_User_Auth_Provider DEFAULT N'UNKNOWN',
    Session_Version BIGINT NOT NULL CONSTRAINT DF_User_Session_Version DEFAULT 0,
    CONSTRAINT CK_User_Auth_Provider CHECK (Auth_Provider IN (N'LOCAL', N'GOOGLE', N'UNKNOWN')),
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

/* ---------- Email_Otp_Challenge ---------- */
CREATE TABLE Email_Otp_Challenge (
    Email NVARCHAR(100) NOT NULL CONSTRAINT PK_Email_Otp_Challenge PRIMARY KEY,
    Purpose NVARCHAR(20) NULL,
    Context_Hash NVARCHAR(64) NULL,
    Context_Expires_At DATETIMEOFFSET(7) NULL,
    User_ID INT NULL,
    Session_Version BIGINT NULL,
    Original_Email NVARCHAR(100) NULL,
    Otp_Hash NVARCHAR(255) NULL,
    Otp_Expires_At DATETIMEOFFSET(7) NULL,
    Failed_Attempts INT NOT NULL CONSTRAINT DF_Email_Otp_Attempts DEFAULT 0,
    Last_Sent_At DATETIMEOFFSET(7) NULL,
    Window_Started_At DATETIMEOFFSET(7) NULL,
    Send_Count INT NOT NULL CONSTRAINT DF_Email_Otp_Send_Count DEFAULT 0,
    Send_History NVARCHAR(1000) NULL,
    Pending_Username NVARCHAR(50) NULL,
    Pending_FullName NVARCHAR(100) NULL,
    Pending_Phone NVARCHAR(15) NULL,
    Pending_Password_Hash NVARCHAR(255) NULL,
    Reset_Hash NVARCHAR(64) NULL,
    Reset_Expires_At DATETIMEOFFSET(7) NULL,
    CONSTRAINT CK_Email_Otp_Purpose CHECK (Purpose IS NULL OR Purpose IN (N'REGISTER', N'VERIFY_EMAIL', N'RESET', N'CHANGE_EMAIL', N'VERIFY_CHANGE_EMAIL')),
    CONSTRAINT CK_Email_Otp_Attempts CHECK (Failed_Attempts BETWEEN 0 AND 5),
    CONSTRAINT CK_Email_Otp_Send_Count CHECK (Send_Count BETWEEN 0 AND 5)
);
GO

/* ---------- Membership_Package ---------- */
CREATE TABLE Membership_Package (
    Package_ID       INT IDENTITY(1,1) NOT NULL,
    Name             NVARCHAR(100)     NOT NULL,
    Description      NVARCHAR(MAX)     NULL,
    Price            DECIMAL(12,2)     NOT NULL,
    Duration         INT               NOT NULL,   -- số ngày
    Freeze_Months    INT               NOT NULL CONSTRAINT DF_Package_Freeze_Months DEFAULT 0,
    Benefits         NVARCHAR(MAX)     NULL,
    Term_Conditions  NVARCHAR(MAX)     NULL,
    Status           NVARCHAR(20)      NOT NULL CONSTRAINT DF_Package_Status DEFAULT N'Active',
    CONSTRAINT PK_Membership_Package PRIMARY KEY (Package_ID),
    CONSTRAINT UQ_Package_Name UNIQUE (Name),
    CONSTRAINT CK_Package_Price    CHECK (Price >= 0),
    CONSTRAINT CK_Package_Duration CHECK (Duration > 0),
    CONSTRAINT CK_Package_Freeze CHECK (Freeze_Months >= 0 AND (Freeze_Months = 0 OR Duration >= 180)),
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
    Freeze_Months_At_Purchase INT NOT NULL CONSTRAINT DF_Subscription_Freeze_Months DEFAULT 0,
    Freeze_Months_Used INT NOT NULL CONSTRAINT DF_Subscription_Freeze_Used DEFAULT 0,
    Freeze_Started_At DATETIME2(0) NULL,
    Freeze_Ends_At DATETIME2(0) NULL,
    CONSTRAINT PK_Member_Subscription PRIMARY KEY (Subscription_ID),
    CONSTRAINT FK_Subscription_User FOREIGN KEY (User_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Subscription_Package FOREIGN KEY (Package_ID)
        REFERENCES Membership_Package (Package_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_Subscription_Dates  CHECK (End_Date >= Start_Date),
    CONSTRAINT CK_Subscription_Status CHECK (Status IN (N'Pending', N'Active', N'Frozen', N'Expired', N'Cancelled')),
    CONSTRAINT CK_Subscription_Freeze_Budget CHECK (Freeze_Months_At_Purchase >= 0 AND Freeze_Months_Used >= 0
        AND Freeze_Months_Used <= Freeze_Months_At_Purchase),
    CONSTRAINT CK_Subscription_Freeze_State CHECK (
        (Status = N'Frozen' AND Freeze_Started_At IS NOT NULL AND Freeze_Ends_At IS NOT NULL AND Freeze_Ends_At > Freeze_Started_At)
        OR (Status <> N'Frozen' AND Freeze_Started_At IS NULL AND Freeze_Ends_At IS NULL))
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
    Room_Type    NVARCHAR(30)      NULL,
    Location     NVARCHAR(150)     NULL,
    Status       NVARCHAR(20)      NOT NULL CONSTRAINT DF_Room_Status DEFAULT N'Active',
    CONSTRAINT PK_Room PRIMARY KEY (Room_ID),
    CONSTRAINT UQ_Room_Name UNIQUE (Name),
    CONSTRAINT CK_Room_Capacity CHECK (Max_Capacity > 0),
    CONSTRAINT CK_Room_Status CHECK (Status IN (N'Active', N'Maintenance', N'Inactive')),
    CONSTRAINT CK_Room_Type CHECK (Room_Type IS NULL OR Room_Type IN (N'GYM', N'PILATES', N'FUNCTIONAL', N'BOXING', N'MARTIAL_ARTS', N'MULTIPURPOSE', N'SWIMMING_POOL', N'FOOTBALL_COURT', N'BASKETBALL_COURT', N'BADMINTON_COURT', N'TABLE_TENNIS_COURT', N'TENNIS_COURT'))
);
GO

CREATE TABLE Subject_Room_Type (
    Subject_ID INT NOT NULL,
    Room_Type NVARCHAR(30) NOT NULL,
    CONSTRAINT PK_Subject_Room_Type PRIMARY KEY (Subject_ID, Room_Type),
    CONSTRAINT FK_Subject_Room_Type_Subject FOREIGN KEY (Subject_ID) REFERENCES Subject(Subject_ID) ON DELETE CASCADE,
    CONSTRAINT CK_Subject_Room_Type CHECK (Room_Type IN (N'GYM', N'PILATES', N'FUNCTIONAL', N'BOXING', N'MARTIAL_ARTS', N'MULTIPURPOSE', N'SWIMMING_POOL', N'FOOTBALL_COURT', N'BASKETBALL_COURT', N'BADMINTON_COURT', N'TABLE_TENNIS_COURT', N'TENNIS_COURT'))
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
    Status       NVARCHAR(20)      NOT NULL CONSTRAINT DF_Class_Status DEFAULT N'Active',
    Date         DATE              NULL,
    Duration_Weeks INT             NULL,
    End_Date     DATE              NULL,
    Time         TIME(0)           NULL,
    Price        DECIMAL(12,2)     NULL CONSTRAINT DF_Class_Price DEFAULT 0,
    Days_Of_Week NVARCHAR(100)     NULL,
    Start_Time   TIME(0)           NULL,
    End_Time     TIME(0)           NULL,
    Has_PT       BIT               NOT NULL CONSTRAINT DF_Class_Has_PT DEFAULT (0),
    CONSTRAINT PK_Class PRIMARY KEY (Class_ID),
    CONSTRAINT FK_Class_Subject FOREIGN KEY (Subject_ID)
        REFERENCES Subject (Subject_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Class_Coach FOREIGN KEY (Coach_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Class_Room FOREIGN KEY (Room_ID)
        REFERENCES Room (Room_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_Class_Capacity CHECK (Max_Capacity > 0),
    CONSTRAINT CK_Class_Status   CHECK (Status IN (N'Open', N'Closed', N'Cancelled', N'Active', N'Completed', N'Cancel')),
    CONSTRAINT CK_Class_Term CHECK (
        (Duration_Weeks IS NULL AND End_Date IS NULL)
        OR (Duration_Weeks IS NOT NULL AND Duration_Weeks > 0 AND [Date] IS NOT NULL AND End_Date IS NOT NULL
            AND End_Date = DATEADD(DAY,
                6 - ((DATEDIFF(DAY, CONVERT(DATE, '19000101', 112), [Date]) % 7 + 7) % 7)
                    + 7 * (Duration_Weeks - 1), [Date]))
    )
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
    Payment_Expires_At DATETIME2(0)     NULL,
    Payment_Method   NVARCHAR(20)      NULL,
    Registration_Code VARCHAR(36)      NULL,
    CONSTRAINT PK_Class_Booking PRIMARY KEY (Booking_ID),
    CONSTRAINT FK_Booking_User FOREIGN KEY (User_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Booking_Session FOREIGN KEY (Session_ID)
        REFERENCES Class_Session (Session_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_Booking_Status CHECK (Status IN (N'Pending', N'Booked', N'Cancelled', N'Completed')),
    CONSTRAINT CK_Booking_Payment_Method
        CHECK (Payment_Method IS NULL OR Payment_Method IN (N'PACKAGE', N'DIRECT'))
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
    Expires_At      DATETIME2(0)      NULL,
    Vnp_Transaction_No NVARCHAR(15)   NULL,
    Vnp_Response_Code NVARCHAR(2)     NULL,
    Vnp_Transaction_Status NVARCHAR(2) NULL,
    Vnp_Pay_Date    NVARCHAR(14)      NULL,
    Sepay_Transaction_ID BIGINT       NULL,
    Requires_Review BIT              NOT NULL CONSTRAINT DF_Payment_Requires_Review DEFAULT 0,
    Item_Name_At_Payment NVARCHAR(200) NULL,
    Created_At DATETIME2(7) NULL,
    Paid_At DATETIME2(7) NULL,
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

CREATE UNIQUE INDEX UX_Payment_Sepay_Transaction ON dbo.Payment (Sepay_Transaction_ID)
    WHERE Sepay_Transaction_ID IS NOT NULL;
GO

/* ---------- Training_Plan ---------- */
CREATE TABLE Training_Plan (
    Plan_ID     INT IDENTITY(1,1) NOT NULL,
    Coach_ID    INT               NOT NULL,
    Member_ID   INT               NULL,
    Plan_Scope  VARCHAR(20)       NOT NULL CONSTRAINT DF_Training_Plan_Scope DEFAULT 'INDIVIDUAL',
    Class_ID    INT               NULL,
    Title       NVARCHAR(150)     NOT NULL CONSTRAINT DF_Training_Plan_Title DEFAULT N'Kế hoạch tập luyện',
    Goal        NVARCHAR(2000)    NOT NULL,
    Description NVARCHAR(MAX)     NULL,
    Assessment  NVARCHAR(2000)    NULL,
    Start_Date  DATE              NOT NULL,
    End_Date    DATE              NULL,
    Status      VARCHAR(20)       NOT NULL CONSTRAINT DF_Training_Plan_Status DEFAULT 'DRAFT',
    Coach_Feedback NVARCHAR(2000) NULL,
    Published_Revision INT NOT NULL CONSTRAINT DF_Training_Plan_Published DEFAULT 0,
    Read_Revision INT NOT NULL CONSTRAINT DF_Training_Plan_Read DEFAULT 0,
    Published_At DATETIME2(7) NULL,
    Created_At DATETIME2(7) NOT NULL CONSTRAINT DF_Training_Plan_Created DEFAULT SYSDATETIME(),
    Updated_At DATETIME2(7) NOT NULL CONSTRAINT DF_Training_Plan_Updated DEFAULT SYSDATETIME(),
    Version BIGINT NOT NULL CONSTRAINT DF_Training_Plan_Version DEFAULT 0,
    CONSTRAINT PK_Training_Plan PRIMARY KEY (Plan_ID),
    CONSTRAINT CK_Training_Plan_Scope CHECK ((Plan_Scope = 'CLASS' AND Member_ID IS NULL AND Class_ID IS NOT NULL)
        OR (Plan_Scope = 'INDIVIDUAL' AND Member_ID IS NOT NULL)),
    CONSTRAINT FK_Training_Plan_Class FOREIGN KEY (Class_ID) REFERENCES Class(Class_ID),
    CONSTRAINT CK_Training_Plan_Status CHECK (Status IN ('DRAFT','ASSIGNED','COMPLETED','CANCELLED')),
    CONSTRAINT CK_Training_Plan_Revisions CHECK (Read_Revision >= 0 AND Published_Revision >= Read_Revision),
    CONSTRAINT FK_Plan_Coach FOREIGN KEY (Coach_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT FK_Plan_Member FOREIGN KEY (Member_ID)
        REFERENCES [User] (User_ID) ON DELETE NO ACTION ON UPDATE NO ACTION,
    CONSTRAINT CK_Plan_Dates  CHECK (End_Date IS NULL OR End_Date >= Start_Date),
    CONSTRAINT CK_Plan_People CHECK (Coach_ID <> Member_ID)
);
GO

CREATE UNIQUE INDEX UX_Training_Plan_Assigned ON Training_Plan(Class_ID, Member_ID) WHERE Status = 'ASSIGNED';
CREATE INDEX IX_Training_Plan_Member ON Training_Plan(Member_ID, Published_At DESC);
CREATE TABLE Training_Exercise (
    Exercise_ID INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Training_Exercise PRIMARY KEY,
    Plan_ID INT NOT NULL REFERENCES Training_Plan(Plan_ID),
    Position INT NOT NULL,
    Training_Date DATE NOT NULL,
    Name NVARCHAR(150) NOT NULL,
    Instructions NVARCHAR(2000) NULL,
    Target NVARCHAR(255) NOT NULL,
    Completed BIT NOT NULL CONSTRAINT DF_Training_Exercise_Completed DEFAULT 0,
    Member_Result NVARCHAR(2000) NULL
);
CREATE INDEX IX_Training_Exercise_Plan ON Training_Exercise(Plan_ID, Position);
GO

CREATE TABLE Training_Plan_Recipient (
    Recipient_ID INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Training_Plan_Recipient PRIMARY KEY,
    Plan_ID INT NOT NULL REFERENCES Training_Plan(Plan_ID),
    Member_ID INT NOT NULL REFERENCES [User](User_ID),
    Read_Revision INT NOT NULL CONSTRAINT DF_Training_Plan_Recipient_Read DEFAULT 0,
    Coach_Feedback NVARCHAR(2000) NULL,
    CONSTRAINT UQ_Training_Plan_Recipient UNIQUE (Plan_ID, Member_ID),
    CONSTRAINT CK_Training_Plan_Recipient_Read CHECK (Read_Revision >= 0)
);
CREATE INDEX IX_Training_Plan_Recipient_Member ON Training_Plan_Recipient(Member_ID, Plan_ID);
CREATE TABLE Training_Exercise_Result (
    Result_ID INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Training_Exercise_Result PRIMARY KEY,
    Recipient_ID INT NOT NULL REFERENCES Training_Plan_Recipient(Recipient_ID),
    Exercise_ID INT NOT NULL REFERENCES Training_Exercise(Exercise_ID),
    Completed BIT NOT NULL CONSTRAINT DF_Training_Exercise_Result_Completed DEFAULT 0,
    Member_Result NVARCHAR(2000) NULL,
    CONSTRAINT UQ_Training_Exercise_Result UNIQUE (Recipient_ID, Exercise_ID)
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
    Metric         NVARCHAR(2000)    NULL,
    Training_Content NVARCHAR(2000) NULL,
    Progress_Status VARCHAR(30) NULL,
    Next_Steps NVARCHAR(2000) NULL,
    Updated_At DATETIME2(7) NULL,
    Is_Withdrawn BIT NOT NULL CONSTRAINT DF_Training_Result_Withdrawn DEFAULT 0,
    Version BIGINT NOT NULL CONSTRAINT DF_Training_Result_Version DEFAULT 0,
    Coach_Feedback NVARCHAR(MAX)     NULL,
    Date           DATE              NOT NULL CONSTRAINT DF_Result_Date DEFAULT CAST(GETDATE() AS DATE),
    CONSTRAINT PK_Training_Result PRIMARY KEY (Result_ID),
    CONSTRAINT UQ_Training_Result_Session_Member UNIQUE (Session_ID, User_ID),
    CONSTRAINT CK_Training_Result_Progress CHECK (Progress_Status IS NULL OR Progress_Status IN ('INITIAL','IMPROVING','STEADY','NEEDS_IMPROVEMENT')),
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
CREATE INDEX IX_Class_End_Date_Status  ON Class (End_Date, Status);
CREATE INDEX IX_Booking_Session        ON Class_Booking (Session_ID);
CREATE INDEX IX_Booking_Pending_Expiry ON Class_Booking (Status, Payment_Expires_At) INCLUDE (Session_ID);
-- Giữ lịch sử huỷ và cho phép đăng ký lại cùng buổi bằng mã đăng ký mới.
CREATE UNIQUE INDEX UQ_Booking_Registration_Session
    ON Class_Booking (Registration_Code, Session_ID) WHERE Registration_Code IS NOT NULL;
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

/* ---------- Class refund workflow ---------- */
IF OBJECT_ID('dbo.Refund_Request', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Refund_Request (
        Refund_ID INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Refund_Request PRIMARY KEY,
        Payment_ID INT NOT NULL CONSTRAINT UQ_Refund_Payment UNIQUE REFERENCES dbo.Payment(Payment_ID),
        Requested_By INT NOT NULL REFERENCES dbo.[User](User_ID),
        Item_Name NVARCHAR(150) NOT NULL,
        Paid_Amount DECIMAL(12,2) NOT NULL,
        Estimated_Amount DECIMAL(12,2) NOT NULL,
        Approved_Amount DECIMAL(12,2) NULL,
        Total_Sessions INT NOT NULL,
        Cancelled_Sessions INT NOT NULL,
        Status VARCHAR(20) NOT NULL,
        Reason NVARCHAR(2000) NOT NULL,
        Refund_Policy VARCHAR(40) NULL,
        Review_Note NVARCHAR(2000) NULL,
        Reviewed_By INT NULL REFERENCES dbo.[User](User_ID),
        Reviewed_At DATETIME2(7) NULL,
        Payout_Method VARCHAR(20) NULL,
        Pickup_Code VARCHAR(22) NULL,
        Bank_Name NVARCHAR(100) NULL,
        Account_Number VARCHAR(40) NULL,
        Account_Holder NVARCHAR(100) NULL,
        Payout_Reference NVARCHAR(150) NULL,
        Payout_Note NVARCHAR(1000) NULL,
        Paid_By INT NULL REFERENCES dbo.[User](User_ID),
        Paid_At DATETIME2(7) NULL,
        Created_At DATETIME2(7) NOT NULL,
        Updated_At DATETIME2(7) NOT NULL,
        Notification_Revision INT NOT NULL,
        Read_Revision INT NOT NULL,
        Version BIGINT NOT NULL,
        CONSTRAINT CK_Refund_Status CHECK (Status IN ('PENDING','APPROVED','REJECTED','PAID')),
        CONSTRAINT CK_Refund_Amount CHECK (Paid_Amount > 0 AND Estimated_Amount > 0 AND Estimated_Amount <= Paid_Amount
            AND (Approved_Amount IS NULL OR (Approved_Amount > 0 AND Approved_Amount <= Paid_Amount))),
        CONSTRAINT CK_Refund_Sessions CHECK (Total_Sessions > 0 AND Cancelled_Sessions > 0 AND Cancelled_Sessions <= Total_Sessions),
        CONSTRAINT CK_Refund_Method CHECK (Payout_Method IS NULL OR Payout_Method IN ('CASH','BANK')),
        CONSTRAINT CK_Refund_Review CHECK (Status = 'PENDING' OR (Reviewed_By IS NOT NULL AND Reviewed_At IS NOT NULL
            AND (Status = 'REJECTED' OR Approved_Amount IS NOT NULL))),
        CONSTRAINT CK_Refund_Paid CHECK (Status <> 'PAID' OR (Paid_By IS NOT NULL AND Paid_At IS NOT NULL
            AND Payout_Reference IS NOT NULL AND Payout_Method IS NOT NULL)),
        CONSTRAINT CK_Refund_Read CHECK (Read_Revision >= 0 AND Notification_Revision >= Read_Revision)
    );
    CREATE INDEX IX_Refund_Status ON dbo.Refund_Request(Status, Updated_At DESC);
    CREATE UNIQUE INDEX UX_Refund_Pickup_Code ON dbo.Refund_Request(Pickup_Code) WHERE Pickup_Code IS NOT NULL;
END;
IF OBJECT_ID('dbo.Refund_Session', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Refund_Session (
        Refund_ID INT NOT NULL REFERENCES dbo.Refund_Request(Refund_ID),
        Position INT NOT NULL,
        Session_ID INT NOT NULL,
        Session_Date DATE NOT NULL,
        Start_Time TIME(7) NOT NULL,
        Refundable BIT NOT NULL,
        CONSTRAINT PK_Refund_Session PRIMARY KEY (Refund_ID, Position)
    );
END;

GO
