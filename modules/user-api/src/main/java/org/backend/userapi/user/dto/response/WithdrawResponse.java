package org.backend.userapi.user.dto.response;


import user.entity.User;

public record WithdrawResponse(
        Long userId,
        String status,
        String message
) {
    public static WithdrawResponse from(User user) {
        return new WithdrawResponse(
                user.getId(),
                user.getUserStatus().name(),
                "회원 탈퇴가 접수되었습니다."
        );
    }
}