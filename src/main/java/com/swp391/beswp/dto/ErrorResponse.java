package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {

    private boolean success;
    private String message;

    public static ErrorResponse fail(String message) {
        return new ErrorResponse(false, message);
    }
}
