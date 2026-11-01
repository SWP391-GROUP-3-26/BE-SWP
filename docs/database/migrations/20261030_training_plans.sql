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

IF OBJECT_ID(N'dbo.Training_Plan', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Training_Plan (
        Plan_ID INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Training_Plan PRIMARY KEY,
        Class_ID INT NULL REFERENCES dbo.[Class](Class_ID),
        Member_ID INT NOT NULL REFERENCES dbo.[User](User_ID),
        Coach_ID INT NOT NULL REFERENCES dbo.[User](User_ID),
        Title NVARCHAR(150) NOT NULL CONSTRAINT DF_Training_Plan_Title DEFAULT N'Kế hoạch tập luyện',
        Goal NVARCHAR(2000) NOT NULL,
        Description NVARCHAR(MAX) NULL,
        Assessment NVARCHAR(2000) NULL,
        Start_Date DATE NOT NULL,
        End_Date DATE NULL,
        Status VARCHAR(20) NOT NULL CONSTRAINT DF_Training_Plan_Status DEFAULT 'DRAFT',
        Coach_Feedback NVARCHAR(2000) NULL,
        Published_Revision INT NOT NULL CONSTRAINT DF_Training_Plan_Published DEFAULT 0,
        Read_Revision INT NOT NULL CONSTRAINT DF_Training_Plan_Read DEFAULT 0,
        Published_At DATETIME2(7) NULL,
        Created_At DATETIME2(7) NOT NULL CONSTRAINT DF_Training_Plan_Created DEFAULT SYSDATETIME(),
        Updated_At DATETIME2(7) NOT NULL CONSTRAINT DF_Training_Plan_Updated DEFAULT SYSDATETIME(),
        Version BIGINT NOT NULL CONSTRAINT DF_Training_Plan_Version DEFAULT 0,
        CONSTRAINT CK_Training_Plan_Status CHECK (Status IN ('DRAFT','ASSIGNED','COMPLETED','CANCELLED')),
        CONSTRAINT CK_Training_Plan_Dates CHECK (End_Date IS NULL OR End_Date >= Start_Date),
        CONSTRAINT CK_Training_Plan_People CHECK (Coach_ID <> Member_ID),
        CONSTRAINT CK_Training_Plan_Revisions CHECK (Read_Revision >= 0 AND Published_Revision >= Read_Revision)
    );
END;

-- Extend the original schema without deleting its plans or Description column.
-- Old plans have no class association; only new class-scoped plans appear in the new feature.
IF COL_LENGTH('dbo.Training_Plan', 'Class_ID') IS NULL
    ALTER TABLE dbo.Training_Plan ADD Class_ID INT NULL REFERENCES dbo.[Class](Class_ID);
IF COL_LENGTH('dbo.Training_Plan', 'Title') IS NULL
    ALTER TABLE dbo.Training_Plan ADD Title NVARCHAR(150) NOT NULL CONSTRAINT DF_Training_Plan_Title DEFAULT N'Kế hoạch tập luyện';
IF COL_LENGTH('dbo.Training_Plan', 'Assessment') IS NULL
    ALTER TABLE dbo.Training_Plan ADD Assessment NVARCHAR(2000) NULL;
IF COL_LENGTH('dbo.Training_Plan', 'Status') IS NULL
    ALTER TABLE dbo.Training_Plan ADD Status VARCHAR(20) NOT NULL CONSTRAINT DF_Training_Plan_Status DEFAULT 'DRAFT'
        CONSTRAINT CK_Training_Plan_Status CHECK (Status IN ('DRAFT','ASSIGNED','COMPLETED','CANCELLED'));
IF COL_LENGTH('dbo.Training_Plan', 'Coach_Feedback') IS NULL
    ALTER TABLE dbo.Training_Plan ADD Coach_Feedback NVARCHAR(2000) NULL;
IF COL_LENGTH('dbo.Training_Plan', 'Published_Revision') IS NULL
    ALTER TABLE dbo.Training_Plan ADD Published_Revision INT NOT NULL CONSTRAINT DF_Training_Plan_Published DEFAULT 0;
IF COL_LENGTH('dbo.Training_Plan', 'Read_Revision') IS NULL
    ALTER TABLE dbo.Training_Plan ADD Read_Revision INT NOT NULL CONSTRAINT DF_Training_Plan_Read DEFAULT 0;
IF COL_LENGTH('dbo.Training_Plan', 'Published_At') IS NULL
    ALTER TABLE dbo.Training_Plan ADD Published_At DATETIME2(7) NULL;
IF COL_LENGTH('dbo.Training_Plan', 'Created_At') IS NULL
    ALTER TABLE dbo.Training_Plan ADD Created_At DATETIME2(7) NOT NULL CONSTRAINT DF_Training_Plan_Created DEFAULT SYSDATETIME();
IF COL_LENGTH('dbo.Training_Plan', 'Updated_At') IS NULL
    ALTER TABLE dbo.Training_Plan ADD Updated_At DATETIME2(7) NOT NULL CONSTRAINT DF_Training_Plan_Updated DEFAULT SYSDATETIME();
IF COL_LENGTH('dbo.Training_Plan', 'Version') IS NULL
    ALTER TABLE dbo.Training_Plan ADD Version BIGINT NOT NULL CONSTRAINT DF_Training_Plan_Version DEFAULT 0;
ALTER TABLE dbo.Training_Plan ALTER COLUMN Goal NVARCHAR(2000) NOT NULL;

-- Dynamic SQL lets SQL Server compile these after the legacy table has been extended.
IF OBJECT_ID('dbo.CK_Training_Plan_Revisions', 'C') IS NULL
    EXEC(N'ALTER TABLE dbo.Training_Plan ADD CONSTRAINT CK_Training_Plan_Revisions CHECK (Read_Revision >= 0 AND Published_Revision >= Read_Revision)');
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.Training_Plan') AND name = 'UX_Training_Plan_Assigned')
    EXEC(N'CREATE UNIQUE INDEX UX_Training_Plan_Assigned ON dbo.Training_Plan(Class_ID, Member_ID) WHERE Status = ''ASSIGNED''');
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.Training_Plan') AND name = 'IX_Training_Plan_Member')
    EXEC(N'CREATE INDEX IX_Training_Plan_Member ON dbo.Training_Plan(Member_ID, Published_At DESC)');

IF OBJECT_ID(N'dbo.Training_Exercise', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Training_Exercise (
        Exercise_ID INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Training_Exercise PRIMARY KEY,
        Plan_ID INT NOT NULL REFERENCES dbo.Training_Plan(Plan_ID),
        Position INT NOT NULL,
        Training_Date DATE NOT NULL,
        Name NVARCHAR(150) NOT NULL,
        Instructions NVARCHAR(2000) NULL,
        Target NVARCHAR(255) NOT NULL,
        Completed BIT NOT NULL CONSTRAINT DF_Training_Exercise_Completed DEFAULT 0,
        Member_Result NVARCHAR(2000) NULL
    );
    CREATE INDEX IX_Training_Exercise_Plan ON dbo.Training_Exercise(Plan_ID, Position);
END;

COMMIT TRANSACTION;
GO
