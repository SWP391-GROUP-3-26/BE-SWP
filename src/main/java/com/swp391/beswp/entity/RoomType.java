package com.swp391.beswp.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoomType {
    GYM("Phòng Gym"), PILATES("Phòng Pilates"),
    FUNCTIONAL("Phòng Functional"), BOXING("Phòng Boxing"), MARTIAL_ARTS("Phòng Võ"),
    MULTIPURPOSE("Phòng Đa Năng"), SWIMMING_POOL("Bể Bơi"),
    FOOTBALL_COURT("Sân Bóng Đá"), BASKETBALL_COURT("Sân Bóng Rổ"), BADMINTON_COURT("Sân Cầu Lông"),
    TABLE_TENNIS_COURT("Sân Bóng Bàn"), TENNIS_COURT("Sân Tennis");

    private final String label;
}
