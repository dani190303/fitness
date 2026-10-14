package com.fitness.auth.service;

import com.fitness.auth.dto.AuthResult;
import com.fitness.auth.dto.LoginRequest;
import com.fitness.auth.dto.RegisterRequest;
import com.fitness.auth.dto.UserResponse;
import com.fitness.auth.security.UserPrincipal;
import com.fitness.common.exception.ResourceNotFoundException;
import com.fitness.common.exception.UserAlreadyExistsException;
import com.fitness.common.util.SecurityUtils;
import com.fitness.user.User;
import com.fitness.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException("Username is already in use: " + request.username());
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = new User(request.username(), encodedPassword, request.displayName());
        User savedUser = userRepository.save(user);

        return new UserResponse(savedUser.getId(), savedUser.getUsername(), savedUser.getDisplayName());
    }

    public AuthResult login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            if (authentication.getPrincipal() instanceof UserPrincipal principal) {
                UserResponse userResponse = new UserResponse(
                        principal.getId(),
                        principal.getUsername(),
                        principal.getDisplayName()
                );
                return new AuthResult(userResponse, authentication);
            }

            throw new BadCredentialsException("Invalid username or password");
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
