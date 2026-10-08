package com.swp391.beswp.controller;

import com.swp391.beswp.dto.MemberListResponse;
import com.swp391.beswp.dto.MemberResponse;
import com.swp391.beswp.dto.ErrorResponse;
import com.swp391.beswp.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.swp391.beswp.dto.RegisterRequest;
import com.swp391.beswp.dto.RegisterResponse;
import com.swp391.beswp.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@RestController
@RequestMapping("/api/receptionist/members")
@RequiredArgsConstructor
public class ReceptionistMemberController {

    private final AuthService authService;
    private final MemberService memberService;

    @Operation(summary = "Search MEMBER accounts", description = "Receptionist only. Case-insensitive substring search on fullName, username, email and phone. Trim outer spaces; blank/missing keyword returns an empty page. Ordered by userId ascending.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Matching members, or an empty data array"),
        @ApiResponse(responseCode = "400", description = "Invalid page or size", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Receptionist role required", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<MemberListResponse> searchMembers(
            @Parameter(description = "Optional substring; blank returns no results") @RequestParam(required = false) String keyword,
            @Parameter(description = "Zero-based page, minimum 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1 to 100") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(memberService.searchMembers(keyword, page, size));
    }

    @Operation(summary = "Get MEMBER profile", description = "Receptionist only. Nonexistent IDs and accounts of other roles both return 404.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Member profile"),
        @ApiResponse(responseCode = "400", description = "ID is not a 32-bit integer", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Receptionist role required", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Member not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<MemberResponse> getMemberById(@Parameter(description = "Member userId") @PathVariable Integer id) {
        return ResponseEntity.ok(memberService.getMemberById(id));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.fail("Invalid parameter: " + exception.getName()));
    }

    @PostMapping
    public ResponseEntity<RegisterResponse> createMember(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }
}
