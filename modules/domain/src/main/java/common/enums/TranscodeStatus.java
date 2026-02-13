package common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TranscodeStatus {
    WAITING("트랜스코딩 대기중"),
    PROCESSING("트랜스코딩 진행중"),
    DONE("트랜스코딩 완료됨"),
    FAILED("트랜스코딩 실패");
    private final String description;
}
