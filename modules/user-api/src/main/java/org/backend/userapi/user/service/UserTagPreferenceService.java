package org.backend.userapi.user.service;

import java.util.List;

import org.backend.userapi.user.dto.request.PreferredTagUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.entity.Tag;
import common.repository.TagRepository; // ✅ import 확인 (common 패키지)
import lombok.RequiredArgsConstructor;
import user.entity.User;
import user.entity.UserPreferredTag;
import user.repository.UserPreferredTagRepository;
import user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserTagPreferenceService {

    private final UserPreferredTagRepository userPreferredTagRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;

    @Transactional
    public void updatePreferredTags(Long userId, PreferredTagUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        userPreferredTagRepository.deleteByUserId(userId);
        
        if (request.tagIds() == null || request.tagIds().isEmpty()) {
            return;
        }

        // 3. 새 태그 등록
        List<Tag> tags = tagRepository.findAllById(request.tagIds());
        
        List<UserPreferredTag> newTags = tags.stream()
                .map(tag -> UserPreferredTag.builder()
                        .user(user)
                        .tag(tag)
                        .build())
                .toList();
        
        userPreferredTagRepository.saveAll(newTags);
    }
}