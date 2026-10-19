package com.swp391.beswp.controller;

import com.swp391.beswp.dto.ReceptionistClassRegistrationRequest;
import com.swp391.beswp.dto.ReceptionistPackageRegistrationRequest;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.ClassCatalogResponse;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.ClassRegistrationPreview;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.ClassRegistrationResponse;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.PackageCatalogResponse;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.PackageRegistrationResponse;
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
            description = "Lễ tân xác nhận đã nhận tiền; các phương thức hợp lệ là Cash, Card, Bank_Transfer và E_Wallet.")
    @PostMapping("/members/{memberId}/packages")
    public ResponseEntity<PackageRegistrationResponse> registerPackage(
            Authentication authentication,
            @PathVariable Integer memberId,
            @Valid @RequestBody ReceptionistPackageRegistrationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.registerPackage(authentication, memberId, request));
    }

    @Operation(summary = "Đăng ký hội viên vào toàn bộ các buổi còn lại của lớp",
            description = "Với DIRECT, lễ tân xác nhận thanh toán tại quầy bằng Cash, Card, Bank_Transfer hoặc E_Wallet. PACKAGE dùng số buổi từ gói hội viên đang hoạt động.")
    @PostMapping("/members/{memberId}/classes")
    public ResponseEntity<ClassRegistrationResponse> registerClass(
            Authentication authentication,
            @PathVariable Integer memberId,
            @Valid @RequestBody ReceptionistClassRegistrationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.registerClass(authentication, memberId, request));
    }
}
