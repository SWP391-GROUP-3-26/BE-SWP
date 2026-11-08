# Bảo lưu Membership Package

## Quy tắc

- Admin cấu hình `freezeMonths`: tổng số tháng lịch được bảo lưu; `0` tắt chức năng. Chỉ gói có thời hạn từ 6 tháng (180 ngày theo quy đổi hiện tại) mới được bật.
- Mỗi lần mua/gia hạn lưu `Freeze_Months_At_Purchase` và điều khoản theo thời điểm mua. Sửa package sau này không đổi quyền đã mua.
- Subscription có trước migration nhận hạn mức `0`, không được cấp hồi tố.
- Member tự bảo lưu subscription Active đang hiệu lực của chính mình. Mỗi yêu cầu là số tháng nguyên từ 1 trở lên, không vượt phần còn lại.
- Bắt đầu ngay lúc xác nhận theo múi giờ nghiệp vụ UTC+7; kết thúc ở cùng giờ sau số tháng lịch đã chọn. Ngày không tồn tại được chốt vào cuối tháng, ví dụ 31/1 → 28/2 ở năm không nhuận.
- Trừ toàn bộ số tháng đã chọn ngay khi xác nhận. Tham gia lại sớm không hoàn hạn mức.
- Không cho bảo lưu nếu có buổi học đã đăng ký giao với khoảng bảo lưu. Xét cả Booked và Pending còn giữ chỗ, mọi hình thức thanh toán; bỏ buổi/lớp đã hủy, đóng, hoặc buổi ngoài thời hạn lớp. Một buổi bắt đầu đúng thời điểm trở lại không giao khoảng bảo lưu.
- Subscription chuyển `Active → Frozen`; không được sử dụng quyền môn. Đăng ký lớp mới không được có buổi học trong khoảng bảo lưu, kể cả DIRECT. Lớp DIRECT sau khoảng bảo lưu vẫn có thể đăng ký theo các điều kiện khác.
- Kết quả tập luyện của kế hoạch lớp không được ghi thêm khi membership Frozen. Đăng nhập và tra cứu dữ liệu không bị khóa.
- Cần hoàn tất hoặc hủy giao dịch mua/gia hạn gói còn đang chờ thanh toán trước khi bảo lưu. Cần tham gia lại trước khi mua/gia hạn gói khác.

## Tham gia lại và ngày hết hạn

- Member chọn tham gia lại hoặc hệ thống tự xử lý khi hết bảo lưu (tác vụ mỗi 60 giây). GET trạng thái bảo lưu cũng xử lý nếu đã đến hạn.
- Số ngày cộng thêm = số khoảng đủ 24 giờ từ bắt đầu đến thời điểm trở lại, không vượt mốc kết thúc đã đăng ký. Không làm tròn lên.
- Ví dụ 01/7 15:00 → 02/7 09:00 cộng 0 ngày; trở lại sau đúng 10 ngày cộng 10 ngày.
- Ngày hết hạn được chốt khi kết thúc bảo lưu. Khi đang Frozen, trường endDate vẫn là ngày trước khi cộng phần bảo lưu hiện tại.
- Gói gia hạn Pending đã Paid và không cần đối soát được dời cả ngày bắt đầu/kết thúc bằng đúng số ngày cộng thêm. Không kích hoạt hoặc cho hết hạn các gói chờ đó trong khi còn Frozen.
- Nếu sau khi cộng gói vẫn đã hết hạn thì trả về Expired; không hồi sinh gói quá hạn. Quản lý có thể chấm dứt subscription Frozen như các gói khác; tác vụ không kích hoạt lại gói đã bị chấm dứt.
- Ghi audit khi bảo lưu/tham gia lại, gồm thời điểm thực tế, thời lượng yêu cầu và số ngày được cộng. Không tạo thanh toán cho thao tác bảo lưu.

## API

Admin dùng API package hiện có, bổ sung field:

```json
{"durationMonths": 6, "freezeMonths": 2}
```

Response package/catalog có `freezeMonths`, `freezeTerms`. Thông tin subscription có `totalFreezeMonths`,
`usedFreezeMonths`, `remainingFreezeMonths`, `freezeStartedAt`, `freezeEndsAt`, `freezeTerms`; bộ lọc lịch sử chấp nhận `Frozen`.

| Method | URL | Ý nghĩa |
| --- | --- | --- |
| GET | `/api/member/packages/subscriptions/{subscriptionId}/freeze` | Trạng thái, hạn mức còn lại và thời điểm bảo lưu |
| POST | `/api/member/packages/subscriptions/{subscriptionId}/freeze` | Bảo lưu, body `{"months":1}` |
| POST | `/api/member/packages/subscriptions/{subscriptionId}/resume` | Tham gia lại, không cần body |

API dành cho Member đang hoạt động và subscription thuộc Member đó. Lỗi điều kiện nghiệp vụ trả 409; body tháng không hợp lệ trả 400; sai quyền hoặc không sở hữu không thay đổi dữ liệu.
Response thao tác có `extendedDays`; đây là số ngày vừa được cộng bởi thao tác đó, không phải hạn mức còn lại.

## Điều khoản

Trong thời gian bảo lưu, toàn bộ quyền lợi của gói, bao gồm tập luyện với HLV, được tạm dừng.
Mọi thỏa thuận tập luyện riêng bên ngoài giữa Member và HLV không thuộc quyền lợi của gói và không được hệ thống ghi nhận hoặc bảo đảm.
Quyền bảo lưu đã chọn không được hoàn lại khi tham gia lại sớm. Ngày hết hạn chỉ được cộng số ngày bảo lưu thực tế đủ 24 giờ.

## Database và kiểm tra

Áp dụng các migration membership/payment hiện có, sau đó chạy theo thứ tự:

1. `docs/database/migrations/20261108_membership_freeze_configuration.sql`
2. `docs/database/migrations/20261108_membership_freeze_lifecycle.sql`

Không chỉnh sửa các migration cũ. SQL tạo mới đã bổ sung cấu hình, snapshot, bộ đếm và mốc thời gian bảo lưu.
Chưa xác nhận các script này trên SQL Server thực tế chỉ bằng việc chạy unit test.

```powershell
.\mvnw.cmd test "-Dtest=MembershipFreezeControllerTest,MembershipFreezeServiceTest,PackageFreezeConfigurationTest,MemberRegistrationScheduleTest,MemberClassBookingServiceTest,ClassRegistrationFlowTest,ReceptionistRegistrationServiceTest,MemberPackageOnlinePaymentServiceTest,MembershipLifecycleServiceTest,OnlineSubscriptionPaymentServiceTest,MemberPaymentServiceTest,SepayServiceTest,BookingRepositoryQueryTest,UnlimitedMembershipResponseTest"
```

FE chưa được cập nhật trong thay đổi BE này.
