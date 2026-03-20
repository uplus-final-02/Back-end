package org.backend.userapi.common.dto;

public record ApiResponse<T>(
        int status,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "성공", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "생성 완료", data);
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(201, message, data);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return success(data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return success(message, data);
    }
}