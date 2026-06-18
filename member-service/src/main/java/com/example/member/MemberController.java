package com.example.member;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/member")
public class MemberController {

    private final JwtProvider jwtProvider;

    public MemberController(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    // 학습용: 비밀번호 검증 없이 id만 받아 토큰 발급
    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> body) {
        String id = body.getOrDefault("id", "anonymous");
        String token = jwtProvider.createToken(id);
        return Map.of("userId", id, "token", token);
    }
}
