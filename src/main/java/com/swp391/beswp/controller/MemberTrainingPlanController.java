package com.swp391.beswp.controller;

import com.swp391.beswp.dto.TrainingPlanDtos.*;
import com.swp391.beswp.service.TrainingPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/member/training-plans")
@RequiredArgsConstructor
public class MemberTrainingPlanController {
    private final TrainingPlanService service;

    @GetMapping
    public Result<List<PlanView>> list(Authentication auth) { return Result.ok(service.memberPlans(auth)); }
    @GetMapping("/{id}")
    public Result<PlanView> detail(Authentication auth, @PathVariable Integer id) {
        return Result.ok(service.detail(auth, id, false));
    }
    @PostMapping("/{id}/read")
    public Result<PlanView> read(Authentication auth, @PathVariable Integer id, @RequestParam int revision) {
        return Result.ok(service.markRead(auth, id, revision));
    }
    @PutMapping("/{id}/exercises/{exerciseId}/result")
    public Result<PlanView> result(Authentication auth, @PathVariable Integer id, @PathVariable Integer exerciseId,
                                   @Valid @RequestBody ExerciseResult input) {
        return Result.ok(service.recordResult(auth, id, exerciseId, input));
    }
}
