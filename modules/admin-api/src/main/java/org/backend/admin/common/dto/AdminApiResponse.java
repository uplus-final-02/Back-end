package org.backend.admin.common.dto;


public record AdminApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {
    public static <T> AdminApiResponse<T> ok(String message, T data) {
        return new AdminApiResponse<>(true, "OK", message, data);
    }

    public static <T> AdminApiResponse<T> ok(String message) {
        return new AdminApiResponse<>(true, "OK", message, null);
    }

    public static <T> AdminApiResponse<T> fail(String code, String message) {
        return new AdminApiResponse<>(false, code, message, null);
    }

    public static <T> AdminApiResponse<T> fail(String code, String message, T data) {
        return new AdminApiResponse<>(false, code, message, data);
    }
    
    public static <T> AdminApiResponse<T> created(String message, T data) {
        return new AdminApiResponse<>(true, "CREATED", message, data);
    }
}