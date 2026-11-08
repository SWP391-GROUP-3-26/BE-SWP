package com.swp391.beswp.controller;

import com.swp391.beswp.dto.RoomDtos.*;
import com.swp391.beswp.entity.RoomType;
import com.swp391.beswp.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService rooms;

    @GetMapping("/options") public Response<Options> options() {
        return new Response<>(true, "Danh mục loại phòng và trạng thái", rooms.options());
    }
    @GetMapping public Response<List<Data>> list(@RequestParam(required = false) String search,
            @RequestParam(required = false) String status, @RequestParam(required = false) RoomType type) {
        return new Response<>(true, "Danh sách phòng", rooms.list(search, status, type));
    }
    @GetMapping("/{id}") public Response<Data> detail(@PathVariable Integer id) {
        return new Response<>(true, "Chi tiết phòng", rooms.detail(id));
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Response<Data> create(@Valid @RequestBody SaveRequest request) {
        return new Response<>(true, "Tạo phòng thành công", rooms.create(request));
    }
    @PutMapping("/{id}") public Response<Data> update(@PathVariable Integer id, @Valid @RequestBody SaveRequest request) {
        return new Response<>(true, "Cập nhật phòng thành công", rooms.update(id, request));
    }
    @PutMapping("/{id}/status") public Response<Data> status(@PathVariable Integer id, @Valid @RequestBody StatusRequest request) {
        return new Response<>(true, "Đổi trạng thái phòng thành công", rooms.changeStatus(id, request.status()));
    }
    @DeleteMapping("/{id}") public Response<Void> delete(@PathVariable Integer id) {
        rooms.delete(id); return new Response<>(true, "Xóa phòng thành công", null);
    }
}
