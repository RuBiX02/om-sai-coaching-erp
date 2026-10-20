package com.omsai.coaching.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Form backing object for student create/edit.
 */
public class StudentForm {

    @NotBlank(message = "Student name is required.")
    @Size(max = 120, message = "Student name must be at most 120 characters.")
    private String fullName;

    @NotBlank(message = "Phone number is required.")
    @Pattern(regexp = "[0-9+][0-9\\- ]{5,19}", message = "Enter a valid phone number (digits, spaces or dashes).")
    @Size(max = 20, message = "Phone number must be at most 20 characters.")
    private String phone;

    @Size(max = 120, message = "Parent name must be at most 120 characters.")
    private String parentName;

    @Pattern(regexp = "^[0-9+][0-9\\- ]{5,19}$", message = "Enter a valid parent phone number (digits, spaces or dashes).")
    private String parentPhone;

    @Size(max = 400, message = "Address must be at most 400 characters.")
    private String address;

    @NotNull(message = "Admission date is required.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate admissionDate;

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

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public String getParentPhone() {
        return parentPhone;
    }

    public void setParentPhone(String parentPhone) {
        this.parentPhone = parentPhone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getAdmissionDate() {
        return admissionDate;
    }

    public void setAdmissionDate(LocalDate admissionDate) {
        this.admissionDate = admissionDate;
    }
}
