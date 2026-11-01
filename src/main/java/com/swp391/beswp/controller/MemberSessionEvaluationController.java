package com.swp391.beswp.controller;

import com.swp391.beswp.dto.SessionEvaluationDtos.*;
import com.swp391.beswp.service.SessionEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/member/session-evaluations")
@RequiredArgsConstructor
public class MemberSessionEvaluationController {
    private final SessionEvaluationService service;
    @GetMapping
    public Result<List<MemberSummary>> list(Authentication auth) { return Result.ok(service.memberSummaries(auth)); }
    @GetMapping("/{sessionId}")
    public Result<EvaluationView> detail(Authentication auth, @PathVariable Integer sessionId) { return Result.ok(service.memberDetail(auth, sessionId)); }
}
