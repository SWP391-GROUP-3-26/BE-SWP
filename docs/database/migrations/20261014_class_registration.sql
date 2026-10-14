-- Review and run manually on the intended database after the VNPay and class-duration migrations.
-- Legacy bookings keep Registration_Code = NULL and still represent only their original session.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF COL_LENGTH('dbo.Class_Booking', 'Registration_Code') IS NULL
        ALTER TABLE dbo.Class_Booking ADD Registration_Code VARCHAR(36) NULL;

    IF EXISTS (SELECT 1 FROM sys.key_constraints
               WHERE parent_object_id = OBJECT_ID('dbo.Class_Booking') AND name = 'UQ_Booking_User_Session')
        ALTER TABLE dbo.Class_Booking DROP CONSTRAINT UQ_Booking_User_Session;

    -- A retry creates a new registration, keeping cancelled bookings and their payments intact.
    IF NOT EXISTS (SELECT 1 FROM sys.indexes
                   WHERE object_id = OBJECT_ID('dbo.Class_Booking') AND name = 'UQ_Booking_Registration_Session')
        EXEC(N'CREATE UNIQUE INDEX UQ_Booking_Registration_Session
            ON dbo.Class_Booking (Registration_Code, Session_ID) WHERE Registration_Code IS NOT NULL');
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
