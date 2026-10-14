-- Run manually against the existing SQL Server swp391_db before starting this backend.
-- Times are DATETIME2(0) in Asia/Ho_Chi_Minh (UTC+07:00).
SET XACT_ABORT ON;
BEGIN TRANSACTION;

ALTER TABLE dbo.Class_Booking DROP CONSTRAINT CK_Booking_Status;
ALTER TABLE dbo.Class_Booking WITH CHECK ADD CONSTRAINT CK_Booking_Status
    CHECK (Status IN (N'Pending', N'Booked', N'Cancelled', N'Completed'));

ALTER TABLE dbo.Class_Booking ADD Payment_Expires_At DATETIME2(0) NULL,
    Payment_Method NVARCHAR(20) NULL;
GO

ALTER TABLE dbo.Class_Booking ADD CONSTRAINT CK_Booking_Payment_Method
    CHECK (Payment_Method IS NULL OR Payment_Method IN (N'PACKAGE', N'DIRECT'));

ALTER TABLE dbo.Payment ADD Expires_At DATETIME2(0) NULL,
    Vnp_Transaction_No NVARCHAR(15) NULL,
    Vnp_Response_Code NVARCHAR(2) NULL,
    Vnp_Transaction_Status NVARCHAR(2) NULL,
    Vnp_Pay_Date NVARCHAR(14) NULL,
    Requires_Review BIT NOT NULL CONSTRAINT DF_Payment_Requires_Review DEFAULT 0 WITH VALUES;

CREATE INDEX IX_Booking_Pending_Expiry ON dbo.Class_Booking (Status, Payment_Expires_At) INCLUDE (Session_ID);
COMMIT TRANSACTION;
