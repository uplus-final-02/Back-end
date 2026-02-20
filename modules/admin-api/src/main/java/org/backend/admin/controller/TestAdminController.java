package org.backend.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestAdminController {

    @GetMapping("/api/admin/test")
    public String test() {
        return "ADMIN OK";
    }
}