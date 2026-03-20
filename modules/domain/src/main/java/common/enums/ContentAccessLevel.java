package common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ContentAccessLevel {
    FREE("무료"),
    BASIC("일반"),
    UPLUS("유플러스");

    private final String description;
}
