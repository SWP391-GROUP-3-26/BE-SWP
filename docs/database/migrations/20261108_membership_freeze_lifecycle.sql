-- Run after 20261108_membership_freeze_configuration.sql and the existing membership/payment migrations.
-- Existing subscriptions intentionally receive no freeze entitlement. New purchases snapshot package configuration.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.Member_Subscription', 'Freeze_Months_At_Purchase') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Member_Subscription ADD
        Freeze_Months_At_Purchase INT NOT NULL CONSTRAINT DF_Subscription_Freeze_Months DEFAULT 0 WITH VALUES,
        Freeze_Months_Used INT NOT NULL CONSTRAINT DF_Subscription_Freeze_Used DEFAULT 0 WITH VALUES,
        Freeze_Started_At DATETIME2(0) NULL, Freeze_Ends_At DATETIME2(0) NULL;';

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.Member_Subscription')
           AND name = 'CK_Subscription_Status')
    ALTER TABLE dbo.Member_Subscription DROP CONSTRAINT CK_Subscription_Status;

ALTER TABLE dbo.Member_Subscription WITH CHECK ADD CONSTRAINT CK_Subscription_Status
    CHECK (Status IN (N'Pending', N'Active', N'Frozen', N'Expired', N'Cancelled'));

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.Member_Subscription')
               AND name = 'CK_Subscription_Freeze_Budget')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Member_Subscription WITH CHECK ADD CONSTRAINT CK_Subscription_Freeze_Budget
        CHECK (Freeze_Months_At_Purchase >= 0 AND Freeze_Months_Used >= 0 AND Freeze_Months_Used <= Freeze_Months_At_Purchase);';

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.Member_Subscription')
               AND name = 'CK_Subscription_Freeze_State')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Member_Subscription WITH CHECK ADD CONSTRAINT CK_Subscription_Freeze_State CHECK (
        (Status = N''Frozen'' AND Freeze_Started_At IS NOT NULL AND Freeze_Ends_At IS NOT NULL AND Freeze_Ends_At > Freeze_Started_At)
        OR (Status <> N''Frozen'' AND Freeze_Started_At IS NULL AND Freeze_Ends_At IS NULL));';

COMMIT TRANSACTION;
