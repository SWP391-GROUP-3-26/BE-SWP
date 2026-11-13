# Business Rules theo chức năng — SereneDesk

Ngày đối chiếu: 03/11/2026. Cơ sở: code backend, quyền truy cập controller/security và schema/migration trong repository tại thời điểm rà soát.

Đây là bản mô tả hành vi đang được triển khai, dùng để cập nhật doc và đối chiếu với ERD mới. Không phải xác nhận rằng mọi chức năng đã được kiểm thử nghiệm thu. Những điểm code và mô tả cũ chưa thống nhất được tách ở cuối tài liệu, không tự chuyển thành yêu cầu nghiệp vụ đã được nhóm duyệt.

Quy ước: `Fxx` là mã chức năng; `BR-Fxx-yy` là mã quy tắc để tham chiếu trong flowchart, state chart và test case. CM = Center Manager; REC = Receptionist; COACH = Coach; MEM = Member. Một quy tắc có thể áp dụng cho nhiều màn hình của cùng chức năng.

## A. Tài khoản và quản lý danh mục

### F01 — Đăng ký tài khoản hội viên

Nguồn: [AuthService](../src/main/java/com/swp391/beswp/service/AuthService.java), [RegisterRequest](../src/main/java/com/swp391/beswp/dto/RegisterRequest.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F01-01 | Khách | Tài khoản tự đăng ký luôn được cấp vai trò Member; người đăng ký không được tự chọn vai trò nhân viên hoặc quản lý. |
| BR-F01-02 | Khách | Username và email phải chưa được sử dụng; kiểm tra trùng không phân biệt chữ hoa/chữ thường. Số điện thoại đăng ký cũng không được trùng. |
| BR-F01-03 | Khách | Mật khẩu phải hợp lệ và mật khẩu xác nhận phải khớp trước khi tạo tài khoản. |
| BR-F01-04 | Hệ thống | Tài khoản đăng ký thành công có trạng thái Active; mật khẩu được lưu dưới dạng đã băm. |

### F02 — Đăng nhập thông thường và Google

Nguồn: [AuthService](../src/main/java/com/swp391/beswp/service/AuthService.java), [GoogleLoginExchangeService](../src/main/java/com/swp391/beswp/service/GoogleLoginExchangeService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F02-01 | Người dùng | Đăng nhập thông thường chỉ thành công khi thông tin đăng nhập đúng, tài khoản Active và có vai trò hợp lệ. |
| BR-F02-02 | Hệ thống | Tài khoản Inactive hoặc Locked không được đăng nhập. |
| BR-F02-03 | Người dùng | Đăng nhập Google sử dụng email đã được luồng Google xác thực; nếu email đã tồn tại thì dùng tài khoản tương ứng. |
| BR-F02-04 | Hệ thống | Nếu email Google chưa tồn tại, hệ thống tạo tài khoản Member rồi thực hiện đăng nhập; đăng nhập Google không bỏ qua kiểm tra trạng thái tài khoản. |

### F03 — Xem và cập nhật hồ sơ cá nhân

Nguồn: [UserProfileService](../src/main/java/com/swp391/beswp/service/UserProfileService.java), [AvatarService](../src/main/java/com/swp391/beswp/service/AvatarService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F03-01 | Người dùng | Người dùng xem và sửa hồ sơ của chính tài khoản đăng nhập. |
| BR-F03-02 | Người dùng | Số điện thoại mới, nếu có, không được trùng với tài khoản khác. |
| BR-F03-03 | Người dùng | Chức năng sửa hồ sơ cá nhân không cho đổi vai trò, trạng thái tài khoản, username hoặc email. |
| BR-F03-04 | Người dùng | Ảnh tải lên qua chức năng avatar phải là PNG, JPEG hoặc GIF hợp lệ; tối đa 1 MB và tối đa 2048 × 2048 pixel. |

### F04 — Quản lý người dùng

Nguồn: [UserManagementController](../src/main/java/com/swp391/beswp/controller/UserManagementController.java), [UserManagementService](../src/main/java/com/swp391/beswp/service/UserManagementService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F04-01 | CM | Quản lý các tài khoản thuộc ba vai trò Receptionist, Coach và Member; chức năng này không dùng để tạo/quản lý tài khoản Center Manager. |
| BR-F04-02 | CM | Khi tạo hoặc sửa người dùng, username, email và số điện thoại nếu có phải duy nhất so với các tài khoản khác. |
| BR-F04-03 | CM | Chỉ được gán một trong ba vai trò được quản lý; trạng thái hợp lệ là Active, Inactive hoặc Locked. |
| BR-F04-04 | CM | Đặt lại mật khẩu yêu cầu mật khẩu hợp lệ và xác nhận khớp; hệ thống lưu mật khẩu mới dưới dạng đã băm. |
| BR-F04-05 | Hệ thống | Các thao tác tạo, cập nhật, đổi vai trò, đổi trạng thái và đặt lại mật khẩu được ghi nhật ký. |

### F05 — Quản lý môn thể thao

Nguồn: [SubjectService](../src/main/java/com/swp391/beswp/service/SubjectService.java), [SecurityConfig](../src/main/java/com/swp391/beswp/config/SecurityConfig.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F05-01 | CM | Được tạo và xóa môn theo quyền quản lý; tài khoản đã đăng nhập được đọc danh mục môn. |
| BR-F05-02 | CM | Tên môn khi tạo không được để trống và không được trùng tên môn đã tồn tại, không phân biệt chữ hoa/chữ thường. |
| BR-F05-03 | Hệ thống | Việc xóa môn chịu ràng buộc tham chiếu dữ liệu; không mặc định xóa kèm lớp học hoặc quyền lợi gói đang tham chiếu môn đó. |

### F06 — Tạo và tra cứu lớp học

Nguồn: [ClassService](../src/main/java/com/swp391/beswp/service/ClassService.java), [ClassSchedule](../src/main/java/com/swp391/beswp/service/ClassSchedule.java), [CreateClassRequest](../src/main/java/com/swp391/beswp/dto/CreateClassRequest.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F06-01 | CM | Tên lớp phải có giá trị và không trùng tên lớp đã tồn tại, không phân biệt chữ hoa/chữ thường. |
| BR-F06-02 | CM | Lớp phải tham chiếu môn và phòng tồn tại; người phụ trách phải có vai trò Coach. |
| BR-F06-03 | CM | Sức chứa lớp phải lớn hơn 0 và không vượt sức chứa phòng. |
| BR-F06-04 | CM | Giờ kết thúc phải sau giờ bắt đầu; khoảng giờ học phải nằm trong slot được chọn. |
| BR-F06-05 | CM | Slot hợp lệ là 07:00–09:15, 09:30–11:45, 12:30–14:45 hoặc 15:00–17:15. |
| BR-F06-06 | Hệ thống | Từ ngày bắt đầu, số tuần và các thứ học, hệ thống tính ngày kết thúc khóa và tạo các Class_Session tương ứng. |
| BR-F06-07 | CM | Khi tạo lớp chưa kết thúc/chưa hủy, không được trùng lịch phòng hoặc coach với các buổi học đang chiếm lịch. |

## B. Gói dịch vụ và gói hội viên

### F07 — Tạo và sửa cấu hình gói dịch vụ

Nguồn: [PackageService](../src/main/java/com/swp391/beswp/service/PackageService.java), [CreatePackageRequest](../src/main/java/com/swp391/beswp/dto/CreatePackageRequest.java), [UpdatePackageRequest](../src/main/java/com/swp391/beswp/dto/UpdatePackageRequest.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F07-01 | CM | Tên gói phải có giá trị và không trùng tên gói khác, không phân biệt chữ hoa/chữ thường. |
| BR-F07-02 | CM | Giá niêm yết phải lớn hơn 0; thời hạn phải lớn hơn 0; tỷ lệ chiết khấu nằm từ 0% đến 100%. |
| BR-F07-03 | CM | Trạng thái gói chỉ là Active hoặc Inactive. |
| BR-F07-04 | CM | Chỉ được đưa môn tồn tại và có trạng thái Active vào cấu hình quyền lợi gói. |
| BR-F07-05 | CM | Không được lặp môn trong danh sách môn có sẵn hoặc trong cùng một nhóm lựa chọn. |
| BR-F07-06 | CM | Mỗi nhóm lựa chọn phải có tên, số môn phải chọn lớn hơn 0 và đủ số môn để đáp ứng lựa chọn đó. |
| BR-F07-07 | Hệ thống | Quy đổi thời hạn: 12 tháng = 365 ngày; 24 tháng = 730 ngày; các thời hạn khác = số tháng × 30 ngày. |

### F08 — Xóa hoặc ngừng bán gói dịch vụ

Nguồn: [PackageService](../src/main/java/com/swp391/beswp/service/PackageService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F08-01 | CM | Không được xóa gói đã có lịch sử mua; có thể chuyển gói sang Inactive để giữ lịch sử. |
| BR-F08-02 | CM | Gói chưa có lịch sử mua có thể được xóa cùng các cấu hình môn và nhóm lựa chọn của gói. |
| BR-F08-03 | Hệ thống | Việc thay đổi cấu hình gói không được ghi đè thông tin giá, tên, thời hạn, điều khoản và môn đã lưu tại thời điểm mua của các membership cũ. |

### F09 — Member mua gói online

Nguồn: [MemberPackageService](../src/main/java/com/swp391/beswp/service/MemberPackageService.java), [MemberPackageController](../src/main/java/com/swp391/beswp/controller/MemberPackageController.java), [OnlineSubscriptionPaymentService](../src/main/java/com/swp391/beswp/service/OnlineSubscriptionPaymentService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F09-01 | MEM | Tài khoản phải Active; gói được mua phải đủ điều kiện bán theo kiểm tra của hệ thống. |
| BR-F09-02 | MEM | Nếu đang có gói Active, Member phải dùng luồng gia hạn thay vì mua mới thông thường. |
| BR-F09-03 | MEM | Phải chọn đủ mọi nhóm lựa chọn, đúng số lượng yêu cầu, không trùng và đúng môn thuộc từng nhóm. |
| BR-F09-04 | Hệ thống | Tiền giảm = giá niêm yết × tỷ lệ chiết khấu / 100; tiền phải trả = giá niêm yết − tiền giảm. Hệ thống tự tính từ dữ liệu gói. |
| BR-F09-05 | MEM | Mua gói online chỉ nhận Card qua VNPay hoặc E_Wallet qua SePay; không dùng tiền mặt ở luồng Member online. |
| BR-F09-06 | Hệ thống | Khoản thu online phải là số VND nguyên dương trong giới hạn thanh toán; không tạo giao dịch online bằng 0 dù cấu hình có chiết khấu 100%. |
| BR-F09-07 | Hệ thống | Khi tạo giao dịch online, payment và membership ở trạng thái Pending; chưa được hưởng quyền lợi chỉ vì đã tạo giao dịch. |
| BR-F09-08 | Hệ thống | Tại thời điểm mua, lưu lại giá thực trả, thời hạn, tên gói, điều khoản, môn có sẵn và môn đã chọn để bảo toàn lịch sử. |
| BR-F09-09 | MEM / REC | Không tạo thêm thanh toán gói nếu hội viên còn một thanh toán gói Pending chưa hết hạn. |

### F10 — Gia hạn gói hội viên

Nguồn: [MemberPackageService](../src/main/java/com/swp391/beswp/service/MemberPackageService.java), [OnlineSubscriptionPaymentService](../src/main/java/com/swp391/beswp/service/OnlineSubscriptionPaymentService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F10-01 | MEM / REC | Có thể chọn bắt đầu ngay (IMMEDIATE) hoặc nối tiếp sau gói hiện tại (AFTER_CURRENT), tùy tình trạng membership. |
| BR-F10-02 | Hệ thống | Gia hạn nối tiếp bắt đầu vào ngày sau End_Date của gói hiện tại; gói mới dùng giá, thời hạn và quyền lợi được kiểm tra tại thời điểm gia hạn. |
| BR-F10-03 | MEM / REC | Không được tạo thêm gói chờ nối tiếp nếu đã có một gói Pending nối tiếp đang được ghi nhận. |
| BR-F10-04 | Hệ thống | End_Date của gói mới = Start_Date + thời hạn theo ngày − 1 ngày. |
| BR-F10-05 | Hệ thống | Với gia hạn online bắt đầu ngay, chỉ chuyển gói cũ sang Expired sau khi nhận xác nhận đã thanh toán và kiểm tra đủ điều kiện. |
| BR-F10-06 | Hệ thống | Gia hạn nối tiếp đã trả tiền vẫn Pending cho đến ngày hiệu lực; thanh toán thất bại không được thay thế gói đang sử dụng. |
| BR-F10-07 | MEM / REC | Luồng gia hạn yêu cầu có gói Active hiện tại. Nếu dữ liệu đang có nhiều gói Active cùng lúc thì phải được quản lý xử lý trước khi gia hạn. |

### F11 — Đăng ký/gia hạn tại quầy và hỗ trợ hội viên

Nguồn: [ReceptionistRegistrationService](../src/main/java/com/swp391/beswp/service/ReceptionistRegistrationService.java), [ReceptionistRegistrationController](../src/main/java/com/swp391/beswp/controller/ReceptionistRegistrationController.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F11-01 | REC | Chỉ Receptionist được thực hiện các API đăng ký tại quầy; hội viên được phục vụ phải là Member đủ điều kiện của luồng tương ứng. |
| BR-F11-02 | REC | Đăng ký bằng tiền mặt được ghi nhận đã thu tiền; đăng ký bằng VNPay/SePay phải chờ xác nhận thanh toán như luồng online. |
| BR-F11-03 | REC | Giá, lựa chọn môn, tình trạng gói và điều kiện đăng ký phải được kiểm tra lại tại server; không được tự nhập số tiền để bỏ qua chính sách gói. |
| BR-F11-04 | REC | Khi gia hạn hộ hội viên, áp dụng cùng cách tính giá, thời hạn và lựa chọn bắt đầu ngay/nối tiếp của chức năng gia hạn. |

### F12 — Quản lý vòng đời và chấm dứt membership

Nguồn: [MembershipLifecycleService](../src/main/java/com/swp391/beswp/service/MembershipLifecycleService.java), [MembershipManagementService](../src/main/java/com/swp391/beswp/service/MembershipManagementService.java), [MemberPackageSubscriptionRepository](../src/main/java/com/swp391/beswp/repository/MemberPackageSubscriptionRepository.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F12-01 | Hệ thống | Gói Active có End_Date trước ngày hiện tại được chuyển sang Expired. |
| BR-F12-02 | Hệ thống | Gói Pending đủ điều kiện thanh toán, đến ngày hiệu lực và không có gói Active khác mới được kích hoạt; gói online chưa trả tiền không được tự kích hoạt. |
| BR-F12-03 | CM | Chỉ được chấm dứt membership đang Active hoặc Pending; phải nhập lý do. |
| BR-F12-04 | Hệ thống | Khi chấm dứt, chuyển membership sang Cancelled, lưu lý do, thời điểm và nhật ký; không xóa lịch sử mua gói. |

## C. Đăng ký lớp và thanh toán

### F13 — Đăng ký lớp học

Nguồn: [MemberClassBookingService](../src/main/java/com/swp391/beswp/service/MemberClassBookingService.java), [ClassReservationService](../src/main/java/com/swp391/beswp/service/ClassReservationService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F13-01 | MEM / REC | Chỉ đăng ký lớp đang Active hoặc Open, chưa bắt đầu, chưa hết thời hạn và có lịch hợp lệ. |
| BR-F13-02 | MEM / REC | Không tạo đăng ký mới nếu hội viên đã có đăng ký còn hiệu lực hoặc giữ chỗ chưa hết hạn trong cùng lớp. |
| BR-F13-03 | Hệ thống | Phải còn chỗ ở mọi buổi được đăng ký; số giữ chỗ Pending chưa hết hạn được tính khi kiểm tra sức chứa. |
| BR-F13-04 | Hệ thống | Đăng ký tạo các booking theo từng buổi của khóa trong phạm vi lịch hợp lệ; các booking cùng lần đăng ký được nhóm bằng Registration_Code. |
| BR-F13-05 | MEM / REC | Được chọn dùng gói, trả trực tiếp hoặc kết hợp dùng gói với trả phần thiếu (MIXED), theo điều kiện từng phương thức. |

### F14 — Dùng gói hoặc kết hợp thanh toán khi đăng ký lớp

Nguồn: [MemberClassBookingService](../src/main/java/com/swp391/beswp/service/MemberClassBookingService.java), [ClassBookingRepository](../src/main/java/com/swp391/beswp/repository/ClassBookingRepository.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F14-01 | MEM / REC | Dùng gói yêu cầu có membership Active hiện tại và quyền lợi môn của gói bao gồm môn của lớp. |
| BR-F14-02 | Hệ thống | Lượt còn lại được tính từ Included_Classes của gói, trừ lượt đã dùng và lượt đang giữ chỗ; số hiển thị không được âm. |
| BR-F14-03 | MEM / REC | Gói phải có hiệu lực đến buổi cuối của đăng ký; nếu chỉ dùng gói thì phải đủ lượt cho toàn bộ các buổi. |
| BR-F14-04 | MEM / REC | MIXED yêu cầu còn ít nhất một lượt nhưng không đủ toàn bộ; phần thiếu phải thanh toán qua VNPay hoặc QR SePay. |
| BR-F14-05 | Hệ thống | Tiền phần thiếu = đơn giá lớp × số buổi không được gói chi trả. Nếu lượt/số tiền khác với xác nhận người dùng vừa xem thì từ chối và yêu cầu xem lại. |

### F15 — Thanh toán online và giữ chỗ

Nguồn: [MemberClassBookingService](../src/main/java/com/swp391/beswp/service/MemberClassBookingService.java), [VnpayService](../src/main/java/com/swp391/beswp/service/VnpayService.java), [SepayService](../src/main/java/com/swp391/beswp/service/SepayService.java), [OnlineSubscriptionPaymentService](../src/main/java/com/swp391/beswp/service/OnlineSubscriptionPaymentService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F15-01 | Hệ thống | Giao dịch online có thời hạn 15 phút; đăng ký lớp online yêu cầu thời hạn này không vượt thời điểm bắt đầu buổi đầu. |
| BR-F15-02 | Hệ thống | Chỉ xác nhận thanh toán từ thông báo cổng thanh toán hợp lệ, đúng giao dịch và số tiền; không coi việc trình duyệt chuyển về trang thành công là đủ để cấp quyền lợi. |
| BR-F15-03 | Hệ thống | Khi xác nhận thanh toán lớp đúng hạn và đăng ký còn đủ điều kiện, payment chuyển Paid và các booking chờ được xác nhận. |
| BR-F15-04 | Hệ thống | Giao dịch hết hạn/thất bại không được tiếp tục giữ chỗ hoặc kích hoạt gói chưa trả tiền. |
| BR-F15-05 | Hệ thống | Nếu tiền đến sau hạn hoặc điều kiện đăng ký đã thay đổi, ghi nhận khoản thu để đối soát; không tự khôi phục quyền lợi đã không còn hợp lệ. |
| BR-F15-06 | Hệ thống | Thông báo thanh toán trùng không được làm thu nhận hoặc kích hoạt quyền lợi lặp lại. |

### F16 — Xem lịch sử gói và giao dịch

Nguồn: [MemberPackageService](../src/main/java/com/swp391/beswp/service/MemberPackageService.java), [MemberPaymentService](../src/main/java/com/swp391/beswp/service/MemberPaymentService.java), [PaymentHistoryService](../src/main/java/com/swp391/beswp/service/PaymentHistoryService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F16-01 | MEM | Member chỉ được xem gói và giao dịch thuộc chính mình. |
| BR-F16-02 | Hệ thống | Thông tin mua gói phải dùng dữ liệu đã ghi nhận tại lúc mua, thay vì tự thay toàn bộ bằng cấu hình gói mới nhất. |
| BR-F16-03 | Hệ thống | Lịch sử giao dịch phân biệt khoản thực trả, tiền giảm và tình trạng hoàn tiền; không xóa giao dịch gốc sau khi hoàn tiền. |

## D. Hủy đăng ký và hoàn tiền

### F17 — Hủy đăng ký lớp

Nguồn: [MemberClassBookingService](../src/main/java/com/swp391/beswp/service/MemberClassBookingService.java), [RefundService](../src/main/java/com/swp391/beswp/service/RefundService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F17-01 | MEM / REC | Chỉ được hủy đăng ký thuộc hội viên đang xử lý, khi lớp chưa bắt đầu và chưa kết thúc; phải còn booking tương lai có thể hủy. |
| BR-F17-02 | Hệ thống | Hủy theo nhóm cùng lần đăng ký; chỉ các booking tương lai chưa bắt đầu được hủy, không xóa lịch sử buổi đã diễn ra. |
| BR-F17-03 | Hệ thống | Khi có dữ liệu xác nhận preview, số buổi hủy và tiền hoàn phải khớp kết quả tính lại; nếu thay đổi thì yêu cầu mở lại xác nhận. |
| BR-F17-04 | Hệ thống | Payment Pending của đăng ký bị hủy chuyển Failed. Khoản đã Paid được xét tạo yêu cầu hoàn theo chính sách; dùng gói thì trả quyền sử dụng các lượt chưa dùng, không tự tạo hoàn tiền mặt cho các lượt đó. |

### F18 — Tạo và tính yêu cầu hoàn tiền

Nguồn: [RefundService](../src/main/java/com/swp391/beswp/service/RefundService.java), [RefundRequest](../src/main/java/com/swp391/beswp/entity/RefundRequest.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F18-01 | Hệ thống | Yêu cầu hoàn tiền lớp phải dựa trên khoản đã Paid và có buổi đủ điều kiện hủy; không hoàn trên khoản chưa thu. |
| BR-F18-02 | Hệ thống | Tiền hoàn dự kiến = tiền thực trả sau giảm giá × số buổi tương lai đã trả tiền bị hủy / tổng số buổi đã mua bằng khoản tiền đó; làm tròn xuống số VND nguyên. |
| BR-F18-03 | Hệ thống | Mỗi payment chỉ được có tối đa một yêu cầu hoàn tiền; lưu lại số tiền và danh sách buổi làm căn cứ xử lý. |
| BR-F18-04 | CM | Có thể mở yêu cầu xử lý cho khoản thu lớp cũ cần đối soát và có booking bị hủy; phải kiểm tra lại căn cứ số buổi được dựng từ lịch sử trước khi duyệt. |

### F19 — Duyệt hoặc từ chối hoàn tiền

Nguồn: [RefundService](../src/main/java/com/swp391/beswp/service/RefundService.java), [RefundDtos](../src/main/java/com/swp391/beswp/dto/RefundDtos.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F19-01 | CM | Chỉ được duyệt hoặc từ chối yêu cầu đang PENDING. |
| BR-F19-02 | CM | Số tiền duyệt phải lớn hơn 0 và không vượt số tiền thực thu của giao dịch. |
| BR-F19-03 | CM | Nếu tiền duyệt khác tiền dự kiến thì phải nhập lý do điều chỉnh; khi từ chối cũng phải nhập lý do. |
| BR-F19-04 | Hệ thống | Ghi người duyệt, thời điểm và kết quả xử lý; cập nhật thông báo cho Member. |

### F20 — Nhận và chi hoàn tiền tại quầy

Nguồn: [RefundService](../src/main/java/com/swp391/beswp/service/RefundService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F20-01 | MEM | Chỉ chủ yêu cầu đã APPROVED được đăng ký nhận tiền tại quầy và lấy mã nhận tiền; luồng mới chỉ hỗ trợ CASH. |
| BR-F20-02 | REC | Chỉ được xác nhận chi yêu cầu APPROVED, đã có phương thức CASH và mã nhận tiền; phải nhập mã Member cung cấp. |
| BR-F20-03 | Hệ thống | Mã phải khớp đúng yêu cầu; so sánh không phân biệt chữ hoa/thường, khoảng trắng và dấu gạch nối. Mã sai không làm chuyển trạng thái. |
| BR-F20-04 | Hệ thống | Chi thành công chuyển yêu cầu sang PAID, lưu người chi/thời điểm và tự tạo mã biên nhận; không cho xác nhận chi lại. |
| BR-F20-05 | Hệ thống | Payment gốc vẫn giữ trạng thái Paid sau hoàn; ứng dụng ghi nhận việc chi tại quầy, không thực hiện chuyển tiền online. |

## E. Coach, điểm danh và kế hoạch tập

### F21 — Xem lớp phụ trách và điểm danh

Nguồn: [CoachClassService](../src/main/java/com/swp391/beswp/service/CoachClassService.java), [CoachAttendanceService](../src/main/java/com/swp391/beswp/service/CoachAttendanceService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F21-01 | COACH | Coach phải Active và chỉ được thao tác điểm danh đối với lớp mình đang phụ trách. |
| BR-F21-02 | COACH | Không được điểm danh lớp hoặc buổi học đã hủy. |
| BR-F21-03 | COACH | Danh sách lưu phải gồm chính xác các hội viên đăng ký hợp lệ của buổi; mỗi hội viên xuất hiện một lần. |
| BR-F21-04 | COACH | Trạng thái mà chức năng hiện nhận chỉ là Present hoặc Absent. |
| BR-F21-05 | COACH | Nếu học viên có đánh giá chưa thu hồi, phải thu hồi đánh giá trước khi đổi điểm danh thành Absent. |

### F22 — Đánh giá học viên theo buổi

Nguồn: [SessionEvaluationService](../src/main/java/com/swp391/beswp/service/SessionEvaluationService.java), [SessionEvaluationDtos](../src/main/java/com/swp391/beswp/dto/SessionEvaluationDtos.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F22-01 | COACH | Chỉ coach phụ trách được đánh giá học viên có đăng ký hợp lệ trong buổi học đó. |
| BR-F22-02 | COACH | Chỉ đánh giá sau thời điểm kết thúc buổi; lớp/buổi không bị hủy và ngày buổi học phải thuộc thời hạn lớp. |
| BR-F22-03 | COACH | Học viên phải được lưu điểm danh Present trước khi được đánh giá. |
| BR-F22-04 | Hệ thống | Mỗi cặp học viên–buổi chỉ có một bản ghi đánh giá; lưu lại các lần sửa qua thông tin cập nhật và kiểm tra phiên bản. |
| BR-F22-05 | MEM | Member chỉ được đọc đánh giá của chính mình và đánh giá chưa bị thu hồi. |

### F23 — Tạo, giao và cập nhật kế hoạch tập

Nguồn: [TrainingPlanService](../src/main/java/com/swp391/beswp/service/TrainingPlanService.java), [TrainingPlanDtos](../src/main/java/com/swp391/beswp/dto/TrainingPlanDtos.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F23-01 | COACH | Chỉ coach đang phụ trách lớp được quản lý kế hoạch của lớp đó; người nhận phải đủ điều kiện đăng ký và tài khoản Active. |
| BR-F23-02 | COACH | Kế hoạch cá nhân gắn một học viên; kế hoạch cả lớp dùng scope CLASS và không chỉ định Member_ID riêng ở plan. |
| BR-F23-03 | COACH | Khoảng ngày kế hoạch phải nằm trong thời hạn lớp, ngày kết thúc không trước ngày bắt đầu và không ở quá khứ khi lưu nội dung. |
| BR-F23-04 | Hệ thống | Bản nháp chưa giao không được hiển thị cho Member; kế hoạch đã giao không được chuyển lại về bản nháp. |
| BR-F23-05 | COACH | Mỗi lớp có tối đa một kế hoạch CLASS đang ASSIGNED; mỗi học viên có tối đa một kế hoạch cá nhân ASSIGNED trong lớp. Hai loại có thể cùng tồn tại. |
| BR-F23-06 | Hệ thống | Khi giao kế hoạch CLASS lần đầu, chốt danh sách học viên đủ điều kiện lúc giao; không được giao nếu danh sách rỗng. |
| BR-F23-07 | COACH | Học viên đăng ký sau được bổ sung qua thao tác thêm người nhận; việc sửa nội dung không tự thay toàn bộ danh sách đã chốt. |
| BR-F23-08 | COACH | Không được đổi lớp, loại kế hoạch hoặc học viên cá nhân của kế hoạch đã tạo; không được xóa bài tập đã có kết quả. |

### F24 — Ghi kết quả tập và xem tiến độ

Nguồn: [TrainingPlanService](../src/main/java/com/swp391/beswp/service/TrainingPlanService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F24-01 | MEM | Chỉ người nhận được giao kế hoạch được ghi kết quả của chính mình; bài tập phải thuộc đúng kế hoạch. |
| BR-F24-02 | MEM | Chỉ ghi kết quả khi plan ASSIGNED, ngày hiện tại nằm trong khoảng kế hoạch và đăng ký/lớp vẫn đủ điều kiện. |
| BR-F24-03 | Hệ thống | Kế hoạch COMPLETED/CANCELLED, lớp không còn hoạt động hoặc người nhận không còn đăng ký hợp lệ được giữ để đọc lịch sử nhưng không nhận kết quả mới. |
| BR-F24-04 | Hệ thống | Kế hoạch CLASS lưu kết quả và trạng thái đọc riêng theo từng người nhận; Member không được xem kết quả hay phản hồi riêng của người khác. |
| BR-F24-05 | Hệ thống | Khi nội dung được giao/cập nhật, phiên bản công bố tăng; đọc phiên bản cũ không tự đánh dấu đã đọc phiên bản mới. |
| BR-F24-06 | COACH | Coach có thể nhận xét riêng từng người nhận của kế hoạch CLASS đang ASSIGNED và đủ điều kiện chỉnh sửa. |

## F. Trao đổi và quy tắc bảo toàn dữ liệu

### F25 — Chat Member–Coach

Nguồn: [ChatService](../src/main/java/com/swp391/beswp/service/ChatService.java), [ChatMediaService](../src/main/java/com/swp391/beswp/service/ChatMediaService.java), [ClassBookingRepository](../src/main/java/com/swp391/beswp/repository/ClassBookingRepository.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F25-01 | MEM / COACH | Chức năng chat dành cho tài khoản Active có vai trò Member hoặc Coach; hai bên trao đổi phải thuộc hai vai trò đối ứng. |
| BR-F25-02 | MEM / COACH | Gửi tin yêu cầu hai bên có booking Booked trong lớp do coach đó phụ trách, lớp Active/Open và chưa hết thời hạn. |
| BR-F25-03 | Hệ thống | Mỗi cặp Member–Coach có tối đa một hội thoại; mỗi tin nhắn thuộc một hội thoại. |
| BR-F25-04 | MEM / COACH | Chỉ người tham gia hội thoại được đọc tin và thao tác trạng thái đọc trong hội thoại đó. |
| BR-F25-05 | MEM / COACH | Tin chữ không được rỗng; tin ảnh phải dùng ảnh đã tải qua chức năng upload của hệ thống. |
| BR-F25-06 | MEM / COACH | Ảnh chat không vượt quá 5 MB và phải đúng định dạng ảnh hệ thống hỗ trợ. |

### F26 — Bảo toàn lịch sử và xử lý cập nhật đồng thời

Nguồn: [RefundService](../src/main/java/com/swp391/beswp/service/RefundService.java), [TrainingPlanService](../src/main/java/com/swp391/beswp/service/TrainingPlanService.java), [SessionEvaluationService](../src/main/java/com/swp391/beswp/service/SessionEvaluationService.java), [AuditLogController](../src/main/java/com/swp391/beswp/controller/AuditLogController.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F26-01 | Người dùng | Khi cập nhật kế hoạch, đánh giá hoặc hoàn tiền có kiểm tra phiên bản, dữ liệu gửi phải dựa trên phiên bản hiện tại; nếu đã có người khác cập nhật thì phải tải lại trước khi lưu. |
| BR-F26-02 | Hệ thống | Giữ lịch sử giao dịch, đăng ký đã hủy, kế hoạch và kết quả cũ theo các chức năng tương ứng; không coi thao tác hủy/ngừng là xóa toàn bộ dữ liệu liên quan. |
| BR-F26-03 | CM | Được xem nhật ký hoạt động theo quyền quản lý; Member không được dùng chức năng nhật ký quản trị để đọc thao tác của người khác. |

### F27 — Lễ tân tra cứu và tạo hội viên

Nguồn: [ReceptionistMemberController](../src/main/java/com/swp391/beswp/controller/ReceptionistMemberController.java), [MemberService](../src/main/java/com/swp391/beswp/service/MemberService.java), [AuthService](../src/main/java/com/swp391/beswp/service/AuthService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F27-01 | REC | Chỉ Receptionist được dùng chức năng tra cứu và tạo hội viên tại quầy. |
| BR-F27-02 | REC | Khi tạo tài khoản hộ khách, áp dụng cùng quy tắc đăng ký F01; tài khoản được tạo là Member, không cho chọn vai trò nhân viên. |
| BR-F27-03 | Hệ thống | Tra cứu chi tiết chỉ trả tài khoản có vai trò Member; ID không tồn tại hoặc thuộc vai trò khác đều không trả hồ sơ hội viên. |
| BR-F27-04 | Hệ thống | Tìm kiếm không có từ khóa trả danh sách rỗng, không tự liệt kê toàn bộ hội viên. |

### F28 — Dashboard quản lý

Nguồn: [CenterManagerDashboardController](../src/main/java/com/swp391/beswp/controller/CenterManagerDashboardController.java), [CenterManagerDashboardService](../src/main/java/com/swp391/beswp/service/CenterManagerDashboardService.java).

| Mã BR | Người thực hiện | Business Rule |
|---|---|---|
| BR-F28-01 | CM | Dashboard quản lý cung cấp các chỉ số tổng quan, chuỗi doanh thu, phân bố trạng thái lớp/gói hội viên và hoạt động gần đây theo quyền quản lý. |
| BR-F28-02 | Hệ thống | Ngày báo cáo và tháng hiện tại được xác định theo múi giờ Asia/Ho_Chi_Minh. |
| BR-F28-03 | CM | Phạm vi xem doanh thu theo tháng từ 1 đến 24 tháng; số hoạt động gần đây từ 1 đến 100 bản ghi. |

## G. Điểm cần nhóm chốt trước khi nộp bản chính thức

| Vấn đề | Hiện trạng đã thấy | Cách dùng trong doc |
|---|---|---|
| Included_Classes | Entity gọi đây là metadata cũ, nhưng MemberClassBookingService vẫn đọc để tính lượt đăng ký bằng gói. | BR-F14-02 mô tả đúng điều kiện đang chạy. Nếu nhóm muốn bỏ cơ chế lượt thì cần đổi code rồi cập nhật BR/ERD. |
| Điểm danh | DDL cho phép thêm Late/Excused, nhưng service hiện chỉ nhận Present/Absent. | Mô tả chức năng hiện tại theo BR-F21-04; không quảng bá Late/Excused là chức năng đã làm xong. |
| Hủy lớp sau khi đã bắt đầu | Một số comment/mô tả API cũ gợi ý hủy khi đang diễn ra; service chặn nếu lớp đã bắt đầu. | Dùng BR-F17-01 cho bản mô tả hiện trạng. Nếu nghiệp vụ mong muốn hủy các buổi còn lại sau khi khóa bắt đầu thì cần nhóm chốt và sửa code. |
| Chuyển khoản hoàn tiền | DB còn cột ngân hàng và giá trị BANK để giữ lịch sử; luồng nhận tiền mới chỉ hỗ trợ CASH. | Mô tả chức năng nhận tại quầy theo F20; không ghi hệ thống tự chuyển tiền ngân hàng. |
| Vai trò Admin | Một số nhánh security còn ROLE_Admin tương thích cũ; các service/controller có thể kiểm tra vai trò chặt hơn. | Actor chính trong tài liệu dùng bốn vai trò sản phẩm. Không suy ra Admin được làm mọi việc từ SecurityConfig. |
| Schedule/Voucher và quản lý phòng | Có cấu trúc dữ liệu không đồng nghĩa đã có đầy đủ màn hình/nghiệp vụ. | Không thêm chức năng quản lý riêng chỉ dựa vào ERD. |
| Dashboard, tìm kiếm, lọc, xuất dữ liệu | Đây là chức năng đọc/hiển thị; công thức chỉ tiêu và quyền xuất cần đối chiếu riêng khi đưa vào phạm vi chi tiết. | Tài liệu này chưa đặc tả công thức thống kê, bộ lọc hoặc định dạng file xuất. |
| Chạy thật và nghiệm thu | Bản này được đối chiếu tĩnh với repository, chưa chạy lại toàn bộ test hoặc xác nhận schema DB thật. | Nhóm xác nhận phạm vi hoàn thành trước khi gắn nhãn Final/Approved. |

## H. Cách đưa vào tài liệu môn học

1. Dùng tên F01–F28 làm danh mục chức năng; đổi mã theo mẫu nhóm nếu cần, nhưng giữ mapping BR để truy vết. F26 là nhóm quy tắc dùng chung thay vì một màn hình riêng.
2. Copy các bảng quy tắc vào mục Business Rules. Nếu mẫu chỉ có hai cột Function / Rules, gộp các BR cùng Fxx vào một hàng, giữ mã BR trong nội dung.
3. Trong flowchart/swimlane, gắn mã BR vào các nhánh kiểm tra; ví dụ nhánh “Còn chỗ?” tham chiếu BR-F13-03.
4. Trong state chart, dùng BR cho điều kiện chuyển trạng thái; ví dụ Pending → Active của membership theo F09, F10 và F12.
5. Sau khi nhóm chốt các điểm mục G, cập nhật cả code, BR, ERD và test case liên quan để các tài liệu thống nhất.
