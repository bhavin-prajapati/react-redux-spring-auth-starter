package com.example.spring.server.controller;

import com.example.spring.server.exception.ResourceNotFoundException;
import com.example.spring.server.model.ApplicationUser;
import com.example.spring.server.repository.UserRepository;
import com.example.spring.server.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.server.Session;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

import static com.example.spring.server.utils.Constants.COOKIE_EXPIRATION_TIME;
import static com.example.spring.server.utils.Constants.SESSION_COOKIE_NAME;

@RestController
@RequestMapping("/auth")
public class UserController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BCryptPasswordEncoder passwordEncoder;

    @Autowired
    JwtTokenProvider tokenProvider;

    @PostMapping(path="/register", produces=MediaType.APPLICATION_JSON_VALUE, consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @CrossOrigin(origins = "http://localhost:3000")
    public Map<String, Object> register(@Valid @RequestBody MultiValueMap paramMap) {
        HashMap<String, Object> response = new HashMap<>();
        String username = (String)paramMap.getFirst("username");
        String email = (String)paramMap.getFirst("email");
        String password = (String)paramMap.getFirst("password");
        if(username != null && email != null && password != null) {
            ApplicationUser applicationUser = userRepository.findByUsername(username);
            if (applicationUser == null) {
                applicationUser = userRepository.findByEmail(email);
                if (applicationUser == null) {
                    applicationUser = new ApplicationUser();
                    applicationUser.setUsername(username);
                    applicationUser.setPassword(passwordEncoder.encode(password));
                    applicationUser.setEmail(email);
                    ApplicationUser createdApplicationUser = userRepository.save(applicationUser);
                    if(createdApplicationUser != null) {
                        response.put("message", "User successfully created");
                        return response;
                    } else {
                        response.put("error", "Internal Server Error");
                        return response;
                    }
                } else {
                    response.put("error", "User with this email already exists");
                    return response;
                }
            } else {
                response.put("error", "User with this username already exists");
                return response;
            }
        } else {
            response.put("error", "Invalid request");
            return response;
        }
    }

    @PostMapping(path="/signin", produces=MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<Map<String, Object>> signin(@Valid @RequestBody MultiValueMap paramMap, HttpServletResponse resp) {
        HashMap<String, Object> response = new HashMap<>();
        String username = (String)paramMap.getFirst("username");
        String password = (String)paramMap.getFirst("password");
        if(username != null && password != null) {
            ApplicationUser applicationUser = userRepository.findByUsername(username);
            if (applicationUser != null) {
                boolean isPasswordValid = passwordEncoder.matches(password, applicationUser.getPassword());
                if(isPasswordValid) {
                    Authentication authentication = authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    username,
                                    password
                            )
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    String jwt = tokenProvider.generateToken(authentication);

                    final Cookie cookie = new Cookie(SESSION_COOKIE_NAME, jwt);
                    cookie.setSecure(true);
                    cookie.setHttpOnly(true);
                    cookie.setMaxAge(COOKIE_EXPIRATION_TIME);
                    resp.addCookie(cookie);

                    response.put("message", "User login successful");
                    return ResponseEntity.ok(response);
                } else {
                    response.put("error", "Invalid username or password");
                    return ResponseEntity.ok(response);
                }
            } else {
                response.put("error", "Invalid username or password");
                return ResponseEntity.ok(response);
            }
        } else {
            response.put("error", "Invalid request");
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/user/{userId}")
    @CrossOrigin(origins = "http://localhost:3000")
    public ApplicationUser getUserById(@PathVariable Long userId) {
        return userRepository.getOne(userId);
    }

    @PutMapping("/user/{userId}")
    @CrossOrigin(origins = "http://localhost:3000")
    public ApplicationUser updateUser(@PathVariable Long userId, @Valid @RequestBody ApplicationUser applicationUserRequest) {
        return userRepository.findById(userId)
                .map(user -> {
                    user.setUsername(applicationUserRequest.getUsername());
                    user.setEmail(applicationUserRequest.getEmail());
                    return userRepository.save(user);
                }).orElseThrow(() -> new ResourceNotFoundException("ApplicationUser not found with id " + userId));
    }

    @DeleteMapping("/user/{userId}")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    userRepository.delete(user);
                    return ResponseEntity.ok().build();
                }).orElseThrow(() -> new ResourceNotFoundException("ApplicationUser not found with id " + userId));
    }
}
