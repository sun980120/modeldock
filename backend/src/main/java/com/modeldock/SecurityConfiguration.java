package com.modeldock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfiguration {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityContextRepository contexts() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, DockRepository repo, SecurityContextRepository contexts) throws Exception {
    http.securityContext(c -> c.securityContextRepository(contexts))
        .csrf(
            c ->
                c.csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(
            c ->
                c.requestMatchers(
                        "/api/auth/csrf",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            c ->
                c.authenticationEntryPoint(
                        (q, r, e) -> json(r, 401, "UNAUTHENTICATED", "로그인이 필요합니다."))
                    .accessDeniedHandler(
                        (q, r, e) -> json(r, 403, "ACCESS_DENIED", "권한 또는 보안 토큰을 확인해 주세요.")))
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .logout(c -> c.disable())
        .addFilterBefore(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  HttpServletRequest q, HttpServletResponse r, FilterChain chain)
                  throws ServletException, IOException {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null
                    && auth.isAuthenticated()
                    && !auth.getName().equals("anonymousUser")) {
                  var user = repo.user(auth.getName());
                  boolean accountEndpoint = q.getRequestURI().startsWith("/api/auth/");
                  if (user.isEmpty()
                      || (!accountEndpoint && !user.get().status().equals("APPROVED"))) {
                    json(r, 403, "ACCOUNT_UNAVAILABLE", "승인 대기 중이거나 이용할 수 없는 계정입니다.");
                    return;
                  }
                  if (user.isPresent())
                    SecurityContextHolder.getContext()
                        .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                user.get().id(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.get().role()))));
                }
                chain.doFilter(q, r);
              }
            },
            AnonymousAuthenticationFilter.class);
    return http.build();
  }

  static void json(HttpServletResponse r, int status, String code, String message)
      throws IOException {
    r.setStatus(status);
    r.setContentType("application/json;charset=UTF-8");
    r.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
  }
}
