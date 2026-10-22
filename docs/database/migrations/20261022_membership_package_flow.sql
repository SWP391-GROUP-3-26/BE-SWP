-- Membership Package flow: Included Sports, Choice Groups and membership purchase snapshots.
-- Review and run once on the target SQL Server database before deploying this backend.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.Membership_Package', 'Discount') IS NULL
    ALTER TABLE dbo.Membership_Package ADD Discount DECIMAL(5,2) NOT NULL
        CONSTRAINT DF_Membership_Package_Discount DEFAULT 0 WITH VALUES;

IF COL_LENGTH('dbo.Subject', 'Status') IS NULL
    ALTER TABLE dbo.Subject ADD Status NVARCHAR(20) NOT NULL
        CONSTRAINT DF_Subject_Status DEFAULT N'Active' WITH VALUES;

IF COL_LENGTH('dbo.Member_Subscription', 'Price_At_Purchase') IS NULL
    EXEC sys.sp_executesql N'
        ALTER TABLE dbo.Member_Subscription ADD Price_At_Purchase DECIMAL(12,2) NULL,
            Duration_At_Purchase INT NULL, Terms_At_Purchase NVARCHAR(MAX) NULL,
            Package_Name_At_Purchase NVARCHAR(200) NULL, Cancellation_Reason NVARCHAR(MAX) NULL,
            Cancelled_At DATETIME2(0) NULL, Created_At DATETIME2(0) NOT NULL
            CONSTRAINT DF_Member_Subscription_Created_At DEFAULT SYSDATETIME() WITH VALUES;';

-- Dynamic SQL is required: SQL Server validates column names before executing ALTER TABLE in this batch.
EXEC sys.sp_executesql N'
    UPDATE s SET Price_At_Purchase = p.Price, Duration_At_Purchase = p.Duration,
        Terms_At_Purchase = p.Term_Conditions, Package_Name_At_Purchase = p.Name
    FROM dbo.Member_Subscription s JOIN dbo.Membership_Package p ON p.Package_ID = s.Package_ID
    WHERE s.Price_At_Purchase IS NULL OR s.Duration_At_Purchase IS NULL OR s.Package_Name_At_Purchase IS NULL;

    ALTER TABLE dbo.Member_Subscription ALTER COLUMN Price_At_Purchase DECIMAL(12,2) NOT NULL;
    ALTER TABLE dbo.Member_Subscription ALTER COLUMN Duration_At_Purchase INT NOT NULL;
    ALTER TABLE dbo.Member_Subscription ALTER COLUMN Package_Name_At_Purchase NVARCHAR(200) NOT NULL;';

IF OBJECT_ID('dbo.Package_Included_Sport', 'U') IS NULL
CREATE TABLE dbo.Package_Included_Sport (
    ID INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    Package_ID INT NOT NULL, Subject_ID INT NOT NULL,
    CONSTRAINT UQ_Package_Included_Sport UNIQUE (Package_ID, Subject_ID),
    CONSTRAINT FK_Package_Included_Sport_Package FOREIGN KEY (Package_ID) REFERENCES dbo.Membership_Package(Package_ID),
    CONSTRAINT FK_Package_Included_Sport_Subject FOREIGN KEY (Subject_ID) REFERENCES dbo.Subject(Subject_ID)
);

IF OBJECT_ID('dbo.Package_Choice_Group', 'U') IS NULL
CREATE TABLE dbo.Package_Choice_Group (
    Choice_Group_ID INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    Package_ID INT NOT NULL, Group_Name NVARCHAR(100) NOT NULL, Description NVARCHAR(MAX) NULL,
    Required_Selection INT NOT NULL, Sort_Order INT NOT NULL CONSTRAINT DF_Package_Choice_Group_Sort DEFAULT 0,
    CONSTRAINT CK_Package_Choice_Group_Required CHECK (Required_Selection > 0),
    CONSTRAINT FK_Package_Choice_Group_Package FOREIGN KEY (Package_ID) REFERENCES dbo.Membership_Package(Package_ID)
);

IF OBJECT_ID('dbo.Package_Choice_Group_Sport', 'U') IS NULL
CREATE TABLE dbo.Package_Choice_Group_Sport (
    ID INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    Choice_Group_ID INT NOT NULL, Subject_ID INT NOT NULL,
    CONSTRAINT UQ_Package_Choice_Group_Sport UNIQUE (Choice_Group_ID, Subject_ID),
    CONSTRAINT FK_Package_Choice_Group_Sport_Group FOREIGN KEY (Choice_Group_ID) REFERENCES dbo.Package_Choice_Group(Choice_Group_ID),
    CONSTRAINT FK_Package_Choice_Group_Sport_Subject FOREIGN KEY (Subject_ID) REFERENCES dbo.Subject(Subject_ID)
);

IF OBJECT_ID('dbo.Membership_Sport_Selection', 'U') IS NULL
CREATE TABLE dbo.Membership_Sport_Selection (
    Selection_ID INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    Subscription_ID INT NOT NULL, Choice_Group_ID INT NOT NULL,
    Choice_Group_Name_At_Purchase NVARCHAR(100) NOT NULL,
    Subject_ID INT NOT NULL, Subject_Name_At_Purchase NVARCHAR(100) NOT NULL,
    CONSTRAINT UQ_Membership_Sport_Selection UNIQUE (Subscription_ID, Choice_Group_ID, Subject_ID),
    CONSTRAINT FK_Membership_Sport_Selection_Subscription FOREIGN KEY (Subscription_ID) REFERENCES dbo.Member_Subscription(Subscription_ID),
    CONSTRAINT FK_Membership_Sport_Selection_Subject FOREIGN KEY (Subject_ID) REFERENCES dbo.Subject(Subject_ID)
);

IF COL_LENGTH('dbo.Membership_Sport_Selection', 'Choice_Group_Name_At_Purchase') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Membership_Sport_Selection ADD Choice_Group_Name_At_Purchase NVARCHAR(100) NULL;';
IF COL_LENGTH('dbo.Membership_Sport_Selection', 'Subject_Name_At_Purchase') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Membership_Sport_Selection ADD Subject_Name_At_Purchase NVARCHAR(100) NULL;';

IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_Membership_Sport_Selection_Group')
    ALTER TABLE dbo.Membership_Sport_Selection DROP CONSTRAINT FK_Membership_Sport_Selection_Group;

EXEC sys.sp_executesql N'
    UPDATE mss SET Choice_Group_Name_At_Purchase = pcg.Group_Name,
        Subject_Name_At_Purchase = s.Name
    FROM dbo.Membership_Sport_Selection mss
    LEFT JOIN dbo.Package_Choice_Group pcg ON pcg.Choice_Group_ID = mss.Choice_Group_ID
    JOIN dbo.Subject s ON s.Subject_ID = mss.Subject_ID
    WHERE mss.Choice_Group_Name_At_Purchase IS NULL OR mss.Subject_Name_At_Purchase IS NULL;

    ALTER TABLE dbo.Membership_Sport_Selection ALTER COLUMN Choice_Group_Name_At_Purchase NVARCHAR(100) NOT NULL;
    ALTER TABLE dbo.Membership_Sport_Selection ALTER COLUMN Subject_Name_At_Purchase NVARCHAR(100) NOT NULL;';

IF OBJECT_ID('dbo.Membership_Included_Sport', 'U') IS NULL
CREATE TABLE dbo.Membership_Included_Sport (
    Snapshot_ID INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    Subscription_ID INT NOT NULL, Subject_ID INT NOT NULL,
    Subject_Name_At_Purchase NVARCHAR(100) NOT NULL,
    CONSTRAINT UQ_Membership_Included_Sport UNIQUE (Subscription_ID, Subject_ID),
    CONSTRAINT FK_Membership_Included_Sport_Subscription FOREIGN KEY (Subscription_ID) REFERENCES dbo.Member_Subscription(Subscription_ID),
    CONSTRAINT FK_Membership_Included_Sport_Subject FOREIGN KEY (Subject_ID) REFERENCES dbo.Subject(Subject_ID)
);
COMMIT TRANSACTION;
