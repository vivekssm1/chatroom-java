package com.chatroom.config;

import com.chatroom.util.JwtUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Replaces the manual JWT middleware in server.js:
 *
 *   function authMiddleware(req, res, next) {
 *     const token = req.cookies?.token ...
 *     const payload = verifyToken(token)
 *     if (!payload) return res.status(401).json({error:"Unauthorized"})
 *     req.userId = payload.id
 *     next()
 *   }
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtUtil jwtUtil;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Same as bcrypt.hash(password, 10) in Node
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — same as Node (no built-in CSRF for REST APIs)
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless — JWTs in cookies, no server-side sessions
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Route permissions — mirrors the middleware usage in server.js
            .authorizeHttpRequests(auth -> auth
                // Public routes (no authMiddleware in Node)
                .requestMatchers(
                    "/api/register", "/api/login", "/api/logout",
                    "/api/admin/login", "/api/admin/logout",
                    "/ws/**",        // WebSocket endpoint
                    "/",  "/*.html", "/static/**", "/admin/**"
                ).permitAll()
                // Everything else requires a valid JWT
                .anyRequest().authenticated()
            )

            // Add our JWT filter before Spring's default auth filter
            .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Reads JWT from cookie (same as req.cookies?.token in Node),
     * then puts userId into Spring's SecurityContext so controllers
     * can call SecurityContextHolder.getContext().getAuthentication().getName()
     */
    @Bean
    public OncePerRequestFilter jwtAuthFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req,
                                            HttpServletResponse res,
                                            FilterChain chain) throws ServletException, IOException {

                String token = extractToken(req);
                if (token != null) {
                    String userId = jwtUtil.extractUserId(token);
                    String role   = jwtUtil.extractRole(token);

                    if (userId != null && "user".equals(role)) {
                        // Set authentication — controllers read this via Principal
                        var auth = new UsernamePasswordAuthenticationToken(
                            userId, null, java.util.Collections.emptyList()
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
                chain.doFilter(req, res);
            }

            private String extractToken(HttpServletRequest req) {
                // 1. Try cookie (primary — same as req.cookies?.token in Node)
                if (req.getCookies() != null) {
                    for (Cookie c : req.getCookies()) {
                        if ("token".equals(c.getName())) return c.getValue();
                    }
                }
                // 2. Fallback: Authorization: Bearer <token> header
                String header = req.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    return header.substring(7);
                }
                return null;
            }
        };
    }
}
