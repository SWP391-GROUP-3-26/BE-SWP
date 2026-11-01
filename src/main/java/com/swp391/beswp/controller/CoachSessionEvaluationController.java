package com.swp391.beswp.controller;

import com.swp391.beswp.dto.SessionEvaluationDtos.*;
import com.swp391.beswp.service.SessionEvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coach/classes/{classId}/sessions/{sessionId}/evaluations")
@RequiredArgsConstructor
public class CoachSessionEvaluationController {
    private final SessionEvaluationService service;
    @GetMapping
    public Result<CoachView> list(Authentication auth, @PathVariable Integer classId, @PathVariable Integer sessionId) {
        return Result.ok(service.coachView(auth, classId, sessionId));
    }
    @PutMapping("/{memberId}")
    public Result<CoachView> save(Authentication auth, @PathVariable Integer classId, @PathVariable Integer sessionId,
                                  @PathVariable Integer memberId, @Valid @RequestBody SaveEvaluation input) {
        return Result.ok(service.save(auth, classId, sessionId, memberId, input));
    }
    @PostMapping("/{memberId}/withdraw")
    public Result<CoachView> withdraw(Authentication auth, @PathVariable Integer classId, @PathVariable Integer sessionId,
                                      @PathVariable Integer memberId, @Valid @RequestBody WithdrawEvaluation input) {
        return Result.ok(service.withdraw(auth, classId, sessionId, memberId, input));
    }
}
