package com.swp391.beswp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MembershipFreezeRequest(@NotNull @Min(1) Integer months) { }
