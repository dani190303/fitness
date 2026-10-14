package com.fitness.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fitness.auth.dto.AuthResult;
import com.fitness.auth.dto.LoginRequest;
import com.fitness.auth.dto.RegisterRequest;
import com.fitness.auth.dto.UserResponse;
import com.fitness.auth.security.UserPrincipal;
import com.fitness.common.exception.UserAlreadyExistsException;
import com.fitness.user.User;
import com.fitness.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Should successfully register a new user")
    void register_Success() {
        RegisterRequest request = new RegisterRequest("testuser", "securePass123", "Test User");

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode("securePass123")).thenReturn("$2a$10$hashedpassword");

        User savedUser = new User("testuser", "$2a$10$hashedpassword", "Test User");
        savedUser.setId(1L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("testuser", response.username());
        assertEquals("Test User", response.displayName());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw UserAlreadyExistsException when username already exists")
    void register_DuplicateUsername_ThrowsException() {
        RegisterRequest request = new RegisterRequest("existinguser", "securePass123", "Existing User");

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        UserAlreadyExistsException exception = assertThrows(
                UserAlreadyExistsException.class,
                () -> authService.register(request)
        );

        assertEquals("Username is already in use: existinguser", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Should successfully login with valid credentials")
    void login_Success() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        UserPrincipal principal = new UserPrincipal(
                1L, "testuser", "hashed", "Test User", true,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        AuthResult result = authService.login(request);

        assertNotNull(result);
        assertEquals(1L, result.user().id());
        assertEquals("testuser", result.user().username());
        assertEquals("Test User", result.user().displayName());
        assertEquals(authentication, result.authentication());
    }

    @Test
    @DisplayName("Should throw BadCredentialsException when password is invalid")
    void login_InvalidPassword_ThrowsBadCredentialsException() {
        LoginRequest request = new LoginRequest("testuser", "wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals("Invalid username or password", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw BadCredentialsException with generic message when user is disabled")
    void login_DisabledUser_ThrowsBadCredentialsException() {
        LoginRequest request = new LoginRequest("inactiveuser", "password123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("User is disabled"));

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals("Invalid username or password", exception.getMessage());
    }
}
