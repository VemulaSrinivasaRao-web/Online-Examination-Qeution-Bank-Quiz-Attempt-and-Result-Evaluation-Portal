package com.example.exam.config;

import com.example.exam.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Autowired
        private CustomUserDetailsService userDetailsService;

        @Autowired
        private CustomAuthSuccessHandler customAuthSuccessHandler;

        @Bean
        public static PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http.csrf(csrf -> csrf.disable())
                                .authorizeHttpRequests((authorize) -> authorize
                                                .requestMatchers("/js/**", "/static/js/**", "/css/**", "/images/**",
                                                                "/uploads/**")
                                                .permitAll()
                                                // Allow static resources and public pages
                                                .requestMatchers("/", "/login", "/register", "/h2-console/**", "/access-denied")
                                                .permitAll()
                                                // Shared Exam/Question Management (allow both roles)
                                                .requestMatchers("/admin/exam/manage-questions/**",
                                                                "/admin/question/**",
                                                                "/admin/exam/*/question/**")
                                                .hasAnyRole("ADMIN", "EXAMINER")
                                                // Admin-only pages
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                // Examiner-only pages
                                                .requestMatchers("/examiner/**").hasRole("EXAMINER")
                                                // Student-only pages
                                                .requestMatchers("/student/**", "/exam/**").hasRole("STUDENT")
                                                // All other requests need authentication
                                                .anyRequest().authenticated())
                                .exceptionHandling(ex -> ex
                                                .accessDeniedHandler((request, response, denied) ->
                                                                response.sendRedirect("/access-denied")))
                                .formLogin(
                                                form -> form
                                                                .loginPage("/login")
                                                                .loginProcessingUrl("/login")
                                                                .successHandler(customAuthSuccessHandler)
                                                                .failureUrl("/login?error=true")
                                                                .permitAll())
                                .logout(
                                                logout -> logout
                                                                .logoutRequestMatcher(
                                                                                new AntPathRequestMatcher("/logout"))
                                                                .permitAll())
                                .userDetailsService(userDetailsService)
                                // This is needed for H2 console
                                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));

                return http.build();
        }
}
/***
 * Subscribe Lazycoder -
 * https://www.youtube.com/c/LazyCoderOnline?sub_confirmation=1
 * whatsapp - https://wa.me/919572181024
 * email - wapka1503@gmail.com
 ***/