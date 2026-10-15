package com.modeldock;

import static com.modeldock.Models.*;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  final AccountService accounts;
  final DockRepository repo;
  final SecurityContextRepository contexts;
  final AuthRateService rates;

  public AuthController(
      AccountService accounts,
      DockRepository repo,
      SecurityContextRepository contexts,
      AuthRateService rates) {
    this.accounts = accounts;
    this.repo = repo;
    this.contexts = contexts;
    this.rates = rates;
  }

  @GetMapping("/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/register")
  UserView register(@Valid @RequestBody Register request, HttpServletRequest req) {
    rates.check("register", request.email(), req.getRemoteAddr());
    return accounts.register(request);
  }

  @PostMapping("/login")
  UserView login(
      @Valid @RequestBody Login request, HttpServletRequest req, HttpServletResponse res) {
    rates.check("login", request.email(), req.getRemoteAddr());
    User u = accounts.login(request);
    req.getSession(true);
    req.changeSessionId();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(
            u.id(), null, List.of(new SimpleGrantedAuthority("ROLE_" + u.role()))));
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, req, res);
    return u.view();
  }

  @GetMapping("/me")
  UserView me(Principal p) {
    return repo.user(p.getName()).orElseThrow(ApiException::missing).view();
  }

  @PostMapping("/logout")
  Map<String, Boolean> logout(HttpServletRequest req) {
    var session = req.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    return Map.of("ok", true);
  }
}
