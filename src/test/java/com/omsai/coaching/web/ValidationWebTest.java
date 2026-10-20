package com.omsai.coaching.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;

/**
 * Bean validation of the management forms over HTTP: invalid submissions
 * re-render the form with the exact field errors shown to the admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ValidationWebTest {

    @Autowired
    private MockMvc mockMvc;

    private String admin() {
        return "admin." + UUID.randomUUID() + "@omsai.local";
    }

    @Test
    void blankCourseFormShowsFieldErrors() throws Exception {
        mockMvc.perform(post("/courses").with(user(admin()).roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("courses/form"))
                .andExpect(model().attributeHasFieldErrors("form", "name", "category"));
    }

    @Test
    void batchEndBeforeStartShowsCombinedValidationError() throws Exception {
        mockMvc.perform(post("/batches").with(user(admin()).roles("ADMIN")).with(csrf())
                        .param("courseId", "1")
                        .param("name", "Wrong Dates Batch")
                        .param("academicYear", "2026-27")
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-08-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("batches/form"))
                .andExpect(model().attributeHasFieldErrorCode("form", "endDateAfterStart", "AssertTrue"));
    }

    @Test
    void badAcademicYearFormatShowsPatternError() throws Exception {
        mockMvc.perform(post("/batches").with(user(admin()).roles("ADMIN")).with(csrf())
                        .param("courseId", "1")
                        .param("name", "Bad Year Batch")
                        .param("academicYear", "27-26")
                        .param("startDate", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("batches/form"))
                .andExpect(model().attributeHasFieldErrorCode("form", "academicYear", "Pattern"));
    }

    @Test
    void blankStudentFormShowsFieldErrors() throws Exception {
        mockMvc.perform(post("/students").with(user(admin()).roles("ADMIN")).with(csrf())
                        .param("fullName", "")
                        .param("phone", "bad!")
                        .param("admissionDate", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("students/form"))
                .andExpect(model().attributeHasFieldErrors("form", "fullName", "phone", "admissionDate"));
    }

    @Test
    void invalidTeacherPhoneShowsPatternError() throws Exception {
        mockMvc.perform(post("/teachers").with(user(admin()).roles("ADMIN")).with(csrf())
                        .param("fullName", "Teacher X")
                        .param("phone", "not-a-phone"))
                .andExpect(status().isOk())
                .andExpect(view().name("teachers/form"))
                .andExpect(model().attributeHasFieldErrorCode("form", "phone", "Pattern"));
    }

    @Test
    void invalidUserAccountFormShowsFieldErrors() throws Exception {
        mockMvc.perform(post("/users").with(user(admin()).roles("ADMIN")).with(csrf())
                        .param("fullName", "")
                        .param("email", "not-an-email")
                        .param("role", "SUPERUSER")
                        .param("password", "short"))
                .andExpect(status().isOk())
                .andExpect(view().name("users/form"))
                .andExpect(model().attributeHasFieldErrors("form",
                        "fullName", "email", "role", "password"));
    }

    @Test
    void blankSubjectNameShowsFieldError() throws Exception {
        mockMvc.perform(post("/subjects").with(user(admin()).roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("subjects/form"))
                .andExpect(model().attributeHasFieldErrors("form", "name"));
    }
}
