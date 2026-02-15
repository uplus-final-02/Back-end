package org.backend.userapi.common.dto;

public record ApiResponse<T>(
        int status,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "성공", data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "생성 완료", data);
    }
}
