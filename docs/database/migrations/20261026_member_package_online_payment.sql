-- Run before starting the updated backend (Hibernate uses ddl-auto=validate).
-- Retain which membership a pending online renewal may replace after payment.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.Payment', 'Renewal_Subscription_ID') IS NULL
    ALTER TABLE dbo.Payment ADD Renewal_Subscription_ID INT NULL;
IF COL_LENGTH('dbo.Payment', 'Renewal_Start') IS NULL
    ALTER TABLE dbo.Payment ADD Renewal_Start NVARCHAR(20) NULL;
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_Payment_Renewal_Subscription')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Payment ADD CONSTRAINT FK_Payment_Renewal_Subscription
        FOREIGN KEY (Renewal_Subscription_ID) REFERENCES dbo.Member_Subscription(Subscription_ID)';
COMMIT;
