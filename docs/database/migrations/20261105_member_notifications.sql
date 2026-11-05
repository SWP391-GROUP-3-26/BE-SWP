-- Member bell notifications: package expiry and assigned training plans.
-- Run once on swp391_db before starting the updated backend (ddl-auto=validate).
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.Member_Notification', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Member_Notification
    (
        Notification_ID BIGINT IDENTITY(1,1) NOT NULL,
        Member_ID        INT NOT NULL,
        Type             VARCHAR(40) NOT NULL,
        Title            NVARCHAR(200) NOT NULL,
        Message          NVARCHAR(1000) NOT NULL,
        Reference_Type   VARCHAR(40) NOT NULL,
        Reference_ID     INT NOT NULL,
        Source_Key       VARCHAR(200) NOT NULL,
        Read_At          DATETIME2(0) NULL,
        Created_At       DATETIME2(0) NOT NULL
            CONSTRAINT DF_Member_Notification_Created_At DEFAULT SYSDATETIME(),

        CONSTRAINT PK_Member_Notification PRIMARY KEY (Notification_ID),
        CONSTRAINT FK_Member_Notification_Member FOREIGN KEY (Member_ID)
            REFERENCES dbo.[User](User_ID),
        CONSTRAINT UQ_Member_Notification_Source UNIQUE (Member_ID, Source_Key),
        CONSTRAINT CK_Member_Notification_Type CHECK
            (Type IN ('PACKAGE_EXPIRING', 'TRAINING_PLAN_ASSIGNED')),
        CONSTRAINT CK_Member_Notification_Reference_Type CHECK
            (Reference_Type IN ('MEMBERSHIP', 'TRAINING_PLAN'))
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_Member_Notification_Member_CreatedAt'
      AND object_id = OBJECT_ID(N'dbo.Member_Notification')
)
BEGIN
    CREATE INDEX IX_Member_Notification_Member_CreatedAt
        ON dbo.Member_Notification(Member_ID, Created_At DESC, Notification_ID DESC);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_Member_Notification_Member_Unread'
      AND object_id = OBJECT_ID(N'dbo.Member_Notification')
)
BEGIN
    CREATE INDEX IX_Member_Notification_Member_Unread
        ON dbo.Member_Notification(Member_ID, Notification_ID DESC)
        WHERE Read_At IS NULL;
END;

COMMIT TRANSACTION;
