-- Run after 20261108_unlimited_membership.sql and before deploying the updated backend.
-- Removes the obsolete package allowance; class bookings and payments are unchanged.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

DECLARE @packageTableId INT = OBJECT_ID(N'dbo.Membership_Package', N'U');
DECLARE @quotaColumnId INT = COLUMNPROPERTY(@packageTableId, N'Included_Classes', 'ColumnId');

IF @quotaColumnId IS NOT NULL
BEGIN
    -- Discover constraint names so this also handles automatically named defaults.
    DECLARE @dropConstraints NVARCHAR(MAX) = N'';
    SELECT @dropConstraints = @dropConstraints
        + N'ALTER TABLE dbo.Membership_Package DROP CONSTRAINT ' + QUOTENAME(dc.name) + N';'
    FROM sys.default_constraints dc
    WHERE dc.parent_object_id = @packageTableId AND dc.parent_column_id = @quotaColumnId;

    SELECT @dropConstraints = @dropConstraints
        + N'ALTER TABLE dbo.Membership_Package DROP CONSTRAINT ' + QUOTENAME(cc.name) + N';'
    FROM sys.check_constraints cc
    WHERE cc.parent_object_id = @packageTableId
      AND (cc.name = N'CK_Package_Classes' OR cc.parent_column_id = @quotaColumnId OR EXISTS (
          SELECT 1 FROM sys.sql_expression_dependencies dep
          WHERE dep.referencing_id = cc.object_id AND dep.referenced_id = @packageTableId
            AND dep.referenced_minor_id = @quotaColumnId));

    IF @dropConstraints <> N'' EXEC sys.sp_executesql @dropConstraints;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.Membership_Package DROP COLUMN Included_Classes;';
END;

COMMIT TRANSACTION;
