package common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TranscodeStatus {
    WAITING("트랜스코딩 대기중"),   // 0초 ~ 60초 미만 (기록은 생성되었으나 본격 시청 전)
    PROCESSING("트랜스코딩 진행중"),    // 60초 이상 지속 시청 (이어보기 대상)
    DONE("트랜스코딩 완료됨"), // 전체 길이의 90% 이상 시청 (다음 화 추천 대상)
    FAILED("트랜스코딩 실패");
    private final String description;
}
