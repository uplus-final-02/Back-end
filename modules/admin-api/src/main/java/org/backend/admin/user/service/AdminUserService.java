package org.backend.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.backend.admin.user.dto.AdminUserListResponse;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.repository.AuthAccountRepository;
import user.repository.UserRepository;
import user.repository.projection.AdminLoginMethodProjection;
import user.repository.projection.AdminUserRowProjection;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;

    public Page<AdminUserListResponse> getUsers(String search, Pageable pageable) {
        Page<AdminUserRowProjection> userPage = userRepository.findAdminUserRows(search, pageable);

        List<AdminUserRowProjection> users = userPage.getContent();
        if (users.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, userPage.getTotalElements());
        }

        List<Long> userIds = users.stream()
                .map(AdminUserRowProjection::getUserId)
                .toList();

        List<AdminLoginMethodProjection> methods = authAccountRepository.findLoginMethodsByUserIds(userIds);

        Map<Long, List<AdminUserListResponse.LoginMethod>> methodsMap = methods.stream()
                .collect(Collectors.groupingBy(
                        AdminLoginMethodProjection::getUserId,
                        Collectors.mapping(
                                m -> new AdminUserListResponse.LoginMethod(m.getAuthProvider(), m.getIdentifier()),
                                Collectors.toList()
                        )
                ));

        List<AdminUserListResponse> content = users.stream()
                .map(u -> new AdminUserListResponse(
                        u.getUserId(),
                        u.getName(),
                        u.getCreatedAt(),
                        methodsMap.getOrDefault(u.getUserId(), List.of())
                ))
                .toList();

        return new PageImpl<>(content, pageable, userPage.getTotalElements());
    }
}