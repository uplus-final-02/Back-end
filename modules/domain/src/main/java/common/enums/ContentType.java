package common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ContentType {
    SINGLE("단편"),
    SERIES("시리즈");

    private final String description;
}
