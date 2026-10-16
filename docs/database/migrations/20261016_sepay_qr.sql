-- Run on the intended database before starting the SePay-enabled backend.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.Payment', 'Sepay_Transaction_ID') IS NULL
    ALTER TABLE dbo.Payment ADD Sepay_Transaction_ID BIGINT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.Payment') AND name = 'UX_Payment_Sepay_Transaction')
    EXEC('CREATE UNIQUE INDEX UX_Payment_Sepay_Transaction ON dbo.Payment (Sepay_Transaction_ID) WHERE Sepay_Transaction_ID IS NOT NULL');
COMMIT TRANSACTION;
