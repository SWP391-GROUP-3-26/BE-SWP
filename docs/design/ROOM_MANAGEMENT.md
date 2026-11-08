# Quản lý phòng/sân (BE)

Admin và Center Manager được quản lý phòng. Mỗi phòng có tên duy nhất, một loại cố định, sức chứa dương, vị trí và trạng thái. Không có thay đổi FE.

## Danh mục

| Mã API | Tên hiển thị |
| --- | --- |
| GYM | Phòng Gym |
| PILATES | Phòng Pilates |
| FUNCTIONAL | Phòng Functional |
| BOXING | Phòng Boxing |
| MARTIAL_ARTS | Phòng Võ |
| MULTIPURPOSE | Phòng Đa Năng |
| SWIMMING_POOL | Bể Bơi |
| FOOTBALL_COURT | Sân Bóng Đá |
| BASKETBALL_COURT | Sân Bóng Rổ |
| BADMINTON_COURT | Sân Cầu Lông |
| TABLE_TENNIS_COURT | Sân Bóng Bàn |
| TENNIS_COURT | Sân Tennis |

Trạng thái: `Active` (Hoạt động), `Maintenance` (Bảo trì), `Inactive` (Ngừng sử dụng).

## API

Các response phòng dùng `{success, message, data}`.

| Method | Đường dẫn | Công dụng |
| --- | --- | --- |
| GET | /api/rooms/options | Danh mục loại và trạng thái |
| GET | /api/rooms?search=&status=&type= | Danh sách; status có thể bỏ qua hoặc all |
| GET | /api/rooms/{id} | Chi tiết kèm upcomingClasses cần xử lý |
| POST | /api/rooms | Tạo phòng, HTTP 201 |
| PUT | /api/rooms/{id} | Cập nhật đầy đủ |
| PUT | /api/rooms/{id}/status | Đổi trạng thái |
| DELETE | /api/rooms/{id} | Chỉ xóa phòng chưa từng được lớp tham chiếu |
| PUT | /api/subjects/{id}/room-types | Cấu hình một hoặc nhiều loại phù hợp của môn |

Body tạo/cập nhật phòng:

```json
{"name":"Yoga 01","type":"MULTIPURPOSE","maxCapacity":20,"location":"Tầng 2","status":"Active"}
```

Body đổi trạng thái: `{"status":"Maintenance"}`.

Body cấu hình môn: `{"allowedRoomTypes":["GYM","FUNCTIONAL"]}`. POST tạo môn cũng bắt buộc có `allowedRoomTypes`; không nhận danh sách rỗng, trùng hoặc mã ngoài danh mục. Response môn và options tạo lớp bổ sung danh sách này. Options phòng khi tạo lớp chỉ trả phòng Active đã phân loại, kèm loại và vị trí; BE tiếp tục kiểm tra tương thích khi tạo lớp.

## Ràng buộc

- Không cho chuyển sang bảo trì/ngừng sử dụng nếu phòng có lớp chưa hủy/hoàn tất với buổi Scheduled chưa kết thúc. Phản hồi 409 nêu mã và tên lớp để người quản lý xử lý trước. Lớp cũ chưa sinh buổi được kiểm tra theo ngày/giờ của lớp.
- Không giảm sức chứa dưới sức chứa cấu hình của lớp còn sử dụng phòng; không đổi sang loại không phù hợp với môn của các lớp đó.
- Không gỡ loại phòng khỏi cấu hình môn nếu còn lớp sắp học/đang học sử dụng loại đó.
- Tạo lớp chỉ nhận phòng Active, đã có loại, thuộc danh sách cho phép của môn; vẫn giữ kiểm tra sức chứa, lịch HLV và trùng giờ phòng hiện có.
- Các thao tác cấu hình và tạo lớp khóa dữ liệu trong transaction để tránh đọc cấu hình cũ khi xử lý đồng thời. Nếu danh sách môn của phòng vừa thay đổi trong lúc cập nhật, trả 409 và yêu cầu tải lại.

## CSDL và chuyển dữ liệu cũ

Với CSDL đang chạy, áp dụng `docs/database/migrations/20261108_room_management.sql` trước khi khởi động BE (`ddl-auto=validate`). Script thêm `Room_Type`, `Location`, `Status` và bảng liên kết `Subject_Room_Type`.

Lần đầu thêm schema, script suy đoán loại từ tên phòng và tên môn theo từ khóa Việt/Anh. Phòng khớp nhiều loại hoặc không rõ giữ NULL; môn có thể khớp nhiều loại. Không suy đoán vị trí. Không sửa lớp/booking hiện có. Khi schema đã tồn tại, script không chạy lại suy đoán tổng quát; vẫn áp dụng các điều chỉnh nghiệp vụ Yoga/Zumba/HIIT bên dưới.

Admin phải xem hai result set cuối script, sửa loại phòng/môn chưa đúng và điền vị trí. Phòng chưa phân loại hoặc môn chưa có loại phù hợp không thể dùng tạo lớp mới. Dữ liệu mẫu cho CSDL mới cũng để trống các trường chưa đủ thông tin thay vì tự gán loại chung.

Yoga và Zumba thuộc `MULTIPURPOSE` (Phòng Đa Năng); HIIT thuộc `GYM` (Phòng Gym). Danh mục hiện có 12 loại, không còn mã `YOGA`; API từ chối mã này với HTTP 400.

Chỉ cần chạy **một file** `docs/database/migrations/20261108_room_management.sql`, đã bao gồm toàn bộ thay đổi loại phòng. File hỗ trợ CSDL chưa có schema phòng mới hoặc đã áp dụng một phần/toàn bộ các migration phòng trước khi gom. Script chuyển toàn bộ phòng và liên kết môn còn dùng mã `YOGA` sang `MULTIPURPOSE` (khử trùng liên kết), cấu hình Yoga/Zumba sang `MULTIPURPOSE`, HIIT sang `GYM`, rồi siết CHECK constraint theo 12 loại cuối cùng. Chạy một lần khi nâng cấp; admin kiểm tra các result set cuối script. Không tự chuyển phòng của lớp HIIT hiện có: admin phải rà soát lớp dùng phòng không còn phù hợp trước khi xếp lịch mới. Các migration package, bảo lưu và nghiệp vụ khác vẫn áp dụng riêng.

Migration cần kiểm tra trực tiếp trên SQL Server; kiểm thử unit và phân tích JPQL không thay thế kiểm tra migration hoặc API với dữ liệu thực.
