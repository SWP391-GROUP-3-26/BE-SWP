-- Admin freeze configuration only. Member freeze/resume lifecycle is introduced separately.
-- Zero disables freezing; eligible package duration is at least 180 days (6 months in this system).
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.Membership_Package', 'Freeze_Months') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Membership_Package ADD Freeze_Months INT NOT NULL
        CONSTRAINT DF_Package_Freeze_Months DEFAULT 0 WITH VALUES;';

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id = OBJECT_ID('dbo.Membership_Package') AND name = 'CK_Package_Freeze')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Membership_Package WITH CHECK ADD CONSTRAINT CK_Package_Freeze
        CHECK (Freeze_Months >= 0 AND (Freeze_Months = 0 OR Duration >= 180));';

COMMIT TRANSACTION;
