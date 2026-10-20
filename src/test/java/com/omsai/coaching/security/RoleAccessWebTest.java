package com.omsai.coaching.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The access matrix of the application over HTTP: anonymous visitors are
 * redirected to the login page, TEACHER accounts reach only their
 * read-only home page, and every management area stays ADMIN-only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleAccessWebTest {

    @Autowired
    private MockMvc mockMvc;

    // ----- anonymous --------------------------------------------------------

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/courses"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/teacher"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginPageAndStaticAssetsArePublic() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    // ----- teacher ----------------------------------------------------------

    @Test
    void teacherRootRedirectsToTeacherHome() throws Exception {
        mockMvc.perform(get("/").with(user(teacherName()).roles("TEACHER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher"));
    }

    @Test
    void teacherCanOpenReadOnlyHome() throws Exception {
        mockMvc.perform(get("/teacher").with(user(teacherName()).roles("TEACHER")))
                .andExpect(status().isOk());
    }

    @Test
    void teacherIsDeniedManagementPages() throws Exception {
        String[] urls = {"/courses", "/batches", "/students", "/teachers",
                "/subjects", "/users", "/dashboard"};
        for (String url : urls) {
            mockMvc.perform(get(url).with(user(teacherName()).roles("TEACHER")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void teacherIsDeniedWriteAccess() throws Exception {
        mockMvc.perform(post("/courses").with(user(teacherName()).roles("TEACHER")).with(csrf())
                        .param("name", "Sneaked Course").param("category", "Sneak"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/batches").with(user(teacherName()).roles("TEACHER")).with(csrf())
                        .param("name", "Sneaked Batch").param("academicYear", "2026-27"))
                .andExpect(status().isForbidden());
    }

    // ----- admin ---------------------------------------------------------------

    @Test
    void adminRootRedirectsToDashboard() throws Exception {
        mockMvc.perform(get("/").with(user("admin." + uuid() + "@omsai.local").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void adminDashboardRenders() throws Exception {
        mockMvc.perform(get("/dashboard").with(user("admin." + uuid() + "@omsai.local").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanOpenEveryManagementList() throws Exception {
        String admin = "admin." + uuid() + "@omsai.local";
        String[] urls = {"/courses", "/batches", "/students", "/teachers", "/subjects", "/users"};
        for (String url : urls) {
            mockMvc.perform(get(url).with(user(admin).roles("ADMIN")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void adminCanCreateACourse() throws Exception {
        mockMvc.perform(post("/courses").with(user("admin." + uuid() + "@omsai.local").roles("ADMIN")).with(csrf())
                        .param("name", "Created In Test " + uuid())
                        .param("category", "Academic"))
                .andExpect(status().is3xxRedirection());
    }

    // ----- helpers ----------------------------------------------------------

    private String teacherName() {
        return "teacher." + uuid() + "@omsai.local";
    }

    private String uuid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
