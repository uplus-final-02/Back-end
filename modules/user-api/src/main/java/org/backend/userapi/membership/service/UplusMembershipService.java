package org.backend.userapi.membership.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.backend.userapi.common.exception.ConflictException;
import org.backend.userapi.membership.dto.UplusVerificationRequest;
import org.backend.userapi.membership.dto.UplusVerificationResponse;
import org.backend.userapi.membership.exception.UplusUserNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.entity.User;
import user.entity.UserUplusVerified;
import user.repository.TelecomMemberRepository;
import user.repository.UserRepository;
import user.repository.UserUplusVerifiedRepository;

import java.time.LocalDateTime;

@Slf4j
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

        String phoneNumber = normalize(request.getPhoneNumber());
        
        boolean isUplusMember = telecomMemberRepository
                .existsByPhoneNumberAndStatus(phoneNumber, "ACTIVE");
        
        if (!isUplusMember) {
            throw new UplusUserNotFoundException("LG U+ 회원이 아닙니다.");
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        UserUplusVerified existingByPhone =
                userUplusVerifiedRepository.findByPhoneNumber(phoneNumber).orElse(null);

        if (existingByPhone != null) {
            Long existingUserId = existingByPhone.getUser().getId();
            if (!existingUserId.equals(userId)) {
                throw new ConflictException("이미 다른 계정에서 인증된 전화번호입니다.");
            }
        }
        
        UserUplusVerified verified = userUplusVerifiedRepository.findByUser_Id(userId)
                .orElse(null);
         
        if (verified == null) {
            verified = UserUplusVerified.createVerified(user, phoneNumber, now);
            userUplusVerifiedRepository.save(verified); 
        } else {
            verified.verify(phoneNumber, now); 
        }
        
        try {
            userUplusVerifiedRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("이미 다른 계정에서 인증된 전화번호입니다.");
        }

        return UplusVerificationResponse.builder()
                .isVerified(true)
                .phoneNumber(phoneNumber)
                .verifiedAt(verified.getVerifiedAt())
                .build();
    }
    
    private String normalize(String phoneNumber) {
        return phoneNumber == null ? null : phoneNumber.replace("-", "").trim();
    }
    
}
