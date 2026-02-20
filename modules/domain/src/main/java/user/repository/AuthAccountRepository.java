package user.repository;

import common.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import user.entity.AuthAccount;

import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    boolean existsByAuthProviderAndAuthProviderSubject(AuthProvider authProvider, String authProviderSubject);

    Optional<AuthAccount> findByAuthProviderAndAuthProviderSubject(AuthProvider authProvider, String authProviderSubject);

    Optional<AuthAccount> findByAuthProviderAndEmail(AuthProvider authProvider, String email);
    
    Optional<AuthAccount> findByUser_Id(Long userId);

    Optional<AuthAccount> findFirstByUserIdAndEmailIsNotNull(Long userId);
}
