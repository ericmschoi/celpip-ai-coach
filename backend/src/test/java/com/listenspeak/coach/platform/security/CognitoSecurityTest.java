package com.listenspeak.coach.platform.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The deployed configuration. Nothing under /api/v1 may be reachable without a
 * valid token, and the dev header must be ignored entirely.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "app.auth.mode=COGNITO",
            "app.auth.issuer-uri=https://cognito-idp.ca-central-1.amazonaws.com/ca-central-1_test"
        })
@MockitoBean(types = JwtDecoder.class)
class CognitoSecurityTest {

    private MockMvc mockMvc;

    @Autowired
    void setUp(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void rejectsUnauthenticatedApiCalls() throws Exception {
        mockMvc.perform(get("/api/v1/config")).andExpect(status().isUnauthorized());
    }

    @Test
    void ignoresTheLocalDevHeaderWhenCognitoIsEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/config").header(LocalStubAuthenticationFilter.HEADER, "someone-else"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keepsHealthPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
