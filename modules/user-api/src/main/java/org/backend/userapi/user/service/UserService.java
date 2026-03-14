package org.backend.userapi.user.service;

import lombok.RequiredArgsConstructor;
import org.backend.userapi.user.dto.response.NicknameUpdateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.enums.WithdrawalReason;
import user.entity.User;
import user.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

  private final UserRepository userRepository;

  // 변경 주기 (30일)
  private static final int CHANGE_LIMIT_DAYS = 30;

  public NicknameUpdateResponse updateNickname(Long userId, String newNickname) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

    // 중복 검사
    if (!user.getNickname().equals(newNickname) && userRepository.existsByNickname(newNickname)) {
      throw new IllegalArgumentException("이미 존재하는 닉네임입니다.");
    }

    // 변경 주기 체크
    LocalDateTime lastModified = user.getUpdatedAt();

    if (lastModified != null) {
      LocalDateTime availableAt = lastModified.plusDays(CHANGE_LIMIT_DAYS);
      if (availableAt.isAfter(LocalDateTime.now())) {
        throw new IllegalStateException("닉네임은 30일마다 변경할 수 있습니다. 다음 변경 가능일: " + availableAt);
      }
    }

    // 닉네임 변경
    user.updateNickname(newNickname);

    // 응답 반환
    return NicknameUpdateResponse.builder()
        .nickname(newNickname)
        .nextChangeAvailableAt(LocalDateTime.now().plusDays(CHANGE_LIMIT_DAYS))
        .build();
  }
  
  public void withdraw(Long userId, WithdrawalReason reason) {

	    User user = userRepository.findById(userId)
	            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

	    if (user.isWithdrawn()) {
	        throw new IllegalStateException("이미 탈퇴 처리된 회원입니다.");
	    }

	    user.withdraw(reason);
	}
}