package user.repository;

import common.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import user.entity.AuthAccount;

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
}
