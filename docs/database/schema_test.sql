/*
FINAL SCHEMA NOT APPROVED
TEST ONLY: recreate swp391_dev to test structure. NOT a production schema.
Sources: docs/erd/logical-erd.drawio, docs/erd/logical-erd.json,
         docs/erd/erd-review.md (existing cardinality analysis).
Run the entire file through SSMS or sqlcmd (GO is a client batch separator).
Requires permission to DROP/CREATE databases. Existing test data is destroyed.

TEMPORARY ASSUMPTIONS
A1. Training_Result.User_ID -> User.User_ID instead of the drawn User.Role_ID.
    Role_ID is not a candidate key; no UNIQUE is added to make it referenceable.
A2. Training_Plan.Member_ID -> User.User_ID; no Member entity exists.
A3. Activity_Log.User_ID -> User.User_ID; its edge has no source attachment.
A4. Schedule.Sport_ID -> Subject.Subject_ID; Subject -> Schedule exists only
    at entity level. Keep Sport_ID unchanged and do not add a Sport table.
A5. Schedule.Coach_ID -> User.User_ID; User -> Schedule is entity-level.
A6. Membership_Package.Name stays a regular column without an FK: FK label
    has no identifiable target entity/column.
A7. Every non-PK column is explicitly NULL. No complete evidence establishes
    mandatory child-to-parent participation. The 21 endArrow=ERoneToMany
    edges and one endArrow=ERmany edge have no startArrow; do not infer
    non-PK NOT NULL, UNIQUE, or minimum child counts from them.
A8. All datatypes, lengths, precision, scale and time granularity below are
    TEMPORARY ASSUMPTIONS. IDs are INT, supplied by the caller (no IDENTITY).
    Duration is INT with unresolved units; Included_classes is an INT count.
    Discount_Value is DECIMAL(18,2), units depend on unresolved Discount_Type.
    Metric is free text, pending definition of measurements/units.
    Status and categorical fields are text, with no inferred enum or CHECK.
    Date/time fields use DATE, TIME(0), DATETIME2(0), with no timezone policy.
    Text uses Unicode NVARCHAR; Password stores a caller-provided hash string.
    The following inventory specifies the temporary datatype for EVERY column:
    Role: Role_id INT, Role_Name NVARCHAR(255), Description NVARCHAR(MAX)
    User: User_ID INT, Role_ID INT, FullName NVARCHAR(255), Username NVARCHAR(50), Phone NVARCHAR(32), Email NVARCHAR(255), DOB DATE, Gender NVARCHAR(50), Address NVARCHAR(500), Avatar_URL NVARCHAR(2048), Password NVARCHAR(255), Status NVARCHAR(50)
    Membership_Package: Package_ID INT, Name NVARCHAR(255), Description NVARCHAR(MAX), Price DECIMAL(18,2), Duration INT, Included_classes INT, Benefits NVARCHAR(MAX), Term_conditions NVARCHAR(MAX), Status NVARCHAR(50)
    Member_Subscription: Subscription_ID INT, User_ID INT, Package_ID INT, Start_Date DATE, End_Date DATE, Status NVARCHAR(50)
    Subject: Subject_ID INT, Name NVARCHAR(255), Description NVARCHAR(MAX)
    Room: Room_ID INT, Name NVARCHAR(255), Max_Capacity INT
    Class: Class_ID INT, Subject_ID INT, Coach_ID INT, Room_ID INT, Name NVARCHAR(255), Max_Capacity INT, Status NVARCHAR(50), Date DATE, Time TIME(0)
    Class_Session: Session_ID INT, Class_ID INT, Date DATE, Start_Time TIME(0), End_Time TIME(0), Status NVARCHAR(50)
    Class_Booking: Booking_ID INT, User_ID INT, Session_ID INT, Booking_Dateime DATETIME2(0), Status NVARCHAR(50)
    Payment: Payment_ID INT, Subscription_ID INT, Receptionist_ID INT, Voucher_ID INT, Booking_ID INT, Payment_Type NVARCHAR(50), Amount DECIMAL(18,2), Method NVARCHAR(50), Payment_Date DATETIME2(0), Status NVARCHAR(50), Discount_Amount DECIMAL(18,2)
    Training_Plan: Plan_ID INT, Coach_ID INT, Member_ID INT, Goal NVARCHAR(MAX), Description NVARCHAR(MAX), Start_Date DATE, End_Date DATE
    Attendance: Attendance_ID INT, Session_ID INT, User_ID INT, Status NVARCHAR(50), Note NVARCHAR(MAX)
    Training_Result: Result_ID INT, Session_ID INT, User_ID INT, Coach_ID INT, Metric NVARCHAR(MAX), Coach_Feedback NVARCHAR(MAX), Date DATE
    Voucher: Voucher_ID INT, Code NVARCHAR(255), Discount_Type NVARCHAR(50), Discount_Value DECIMAL(18,2), Start_Date DATE, End_Date DATE
    Activity_Log: Log_ID INT, User_ID INT, Action_Type NVARCHAR(50), Target_Entity NVARCHAR(255), Description NVARCHAR(MAX), Created_At DATETIME2(0)
    Schedule: Schedule_ID INT, Sport_ID INT, Coach_ID INT, Schedule_date DATE, Start_time TIME(0), End_time TIME(0), Status NVARCHAR(50)

ERD-CONFIRMED FK MAPPINGS (current ERD evidence, not production approval)
    User.Role_ID -> Role.Role_id
    Member_Subscription.User_ID -> User.User_ID
    Member_Subscription.Package_ID -> Membership_Package.Package_ID
    Class.Subject_ID -> Subject.Subject_ID
    Class.Coach_ID -> User.User_ID
    Class.Room_ID -> Room.Room_ID
    Class_Session.Class_ID -> Class.Class_ID
    Class_Booking.User_ID -> User.User_ID
    Class_Booking.Session_ID -> Class_Session.Session_ID
    Payment.Subscription_ID -> Member_Subscription.Subscription_ID
    Payment.Receptionist_ID -> User.User_ID
    Payment.Voucher_ID -> Voucher.Voucher_ID
    Payment.Booking_ID -> Class_Booking.Booking_ID
    Training_Plan.Coach_ID -> User.User_ID
    Attendance.Session_ID -> Class_Session.Session_ID
    Attendance.User_ID -> User.User_ID
    Training_Result.Session_ID -> Class_Session.Session_ID
    Training_Result.Coach_ID -> User.User_ID


LIKELY ERD ISSUES
- Correct/confirm Training_Result.User_ID endpoint (currently User.Role_ID).
- Resolve missing Member_ID target, Activity_Log source, and Name FK label.
- Confirm Schedule column endpoints and Sport_ID/Subject naming semantics.
- Specify both ends of cardinality, optionality and any approved unique keys.
- Prior review reports 107 columns; both current source files contain 104.
  This script preserves all 104 source columns without adding any.
- Approve every datatype, length, precision/scale, units and nullability.
- Confirm naming including Role_id and Booking_Dateime; names are preserved.
- Define role eligibility for member/coach/receptionist. User FKs alone only
  enforce user existence; they do not enforce roles. No CHECK is introduced.

FINAL SCHEMA NOT APPROVED
16 tables, 104 columns, 16 PKs, 23 FKs (18 ERD-confirmed + 5 temporary).
No additional tables, columns, UNIQUE, CHECK, defaults or cascading actions.
Static validation only; this script has not been run on an actual database.
*/
USE [master];
GO
IF DB_ID(N'swp391_dev') IS NOT NULL
BEGIN
    ALTER DATABASE [swp391_dev] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE [swp391_dev];
END;
GO
CREATE DATABASE [swp391_dev];
GO
USE [swp391_dev];
GO
CREATE TABLE [dbo].[Role] (
    [Role_id] INT NOT NULL,
    [Role_Name] NVARCHAR(255) NULL,
    [Description] NVARCHAR(MAX) NULL,
    CONSTRAINT [PK_Role] PRIMARY KEY ([Role_id])
);
GO

CREATE TABLE [dbo].[User] (
    [User_ID] INT NOT NULL,
    [Role_ID] INT NULL,
    [FullName] NVARCHAR(255) NULL,
    [Username] NVARCHAR(50) NULL,
    [Phone] NVARCHAR(32) NULL,
    [Email] NVARCHAR(255) NULL,
    [DOB] DATE NULL,
    [Gender] NVARCHAR(50) NULL,
    [Address] NVARCHAR(500) NULL,
    [Avatar_URL] NVARCHAR(2048) NULL,
    [Password] NVARCHAR(255) NULL,
    [Status] NVARCHAR(50) NULL,
    CONSTRAINT [PK_User] PRIMARY KEY ([User_ID]),
    CONSTRAINT [FK_User_Role_ID] FOREIGN KEY ([Role_ID]) REFERENCES [dbo].[Role] ([Role_id])
);
GO

CREATE TABLE [dbo].[Membership_Package] (
    [Package_ID] INT NOT NULL,
    [Name] NVARCHAR(255) NULL,
    [Description] NVARCHAR(MAX) NULL,
    [Price] DECIMAL(18,2) NULL,
    [Duration] INT NULL,
    [Included_classes] INT NULL,
    [Benefits] NVARCHAR(MAX) NULL,
    [Term_conditions] NVARCHAR(MAX) NULL,
    [Status] NVARCHAR(50) NULL,
    CONSTRAINT [PK_Membership_Package] PRIMARY KEY ([Package_ID])
);
GO

CREATE TABLE [dbo].[Subject] (
    [Subject_ID] INT NOT NULL,
    [Name] NVARCHAR(255) NULL,
    [Description] NVARCHAR(MAX) NULL,
    CONSTRAINT [PK_Subject] PRIMARY KEY ([Subject_ID])
);
GO

CREATE TABLE [dbo].[Room] (
    [Room_ID] INT NOT NULL,
    [Name] NVARCHAR(255) NULL,
    [Max_Capacity] INT NULL,
    CONSTRAINT [PK_Room] PRIMARY KEY ([Room_ID])
);
GO

CREATE TABLE [dbo].[Voucher] (
    [Voucher_ID] INT NOT NULL,
    [Code] NVARCHAR(255) NULL,
    [Discount_Type] NVARCHAR(50) NULL,
    [Discount_Value] DECIMAL(18,2) NULL,
    [Start_Date] DATE NULL,
    [End_Date] DATE NULL,
    CONSTRAINT [PK_Voucher] PRIMARY KEY ([Voucher_ID])
);
GO

CREATE TABLE [dbo].[Member_Subscription] (
    [Subscription_ID] INT NOT NULL,
    [User_ID] INT NULL,
    [Package_ID] INT NULL,
    [Start_Date] DATE NULL,
    [End_Date] DATE NULL,
    [Status] NVARCHAR(50) NULL,
    CONSTRAINT [PK_Member_Subscription] PRIMARY KEY ([Subscription_ID]),
    CONSTRAINT [FK_Member_Subscription_User_ID] FOREIGN KEY ([User_ID]) REFERENCES [dbo].[User] ([User_ID]),
    CONSTRAINT [FK_Member_Subscription_Package_ID] FOREIGN KEY ([Package_ID]) REFERENCES [dbo].[Membership_Package] ([Package_ID])
);
GO

CREATE TABLE [dbo].[Class] (
    [Class_ID] INT NOT NULL,
    [Subject_ID] INT NULL,
    [Coach_ID] INT NULL,
    [Room_ID] INT NULL,
    [Name] NVARCHAR(255) NULL,
    [Max_Capacity] INT NULL,
    [Status] NVARCHAR(50) NULL,
    [Date] DATE NULL,
    [Time] TIME(0) NULL,
    CONSTRAINT [PK_Class] PRIMARY KEY ([Class_ID]),
    CONSTRAINT [FK_Class_Subject_ID] FOREIGN KEY ([Subject_ID]) REFERENCES [dbo].[Subject] ([Subject_ID]),
    CONSTRAINT [FK_Class_Coach_ID] FOREIGN KEY ([Coach_ID]) REFERENCES [dbo].[User] ([User_ID]),
    CONSTRAINT [FK_Class_Room_ID] FOREIGN KEY ([Room_ID]) REFERENCES [dbo].[Room] ([Room_ID])
);
GO

CREATE TABLE [dbo].[Class_Session] (
    [Session_ID] INT NOT NULL,
    [Class_ID] INT NULL,
    [Date] DATE NULL,
    [Start_Time] TIME(0) NULL,
    [End_Time] TIME(0) NULL,
    [Status] NVARCHAR(50) NULL,
    CONSTRAINT [PK_Class_Session] PRIMARY KEY ([Session_ID]),
    CONSTRAINT [FK_Class_Session_Class_ID] FOREIGN KEY ([Class_ID]) REFERENCES [dbo].[Class] ([Class_ID])
);
GO

CREATE TABLE [dbo].[Class_Booking] (
    [Booking_ID] INT NOT NULL,
    [User_ID] INT NULL,
    [Session_ID] INT NULL,
    [Booking_Dateime] DATETIME2(0) NULL,
    [Status] NVARCHAR(50) NULL,
    CONSTRAINT [PK_Class_Booking] PRIMARY KEY ([Booking_ID]),
    CONSTRAINT [FK_Class_Booking_User_ID] FOREIGN KEY ([User_ID]) REFERENCES [dbo].[User] ([User_ID]),
    CONSTRAINT [FK_Class_Booking_Session_ID] FOREIGN KEY ([Session_ID]) REFERENCES [dbo].[Class_Session] ([Session_ID])
);
GO

CREATE TABLE [dbo].[Payment] (
    [Payment_ID] INT NOT NULL,
    [Subscription_ID] INT NULL,
    [Receptionist_ID] INT NULL,
    [Voucher_ID] INT NULL,
    [Booking_ID] INT NULL,
    [Payment_Type] NVARCHAR(50) NULL,
    [Amount] DECIMAL(18,2) NULL,
    [Method] NVARCHAR(50) NULL,
    [Payment_Date] DATETIME2(0) NULL,
    [Status] NVARCHAR(50) NULL,
    [Discount_Amount] DECIMAL(18,2) NULL,
    CONSTRAINT [PK_Payment] PRIMARY KEY ([Payment_ID]),
    CONSTRAINT [FK_Payment_Subscription_ID] FOREIGN KEY ([Subscription_ID]) REFERENCES [dbo].[Member_Subscription] ([Subscription_ID]),
    CONSTRAINT [FK_Payment_Receptionist_ID] FOREIGN KEY ([Receptionist_ID]) REFERENCES [dbo].[User] ([User_ID]),
    CONSTRAINT [FK_Payment_Voucher_ID] FOREIGN KEY ([Voucher_ID]) REFERENCES [dbo].[Voucher] ([Voucher_ID]),
    CONSTRAINT [FK_Payment_Booking_ID] FOREIGN KEY ([Booking_ID]) REFERENCES [dbo].[Class_Booking] ([Booking_ID])
);
GO

CREATE TABLE [dbo].[Training_Plan] (
    [Plan_ID] INT NOT NULL,
    [Coach_ID] INT NULL,
    [Member_ID] INT NULL,
    [Goal] NVARCHAR(MAX) NULL,
    [Description] NVARCHAR(MAX) NULL,
    [Start_Date] DATE NULL,
    [End_Date] DATE NULL,
    CONSTRAINT [PK_Training_Plan] PRIMARY KEY ([Plan_ID]),
    CONSTRAINT [FK_Training_Plan_Coach_ID] FOREIGN KEY ([Coach_ID]) REFERENCES [dbo].[User] ([User_ID]),
    CONSTRAINT [FK_Training_Plan_Member_ID] FOREIGN KEY ([Member_ID]) REFERENCES [dbo].[User] ([User_ID])
);
GO

CREATE TABLE [dbo].[Attendance] (
    [Attendance_ID] INT NOT NULL,
    [Session_ID] INT NULL,
    [User_ID] INT NULL,
    [Status] NVARCHAR(50) NULL,
    [Note] NVARCHAR(MAX) NULL,
    CONSTRAINT [PK_Attendance] PRIMARY KEY ([Attendance_ID]),
    CONSTRAINT [FK_Attendance_Session_ID] FOREIGN KEY ([Session_ID]) REFERENCES [dbo].[Class_Session] ([Session_ID]),
    CONSTRAINT [FK_Attendance_User_ID] FOREIGN KEY ([User_ID]) REFERENCES [dbo].[User] ([User_ID])
);
GO

CREATE TABLE [dbo].[Training_Result] (
    [Result_ID] INT NOT NULL,
    [Session_ID] INT NULL,
    [User_ID] INT NULL,
    [Coach_ID] INT NULL,
    [Metric] NVARCHAR(MAX) NULL,
    [Coach_Feedback] NVARCHAR(MAX) NULL,
    [Date] DATE NULL,
    CONSTRAINT [PK_Training_Result] PRIMARY KEY ([Result_ID]),
    CONSTRAINT [FK_Training_Result_Session_ID] FOREIGN KEY ([Session_ID]) REFERENCES [dbo].[Class_Session] ([Session_ID]),
    CONSTRAINT [FK_Training_Result_User_ID] FOREIGN KEY ([User_ID]) REFERENCES [dbo].[User] ([User_ID]),
    CONSTRAINT [FK_Training_Result_Coach_ID] FOREIGN KEY ([Coach_ID]) REFERENCES [dbo].[User] ([User_ID])
);
GO

CREATE TABLE [dbo].[Activity_Log] (
    [Log_ID] INT NOT NULL,
    [User_ID] INT NULL,
    [Action_Type] NVARCHAR(50) NULL,
    [Target_Entity] NVARCHAR(255) NULL,
    [Description] NVARCHAR(MAX) NULL,
    [Created_At] DATETIME2(0) NULL,
    CONSTRAINT [PK_Activity_Log] PRIMARY KEY ([Log_ID]),
    CONSTRAINT [FK_Activity_Log_User_ID] FOREIGN KEY ([User_ID]) REFERENCES [dbo].[User] ([User_ID])
);
GO

CREATE TABLE [dbo].[Schedule] (
    [Schedule_ID] INT NOT NULL,
    [Sport_ID] INT NULL,
    [Coach_ID] INT NULL,
    [Schedule_date] DATE NULL,
    [Start_time] TIME(0) NULL,
    [End_time] TIME(0) NULL,
    [Status] NVARCHAR(50) NULL,
    CONSTRAINT [PK_Schedule] PRIMARY KEY ([Schedule_ID]),
    CONSTRAINT [FK_Schedule_Sport_ID] FOREIGN KEY ([Sport_ID]) REFERENCES [dbo].[Subject] ([Subject_ID]),
    CONSTRAINT [FK_Schedule_Coach_ID] FOREIGN KEY ([Coach_ID]) REFERENCES [dbo].[User] ([User_ID])
);
GO
