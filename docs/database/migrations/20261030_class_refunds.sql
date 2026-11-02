USE [swp391_db];
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.Payment', 'Item_Name_At_Payment') IS NULL
    ALTER TABLE dbo.Payment ADD Item_Name_At_Payment NVARCHAR(200) NULL;
IF COL_LENGTH('dbo.Payment', 'Created_At') IS NULL
    ALTER TABLE dbo.Payment ADD Created_At DATETIME2(7) NULL;
IF COL_LENGTH('dbo.Payment', 'Paid_At') IS NULL
    ALTER TABLE dbo.Payment ADD Paid_At DATETIME2(7) NULL;
EXEC(N'UPDATE dbo.Payment SET Created_At = Payment_Date WHERE Created_At IS NULL;
UPDATE p SET Item_Name_At_Payment = COALESCE(s.Package_Name_At_Purchase, c.Name)
FROM dbo.Payment p LEFT JOIN dbo.Member_Subscription s ON s.Subscription_ID = p.Subscription_ID
LEFT JOIN dbo.Class_Booking b ON b.Booking_ID = p.Booking_ID
LEFT JOIN dbo.Class_Session cs ON cs.Session_ID = b.Session_ID
LEFT JOIN dbo.Class c ON c.Class_ID = cs.Class_ID WHERE p.Item_Name_At_Payment IS NULL;');
-- Paid_At stays NULL for legacy receipts: their exact confirmation time was not recorded consistently.

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
COMMIT TRANSACTION;
GO
