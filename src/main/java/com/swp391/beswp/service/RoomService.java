package com.swp391.beswp.service;

import com.swp391.beswp.dto.RoomDtos.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepository rooms;
    private final ClassRepository classes;
    private final SubjectRepository subjects;
    private final Clock paymentClock;

    public Options options() {
        return new Options(Arrays.stream(RoomType.values()).map(t -> new TypeOption(t, t.getLabel())).toList(),
                List.of(new StatusOption("Active", "Hoạt động"), new StatusOption("Maintenance", "Bảo trì"),
                        new StatusOption("Inactive", "Ngừng sử dụng")));
    }

    @Transactional(readOnly = true)
    public List<Data> list(String search, String status, RoomType type) {
        String filter = search == null || search.isBlank() ? null : search.trim();
        String state = status == null || status.isBlank() || "all".equalsIgnoreCase(status) ? null : status.trim();
        if (state != null) validateStatus(state);
        return rooms.searchRooms(filter, state, type).stream().map(r -> Data.from(r, null)).toList();
    }

    @Transactional(readOnly = true)
    public Data detail(Integer id) {
        return data(rooms.findById(id).orElseThrow(this::notFound));
    }

    @Transactional
    public Data create(SaveRequest request) {
        validate(request);
        if (rooms.existsByNameIgnoreCase(request.name().trim())) throw conflict("Tên phòng đã tồn tại");
        Room room = Room.builder().name(request.name().trim()).type(request.type()).maxCapacity(request.maxCapacity())
                .location(request.location().trim()).status(request.status()).build();
        return Data.from(rooms.save(room), List.of());
    }

    @Transactional
    public Data update(Integer id, SaveRequest request) {
        validate(request);
        Room room = lockRoomWithSubjects(id);
        if (rooms.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) throw conflict("Tên phòng đã tồn tại");
        List<ClassEntity> assigned = upcoming(id);
        validateStatusChange(room, request.status(), assigned);
        List<ClassEntity> incompatible = assigned.stream().filter(c -> c.getMaxCapacity() > request.maxCapacity()
                || (room.getType() != request.type() && !c.getSubject().getAllowedRoomTypes().contains(request.type()))).toList();
        if (!incompatible.isEmpty()) throw conflict("Cần xử lý lớp trước khi giảm sức chứa hoặc đổi loại phòng: " + classNames(incompatible));
        room.setName(request.name().trim()); room.setType(request.type()); room.setMaxCapacity(request.maxCapacity());
        room.setLocation(request.location().trim()); room.setStatus(request.status());
        return data(room);
    }

    @Transactional
    public Data changeStatus(Integer id, String status) {
        validateStatus(status);
        Room room = lockRoomWithSubjects(id);
        validateStatusChange(room, status, upcoming(id));
        room.setStatus(status);
        return data(room);
    }

    @Transactional
    public void delete(Integer id) {
        Room room = rooms.findByIdForUpdate(id).orElseThrow(this::notFound);
        if (classes.existsByRoom_Id(id)) throw conflict("Phòng đã có lớp hoặc lịch sử sử dụng; hãy ngừng sử dụng thay vì xóa");
        rooms.delete(room);
    }

    public static boolean usable(Room room) { return "Active".equals(room.getStatus()) && room.getType() != null; }

    private Room lockRoomWithSubjects(Integer id) {
        // Follow the same subject -> room lock order as class creation and subject configuration.
        List<Integer> ids = classes.findSubjectIdsByRoomId(id);
        for (Integer subjectId : ids) subjects.findByIdForUpdate(subjectId).orElseThrow();
        Room room = rooms.findByIdForUpdate(id).orElseThrow(this::notFound);
        if (!ids.containsAll(classes.findSubjectIdsByRoomId(id))) {
            throw conflict("Danh sách lớp của phòng vừa thay đổi; vui lòng mở lại thông tin và thử lại");
        }
        return room;
    }

    private List<ClassEntity> upcoming(Integer id) {
        return classes.findUpcomingClassesByRoomId(id, LocalDateTime.now(paymentClock));
    }
    private Data data(Room room) {
        return Data.from(room, upcoming(room.getId()).stream().map(c -> new AssignedClass(c.getId(), c.getName(),
                c.getSubject().getName(), c.getMaxCapacity(), c.getDate(), c.getEndDate(), c.getStatus())).toList());
    }
    private void validateStatusChange(Room room, String status, List<ClassEntity> assigned) {
        if (!Objects.equals(room.getStatus(), status) && !"Active".equals(status) && !assigned.isEmpty()) {
            throw conflict("Không thể đổi trạng thái; cần xử lý các lớp đang sử dụng phòng: " + classNames(assigned));
        }
    }
    private String classNames(List<ClassEntity> assigned) {
        return String.join(", ", assigned.stream().map(c -> "#" + c.getId() + " " + c.getName()).toList());
    }
    private void validate(SaveRequest r) {
        if (r.name() == null || r.name().isBlank() || r.name().trim().length() > 50 || r.type() == null
                || r.maxCapacity() == null || r.maxCapacity() < 1 || r.location() == null || r.location().isBlank()
                || r.location().trim().length() > 150) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin phòng không hợp lệ");
        validateStatus(r.status());
    }
    private void validateStatus(String status) {
        if (status == null || !List.of("Active", "Maintenance", "Inactive").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái phòng không hợp lệ");
        }
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng"); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
