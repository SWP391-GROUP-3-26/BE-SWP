# Membership theo thời hạn và quyền môn

Áp dụng cho tất cả gói và subscription cũ/mới, không giới hạn số buổi.

- Mua gói lưu Included Sports và các môn đã chọn trong Choice Groups theo snapshot.
- Mua gói không tự xếp lớp. Member hoặc lễ tân đăng ký một lớp cụ thể.
- PACKAGE đăng ký toàn khóa bằng quyền lợi gói: subscription đang hiệu lực, đúng môn và có hiệu lực đến buổi cuối.
- Không kiểm tra, giữ, trừ hoặc hoàn lượt gói. Field entity và các truy vấn đếm hạn mức đã được xóa. Migration mới xóa cột `Included_Classes` khỏi database.
- Giữ điều kiện lớp đang mở, chưa bắt đầu, còn chỗ ở tất cả buổi và không đăng ký trùng chính lớp đó.
- Mỗi môn chỉ đăng ký một lớp trong các khoảng thời hạn khóa học giao nhau (tính cả ngày bắt đầu và kết thúc). Có thể đặt lớp tiếp theo bắt đầu sau ngày kết thúc lớp trước.
- Không được đăng ký hai lớp có buổi học giao nhau trên cùng ngày. Hai buổi nối tiếp nhau không bị coi là trùng.
- Xét cả booking Booked và Pending còn hạn giữ chỗ; không xét booking đã hủy/hoàn thành, hold hết hạn, lớp đóng và buổi đã hủy/đã kết thúc.
- Cả Member và lễ tân kiểm tra lại trong transaction giữ khóa ghi của Member trước khi tạo booking, áp dụng cho PACKAGE lẫn DIRECT. Preview chỉ để tham khảo.
- Có thể học lớp tiếp theo bằng cùng gói nếu đáp ứng các điều kiện trên. Không thêm quy tắc trình độ đầu vào.
- DIRECT vẫn thanh toán toàn bộ khóa như trước. Không tạo mới MIXED, kể cả checkout có voucher.

## Chuyển đổi dữ liệu

Chạy `docs/database/migrations/20261108_unlimited_membership.sql` trên SQL Server trước khi triển khai BE.
Sau đó chạy `docs/database/migrations/20261108_drop_membership_session_quota.sql` để xóa cột hạn mức và các DEFAULT/CHECK constraint liên quan. Migration không thay đổi booking, payment hay snapshot quyền môn; có thể chạy lại. Dữ liệu hạn mức cũ bị xóa vĩnh viễn.

Script `20261108_unlimited_membership.sql` hủy các booking Pending của nhóm có cả PACKAGE và DIRECT, chuyển payment Pending của nhóm thành Failed.
Không đổi payment Paid hoặc booking Booked/Completed. Script có thể chạy lại.

BE cũng vô hiệu hóa các nhóm MIXED Pending trước khi tiếp tục thanh toán hoặc xử lý callback.
Nếu tiền về sau khi hủy: giữ booking Cancelled, ghi nhận tiền Paid và requiresReview để đối soát;
không tự xác nhận lại lớp. Lịch sử MIXED đã thanh toán và cách tính hoàn phần tiền trực tiếp được giữ.

## Thay đổi response API

Giữ URL endpoint. Bỏ các trường hạn mức và phần chia lượt không còn sử dụng:

| Response | Field đã bỏ |
| --- | --- |
| PackageData, MemberPackageSubscriptionData, danh mục gói lễ tân | includedClasses |
| MemberResponse.PackageData | includedClasses, remainingClasses |
| MemberSubscriptionInfoResponse | totalSessions, usedSessions, heldSessions, remainingSessions |
| ClassConfirmationDetailResponse.packageOption | remainingSessions, requiredSessions, packageSessions, payableSessions, additionalAmount, mixedEligible |
| MemberClassBookingResponse | remainingPackageSessions |
| MyClassItemResponse | heldPackageSessions |
| ClassRegistrationPreview của lễ tân | totalPackageClasses, usedPackageClasses, remainingPackageClasses, requiredPackageClasses |
| ClassRegistrationResponse của lễ tân | remainingPackageClasses |
| CancellationPreview | packageSessionsReturned |

Các số lượng buổi thực tế của lớp (`sessionCount`, `sessions`) vẫn giữ.
`packageSessions` và `payableSessions` trong chi tiết payment cũ vẫn mô tả phần thanh toán đã ghi, không phải hạn mức.
Request đăng ký lớp chỉ còn PACKAGE hoặc DIRECT; bỏ các field chia MIXED `packageSessions`, `paymentAmount`.
FE chưa được cập nhật trong thay đổi này; bên tích hợp cần đọc `packageOption.eligible` và bỏ giao diện hạn mức/MIXED.

## Kiểm tra

```powershell
.\mvnw.cmd test "-Dtest=ClassRegistrationFlowTest,MemberRegistrationScheduleTest,MemberClassBookingServiceTest,ReceptionistRegistrationServiceTest,MemberServiceTest,UnlimitedMembershipResponseTest,RefundServiceTest,BookingRepositoryQueryTest,SepayServiceTest,MemberPaymentServiceTest,MemberPackageOnlinePaymentServiceTest,MembershipLifecycleServiceTest,OnlineSubscriptionPaymentServiceTest,VnpayServiceTest"
```

Test không dùng SQL Server bao phủ gói không có hạn mức vẫn học toàn khóa, quyền môn có sẵn/tự chọn,
thời hạn tới buổi cuối, đăng ký khóa tiếp theo, chặn MIXED mới, hủy MIXED cũ,
callback tiền về muộn, lịch sử đã thanh toán và response không còn hạn mức.
Các test SQL Server yêu cầu môi trường riêng; chạy unit test không đồng nghĩa migration đã được áp dụng.

Các file ERD trong `docs/erd` là tài liệu tham chiếu trước thay đổi, chưa được vẽ lại; cột hạn mức trong ERD cũ không còn thuộc schema hiện tại. Các migration lịch sử được giữ nguyên.
