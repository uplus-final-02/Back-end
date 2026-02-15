package org.backend.userapi.auth.service;

import common.entity.Tag;
import common.enums.AuthProvider;
import common.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.auth.dto.SignupRequest;
import org.backend.userapi.auth.dto.SignupResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.entity.AuthAccount;
import user.entity.User;
import user.entity.UserPreferredTag;
import user.repository.AuthAccountRepository;
import user.repository.UserPreferredTagRepository;
import user.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthAccountRepository authAccountRepository;
    private final UserRepository userRepository;
    private final UserPreferredTagRepository userPreferredTagRepository;
    private final TagRepository tagRepository;
    private final PasswordEncoder passwordEncoder;

    /*
     * 회원가입
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 1. 검증
        validateDuplicateEmail(request.email());
        validateDuplicateNickname(request.nickname());
        validateTagCount(request.tagIds());
        List<Tag> tags = validateAndGetTags(request.tagIds());

        // 2. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        // 3. User 저장
        User user = User.builder()
                .nickname(request.nickname())
                .build();
        userRepository.save(user);

        // 4. AuthAccount 저장
        AuthAccount authAccount = AuthAccount.builder()
                .user(user)
                .authProvider(AuthProvider.EMAIL)
                .authProviderSubject(request.email())
                .email(request.email())
                .passwordHash(encodedPassword)
                .build();
        authAccountRepository.save(authAccount);

        // 5. UserPreferredTag 일괄 저장
        List<UserPreferredTag> preferredTags = tags.stream()
                .map(tag -> UserPreferredTag.builder()
                        .user(user)
                        .tag(tag)
                        .build())
                .toList();
        userPreferredTagRepository.saveAll(preferredTags);

        // 6. 응답 반환
        List<String> tagNames = tags.stream()
                .map(Tag::getName)
                .toList();

        return new SignupResponse(user.getId(), user.getNickname(), tagNames);
    }

    // ── 검증 메서드 ──

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

    /*
     * 선호 태그 개수 검증 (3~5개)
     */
    private void validateTagCount(List<Long> tagIds) {
        if (tagIds.size() < 3 || tagIds.size() > 5) {
            throw new IllegalArgumentException("선호 태그는 3개 이상 5개 이하로 선택해야 합니다.");
        }
    }

    /*
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
