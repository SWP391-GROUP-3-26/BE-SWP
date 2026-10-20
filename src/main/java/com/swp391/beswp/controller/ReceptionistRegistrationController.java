package com.swp391.beswp.controller;

import com.swp391.beswp.dto.ReceptionistClassRegistrationRequest;
import com.swp391.beswp.dto.ReceptionistPackageRegistrationRequest;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.ClassCatalogResponse;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.ClassRegistrationPreview;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.PackageCatalogResponse;
import com.swp391.beswp.service.ReceptionistRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

@Tag(name = "Receptionist Registrations", description = "Lễ tân đăng ký lớp và gói tập cho hội viên tại quầy")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/receptionist/registrations")
@RequiredArgsConstructor
public class ReceptionistRegistrationController {

    private final ReceptionistRegistrationService registrationService;

    @Operation(summary = "Danh mục gói tập đang mở bán")
    @GetMapping("/packages")
    public ResponseEntity<PackageCatalogResponse> getPackages(
            Authentication authentication,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(registrationService.getPackages(authentication, search));
    }

    @Operation(summary = "Danh sách lớp đang mở đăng ký kèm số buổi và chỗ còn")
    @GetMapping("/classes")
    public ResponseEntity<ClassCatalogResponse> getClasses(
            Authentication authentication,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(registrationService.getClasses(authentication, search));
    }

    @Operation(summary = "Xem điều kiện đăng ký lớp cho hội viên")
    @GetMapping("/members/{memberId}/classes/{classId}")
    public ResponseEntity<ClassRegistrationPreview> getClassRegistrationPreview(
            Authentication authentication,
            @PathVariable Integer memberId,
            @PathVariable Integer classId
    ) {
        return ResponseEntity.ok(registrationService.getClassRegistrationPreview(authentication, memberId, classId));
    }

    @Operation(summary = "Đăng ký gói tập cho hội viên và ghi nhận thanh toán tại quầy",
            description = "Cash ghi Paid sau xác nhận nhận tiền. Card (VNPay) và E_Wallet (SePay) tạo Pending; chỉ kích hoạt gói khi callback hợp lệ.")
    @PostMapping("/members/{memberId}/packages")
    public ResponseEntity<Object> registerPackage(
            Authentication authentication,
            @PathVariable Integer memberId,
            jakarta.servlet.http.HttpServletRequest servletRequest,
            @Valid @RequestBody ReceptionistPackageRegistrationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Cash".equalsIgnoreCase(request.paymentMethod())
                        ? registrationService.registerPackage(authentication, memberId, request)
                        : registrationService.registerOnlinePackage(authentication, memberId, request, servletRequest.getRemoteAddr()));
    }

    @Operation(summary = "Đăng ký hội viên vào toàn bộ các buổi còn lại của lớp",
            description = "DIRECT/Cash ghi Paid sau xác nhận nhận tiền; DIRECT/Card hoặc E_Wallet giữ chỗ Pending qua cổng thanh toán. PACKAGE trừ lượt gói, không gửi paymentChannel.")
    @PostMapping("/members/{memberId}/classes")
    public ResponseEntity<Object> registerClass(
            Authentication authentication,
            @PathVariable Integer memberId,
            jakarta.servlet.http.HttpServletRequest servletRequest,
            @Valid @RequestBody ReceptionistClassRegistrationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("DIRECT".equalsIgnoreCase(request.paymentMethod()) && !"Cash".equalsIgnoreCase(request.paymentChannel())
                        ? registrationService.registerOnlineClass(authentication, memberId, request, servletRequest.getRemoteAddr())
                        : registrationService.registerClass(authentication, memberId, request));
    }
    @GetMapping("/members/{memberId}/classes")
    public Object memberClasses(Authentication auth, @PathVariable Integer memberId) {
        return registrationService.getMemberClasses(auth, memberId);
    }

    @GetMapping("/members/{memberId}/packages")
    public Object memberPackages(Authentication auth, @PathVariable Integer memberId) {
        return registrationService.getMemberPackages(auth, memberId);
    }

    @GetMapping("/members/{memberId}/payments/{paymentId}")
    public Object payment(Authentication auth, @PathVariable Integer memberId, @PathVariable Integer paymentId) {
        return registrationService.getPayment(auth, memberId, paymentId);
    }

    @PostMapping("/members/{memberId}/classes/bookings/{bookingId}/cancel")
    public Object cancelClass(Authentication auth, @PathVariable Integer memberId, @PathVariable Integer bookingId) {
        return registrationService.cancelClass(auth, memberId, bookingId);
    }

    @PostMapping("/members/{memberId}/payments/{paymentId}/cancel")
    public Object cancelPendingPayment(Authentication auth, @PathVariable Integer memberId, @PathVariable Integer paymentId) {
        return registrationService.cancelPendingPayment(auth, memberId, paymentId);
    }
}
