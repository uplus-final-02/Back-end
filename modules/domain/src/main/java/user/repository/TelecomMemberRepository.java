package user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import user.entity.TelecomMember;

public interface TelecomMemberRepository extends JpaRepository<TelecomMember, Long> {
    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndStatus(String phoneNumber, String status);
}
