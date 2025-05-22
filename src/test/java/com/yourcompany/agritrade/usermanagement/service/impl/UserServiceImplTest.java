//package com.yourcompany.agritrade.usermanagement.service.impl;
//
//
//import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
//import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
//import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
//import com.google.api.client.http.javanet.NetHttpTransport;
//import com.google.api.client.json.jackson2.JacksonFactory;
//import com.yourcompany.agritrade.usermanagement.domain.User;
//import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//
//import java.io.IOException;
//import java.security.GeneralSecurityException;
//import java.util.Collections;
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
//class UserServiceImplTest {
//
//    @Mock
//    private UserRepository userRepository;
//
//    @Mock
//    private JwtUtil jwtUtil;
//
//    @Mock
//    private GoogleIdTokenVerifier googleIdTokenVerifier;
//
//    @InjectMocks
//    private UserServiceImpl userService;
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//        // Provide a default verifier in case it's used in the tested method logic (though we are mocking it explicitly)
//        userService.setGoogleIdTokenVerifier(new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new JacksonFactory())
//                .setAudience(Collections.singletonList("test_client_id"))
//                .build());
//    }
//
//    @Test
//    void processGoogleLogin_existingUser() throws GeneralSecurityException, IOException {
//        String idTokenString = "someValidIdToken";
//        String email = "testuser@example.com";
//        String name = "Test User";
//        String pictureUrl = "http://example.com/pic.jpg";
//
//        GoogleIdToken googleIdToken = mock(GoogleIdToken.class);
//        Payload payload = new Payload();
//        payload.setEmail(email);
//        payload.set("name", name);
//        payload.set("picture", pictureUrl);
//
//        when(googleIdToken.getPayload()).thenReturn(payload);
//        when(googleIdTokenVerifier.verify(idTokenString)).thenReturn(googleIdToken);
//
//        User existingUser = new User();
//        existingUser.setEmail(email);
//        existingUser.setName(name);
//        existingUser.setPictureUrl(pictureUrl);
//
//        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
//        when(jwtUtil.generateToken(email)).thenReturn("accessToken");
//        when(jwtUtil.generateRefreshToken(email)).thenReturn("refreshToken");
//
//        UserLoginDto userLoginDto = userService.processGoogleLogin(idTokenString);
//
//        assertNotNull(userLoginDto);
//        assertEquals(email, userLoginDto.getEmail());
//        assertEquals(name, userLoginDto.getName());
//        assertEquals("accessToken", userLoginDto.getAccessToken());
//        assertEquals("refreshToken", userLoginDto.getRefreshToken());
//        verify(userRepository, never()).save(any(User.class));
//    }
//
//    @Test
//    void processGoogleLogin_newUser() throws GeneralSecurityException, IOException {
//        String idTokenString = "someValidIdToken";
//        String email = "newUser@example.com";
//        String name = "New User";
//        String pictureUrl = "http://example.com/newpic.jpg";
//
//        GoogleIdToken googleIdToken = mock(GoogleIdToken.class);
//        Payload payload = new Payload();
//        payload.setEmail(email);
//        payload.set("name", name);
//        payload.set("picture", pictureUrl);
//
//        when(googleIdToken.getPayload()).thenReturn(payload);
//        when(googleIdTokenVerifier.verify(idTokenString)).thenReturn(googleIdToken);
//
//        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
//        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
//            User savedUser = invocation.getArgument(0);
//            savedUser.setId(1L); // Simulate saving and getting an ID
//            return savedUser;
//        });
//        when(jwtUtil.generateToken(email)).thenReturn("accessToken");
//        when(jwtUtil.generateRefreshToken(email)).thenReturn("refreshToken");
//
//        UserLoginDto userLoginDto = userService.processGoogleLogin(idTokenString);
//
//        assertNotNull(userLoginDto);
//        assertEquals(email, userLoginDto.getEmail());
//        assertEquals(name, userLoginDto.getName());
//        assertEquals("accessToken", userLoginDto.getAccessToken());
//        assertEquals("refreshToken", userLoginDto.getRefreshToken());
//        verify(userRepository, times(1)).save(any(User.class));
//    }
//
//    @Test
//    void processGoogleLogin_invalidToken() throws GeneralSecurityException, IOException {
//        String idTokenString = "invalidIdToken";
//
//        when(googleIdTokenVerifier.verify(idTokenString)).thenReturn(null);
//
//        assertThrows(InvalidTokenException.class, () -> userService.processGoogleLogin(idTokenString));
//        verify(userRepository, never()).findByEmail(anyString());
//        verify(userRepository, never()).save(any(User.class));
//        verify(jwtUtil, never()).generateToken(anyString());
//        verify(jwtUtil, never()).generateRefreshToken(anyString());
//    }
//
//    @Test
//    void refreshToken_validToken() {
//        String oldRefreshToken = "validRefreshToken";
//        String email = "testuser@example.com";
//        String newAccessToken = "newAccessToken";
//        String newRefreshToken = "newRefreshToken";
//
//        when(jwtUtil.extractUsername(oldRefreshToken)).thenReturn(email);
//        when(jwtUtil.isTokenValid(oldRefreshToken, email)).thenReturn(true);
//        when(jwtUtil.generateToken(email)).thenReturn(newAccessToken);
//        when(jwtUtil.generateRefreshToken(email)).thenReturn(newRefreshToken);
//
//        UserDto userDto = userService.refreshToken(oldRefreshToken);
//
//        assertNotNull(userDto);
//        assertEquals(email, userDto.getEmail());
//        assertEquals(newAccessToken, userDto.getAccessToken());
//        assertEquals(newRefreshToken, userDto.getRefreshToken());
//        verify(jwtUtil).extractUsername(oldRefreshToken);
//        verify(jwtUtil).isTokenValid(oldRefreshToken, email);
//        verify(jwtUtil).generateToken(email);
//        verify(jwtUtil).generateRefreshToken(email);
//    }
//
//    @Test
//    void refreshToken_invalidToken_usernameExtractionFails() {
//        String oldRefreshToken = "invalidRefreshToken";
//
//        when(jwtUtil.extractUsername(oldRefreshToken)).thenReturn(null);
//
//        assertThrows(InvalidTokenException.class, () -> userService.refreshToken(oldRefreshToken));
//        verify(jwtUtil).extractUsername(oldRefreshToken);
//        verify(jwtUtil, never()).isTokenValid(anyString(), anyString());
//        verify(jwtUtil, never()).generateToken(anyString());
//        verify(jwtUtil, never()).generateRefreshToken(anyString());
//    }
//
//    @Test
//    void refreshToken_invalidToken_validationFails() {
//        String oldRefreshToken = "invalidRefreshToken";
//        String email = "testuser@example.com";
//
//        when(jwtUtil.extractUsername(oldRefreshToken)).thenReturn(email);
//        when(jwtUtil.isTokenValid(oldRefreshToken, email)).thenReturn(false);
//
//        assertThrows(InvalidTokenException.class, () -> userService.refreshToken(oldRefreshToken));
//        verify(jwtUtil).extractUsername(oldRefreshToken);
//        verify(jwtUtil).isTokenValid(oldRefreshToken, email);
//        verify(jwtUtil, never()).generateToken(anyString());
//        verify(jwtUtil, never()).generateRefreshToken(anyString());
//    }
//}