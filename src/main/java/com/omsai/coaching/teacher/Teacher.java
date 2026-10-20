package com.omsai.coaching.teacher;

import com.omsai.coaching.common.Auditable;
import com.omsai.coaching.security.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * A teacher profile. A teacher does not need a login account; when one
 * exists it must be a TEACHER-role user account (enforced in TeacherService).
 */
@Entity
@Table(name = "teachers")
public class Teacher extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount linkedUser;

    @NotBlank
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 180)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public UserAccount getLinkedUser() {
        return linkedUser;
    }

    public void setLinkedUser(UserAccount linkedUser) {
        this.linkedUser = linkedUser;
    }

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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
