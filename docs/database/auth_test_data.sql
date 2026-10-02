/*
TEST DATA ONLY for authentication manual testing.
Target database: swp391_db

All test users below use password: 12345@
Password is stored as BCrypt because AuthService uses BCryptPasswordEncoder.

Logout has no database data in the current scope. Frontend logout deletes JWT.
*/
USE [swp391_db];
GO

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'UX_User_Username'
      AND object_id = OBJECT_ID(N'dbo.[User]')
)
BEGIN
    CREATE UNIQUE INDEX [UX_User_Username]
    ON [dbo].[User] ([Username])
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'UX_User_Phone'
      AND object_id = OBJECT_ID(N'dbo.[User]')
)
BEGIN
    CREATE UNIQUE INDEX [UX_User_Phone]
    ON [dbo].[User] ([Phone])
    WHERE [Phone] IS NOT NULL;
END
GO

MERGE [dbo].[Role] AS target
USING (VALUES
    (1, N'Admin', N'Test admin role'),
    (2, N'Receptionist', N'Test receptionist role'),
    (3, N'Coach', N'Test coach role'),
    (4, N'Member', N'Test member role')
) AS source ([Role_ID], [Role_Name], [Description])
ON target.[Role_ID] = source.[Role_ID]
WHEN MATCHED THEN
    UPDATE SET
        [Role_Name] = source.[Role_Name],
        [Description] = source.[Description]
WHEN NOT MATCHED THEN
    INSERT ([Role_ID], [Role_Name], [Description])
    VALUES (source.[Role_ID], source.[Role_Name], source.[Description]);
GO

DECLARE @PasswordHash NVARCHAR(255) = N'$2a$10$HCZe8IHqoiwYaP8e2gnra.B4.ENayRlCCLaQGPflzHnx6DREzgowy';

SET IDENTITY_INSERT [dbo].[User] ON;

MERGE [dbo].[User] AS target
USING (VALUES
    (900001, 1, N'Test Admin', N'admin.test', N'0900000001', N'admin.test@swp.local', CAST('1990-01-01' AS DATE), N'Other', N'Test address', NULL, @PasswordHash, N'Active'),
    (900002, 4, N'Test Member', N'member.test', N'0900000002', N'member.test@swp.local', CAST('1995-02-02' AS DATE), N'Other', N'Test address', NULL, @PasswordHash, N'Active'),
    (900003, 3, N'Test Coach', N'coach.test', N'0900000003', N'coach.test@swp.local', CAST('1988-03-03' AS DATE), N'Other', N'Test address', NULL, @PasswordHash, N'Active'),
    (900004, 2, N'Test Receptionist', N'receptionist.test', N'0900000004', N'receptionist.test@swp.local', CAST('1992-04-04' AS DATE), N'Other', N'Test address', NULL, @PasswordHash, N'Active')
) AS source ([User_ID], [Role_ID], [FullName], [Username], [Phone], [Email], [DOB], [Gender], [Address], [Avatar_URL], [Password], [Status])
ON target.[User_ID] = source.[User_ID]
WHEN MATCHED THEN
    UPDATE SET
        [Role_ID] = source.[Role_ID],
        [FullName] = source.[FullName],
        [Username] = source.[Username],
        [Phone] = source.[Phone],
        [Email] = source.[Email],
        [DOB] = source.[DOB],
        [Gender] = source.[Gender],
        [Address] = source.[Address],
        [Avatar_URL] = source.[Avatar_URL],
        [Password] = source.[Password],
        [Status] = source.[Status]
WHEN NOT MATCHED THEN
    INSERT ([User_ID], [Role_ID], [FullName], [Username], [Phone], [Email], [DOB], [Gender], [Address], [Avatar_URL], [Password], [Status])
    VALUES (source.[User_ID], source.[Role_ID], source.[FullName], source.[Username], source.[Phone], source.[Email], source.[DOB], source.[Gender], source.[Address], source.[Avatar_URL], source.[Password], source.[Status]);
GO

SET IDENTITY_INSERT [dbo].[User] OFF;
GO

SELECT
    [User_ID],
    [Username],
    [Email],
    [Role_ID],
    [Status]
FROM [dbo].[User]
WHERE [User_ID] BETWEEN 900001 AND 900004
ORDER BY [User_ID];
GO
