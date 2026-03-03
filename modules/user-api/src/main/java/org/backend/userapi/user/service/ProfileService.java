package org.backend.userapi.user.service;

import java.util.List;
import java.util.stream.Collectors;

import org.backend.userapi.user.dto.response.ProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import user.entity.AuthAccount;
import user.entity.Subscriptions;
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

  public ProfileResponse getMyProfile(Long userId) {
    // 유저 조회
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

    // 이메일 조회 (AuthAccountRepository에 추가한 메서드 사용)
    String email = authAccountRepository.findFirstByUserIdAndEmailIsNotNull(userId)
        .map(AuthAccount::getEmail)
        .orElse(null); // 이메일 없으면 null

    // 태그 목록 조회 (UserPreferredTagRepository에 추가한 메서드 사용)
    List<UserPreferredTag> userTags = userPreferredTagRepository.findAllByUserIdWithTag(userId);

    List<ProfileResponse.TagDto> tagDtos = userTags.stream()
        .map(upt -> ProfileResponse.TagDto.builder()
            .tagId(upt.getTag().getId())
            .name(upt.getTag().getName())
            .build())
        .collect(Collectors.toList());

    // 구독 상태 확인 ("SUBSCRIBED" / "NONE")
    boolean isSubscribed = subscriptionsRepository.findByUser_Id(userId)
    	    .map(Subscriptions::isAvailable)
    	    .orElse(false);

    String subStatus = isSubscribed ? "SUBSCRIBED" : "NONE";

    // 유플러스 인증 여부
    boolean isUPlus = user.getUplusVerified() != null && user.getUplusVerified().isVerified();

    // DTO 변환
    return ProfileResponse.builder()
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
  @Transactional 
  public ProfileResponse updateProfileImage(Long userId, String newImageUrl) {
      // 1. 유저 조회
      User user = userRepository.findById(userId)
          .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

      user.updateProfileImage(newImageUrl); 
      
      // 3. 변경된 정보가 반영된 프로필 정보를 다시 조회해서 반환
      return getMyProfile(userId);
  }
  
}
