package com.omsai.coaching.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One student's submitted quiz with the score the server computed.
 * The score is a fact the server derived; a browser cannot post one.
 */
@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private com.omsai.coaching.student.Student student;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", nullable = false)
    private com.omsai.coaching.security.UserAccount account;

    @Column(nullable = false, insertable = false, updatable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    public Long getId() {
        return id;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public com.omsai.coaching.student.Student getStudent() {
        return student;
    }

    public com.omsai.coaching.security.UserAccount getAccount() {
        return account;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    /** Percentage score, rounded to whole numbers. */
    public int percentage() {
        return totalCount == 0 ? 0 : Math.round(correctCount * 100f / totalCount);
    }

    public void setQuiz(Quiz quiz) {
        this.quiz = quiz;
    }

    public void setStudent(com.omsai.coaching.student.Student student) {
        this.student = student;
    }

    public void setAccount(com.omsai.coaching.security.UserAccount account) {
        this.account = account;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
