package com.omsai.coaching.teacher;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Form backing object for teacher create/edit.
 */
public class TeacherForm {

    @NotBlank(message = "Teacher name is required.")
    @Size(max = 120, message = "Teacher name must be at most 120 characters.")
    private String fullName;

    @NotBlank(message = "Phone number is required.")
    @Pattern(regexp = "[0-9+][0-9\\- ]{5,19}", message = "Enter a valid phone number (digits, spaces or dashes).")
    @Size(max = 20, message = "Phone number must be at most 20 characters.")
    private String phone;

    @Email(message = "Enter a valid email address.")
    @Size(max = 180, message = "Email must be at most 180 characters.")
    private String email;

    /** Optional id of the TEACHER-role user account linked to this profile. */
    private Long userId;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
