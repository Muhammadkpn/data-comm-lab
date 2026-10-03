package com.learn.datacomm.auth.config;

import com.learn.datacomm.auth.jwt.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.util.Collections;

/**
 * Empat pola autentikasi berdampingan, masing-masing di SecurityFilterChain sendiri
 * (dipilih berdasarkan prefix path) -- meniru satu backend yang melayani beberapa jenis client:
 *
 *   /mobile/**    Bearer JWT, stateless       (Aira: mobile app)
 *   /web/**       Session cookie + CSRF       (Aira: EBizbanking di browser)
 *   /partner/**   API key di header           (integrasi partner server-to-server)
 *   /internal/**  HTTP Basic                  (tooling internal, hanya lewat HTTPS)
 *
 * Kenapa mobile tidak butuh CSRF tapi web butuh? CSRF terjadi karena BROWSER otomatis
 * melampirkan cookie ke request lintas situs. Bearer token tidak dilampirkan otomatis
 * oleh siapa pun -- client harus menaruhnya sendiri di header -- jadi tidak bisa dipalsukan
 * lewat form/situs lain.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * User demo. Authority SCOPE_* dipakai untuk menentukan scope token mobile:
     *   andi -> boleh baca rekening DAN transfer
     *   budi -> hanya boleh baca (untuk demo 403 insufficient_scope)
     */
    @Bean
    public UserDetailsService users(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("andi").password(encoder.encode("andi123"))
                        .authorities("SCOPE_accounts:read", "SCOPE_transfers:write").build(),
                User.withUsername("budi").password(encoder.encode("budi123"))
                        .authorities("SCOPE_accounts:read").build(),
                User.withUsername("ops").password(encoder.encode("ops-secret"))
                        .roles("OPS").build());
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain mobileChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http.antMatcher("/mobile/**")
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                .addFilterBefore(new BearerTokenFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .authorizeRequests()
                    .antMatchers("/mobile/auth/**").permitAll()
                    .antMatchers("/mobile/transfers/**").hasAuthority("SCOPE_transfers:write")
                    .anyRequest().hasAuthority("SCOPE_accounts:read").and()
                .exceptionHandling()
                    .authenticationEntryPoint(BearerErrorHandlers.entryPoint())
                    .accessDeniedHandler(BearerErrorHandlers.accessDenied());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http.antMatcher("/web/**")
                // Token CSRF disimpan di cookie XSRF-TOKEN (bisa dibaca JavaScript halaman
                // kita sendiri), lalu harus dikirim balik di header X-XSRF-TOKEN. Situs lain
                // tidak bisa membaca cookie kita, jadi tidak bisa menyertakan header ini.
                .csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringAntMatchers("/web/login").and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).and()
                .authorizeRequests()
                    .antMatchers("/web/login").permitAll()
                    .anyRequest().authenticated().and()
                // API JSON: jawab 401, jangan redirect ke halaman login HTML.
                .exceptionHandling().authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain partnerChain(HttpSecurity http) throws Exception {
        http.antMatcher("/partner/**")
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                .addFilterBefore(new ApiKeyFilter(Collections.singletonMap("pk_test_mitra_ewallet_001", "mitra-ewallet")),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeRequests().anyRequest().hasRole("PARTNER").and()
                .exceptionHandling().authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain internalChain(HttpSecurity http) throws Exception {
        http.antMatcher("/internal/**")
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                .authorizeRequests().anyRequest().hasRole("OPS").and()
                .httpBasic();
        return http.build();
    }

    /** Sisanya (`/naive/**` demo verifier rentan, `/error`) tidak memakai Spring Security. */
    @Bean
    @Order(5)
    public SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeRequests()
                    .antMatchers("/naive/**", "/error").permitAll()
                    .anyRequest().denyAll();
        return http.build();
    }
}
