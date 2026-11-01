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

IF COL_LENGTH('dbo.Training_Plan', 'Plan_Scope') IS NULL
    ALTER TABLE dbo.Training_Plan ADD Plan_Scope VARCHAR(20) NOT NULL
        CONSTRAINT DF_Training_Plan_Scope DEFAULT 'INDIVIDUAL' WITH VALUES;

-- The filtered unique index depends on Member_ID; recreate it around the nullability change.
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.Training_Plan') AND name = 'Member_ID' AND is_nullable = 0)
BEGIN
    DROP INDEX IF EXISTS UX_Training_Plan_Assigned ON dbo.Training_Plan;
    DROP INDEX IF EXISTS IX_Training_Plan_Member ON dbo.Training_Plan;
    ALTER TABLE dbo.Training_Plan ALTER COLUMN Member_ID INT NULL;
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.Training_Plan') AND name = 'UX_Training_Plan_Assigned')
    CREATE UNIQUE INDEX UX_Training_Plan_Assigned ON dbo.Training_Plan(Class_ID, Member_ID) WHERE Status = 'ASSIGNED';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.Training_Plan') AND name = 'IX_Training_Plan_Member')
    CREATE INDEX IX_Training_Plan_Member ON dbo.Training_Plan(Member_ID, Published_At DESC);
IF OBJECT_ID('dbo.CK_Training_Plan_Scope', 'C') IS NULL
    EXEC(N'ALTER TABLE dbo.Training_Plan ADD CONSTRAINT CK_Training_Plan_Scope CHECK
        ((Plan_Scope = ''CLASS'' AND Member_ID IS NULL AND Class_ID IS NOT NULL)
          OR (Plan_Scope = ''INDIVIDUAL'' AND Member_ID IS NOT NULL))');

IF OBJECT_ID('dbo.Training_Plan_Recipient', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Training_Plan_Recipient (
        Recipient_ID INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Training_Plan_Recipient PRIMARY KEY,
        Plan_ID INT NOT NULL REFERENCES dbo.Training_Plan(Plan_ID),
        Member_ID INT NOT NULL REFERENCES dbo.[User](User_ID),
        Read_Revision INT NOT NULL CONSTRAINT DF_Training_Plan_Recipient_Read DEFAULT 0,
        Coach_Feedback NVARCHAR(2000) NULL,
        CONSTRAINT UQ_Training_Plan_Recipient UNIQUE (Plan_ID, Member_ID),
        CONSTRAINT CK_Training_Plan_Recipient_Read CHECK (Read_Revision >= 0)
    );
    CREATE INDEX IX_Training_Plan_Recipient_Member ON dbo.Training_Plan_Recipient(Member_ID, Plan_ID);
END;
IF OBJECT_ID('dbo.Training_Exercise_Result', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Training_Exercise_Result (
        Result_ID INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Training_Exercise_Result PRIMARY KEY,
        Recipient_ID INT NOT NULL REFERENCES dbo.Training_Plan_Recipient(Recipient_ID),
        Exercise_ID INT NOT NULL REFERENCES dbo.Training_Exercise(Exercise_ID),
        Completed BIT NOT NULL CONSTRAINT DF_Training_Exercise_Result_Completed DEFAULT 0,
        Member_Result NVARCHAR(2000) NULL,
        CONSTRAINT UQ_Training_Exercise_Result UNIQUE (Recipient_ID, Exercise_ID)
    );
END;
COMMIT TRANSACTION;
GO
