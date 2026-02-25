package org.backend.userapi.membership.service;

import lombok.RequiredArgsConstructor;
import org.backend.userapi.membership.dto.UplusVerificationRequest;
import org.backend.userapi.membership.dto.UplusVerificationResponse;
import org.backend.userapi.membership.exception.UplusUserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.entity.User;
import user.entity.UserUplusVerified;
import user.repository.TelecomMemberRepository;
import user.repository.UserRepository;
import user.repository.UserUplusVerifiedRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class UplusMembershipService {
	
	private final UserRepository userRepository;
    private final UserUplusVerifiedRepository userUplusVerifiedRepository;
    private final TelecomMemberRepository telecomMemberRepository;
   
    
    public UplusVerificationResponse verify(Long userId, UplusVerificationRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String phoneNumber = request.getPhoneNumber();
        
        boolean isUplusMember = telecomMemberRepository
                .existsByPhoneNumberAndStatus(phoneNumber, "ACTIVE");
        
        if (!isUplusMember) {
            throw new UplusUserNotFoundException("LG U+ 회원이 아닙니다.");
        }
        
        LocalDateTime now = LocalDateTime.now();

        UserUplusVerified verified = userUplusVerifiedRepository.findById(userId)
                .orElse(null);
        
        if (verified == null) {
            // 최초 인증
            verified = UserUplusVerified.createVerified(user, phoneNumber, now);
            userUplusVerifiedRepository.save(verified);
        } else {
            // 이미 레코드가 있으면 다시 인증 처리
            verified.verify(phoneNumber, now);
        }
        
        return UplusVerificationResponse.builder()
                .isVerified(true)
                .phoneNumber(phoneNumber)
                .verifiedAt(verified.getVerifiedAt())
                .build();
    }
    
}
