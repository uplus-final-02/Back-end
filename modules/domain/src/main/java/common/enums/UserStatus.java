package common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserStatus {
    ACTIVE("활성"),
    INACTIVE("비활성"),
    WITHDRAW_PENDING("탈퇴 진행 중"),
    DELETED("탈퇴");

    private final String description;
}
