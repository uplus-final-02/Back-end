package common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HistoryStatus {
    STARTED("시청 시작"),   // 0초 ~ 60초 미만 (기록은 생성되었으나 본격 시청 전)
    WATCHING("시청 중"),    // 60초 이상 지속 시청 (이어보기 대상)
    COMPLETED("시청 완료"); // 전체 길이의 90% 이상 시청 (다음 화 추천 대상)

    private final String description;
}
