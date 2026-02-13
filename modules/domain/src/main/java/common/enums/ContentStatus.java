package common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ContentStatus {
    ACTIVE("컨텐츠 조회 가능"),
    HIDDEN("컨텐츠 숨김"),
    DELETED("컨텐츠 삭제됨");

    private final String description;
}
