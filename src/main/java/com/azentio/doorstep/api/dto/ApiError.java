package com.azentio.doorstep.api.dto;

import java.util.List;

public record ApiError(int status, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, List.of());
    }
}
