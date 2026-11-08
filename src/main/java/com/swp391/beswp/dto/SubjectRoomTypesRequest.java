package com.swp391.beswp.dto;

import com.swp391.beswp.entity.RoomType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SubjectRoomTypesRequest(@NotEmpty List<@NotNull RoomType> allowedRoomTypes) { }
