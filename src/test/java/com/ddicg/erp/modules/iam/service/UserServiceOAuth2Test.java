package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.core.common.model.enums.RoleType;
import com.ddicg.erp.modules.iam.dto.GoogleUserInfo;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ddicg.erp.modules.iam.dto.response.OAuth2TokenResponse;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
@ExtendWith(MockitoExtension.class)
class UserServiceOAuth2Test {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;
    @Mock
    private AuthTicketStore authTicketStore;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private JwtService jwtService;


    private GoogleUserInfo googleUserInfo;

    @BeforeEach
    void setUp() {
        googleUserInfo = new GoogleUserInfo();
        googleUserInfo.setSub("google-123");
        googleUserInfo.setEmail("test@gmail.com");
        googleUserInfo.setName("Test User");
        googleUserInfo.setPicture("http://avatar.com/pic.jpg");
    }

    @Test
    void processOAuth2User_ExistingGoogleUser_UpdatesAvatar() {
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setGoogleId("google-123");
        existingUser.setAvatarUrl("http://old-avatar.com/pic.jpg");

        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        User result = userService.processOAuth2User(googleUserInfo);

        assertNotNull(result);
        assertEquals("google-123", result.getGoogleId());
        assertEquals("http://avatar.com/pic.jpg", result.getAvatarUrl());
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository).save(existingUser);
    }

    @Test
    void processOAuth2User_ExistingEmailUser_AutoLinksAccount() {
        User existingUser = new User();
        existingUser.setId(2L);
        existingUser.setEmail("test@gmail.com");
        existingUser.setAvatarUrl(null);
        existingUser.setAuthProvider("LOCAL");

        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        User result = userService.processOAuth2User(googleUserInfo);

        assertNotNull(result);
        assertEquals("google-123", result.getGoogleId());
        assertEquals("http://avatar.com/pic.jpg", result.getAvatarUrl());
        assertEquals("GOOGLE", result.getAuthProvider());
        verify(userRepository).save(existingUser);
    }

    @Test
    void processOAuth2User_NewUser_CreatesSuccessfully() {
        when(userRepository.findByGoogleId("google-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random-uuid");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        User result = userService.processOAuth2User(googleUserInfo);

        assertNotNull(result);
        assertEquals("test@gmail.com", result.getEmail());
        assertEquals("test@gmail.com", result.getName());
        assertEquals("Test User", result.getFullName());
        assertEquals("http://avatar.com/pic.jpg", result.getAvatarUrl());
        assertEquals("google-123", result.getGoogleId());
        assertEquals("GOOGLE", result.getAuthProvider());
        assertEquals(ActiveStatus.ACTIVE, result.getStatus());
        assertTrue(result.getRoles().contains(RoleType.USER));
        assertEquals("encoded-random-uuid", result.getPassword());

        verify(userRepository).save(any(User.class));
    }

    @Test
    void issueTokensFromTicket_Success() {
        String ticket = "valid-ticket";
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        user.setName("test@gmail.com");
        user.setEmail("test@gmail.com");
        
        UserDetails userDetails = mock(UserDetails.class);
        
        when(authTicketStore.consumeTicket(ticket)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(userDetails);
        when(jwtService.generateToken(eq(userDetails), anyLong())).thenReturn("mock-token");

        OAuth2TokenResponse response = userService.issueTokensFromTicket(ticket);

        assertNotNull(response);
        assertEquals("mock-token", response.getAccessToken());
        assertEquals("mock-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600L, response.getExpiresIn());
        
        verify(authTicketStore).consumeTicket(ticket);
        verify(jwtService, times(2)).generateToken(eq(userDetails), anyLong());
    }

    @Test
    void issueTokensFromTicket_ThrowsException_WhenUserNotFound() {
        String ticket = "valid-ticket";
        Long userId = 99L;
        
        when(authTicketStore.consumeTicket(ticket)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.issueTokensFromTicket(ticket));
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(authTicketStore).consumeTicket(ticket);
    }
}
