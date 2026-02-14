package org.backend.userapi.auth.service;

import common.entity.Tag;
import common.enums.AuthProvider;
import common.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import user.repository.AuthAccountRepository;
import user.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthAccountRepository authAccountRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;

    /*
     * 이메일 중복 체크
     * EMAIL provider + email 조합으로 auth_accounts 테이블에 이미 존재하는지 확인
     */
    private void validateDuplicateEmail(String email) {
        if (authAccountRepository.existsByAuthProviderAndAuthProviderSubject(AuthProvider.EMAIL, email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
    }

    /*
     * 닉네임 중복 체크
     * users 테이블에 동일 닉네임이 존재하는지 확인
     */
    private void validateDuplicateNickname(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }
    }

    /**
     * 선호 태그 개수 검증 (3~5개)
     */
    private void validateTagCount(List<Long> tagIds) {
        if (tagIds.size() < 3 || tagIds.size() > 5) {
            throw new IllegalArgumentException("선호 태그는 3개 이상 5개 이하로 선택해야 합니다.");
        }
    }

    /**
     * 태그 ID 유효성 검증
     * DB에 실제 존재하는 태그인지 확인하고, 유효한 Tag 목록을 반환
     */
    private List<Tag> validateAndGetTags(List<Long> tagIds) {
        List<Tag> tags = tagRepository.findAllByIdIn(tagIds);
        if (tags.size() != tagIds.size()) {
            throw new IllegalArgumentException("유효하지 않은 태그 ID가 포함되어 있습니다.");
        }
        return tags;
    }
}
