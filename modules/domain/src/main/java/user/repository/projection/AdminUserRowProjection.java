package user.repository.projection;

import java.time.LocalDateTime;

public interface AdminUserRowProjection {
    Long getUserId();
    String getName();
    LocalDateTime getCreatedAt();
}