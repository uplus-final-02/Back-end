package org.backend.userapi.user.dto.request;

import common.enums.WithdrawalReason;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WithdrawRequest {

    @NotNull(message = "탈퇴 사유를 선택해주세요.")
    private WithdrawalReason reason;
}
