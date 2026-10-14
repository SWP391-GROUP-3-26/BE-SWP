-- Review and run manually on the intended database before deploying this backend.
-- Existing rows keep NULL Duration_Weeks / End_Date; no duration is inferred.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH('dbo.Class', 'Duration_Weeks') IS NULL
        ALTER TABLE dbo.Class ADD Duration_Weeks INT NULL;
    IF COL_LENGTH('dbo.Class', 'End_Date') IS NULL
        ALTER TABLE dbo.Class ADD End_Date DATE NULL;

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.Class') AND name = 'CK_Class_Status')
        ALTER TABLE dbo.Class DROP CONSTRAINT CK_Class_Status;

    UPDATE dbo.Class SET Status = N'Completed' WHERE LOWER(LTRIM(RTRIM(Status))) = N'complete';
    ALTER TABLE dbo.Class WITH CHECK ADD CONSTRAINT CK_Class_Status
        CHECK (Status IN (N'Open', N'Closed', N'Cancelled', N'Active', N'Completed', N'Cancel'));

    -- Dynamic SQL compiles only after the new columns exist. Monday anchor avoids SET DATEFIRST dependencies.
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.Class') AND name = 'CK_Class_Term')
        EXEC(N'ALTER TABLE dbo.Class WITH CHECK ADD CONSTRAINT CK_Class_Term CHECK (
            (Duration_Weeks IS NULL AND End_Date IS NULL)
            OR (Duration_Weeks IS NOT NULL AND Duration_Weeks > 0 AND [Date] IS NOT NULL AND End_Date IS NOT NULL
                AND End_Date = DATEADD(DAY,
                    6 - ((DATEDIFF(DAY, CONVERT(DATE, ''19000101'', 112), [Date]) % 7 + 7) % 7)
                        + 7 * (Duration_Weeks - 1), [Date]))
        )');

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.Class') AND name = 'IX_Class_End_Date_Status')
        EXEC(N'CREATE INDEX IX_Class_End_Date_Status ON dbo.Class (End_Date, Status)');

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
