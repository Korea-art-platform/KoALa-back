package com.koala.koalaback.domain.user.event;

/**
 * 가입이 끝났다.
 *
 * 가입한 뒤에 해야 할 일이 도메인 밖에도 있다 — 지금은 같은 이메일로 했던
 * 비회원 주문을 계정에 붙이는 것. UserService 가 OrderService 를 직접 부르면
 * OrderService 가 이미 UserService 를 쓰고 있어 순환이 된다.
 */
public record UserSignedUpEvent(Long userId, String email) {}
