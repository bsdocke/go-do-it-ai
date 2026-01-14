package com.antigravity.jira.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .authorizeHttpRequests(authorize -> authorize
                                                .requestMatchers("/", "/css/**", "/js/**", "/images/**", "/webjars/**")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .oauth2Login(oauth2 -> oauth2
                                                .loginPage("/") // Our custom login page is the landing page
                                                .defaultSuccessUrl("/current", true))
                                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**")) // Allow H2 console
                                .headers(headers -> headers.frameOptions(FrameOptionsConfig::sameOrigin)); // Allow H2
                                                                                                           // console
                                                                                                           // frames

                return http.build();
        }
}
