package user.repository.projection;

import common.enums.AuthProvider;

public interface AdminLoginMethodProjection {
    Long getUserId();
    AuthProvider getAuthProvider();
    String getIdentifier(); // email 우선, 없으면 subject
}