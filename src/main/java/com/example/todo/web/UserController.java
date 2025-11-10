package com.example.todo.web;

import com.example.todo.dto.MeDto;
import com.example.todo.entity.AppUser;
import com.example.todo.service.UserService;
import com.example.todo.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    public UserController(UserService userService) { this.userService = userService; }

    @GetMapping("/me")
    public ResponseEntity<MeDto> me(Authentication authentication) {
        var principal = (UserPrincipal) authentication.getPrincipal();
        AppUser u = userService.getById(principal.getId());
        MeDto dto = new MeDto();
        dto.id = u.getId();
        dto.email = u.getEmail();
        dto.displayName = u.getDisplayName();
        return ResponseEntity.ok(dto);
    }
}
