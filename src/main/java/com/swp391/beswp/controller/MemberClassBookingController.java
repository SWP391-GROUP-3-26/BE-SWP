package com.swp391.beswp.controller;

import com.swp391.beswp.dto.AvailableClassListResponse;
import com.swp391.beswp.dto.ClassConfirmationDetailResponse;
import com.swp391.beswp.dto.MemberClassBookingRequest;
import com.swp391.beswp.dto.MemberClassBookingResponse;
import com.swp391.beswp.dto.MemberSubscriptionInfoResponse;
import com.swp391.beswp.dto.MyClassListResponse;
import com.swp391.beswp.service.MemberClassBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Member Class Booking", description = "API Đăng ký lớp học dành cho Hội viên (Member)")
@RestController
@RequestMapping("/api/member/classes")
@RequiredArgsConstructor
public class MemberClassBookingController {

    private final MemberClassBookingService bookingService;

    /**
     * Màn hình 1: Lớp học của tôi
     */
    @Operation(summary = "Danh sách lớp học của tôi", description = "Lấy danh sách các lớp học hội viên đã đăng ký kèm bộ lọc trạng thái và thống kê số lượng.")
    @GetMapping("/my-classes")
    public ResponseEntity<MyClassListResponse> getMyClasses(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(bookingService.getMyClasses(authentication.getName(), status, search));
    }

    /**
     * Màn hình 2: Đăng ký lớp - Tìm kiếm và duyệt danh sách lớp khả dụng
     */
    @Operation(summary = "Danh sách lớp học khả dụng để đăng ký", description = "Tìm kiếm và xem danh sách các lớp mở đăng ký, lọc theo bộ môn, hiển thị số chỗ còn lại và banner gói hội viên khả dụng.")
    @GetMapping("/available")
    public ResponseEntity<AvailableClassListResponse> getAvailableClasses(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category
    ) {
        return ResponseEntity.ok(bookingService.getAvailableClasses(authentication.getName(), search, category));
    }

    /**
     * Lấy thông tin gói hội viên của học viên
     */
    @Operation(summary = "Kiểm tra gói hội viên khả dụng", description = "Lấy thông tin gói hội viên đang hoạt động và số buổi còn lại của học viên.")
    @GetMapping("/subscription")
    public ResponseEntity<MemberSubscriptionInfoResponse> getSubscriptionInfo(Authentication authentication) {
        // Find user by principal and get subscription
        return ResponseEntity.ok(bookingService.getAvailableClasses(authentication.getName(), null, null).getActiveSubscription());
    }

    /**
     * Màn hình 3: Pop-up xác nhận thông tin đặt lớp
     */
    @Operation(summary = "Chi tiết xác nhận đặt lớp (Pop-up)", description = "Lấy thông tin chi tiết lớp học và kiểm tra tính khả dụng của 2 hình thức thanh toán (Trừ gói hội viên / Thanh toán trực tiếp).")
    @GetMapping("/{classId}/confirm")
    public ResponseEntity<ClassConfirmationDetailResponse> getClassConfirmationDetail(
            Authentication authentication,
            @PathVariable Integer classId
    ) {
        return ResponseEntity.ok(bookingService.getClassConfirmationDetail(authentication.getName(), classId));
    }

    /**
     * Màn hình 3: Thực hiện đăng ký lớp
     */
    @Operation(summary = "Tiến hành đăng ký lớp học", description = "Đăng ký lớp học và chọn hình thức thanh toán (PACKAGE - Trừ gói hội viên hoặc DIRECT - Thanh toán trực tiếp).")
    @PostMapping("/{classId}/register")
    public ResponseEntity<MemberClassBookingResponse> registerClassWithPathVariable(
            Authentication authentication,
            @PathVariable Integer classId,
            @Valid @RequestBody MemberClassBookingRequest request
    ) {
        MemberClassBookingResponse response = bookingService.registerClass(authentication.getName(), classId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Endpoint thay thế: đăng ký lớp học với classId nằm trong Request Body
     */
    @Operation(summary = "Tiến hành đăng ký lớp học (RequestBody)", description = "Đăng ký lớp học truyền classId trong body.")
    @PostMapping("/register")
    public ResponseEntity<MemberClassBookingResponse> registerClass(
            Authentication authentication,
            @Valid @RequestBody MemberClassBookingRequest request
    ) {
        MemberClassBookingResponse response = bookingService.registerClass(authentication.getName(), request.getClassId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Hủy đăng ký lớp học
     */
    @Operation(summary = "Hủy đăng ký lớp học", description = "Học viên hủy lớp đã đăng ký (chỉ hủy được khi trạng thái là 'Đang diễn ra').")
    @PostMapping("/cancel/{bookingId}")
    public ResponseEntity<Map<String, Object>> cancelBooking(
            Authentication authentication,
            @PathVariable Integer bookingId
    ) {
        bookingService.cancelClassBooking(authentication.getName(), bookingId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Hủy đăng ký lớp học thành công"
        ));
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelBookingAlias(
            Authentication authentication,
            @PathVariable Integer bookingId
    ) {
        bookingService.cancelClassBooking(authentication.getName(), bookingId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Hủy đăng ký lớp học thành công"
        ));
    }
}
