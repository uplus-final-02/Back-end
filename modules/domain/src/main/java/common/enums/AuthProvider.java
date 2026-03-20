package common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuthProvider {
    EMAIL("이메일"),
    GOOGLE("구글"),
    KAKAO("카카오"),
    NAVER("네이버");

    private final String description;
}
