-- Consolidated room management migration: 12 room types, Yoga/Zumba -> MULTIPURPOSE, HIIT -> GYM.
-- Supports databases with no room migration or any earlier partial room migration applied.
-- Run once during upgrade and review result sets. Existing classes/bookings are preserved.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
DECLARE @inferRooms BIT = CASE WHEN COL_LENGTH('dbo.Room', 'Room_Type') IS NULL THEN 1 ELSE 0 END;
DECLARE @inferSubjects BIT = CASE WHEN OBJECT_ID('dbo.Subject_Room_Type', 'U') IS NULL THEN 1 ELSE 0 END;
IF COL_LENGTH('dbo.Room', 'Room_Type') IS NULL
    ALTER TABLE dbo.Room ADD Room_Type NVARCHAR(30) NULL;
IF COL_LENGTH('dbo.Room', 'Location') IS NULL
    ALTER TABLE dbo.Room ADD Location NVARCHAR(150) NULL;
IF COL_LENGTH('dbo.Room', 'Status') IS NULL
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Room ADD Status NVARCHAR(20) NOT NULL CONSTRAINT DF_Room_Status DEFAULT N''Active'' WITH VALUES;';
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.Room') AND name = 'CK_Room_Status')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Room WITH CHECK ADD CONSTRAINT CK_Room_Status CHECK (Status IN (N''Active'', N''Maintenance'', N''Inactive''));';
-- Remove previous type constraints before converting legacy values and installing the final set.
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.Room') AND name = 'CK_Room_Type')
    ALTER TABLE dbo.Room DROP CONSTRAINT CK_Room_Type;
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.Subject_Room_Type') AND name = 'CK_Subject_Room_Type')
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Subject_Room_Type DROP CONSTRAINT CK_Subject_Room_Type;';
IF OBJECT_ID('dbo.Subject_Room_Type', 'U') IS NULL
    EXEC sys.sp_executesql N'CREATE TABLE dbo.Subject_Room_Type (Subject_ID INT NOT NULL, Room_Type NVARCHAR(30) NOT NULL, CONSTRAINT PK_Subject_Room_Type PRIMARY KEY (Subject_ID, Room_Type), CONSTRAINT FK_Subject_Room_Type_Subject FOREIGN KEY (Subject_ID) REFERENCES dbo.Subject(Subject_ID) ON DELETE CASCADE);';

CREATE TABLE #RoomTypeHints (Room_Type NVARCHAR(30) NOT NULL, Pattern NVARCHAR(100) NOT NULL);
INSERT INTO #RoomTypeHints VALUES
(N'GYM', N'%gym%'),
(N'GYM', N'%hiit%'),
(N'GYM', N'%fitness%'),
(N'GYM', N'%thể hình%'),
(N'MULTIPURPOSE', N'%yoga%'),
(N'MULTIPURPOSE', N'%zumba%'),
(N'MULTIPURPOSE', N'%đa năng%'),
(N'MULTIPURPOSE', N'%multipurpose%'),
(N'MARTIAL_ARTS', N'%phòng võ%'),
(N'MARTIAL_ARTS', N'%võ thuật%'),
(N'MARTIAL_ARTS', N'%martial%'),
(N'PILATES', N'%pilates%'),
(N'FUNCTIONAL', N'%functional%'),
(N'FUNCTIONAL', N'%chức năng%'),
(N'BOXING', N'%boxing%'),
(N'BOXING', N'%quyền anh%'),
(N'SWIMMING_POOL', N'%bơi%'),
(N'SWIMMING_POOL', N'%swim%'),
(N'SWIMMING_POOL', N'%pool%'),
(N'FOOTBALL_COURT', N'%bóng đá%'),
(N'FOOTBALL_COURT', N'%football%'),
(N'FOOTBALL_COURT', N'%soccer%'),
(N'BASKETBALL_COURT', N'%bóng rổ%'),
(N'BASKETBALL_COURT', N'%basketball%'),
(N'BADMINTON_COURT', N'%cầu lông%'),
(N'BADMINTON_COURT', N'%badminton%'),
(N'TABLE_TENNIS_COURT', N'%bóng bàn%'),
(N'TABLE_TENNIS_COURT', N'%table tennis%'),
(N'TABLE_TENNIS_COURT', N'%ping pong%'),
(N'TENNIS_COURT', N'%tennis%'),
(N'TENNIS_COURT', N'%quần vợt%');

IF @inferRooms = 1
    EXEC sys.sp_executesql N'UPDATE r SET Room_Type = m.Room_Type FROM dbo.Room r JOIN (SELECT x.Room_ID, MIN(h.Room_Type) AS Room_Type FROM dbo.Room x JOIN #RoomTypeHints h ON x.Name COLLATE Latin1_General_100_CI_AI LIKE h.Pattern COLLATE Latin1_General_100_CI_AI AND NOT (h.Room_Type = N''TENNIS_COURT'' AND (x.Name COLLATE Latin1_General_100_CI_AI LIKE N''%table tennis%'' OR x.Name COLLATE Latin1_General_100_CI_AI LIKE N''%bóng bàn%'')) GROUP BY x.Room_ID HAVING COUNT(DISTINCT h.Room_Type) = 1) m ON m.Room_ID = r.Room_ID WHERE r.Room_Type IS NULL;';
IF @inferSubjects = 1
    EXEC sys.sp_executesql N'INSERT INTO dbo.Subject_Room_Type (Subject_ID, Room_Type) SELECT DISTINCT x.Subject_ID, h.Room_Type FROM dbo.Subject x JOIN #RoomTypeHints h ON x.Name COLLATE Latin1_General_100_CI_AI LIKE h.Pattern COLLATE Latin1_General_100_CI_AI AND NOT (h.Room_Type = N''TENNIS_COURT'' AND (x.Name COLLATE Latin1_General_100_CI_AI LIKE N''%table tennis%'' OR x.Name COLLATE Latin1_General_100_CI_AI LIKE N''%bóng bàn%''));';
DROP TABLE #RoomTypeHints;

-- Dynamic SQL also supports first-time upgrades: columns/tables above do not exist at batch compilation.
EXEC sys.sp_executesql N'
UPDATE dbo.Room SET Room_Type = N''MULTIPURPOSE'' WHERE Room_Type = N''YOGA'';
UPDATE dbo.Room SET Room_Type = N''MULTIPURPOSE''
WHERE Room_Type IS NULL AND LTRIM(RTRIM(Name)) COLLATE Latin1_General_100_CI_AI
    IN (N''Phòng Yoga'', N''Yoga'', N''Phòng Zumba'', N''Zumba'', N''Phòng Đa Năng'', N''Multipurpose'');
UPDATE dbo.Room SET Room_Type = N''MARTIAL_ARTS''
WHERE Room_Type IS NULL AND LTRIM(RTRIM(Name)) COLLATE Latin1_General_100_CI_AI
    IN (N''Phòng Võ'', N''Võ Thuật'', N''Phòng Võ Thuật'', N''Martial Arts'');

-- Preserve other subjects using the removed type, avoiding duplicate composite primary keys.
INSERT INTO dbo.Subject_Room_Type (Subject_ID, Room_Type)
SELECT legacyType.Subject_ID, N''MULTIPURPOSE'' FROM dbo.Subject_Room_Type legacyType
WHERE legacyType.Room_Type = N''YOGA''
AND NOT EXISTS (SELECT 1 FROM dbo.Subject_Room_Type t
                WHERE t.Subject_ID = legacyType.Subject_ID AND t.Room_Type = N''MULTIPURPOSE'');
DELETE FROM dbo.Subject_Room_Type WHERE Room_Type = N''YOGA'';

-- Explicit business classifications supersede name inference and earlier configuration.
DELETE t FROM dbo.Subject_Room_Type t JOIN dbo.Subject s ON s.Subject_ID = t.Subject_ID
WHERE (s.Name COLLATE Latin1_General_100_CI_AI LIKE N''%yoga%'' OR
       s.Name COLLATE Latin1_General_100_CI_AI LIKE N''%zumba%'')
AND t.Room_Type <> N''MULTIPURPOSE'';
INSERT INTO dbo.Subject_Room_Type (Subject_ID, Room_Type)
SELECT s.Subject_ID, N''MULTIPURPOSE'' FROM dbo.Subject s
WHERE (s.Name COLLATE Latin1_General_100_CI_AI LIKE N''%yoga%'' OR
       s.Name COLLATE Latin1_General_100_CI_AI LIKE N''%zumba%'')
AND NOT EXISTS (SELECT 1 FROM dbo.Subject_Room_Type t
                WHERE t.Subject_ID = s.Subject_ID AND t.Room_Type = N''MULTIPURPOSE'');

DELETE t FROM dbo.Subject_Room_Type t JOIN dbo.Subject s ON s.Subject_ID = t.Subject_ID
WHERE s.Name COLLATE Latin1_General_100_CI_AI LIKE N''%hiit%'' AND t.Room_Type <> N''GYM'';
INSERT INTO dbo.Subject_Room_Type (Subject_ID, Room_Type)
SELECT s.Subject_ID, N''GYM'' FROM dbo.Subject s
WHERE s.Name COLLATE Latin1_General_100_CI_AI LIKE N''%hiit%''
AND NOT EXISTS (SELECT 1 FROM dbo.Subject_Room_Type t WHERE t.Subject_ID = s.Subject_ID AND t.Room_Type = N''GYM'');

ALTER TABLE dbo.Room WITH CHECK ADD CONSTRAINT CK_Room_Type CHECK
    (Room_Type IS NULL OR Room_Type IN (N''GYM'', N''PILATES'', N''FUNCTIONAL'', N''BOXING'',
    N''MARTIAL_ARTS'', N''MULTIPURPOSE'', N''SWIMMING_POOL'', N''FOOTBALL_COURT'', N''BASKETBALL_COURT'',
    N''BADMINTON_COURT'', N''TABLE_TENNIS_COURT'', N''TENNIS_COURT''));
ALTER TABLE dbo.Subject_Room_Type WITH CHECK ADD CONSTRAINT CK_Subject_Room_Type CHECK
    (Room_Type IN (N''GYM'', N''PILATES'', N''FUNCTIONAL'', N''BOXING'',
    N''MARTIAL_ARTS'', N''MULTIPURPOSE'', N''SWIMMING_POOL'', N''FOOTBALL_COURT'', N''BASKETBALL_COURT'',
    N''BADMINTON_COURT'', N''TABLE_TENNIS_COURT'', N''TENNIS_COURT''));';
COMMIT TRANSACTION;

-- Show all inferred/current assignments for admin review; NULL locations must be entered manually.
EXEC sys.sp_executesql N'SELECT Room_ID, Name, Room_Type, Location, Status FROM dbo.Room ORDER BY Room_ID;';
EXEC sys.sp_executesql N'SELECT s.Subject_ID, s.Name, t.Room_Type FROM dbo.Subject s LEFT JOIN dbo.Subject_Room_Type t ON t.Subject_ID = s.Subject_ID ORDER BY s.Subject_ID, t.Room_Type;';
-- Report HIIT classes whose assigned room needs review; do not move classes automatically.
EXEC sys.sp_executesql N'SELECT c.Class_ID, c.Name, r.Room_ID, r.Name AS Room_Name, r.Room_Type
FROM dbo.Class c JOIN dbo.Subject s ON s.Subject_ID = c.Subject_ID JOIN dbo.Room r ON r.Room_ID = c.Room_ID
WHERE s.Name COLLATE Latin1_General_100_CI_AI LIKE N''%hiit%'' AND (r.Room_Type IS NULL OR r.Room_Type <> N''GYM'');';
