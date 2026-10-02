# Các vấn đề cần nhóm xác nhận trong Logical ERD

Nguồn đối chiếu: [logical-erd.drawio](logical-erd.drawio) và [logical-erd.json](logical-erd.json).

ERD là source of truth. Tài liệu này ghi lại 9 vấn đề đang chờ nhóm xác nhận; mọi lựa chọn dưới đây chỉ là đề xuất, chưa được áp dụng. Không tự thêm/bỏ table hoặc column, đổi relationship, hoặc suy đoán business rule.

## 1. PK/FK và relationship

### Vấn đề 1: Đích tham chiếu của Training_Result.User_ID

- **Entity/table liên quan:** `Training_Result`, `User`.
- **Column liên quan:** `Training_Result.User_ID`, `User.Role_ID`, `User.User_ID`.
- **Vấn đề hiện tại:** Đường nối đi từ `User.Role_ID` tới `Training_Result.User_ID`. `User.Role_ID` được đánh dấu FK, không phải PK; ERD không thể hiện UNIQUE cho column này.
- **Thông tin cần nhóm xác nhận:** `Training_Result.User_ID` thực sự tham chiếu column nào? Đầu nối hiện tại có chủ đích không? Nếu giữ tham chiếu `User.Role_ID`, khóa ứng viên nào bảo đảm tính duy nhất để SQL Server cho phép FK?
- **Đề xuất lựa chọn, chưa áp dụng:** Nhóm có thể xác nhận tham chiếu `User.User_ID` nếu đó là ý định, hoặc xác nhận thiết kế tham chiếu `User.Role_ID` cùng ràng buộc khóa cần thiết. Không tự đổi đầu nối hoặc thêm UNIQUE.

### Vấn đề 2: Membership_Package.Name có nhãn FK nhưng thiếu tham chiếu

- **Entity/table liên quan:** `Membership_Package`; entity đích chưa xác định.
- **Column liên quan:** `Membership_Package.Name`.
- **Vấn đề hiện tại:** Column có nhãn FK nhưng không có đường nối xác định table/column được tham chiếu.
- **Thông tin cần nhóm xác nhận:** Nhãn FK có chủ đích không? Nếu có, table và column đích là gì?
- **Đề xuất lựa chọn, chưa áp dụng:** Xác nhận và thể hiện đầy đủ tham chiếu nếu đây là FK; hoặc xác nhận nhãn FK là lỗi ký hiệu trước khi nhóm sửa ERD. Không tự bỏ nhãn hay thêm table.

### Vấn đề 3: Training_Plan.Member_ID thiếu đích tham chiếu

- **Entity/table liên quan:** `Training_Plan`; entity đích chưa xác định.
- **Column liên quan:** `Training_Plan.Member_ID`.
- **Vấn đề hiện tại:** Column có nhãn FK nhưng không có đường nối xác định tham chiếu. ERD không có entity tên `Member`; tên trang `Member` không phải entity.
- **Thông tin cần nhóm xác nhận:** Table và column đích của FK này là gì?
- **Đề xuất lựa chọn, chưa áp dụng:** Nếu nhóm xác nhận đích là `User.User_ID`, thể hiện rõ đường nối đó trong ERD; nếu là đích khác, nhóm cần chỉ rõ thiết kế chính thức. Không suy ra tham chiếu từ tên `Member_ID`.

### Vấn đề 4: Relationship tới Activity_Log thiếu đầu nguồn

- **Entity/table liên quan:** `Activity_Log`; entity nguồn chưa xác định.
- **Column liên quan:** `Activity_Log.User_ID`.
- **Vấn đề hiện tại:** Column có nhãn FK và đường nối tới hàng column này, nhưng edge không có thuộc tính `source`; đầu nguồn chỉ có tọa độ. Chưa đủ cơ sở khẳng định tham chiếu `User.User_ID`.
- **Thông tin cần nhóm xác nhận:** Entity và column nguồn chính xác của đường nối là gì?
- **Đề xuất lựa chọn, chưa áp dụng:** Gắn đầu nguồn vào đúng column sau khi nhóm xác nhận; nếu đích tham chiếu là `User.User_ID`, thể hiện rõ trong ERD. Không suy ra nguồn chỉ từ vị trí đường nối.

### Vấn đề 5: Schedule.Sport_ID chưa khớp với entity đang nối

- **Entity/table liên quan:** `Schedule`, `Subject`.
- **Column liên quan:** `Schedule.Sport_ID`, `Subject.Subject_ID`.
- **Vấn đề hiện tại:** `Schedule.Sport_ID` có nhãn FK, trong khi đường relationship là `Subject → Schedule`. `Subject` có PK `Subject_ID`, không có `Sport_ID`; ERD không có entity `Sport`.
- **Thông tin cần nhóm xác nhận:** `Sport_ID` có chủ đích tham chiếu `Subject.Subject_ID` không? Tên column và relationship hiện tại có đúng thiết kế không?
- **Đề xuất lựa chọn, chưa áp dụng:** Có thể giữ tên `Sport_ID` và xác nhận tham chiếu `Subject.Subject_ID`, hoặc nhóm duyệt việc thống nhất tên nếu đó là ý định. Tên FK không bắt buộc giống tên khóa được tham chiếu; khác tên chưa đủ để kết luận ERD sai. Không tự đổi tên hoặc thêm entity `Sport`.

### Vấn đề 6: Hai relationship của Schedule chưa nối tới column cụ thể

- **Entity/table liên quan:** `Schedule`, `Subject`, `User`.
- **Column liên quan:** `Schedule.Sport_ID`, `Schedule.Coach_ID`; các column nguồn chưa được edge xác định trực tiếp.
- **Vấn đề hiện tại:** Hai đường `Subject → Schedule` và `User → Schedule` gắn ở cấp entity, không gắn tới hàng column. Chưa có mapping column trực tiếp trong edge.
- **Thông tin cần nhóm xác nhận:** Mapping chính xác của từng đường là gì? Có phải `Schedule.Sport_ID → Subject.Subject_ID` và `Schedule.Coach_ID → User.User_ID` không?
- **Đề xuất lựa chọn, chưa áp dụng:** Sau khi nhóm xác nhận mapping, thể hiện đầu nối ở đúng hàng column hoặc bổ sung ghi chú tham chiếu rõ ràng trong ERD. Vấn đề này về độ rõ của đầu nối; vấn đề 5 riêng về ý nghĩa và tên `Sport_ID`.

## 2. Datatype/nullability

### Vấn đề 7: Chưa có datatype và nullability đầy đủ

- **Entity/table liên quan:** Toàn bộ 16 entity trong ERD.
- **Column liên quan:** Toàn bộ 107 column chưa có datatype; nullability riêng của FK và column thường chưa được thể hiện.
- **Vấn đề hiện tại:** ERD không ghi datatype, độ dài, precision/scale, hoặc nhãn NULL/NOT NULL riêng. Không thấy UNIQUE riêng ngoài tính duy nhất vốn có của PK. PK sẽ có tính chất duy nhất và không nhận NULL khi triển khai SQL Server, nhưng không có cơ sở suy ra các ràng buộc tương tự cho column khác.
- **Thông tin cần nhóm xác nhận:** Datatype SQL Server và các tham số cần thiết của từng column; column nào cho phép NULL; column hoặc tổ hợp column nào có UNIQUE nếu thiết kế yêu cầu.
- **Đề xuất lựa chọn, chưa áp dụng:** Nhóm có thể duyệt một bảng đặc tả từng column hoặc bổ sung trực tiếp thông tin vào ERD. Không tự chọn kiểu từ tên column, đặt NOT NULL cho FK, hoặc thêm UNIQUE cho `Email`, `Phone`, `Voucher.Code` hay cặp FK.

## 3. Cardinality

### Vấn đề 8: Cardinality chưa được thể hiện đầy đủ ở hai đầu

- **Entity/table liên quan:** Các entity tham gia 22 đường relationship, bao gồm đường thiếu nguồn tới `Activity_Log`.
- **Column liên quan:** Các column tại đầu nối relationship; riêng `Class_Session.Session_ID` và `Training_Result.Session_ID` nằm trên đường có ký hiệu khác các đường còn lại. Hai đường của `Schedule` đang nối ở cấp entity.
- **Vấn đề hiện tại:** 21 đường dùng `endArrow=ERoneToMany`, thể hiện ký hiệu 1..N phía đích; một đường `Class_Session → Training_Result` dùng `endArrow=ERmany`, thể hiện phía đích là nhiều nhưng không ghi mức tối thiểu. Các đường không khai báo `startArrow`, nên chưa đủ để chốt min/max ở cả hai phía. File JSON không lưu các ký hiệu cardinality này.
- **Thông tin cần nhóm xác nhận:** Min/max ở từng đầu của mỗi relationship, ví dụ `0..1`, `1`, `0..N`, `1..N`; sự khác biệt của đường `Class_Session → Training_Result` có chủ đích không?
- **Đề xuất lựa chọn, chưa áp dụng:** Nhóm duyệt ma trận cardinality cho từng relationship rồi thể hiện ký hiệu ở cả hai đầu. Không tự coi mọi relationship là bắt buộc hoặc tự chuyển ký hiệu cardinality thành NOT NULL.

## 4. Naming

### Vấn đề 9: Một số tên cần xác nhận trước khi đưa vào DDL

- **Entity/table liên quan:** `Role`, `Class_Booking`, `Membership_Package`, `Schedule`.
- **Column liên quan:** `Role_id`, `Booking_Dateime`, `Included_classes`, `Term_conditions`, `Schedule_date`, `Start_time`, `End_time`.
- **Vấn đề hiện tại:** Cách viết hoa/thường chưa đồng nhất; `Booking_Dateime` có dấu hiệu cần kiểm tra chính tả. Đây vẫn là tên chính thức đang có trong ERD, chưa được phép tự sửa.
- **Thông tin cần nhóm xác nhận:** Giữ nguyên toàn bộ tên hiện tại hay duyệt danh sách đổi tên cụ thể? `Booking_Dateime` có đúng tên nhóm muốn sử dụng không?
- **Đề xuất lựa chọn, chưa áp dụng:** Giữ nguyên tên ERD; hoặc nhóm duyệt từng mapping tên, chẳng hạn `Role_id → Role_ID`, `Booking_Dateime → Booking_Datetime`, `Included_classes → Included_Classes`, `Term_conditions → Term_Conditions`, `Schedule_date → Schedule_Date`, `Start_time → Start_Time`, `End_time → End_Time`. Các mapping này chỉ là lựa chọn để review, không phải quyết định đã được thông qua.

---

**Trạng thái:** Cả 9 vấn đề đang chờ nhóm xác nhận. Việc tạo tài liệu này không thay đổi ERD, không tạo `schema.sql`, không tạo database và không tạo code.
