package org.backend.userapi.tag.service;

import lombok.RequiredArgsConstructor;
import org.backend.userapi.tag.dto.request.PreferredTagUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.entity.Tag;
import user.entity.User;
import user.entity.UserPreferredTag;
import common.repository.TagRepository; // ✅ import 확인 (common 패키지)
import user.repository.UserPreferredTagRepository;
import user.repository.UserRepository;

import java.util.List;

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

        // 1. 기존 태그 삭제
        userPreferredTagRepository.deleteByUserId(userId);
        
        // 🚨 [핵심 수정] 삭제 쿼리를 DB에 즉시 반영하라고 강제합니다.
        // 이게 없으면 insert가 먼저 실행되면서 "이미 존재하는 데이터"라고 에러가 납니다.
        userPreferredTagRepository.flush(); 

        // 2. 태그 삭제만 원하는 경우 종료
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