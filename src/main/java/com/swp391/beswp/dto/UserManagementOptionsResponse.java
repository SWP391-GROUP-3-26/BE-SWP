package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class UserManagementOptionsResponse {

    private boolean success;
    private String message;
    private List<String> roles;
    private List<String> statuses;
    private List<String> genders;
}
