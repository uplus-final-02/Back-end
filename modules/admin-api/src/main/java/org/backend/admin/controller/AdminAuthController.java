package org.backend.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.backend.admin.auth.dto.AdminLoginRequest;
import org.backend.admin.auth.dto.AdminLoginResponse;
import org.backend.admin.auth.service.AdminAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/admin/login")
    public ResponseEntity<AdminLoginResponse> login(@RequestBody @Valid AdminLoginRequest request) {
    	log.info("[ADMIN] login HIT");
    	return ResponseEntity.ok(adminAuthService.login(request));
    }
}
