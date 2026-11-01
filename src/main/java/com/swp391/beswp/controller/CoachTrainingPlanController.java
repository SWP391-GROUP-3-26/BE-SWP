package com.swp391.beswp.controller;

import com.swp391.beswp.dto.TrainingPlanDtos.*;
import com.swp391.beswp.service.TrainingPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/coach/training-plans")
@RequiredArgsConstructor
public class CoachTrainingPlanController {
    private final TrainingPlanService service;

    @GetMapping
    public Result<List<PlanView>> list(Authentication auth) { return Result.ok(service.coachPlans(auth)); }
    @GetMapping("/eligible-members")
    public Result<List<MemberOption>> eligibleMembers(Authentication auth, @RequestParam Integer classId) {
        return Result.ok(service.eligibleMembers(auth, classId));
    }
    @GetMapping("/{id}")
    public Result<PlanView> detail(Authentication auth, @PathVariable Integer id) {
        return Result.ok(service.detail(auth, id, true));
    }
    @PostMapping
    public Result<PlanView> create(Authentication auth, @Valid @RequestBody SavePlan input) {
        return Result.ok(service.create(auth, input));
    }
    @PutMapping("/{id}")
    public Result<PlanView> update(Authentication auth, @PathVariable Integer id, @Valid @RequestBody SavePlan input) {
        return Result.ok(service.update(auth, id, input));
    }
    @PatchMapping("/{id}/review")
    public Result<PlanView> review(Authentication auth, @PathVariable Integer id, @Valid @RequestBody ReviewPlan input) {
        return Result.ok(service.review(auth, id, input));
    }
    @PostMapping("/{id}/recipients")
    public Result<PlanView> addRecipients(Authentication auth, @PathVariable Integer id, @Valid @RequestBody VersionInput input) {
        return Result.ok(service.addRecipients(auth, id, input));
    }
    @PatchMapping("/{id}/recipients/{memberId}/feedback")
    public Result<PlanView> reviewRecipient(Authentication auth, @PathVariable Integer id, @PathVariable Integer memberId,
                                             @Valid @RequestBody RecipientFeedback input) {
        return Result.ok(service.reviewRecipient(auth, id, memberId, input));
    }
}
