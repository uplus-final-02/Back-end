package core.security.principal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public class JwtPrincipal {
    private final Long userId;
    private final boolean paid;
    private final boolean uplus;
    
    public JwtPrincipal(Long userId) {
        this.userId = userId;
        this.paid = false;
        this.uplus = false;
    }

    public JwtPrincipal(Long userId, boolean paid, boolean uplus) {
        this.userId = userId;
        this.paid = paid;
        this.uplus = uplus;
    }
}
