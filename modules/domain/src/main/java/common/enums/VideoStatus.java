package common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VideoStatus {
    DRAFT("영상 준비중"),
    PUBLIC("영상 공개"),
    PRIVATE("영상 비공개");

    private final String description;
}
