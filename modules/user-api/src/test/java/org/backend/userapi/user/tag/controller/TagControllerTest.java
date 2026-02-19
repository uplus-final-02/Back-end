package org.backend.userapi.user.tag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.backend.userapi.auth.jwt.UserPrincipal;
import org.backend.userapi.common.exception.GlobalExceptionHandler;
import org.backend.userapi.user.controller.TagController;
import org.backend.userapi.user.dto.request.PreferredTagUpdateRequest;
import org.backend.userapi.user.service.UserTagPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TagControllerTest {

    @Mock
    private UserTagPreferenceService userTagPreferenceService;

    @InjectMocks
    private TagController tagController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tagController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().equals(UserPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, 
                                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        UserPrincipal mockPrincipal = Mockito.mock(UserPrincipal.class);
                        Mockito.when(mockPrincipal.getUserId()).thenReturn(1L);
                        return mockPrincipal;
                    }
                })
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("선호 태그 변경 요청 시 200 OK를 반환한다")
    void updatePreferredTags_returnsOk() throws Exception {
        PreferredTagUpdateRequest request = new PreferredTagUpdateRequest(List.of(1L, 2L, 3L));

        doNothing().when(userTagPreferenceService).updatePreferredTags(eq(1L), any(PreferredTagUpdateRequest.class));

        mockMvc.perform(put("/api/users/me/preferred-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200)) // ApiResponse 필드명 status 확인
                .andExpect(jsonPath("$.message").value("선호 태그 변경 성공"));
    }
}