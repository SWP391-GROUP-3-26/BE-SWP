-- Apply before starting the updated BE (Hibernate ddl-auto=validate).
-- Backfill provider only when adding the column; unresolved or conflicting evidence stays UNKNOWN.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
DECLARE @backfill BIT = CASE WHEN COL_LENGTH('dbo.User', 'Auth_Provider') IS NULL THEN 1 ELSE 0 END;
IF COL_LENGTH('dbo.User', 'Email_Verified') IS NULL
    ALTER TABLE dbo.[User] ADD Email_Verified BIT NOT NULL CONSTRAINT DF_User_Email_Verified DEFAULT 0 WITH VALUES;
IF COL_LENGTH('dbo.User', 'Auth_Provider') IS NULL
    ALTER TABLE dbo.[User] ADD Auth_Provider NVARCHAR(10) NOT NULL CONSTRAINT DF_User_Auth_Provider DEFAULT N'UNKNOWN' WITH VALUES;
IF COL_LENGTH('dbo.User', 'Session_Version') IS NULL
    ALTER TABLE dbo.[User] ADD Session_Version BIGINT NOT NULL CONSTRAINT DF_User_Session_Version DEFAULT 0 WITH VALUES;

IF @backfill = 1
    EXEC sys.sp_executesql N'
    ;WITH Evidence AS (
        SELECT u.User_ID,
            CASE WHEN EXISTS (SELECT 1 FROM dbo.Activity_Log a
                WHERE a.User_ID = u.User_ID AND a.Action_Type = N''REGISTER_USER'' AND a.Target_Entity = N''User''
                AND a.Description LIKE N''Người dùng tự đăng ký tài khoản trực tuyến qua Google #'' + CAST(u.User_ID AS NVARCHAR(20)) + N'' (%'')
                THEN 1 ELSE 0 END AS Google_Evidence,
            CASE WHEN EXISTS (SELECT 1 FROM dbo.Activity_Log a WHERE a.Target_Entity = N''User'' AND (
                (a.User_ID = u.User_ID AND a.Action_Type = N''REGISTER_USER''
                    AND a.Description LIKE N''Người dùng tự đăng ký tài khoản trực tuyến #'' + CAST(u.User_ID AS NVARCHAR(20)) + N'' (%'')
                OR (a.Action_Type = N''CREATE_MEMBER''
                    AND a.Description LIKE N''Lễ tân đăng ký tài khoản hội viên #'' + CAST(u.User_ID AS NVARCHAR(20)) + N'' (%'')
                OR (a.Action_Type = N''CREATE_USER'' AND (
                    a.Description LIKE N''Tạo tài khoản Receptionist #'' + CAST(u.User_ID AS NVARCHAR(20)) + N'' (%'' OR
                    a.Description LIKE N''Tạo tài khoản Coach #'' + CAST(u.User_ID AS NVARCHAR(20)) + N'' (%'' OR
                    a.Description LIKE N''Tạo tài khoản Member #'' + CAST(u.User_ID AS NVARCHAR(20)) + N'' (%''))
            )) THEN 1 ELSE 0 END AS Local_Evidence
        FROM dbo.[User] u
    )
    UPDATE u SET Auth_Provider = CASE
        WHEN e.Google_Evidence = 1 AND e.Local_Evidence = 0 THEN N''GOOGLE''
        WHEN e.Local_Evidence = 1 AND e.Google_Evidence = 0 THEN N''LOCAL''
        ELSE N''UNKNOWN'' END,
        Email_Verified = CASE WHEN e.Google_Evidence = 1 AND e.Local_Evidence = 0 THEN 1 ELSE 0 END
    FROM dbo.[User] u JOIN Evidence e ON e.User_ID = u.User_ID;';

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.User') AND name = 'CK_User_Auth_Provider')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.[User] WITH CHECK ADD CONSTRAINT CK_User_Auth_Provider CHECK (Auth_Provider IN (N''LOCAL'', N''GOOGLE'', N''UNKNOWN''));';

IF OBJECT_ID('dbo.Email_Otp_Challenge', 'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.Email_Otp_Challenge (
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
        CONSTRAINT CK_Email_Otp_Attempts CHECK (Failed_Attempts BETWEEN 0 AND 5),
        CONSTRAINT CK_Email_Otp_Send_Count CHECK (Send_Count BETWEEN 0 AND 5)
    );';

-- Safe to rerun after an earlier version of this migration: preserve users, audits and send budgets.
IF COL_LENGTH('dbo.Email_Otp_Challenge', 'Original_Email') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Email_Otp_Challenge ADD Original_Email NVARCHAR(100) NULL;';
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.Email_Otp_Challenge') AND name = 'CK_Email_Otp_Purpose')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Email_Otp_Challenge DROP CONSTRAINT CK_Email_Otp_Purpose;';
EXEC sys.sp_executesql N'ALTER TABLE dbo.Email_Otp_Challenge WITH CHECK ADD CONSTRAINT CK_Email_Otp_Purpose
    CHECK (Purpose IS NULL OR Purpose IN (N''REGISTER'', N''VERIFY_EMAIL'', N''RESET'', N''CHANGE_EMAIL'', N''VERIFY_CHANGE_EMAIL''));';
COMMIT TRANSACTION;

-- Admin must review this list; never infer provider from email domain or password hash.
EXEC sys.sp_executesql N'SELECT User_ID, Username, Email, Auth_Provider, Email_Verified FROM dbo.[User] WHERE Auth_Provider = N''UNKNOWN'' ORDER BY User_ID;';
