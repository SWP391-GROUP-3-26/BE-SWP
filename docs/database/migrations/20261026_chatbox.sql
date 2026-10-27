SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.Chat_Conversation', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Chat_Conversation
    (
        Conversation_ID INT IDENTITY(1,1) NOT NULL,
        Member_ID       INT NOT NULL,
        Coach_ID        INT NOT NULL,
        Class_ID        INT NULL,
        Last_Message    NVARCHAR(1000) NULL,
        Last_Message_At DATETIME2(7) NULL,
        Created_At      DATETIME2(7) NOT NULL
            CONSTRAINT DF_Chat_Conversation_Created_At DEFAULT SYSDATETIME(),

        CONSTRAINT PK_Chat_Conversation PRIMARY KEY (Conversation_ID),
        CONSTRAINT UQ_Chat_Conversation_Member_Coach UNIQUE (Member_ID, Coach_ID),
        CONSTRAINT FK_Chat_Conversation_Member FOREIGN KEY (Member_ID)
            REFERENCES dbo.[User](User_ID),
        CONSTRAINT FK_Chat_Conversation_Coach FOREIGN KEY (Coach_ID)
            REFERENCES dbo.[User](User_ID),
        CONSTRAINT FK_Chat_Conversation_Class FOREIGN KEY (Class_ID)
            REFERENCES dbo.[Class](Class_ID),
        CONSTRAINT CK_Chat_Conversation_Different_Users CHECK (Member_ID <> Coach_ID)
    );
END;

IF OBJECT_ID(N'dbo.Chat_Message', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Chat_Message
    (
        Message_ID     BIGINT IDENTITY(1,1) NOT NULL,
        Conversation_ID INT NOT NULL,
        Sender_ID      INT NOT NULL,
        Receiver_ID    INT NOT NULL,
        Content        NVARCHAR(MAX) NULL,
        Message_Type   VARCHAR(20) NOT NULL,
        Media_URL      VARCHAR(500) NULL,
        Is_Read        BIT NOT NULL
            CONSTRAINT DF_Chat_Message_Is_Read DEFAULT 0,
        Created_At     DATETIME2(7) NOT NULL
            CONSTRAINT DF_Chat_Message_Created_At DEFAULT SYSDATETIME(),

        CONSTRAINT PK_Chat_Message PRIMARY KEY (Message_ID),
        CONSTRAINT FK_Chat_Message_Conversation FOREIGN KEY (Conversation_ID)
            REFERENCES dbo.Chat_Conversation(Conversation_ID),
        CONSTRAINT FK_Chat_Message_Sender FOREIGN KEY (Sender_ID)
            REFERENCES dbo.[User](User_ID),
        CONSTRAINT FK_Chat_Message_Receiver FOREIGN KEY (Receiver_ID)
            REFERENCES dbo.[User](User_ID),
        CONSTRAINT CK_Chat_Message_Different_Users CHECK (Sender_ID <> Receiver_ID),
        CONSTRAINT CK_Chat_Message_Type CHECK (Message_Type IN ('TEXT', 'IMAGE', 'SYSTEM')),
        CONSTRAINT CK_Chat_Message_Payload CHECK
        (
            (Message_Type = 'TEXT' AND Content IS NOT NULL AND LEN(LTRIM(RTRIM(Content))) > 0)
            OR (Message_Type = 'IMAGE' AND Media_URL IS NOT NULL)
            OR Message_Type = 'SYSTEM'
        )
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_Chat_Conversation_Member_LastMessage'
      AND object_id = OBJECT_ID(N'dbo.Chat_Conversation')
)
BEGIN
    CREATE INDEX IX_Chat_Conversation_Member_LastMessage
        ON dbo.Chat_Conversation(Member_ID, Last_Message_At DESC);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_Chat_Conversation_Coach_LastMessage'
      AND object_id = OBJECT_ID(N'dbo.Chat_Conversation')
)
BEGIN
    CREATE INDEX IX_Chat_Conversation_Coach_LastMessage
        ON dbo.Chat_Conversation(Coach_ID, Last_Message_At DESC);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_Chat_Message_Conversation_CreatedAt'
      AND object_id = OBJECT_ID(N'dbo.Chat_Message')
)
BEGIN
    CREATE INDEX IX_Chat_Message_Conversation_CreatedAt
        ON dbo.Chat_Message(Conversation_ID, Created_At DESC, Message_ID DESC);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_Chat_Message_Receiver_Unread'
      AND object_id = OBJECT_ID(N'dbo.Chat_Message')
)
BEGIN
    CREATE INDEX IX_Chat_Message_Receiver_Unread
        ON dbo.Chat_Message(Receiver_ID, Conversation_ID)
        WHERE Is_Read = 0;
END;

COMMIT TRANSACTION;
