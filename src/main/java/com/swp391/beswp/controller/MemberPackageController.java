package com.swp391.beswp.controller;

import com.swp391.beswp.dto.MemberPackageCatalogResponse;
import com.swp391.beswp.dto.MemberPackageRegistrationRequest;
import com.swp391.beswp.dto.MemberPackageRenewalOverviewResponse;
import com.swp391.beswp.dto.MemberPackageRenewalPreviewRequest;
import com.swp391.beswp.dto.MemberPackageRenewalPreviewResponse;
import com.swp391.beswp.dto.MemberPackageSubscriptionListResponse;
import com.swp391.beswp.dto.MemberPackageSubscriptionResponse;
import com.swp391.beswp.dto.PackageResponse;
import com.swp391.beswp.service.MemberPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

@RestController
@RequestMapping("/api/member/packages")
@RequiredArgsConstructor
public class MemberPackageController {

    private final MemberPackageService memberPackageService;

    @Operation(
            summary = "Danh sách gói hội viên đang mở bán",
            description = "Trả về các gói Active, có thể tìm theo tên/mô tả và phân trang.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping
    public ResponseEntity<MemberPackageCatalogResponse> getAvailablePackages(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(memberPackageService.getAvailablePackages(
                authentication.getName(), search, page, size
        ));
    }

    @Operation(
            summary = "Chi tiết gói hội viên đang mở bán",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/{packageId}")
    public ResponseEntity<PackageResponse> getAvailablePackage(
            Authentication authentication,
            @PathVariable Integer packageId
    ) {
        return ResponseEntity.ok(memberPackageService.getAvailablePackage(authentication.getName(), packageId));
    }

    @Operation(summary = "Thông tin gia hạn gói hội viên", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/renewal")
    public ResponseEntity<MemberPackageRenewalOverviewResponse> getRenewalOverview(Authentication authentication) {
        return ResponseEntity.ok(memberPackageService.getRenewalOverview(authentication.getName()));
    }

    @Operation(summary = "Xem trước thông tin gia hạn", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/renewal/preview")
    public ResponseEntity<MemberPackageRenewalPreviewResponse> previewRenewal(
            Authentication authentication, @Valid @RequestBody MemberPackageRenewalPreviewRequest request) {
        return ResponseEntity.ok(memberPackageService.previewRenewal(authentication.getName(), request));
    }

    @Operation(summary = "Xác nhận gia hạn gói hội viên", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/renewal")
    public ResponseEntity<MemberPackageSubscriptionResponse> renewPackage(
            Authentication authentication, jakarta.servlet.http.HttpServletRequest servletRequest,
            @Valid @RequestBody MemberPackageRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberPackageService.registerOnlinePackage(authentication.getName(), request, servletRequest.getRemoteAddr(), true));
    }

    @Operation(
            summary = "Đăng ký một gói hội viên",
            description = "Card tạo giao dịch VNPay; E_Wallet tạo giao dịch QR SePay. Chỉ kích hoạt gói sau callback hợp lệ. Không hỗ trợ tiền mặt cho Member.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/register")
    public ResponseEntity<MemberPackageSubscriptionResponse> registerPackage(
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest servletRequest,
            @Valid @RequestBody MemberPackageRegistrationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberPackageService.registerOnlinePackage(authentication.getName(), request, servletRequest.getRemoteAddr(), false));
    }

    @Operation(
            summary = "Lịch sử gói hội viên của tôi",
            description = "Chỉ trả về subscription thuộc tài khoản đang đăng nhập. Có thể lọc theo trạng thái.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/subscriptions")
    public ResponseEntity<MemberPackageSubscriptionListResponse> getMySubscriptions(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(memberPackageService.getMySubscriptions(
                authentication.getName(), status, page, size
        ));
    }

    @Operation(
            summary = "Chi tiết một subscription của tôi",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/subscriptions/{subscriptionId}")
    public ResponseEntity<MemberPackageSubscriptionResponse> getMySubscription(
            Authentication authentication,
            @PathVariable Integer subscriptionId
    ) {
        return ResponseEntity.ok(memberPackageService.getMySubscription(
                authentication.getName(), subscriptionId
        ));
    }
}
