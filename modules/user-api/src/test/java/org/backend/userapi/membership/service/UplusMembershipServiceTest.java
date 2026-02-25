package org.backend.userapi.membership.service;

import org.backend.userapi.membership.dto.UplusVerificationRequest;
import org.backend.userapi.membership.dto.UplusVerificationResponse;
import org.backend.userapi.membership.exception.UplusUserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import user.entity.User;
import user.entity.UserUplusVerified;
import user.repository.TelecomMemberRepository;
import user.repository.UserRepository;
import user.repository.UserUplusVerifiedRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UplusMembershipServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserUplusVerifiedRepository userUplusVerifiedRepository;
    @Mock private TelecomMemberRepository telecomMemberRepository;

    private UplusMembershipService service;

    @BeforeEach
    void setUp() {
        service = new UplusMembershipService(
                userRepository,
                userUplusVerifiedRepository,
                telecomMemberRepository
        );
    }

    @Test
    @DisplayName("최초 인증 성공 - createVerified + save 호출")
    void verify_firstTime_success() {
        // given
        Long userId = 1L;
        String phoneNumber = "01012345678";

        UplusVerificationRequest request = mock(UplusVerificationRequest.class);
        when(request.getPhoneNumber()).thenReturn(phoneNumber);

        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(telecomMemberRepository.existsByPhoneNumberAndStatus(phoneNumber, "ACTIVE"))
                .thenReturn(true);

        when(userUplusVerifiedRepository.findById(userId))
                .thenReturn(Optional.empty());

        UserUplusVerified created = mock(UserUplusVerified.class);
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 2, 26, 3, 30, 0);
        when(created.getVerifiedAt()).thenReturn(verifiedAt);

        try (MockedStatic<UserUplusVerified> mocked =
                     Mockito.mockStatic(UserUplusVerified.class)) {

            mocked.when(() ->
                    UserUplusVerified.createVerified(eq(user), eq(phoneNumber), any(LocalDateTime.class))
            ).thenReturn(created);

            // when
            UplusVerificationResponse response = service.verify(userId, request);

            // then
            verify(userUplusVerifiedRepository).save(created);
            assertThat(response.isVerified()).isTrue();  // isVerified 필드
            assertThat(response.getPhoneNumber()).isEqualTo(phoneNumber);
            assertThat(response.getVerifiedAt()).isEqualTo(verifiedAt);
        }
    }

    @Test
    @DisplayName("이미 인증된 경우 - verify() 호출, save 호출 안 함")
    void verify_existingUser_callsVerifyOnly() {
        // given
        Long userId = 2L;
        String phoneNumber = "01099998888";

        UplusVerificationRequest request = mock(UplusVerificationRequest.class);
        when(request.getPhoneNumber()).thenReturn(phoneNumber);

        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(telecomMemberRepository.existsByPhoneNumberAndStatus(phoneNumber, "ACTIVE"))
                .thenReturn(true);

        UserUplusVerified existing = mock(UserUplusVerified.class);
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 2, 26, 3, 31, 0);
        when(existing.getVerifiedAt()).thenReturn(verifiedAt);

        when(userUplusVerifiedRepository.findById(userId))
                .thenReturn(Optional.of(existing));

        // when
        UplusVerificationResponse response = service.verify(userId, request);

        // then
        verify(existing).verify(eq(phoneNumber), any(LocalDateTime.class));
        verify(userUplusVerifiedRepository, never()).save(existing);

        assertThat(response.isVerified()).isTrue();
        assertThat(response.getPhoneNumber()).isEqualTo(phoneNumber);
        assertThat(response.getVerifiedAt()).isEqualTo(verifiedAt);
    }

    @Test
    @DisplayName("U+ 회원이 아니면 예외 발생")
    void verify_notUplusMember_throwsException() {
        // given
        Long userId = 3L;
        String phoneNumber = "01000000000";

        UplusVerificationRequest request = mock(UplusVerificationRequest.class);
        when(request.getPhoneNumber()).thenReturn(phoneNumber);

        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(telecomMemberRepository.existsByPhoneNumberAndStatus(phoneNumber, "ACTIVE"))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> service.verify(userId, request))
                .isInstanceOf(UplusUserNotFoundException.class)
                .hasMessageContaining("LG U+ 회원이 아닙니다.");

        verify(userUplusVerifiedRepository, never()).save(any());
    }

    @Test
    @DisplayName("사용자가 없으면 예외 발생")
    void verify_userNotFound_throwsException() {
        // given
        Long userId = 999L;

        UplusVerificationRequest request = mock(UplusVerificationRequest.class);

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.verify(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다.");

        verify(telecomMemberRepository, never())
                .existsByPhoneNumberAndStatus(any(), any());
    }
}