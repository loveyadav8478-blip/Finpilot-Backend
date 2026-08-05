package com.finpilot.auth.service;

import com.finpilot.auth.domain.User;
import com.finpilot.auth.exceptions.EmailAlreadyExistsException;
import com.finpilot.auth.exceptions.InvalidCredentialsException;
import com.finpilot.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

// Handles registering a new user. Login (Stage 2) will be a separate
// method here later, once JWT issuing is built.
@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }


    public UUID register(String email, String rawPassword) {
        if (userRepository.findByEmail(email).isPresent()) {
            log.warn("An account with email " + email + " already exists. PLEASE LOGIN");
            throw new EmailAlreadyExistsException("An account with email " + email + " already exists. PLEASE LOGIN");
        }
        String encodedPassword = passwordEncoder.encode(rawPassword);
        User user = new User(email,encodedPassword);
        user = userRepository.save(user);

        return user.getId();
    }

    public String login(String email, String rawPassword){
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if(!passwordEncoder.matches(rawPassword,user.getPasswordHash())){
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getId(), email);
        return token;
    }
}