-- All memberships now grant sport access for their term, regardless of Included_Classes.
-- Keep the legacy column and paid history. Run before deploying the new backend.
-- Cancel outstanding MIXED registrations, including their seat reservations.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

SELECT DISTINCT b.Registration_Code
INTO #PendingMixedRegistrations
FROM dbo.Class_Booking b
WHERE b.Registration_Code IS NOT NULL AND b.Status = N'Pending'
  AND EXISTS (SELECT 1 FROM dbo.Class_Booking p
              WHERE p.Registration_Code = b.Registration_Code AND p.Payment_Method = N'PACKAGE')
  AND EXISTS (SELECT 1 FROM dbo.Class_Booking d
              WHERE d.Registration_Code = b.Registration_Code AND d.Payment_Method = N'DIRECT');

UPDATE p SET p.Status = N'Failed'
FROM dbo.Payment p
JOIN dbo.Class_Booking b ON b.Booking_ID = p.Booking_ID
JOIN #PendingMixedRegistrations m ON m.Registration_Code = b.Registration_Code
WHERE p.Status = N'Pending';

UPDATE b SET b.Status = N'Cancelled'
FROM dbo.Class_Booking b
JOIN #PendingMixedRegistrations m ON m.Registration_Code = b.Registration_Code
WHERE b.Status = N'Pending';

DROP TABLE #PendingMixedRegistrations;
COMMIT TRANSACTION;
