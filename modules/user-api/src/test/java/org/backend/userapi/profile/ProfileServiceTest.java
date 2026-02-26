package org.backend.userapi.profile; // ★ 이 부분이 폴더 경로와 일치해야 합니다.

import common.enums.UserStatus;
import org.backend.userapi.user.dto.response.ProfileResponse;
import org.backend.userapi.user.service.ProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import user.entity.AuthAccount;
import user.entity.User;
import common.enums.SubscriptionStatus;
import user.repository.AuthAccountRepository;
import user.repository.SubscriptionsRepository;
import user.repository.UserPreferredTagRepository;
import user.repository.UserRepository;
import user.entity.Subscriptions;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

  @InjectMocks
  private ProfileService profileService; // 테스트할 서비스

  @Mock private UserRepository userRepository;
  @Mock private AuthAccountRepository authAccountRepository;
  @Mock private UserPreferredTagRepository userPreferredTagRepository;
  @Mock private SubscriptionsRepository subscriptionsRepository;

  @Test
  @DisplayName("마이페이지 조회 성공 테스트")
  void getMyProfile_Success() {
    // 1. Given (가짜 데이터 준비)
    Long userId = 1L;

    User mockUser = User.builder()
        .nickname("테스트유저")
        .userStatus(UserStatus.ACTIVE)
        .build();

    AuthAccount mockAccount = AuthAccount.builder()
        .email("test@example.com")
        .build();

    // Mocking: Repository가 호출될 때 반환할 가짜 값 설정
    given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    given(authAccountRepository.findFirstByUserIdAndEmailIsNotNull(userId)).willReturn(Optional.of(mockAccount));
    // 태그는 빈 리스트 반환
    given(userPreferredTagRepository.findAllByUserIdWithTag(userId)).willReturn(Collections.emptyList());
    // 구독 상태는 true(ACTIVE) 반환
    Subscriptions mockSubscription = Subscriptions.builder()
    	    .subscriptionStatus(SubscriptionStatus.ACTIVE)
    	    .expiredAt(LocalDateTime.now().plusDays(1)) // 만료 안 됨
    	    .build();

    	given(subscriptionsRepository.findByUser_Id(userId))
    	    .willReturn(Optional.of(mockSubscription));
    // 2. When (서비스 실행)
    ProfileResponse result = profileService.getMyProfile(userId);

    // 3. Then (검증)
    assertThat(result.getNickname()).isEqualTo("테스트유저");
    assertThat(result.getEmail()).isEqualTo("test@example.com");
    assertThat(result.getSubscriptionStatus()).isEqualTo("SUBSCRIBED");
  }
}