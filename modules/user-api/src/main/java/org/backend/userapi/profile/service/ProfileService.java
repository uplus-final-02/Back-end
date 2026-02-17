package org.backend.userapi.profile.service;

import common.enums.UserStatus;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.profile.dto.ProfileDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.entity.AuthAccount;
import user.entity.User;
import user.entity.UserPreferredTag;
import user.repository.AuthAccountRepository;
import user.repository.SubscriptionsRepository;
import user.repository.UserPreferredTagRepository;
import user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)

public class ProfileService {
  private final UserRepository userRepository;
  private final AuthAccountRepository authAccountRepository;
  private final UserPreferredTagRepository userPreferredTagRepository;
  private final SubscriptionsRepository subscriptionsRepository;

  public ProfileDto getMyProfile(Long userId) {
    // 1. 유저 조회
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

    // 2. 이메일 조회 (AuthAccountRepository에 추가한 메서드 사용)
    String email = authAccountRepository.findFirstByUserIdAndEmailIsNotNull(userId)
        .map(AuthAccount::getEmail)
        .orElse(null); // 이메일 없으면 null

    // 3. 태그 목록 조회 (UserPreferredTagRepository에 추가한 메서드 사용)
    List<UserPreferredTag> userTags = userPreferredTagRepository.findAllByUserIdWithTag(userId);

    List<ProfileDto.TagDto> tagDtos = userTags.stream()
        .map(upt -> ProfileDto.TagDto.builder()
            .tagId(upt.getTag().getId())
            .name(upt.getTag().getName())
            .build())
        .collect(Collectors.toList());

    // 4. 구독 상태 확인 ("SUBSCRIBED" / "NONE")
    boolean isSubscribed = subscriptionsRepository.existsByUserIdAndStatus(userId, UserStatus.ACTIVE);
    String subStatus = isSubscribed ? "SUBSCRIBED" : "NONE";

    // 5. 유플러스 인증 여부
    boolean isUPlus = user.getUplusVerified() != null && user.getUplusVerified().isVerified();

    // 6. DTO 변환
    return ProfileDto.builder()
        .userId(user.getId())
        .email(email)
        .nickname(user.getNickname())
        .profileImageUrl(user.getProfileImage())
        .subscriptionStatus(subStatus)
        .isUPlusMember(isUPlus)
        .preferredTags(tagDtos)
        .createdAt(user.getCreatedAt())
        .lastNicknameChangedAt(user.getUpdatedAt())
        .build();
  }
}
