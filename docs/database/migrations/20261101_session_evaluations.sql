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

IF EXISTS (SELECT 1 FROM dbo.Training_Result GROUP BY Session_ID, User_ID HAVING COUNT(*) > 1)
    THROW 50001, 'Training_Result has duplicate session/member rows. Review them before applying this migration.', 1;
ALTER TABLE dbo.Training_Result ALTER COLUMN Metric NVARCHAR(2000) NULL;
IF COL_LENGTH('dbo.Training_Result', 'Training_Content') IS NULL
    ALTER TABLE dbo.Training_Result ADD Training_Content NVARCHAR(2000) NULL;
IF COL_LENGTH('dbo.Training_Result', 'Progress_Status') IS NULL
    ALTER TABLE dbo.Training_Result ADD Progress_Status VARCHAR(30) NULL;
IF COL_LENGTH('dbo.Training_Result', 'Next_Steps') IS NULL
    ALTER TABLE dbo.Training_Result ADD Next_Steps NVARCHAR(2000) NULL;
IF COL_LENGTH('dbo.Training_Result', 'Updated_At') IS NULL
    ALTER TABLE dbo.Training_Result ADD Updated_At DATETIME2(7) NULL;
IF COL_LENGTH('dbo.Training_Result', 'Is_Withdrawn') IS NULL
    ALTER TABLE dbo.Training_Result ADD Is_Withdrawn BIT NOT NULL CONSTRAINT DF_Training_Result_Withdrawn DEFAULT 0 WITH VALUES;
IF COL_LENGTH('dbo.Training_Result', 'Version') IS NULL
    ALTER TABLE dbo.Training_Result ADD Version BIGINT NOT NULL CONSTRAINT DF_Training_Result_Version DEFAULT 0 WITH VALUES;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.Training_Result') AND name = 'UQ_Training_Result_Session_Member')
    CREATE UNIQUE INDEX UQ_Training_Result_Session_Member ON dbo.Training_Result(Session_ID, User_ID);
IF OBJECT_ID('dbo.CK_Training_Result_Progress', 'C') IS NULL
    EXEC(N'ALTER TABLE dbo.Training_Result ADD CONSTRAINT CK_Training_Result_Progress CHECK
        (Progress_Status IS NULL OR Progress_Status IN (''INITIAL'', ''IMPROVING'', ''STEADY'', ''NEEDS_IMPROVEMENT''))');
COMMIT TRANSACTION;
GO
