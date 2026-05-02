package de.devboard;

import de.devboard.domain.QaRepository;
import de.devboard.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired QaRepository qaRepository;

    @BeforeEach
    void wipeTables() {
        // qa.user_id → users; feedback cascades from qa. Delete qa first, then users.
        qaRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void authBoundaries() throws Exception {
        // Unauthenticated GET / redirects to /login
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        // First signup → first user, auto-ADMIN, auto-logged-in
        MvcResult adminSignup = mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("email", "admin@test.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession adminSession = (MockHttpSession) adminSignup.getRequest().getSession(false);

        // Authenticated user can GET /
        mockMvc.perform(get("/").session(adminSession))
                .andExpect(status().isOk());

        // ADMIN can POST /ingest (controller catches file-not-found, returns 200)
        mockMvc.perform(post("/ingest")
                        .with(csrf())
                        .session(adminSession)
                        .param("folderPath", "/nonexistent"))
                .andExpect(status().isOk());

        // Second signup → USER role, auto-logged-in
        MvcResult userSignup = mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("email", "user@test.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession userSession = (MockHttpSession) userSignup.getRequest().getSession(false);

        // USER gets 403 on POST /ingest
        mockMvc.perform(post("/ingest")
                        .with(csrf())
                        .session(userSession)
                        .param("folderPath", "/nonexistent"))
                .andExpect(status().isForbidden());
    }
}
