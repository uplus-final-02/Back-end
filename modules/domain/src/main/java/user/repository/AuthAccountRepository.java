package user.repository;

import common.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import user.entity.AuthAccount;
import user.repository.projection.AdminLoginMethodProjection;

import java.util.List;
import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    boolean existsByAuthProviderAndAuthProviderSubject(AuthProvider authProvider, String authProviderSubject);

    Optional<AuthAccount> findByAuthProviderAndAuthProviderSubject(AuthProvider authProvider, String authProviderSubject);

    Optional<AuthAccount> findByAuthProviderAndEmail(AuthProvider authProvider, String email);

    /**
     * 이메일로 모든 provider의 auth_account 조회.
     * 이메일 중복/충돌 검사에 사용합니다.
     */
    List<AuthAccount> findAllByEmail(String email);

    Optional<AuthAccount> findByUser_Id(Long userId);

    Optional<AuthAccount> findFirstByUserIdAndEmailIsNotNull(Long userId);

    @Query("""
        select
            a.user.id as userId,
            a.authProvider as authProvider,
            coalesce(a.email, a.authProviderSubject) as identifier
        from AuthAccount a
        where a.user.id in :userIds
        order by a.user.id asc
        """)
    List<AdminLoginMethodProjection> findLoginMethodsByUserIds(@Param("userIds") List<Long> userIds);
}
