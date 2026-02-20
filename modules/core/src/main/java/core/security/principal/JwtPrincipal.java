package core.security.principal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class JwtPrincipal {
    private final Long userId;
}
