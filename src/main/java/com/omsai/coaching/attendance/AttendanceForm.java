package com.omsai.coaching.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Form backing the attendance marking page: one row per eligible
 * enrollment of the roster, submitted together. Every row is
 * re-validated on the backend before saving.
 */
public class AttendanceForm {

    @Valid
    private List<Row> rows = new ArrayList<>();

    public List<Row> getRows() {
        return rows;
    }

    public void setRows(List<Row> rows) {
        this.rows = rows;
    }

    public static class Row {

        @NotNull(message = "Marked row without enrollment.")
        private Long enrollmentId;

        @NotNull(message = "Pick a status for every student.")
        private Attendance.Status status;

        @Size(max = 300, message = "Remarks can be at most 300 characters.")
        private String remarks;

        public Long getEnrollmentId() {
            return enrollmentId;
        }

        public void setEnrollmentId(Long enrollmentId) {
            this.enrollmentId = enrollmentId;
        }

        public Attendance.Status getStatus() {
            return status;
        }

        public void setStatus(Attendance.Status status) {
            this.status = status;
        }

        public String getRemarks() {
            return remarks;
        }

        public void setRemarks(String remarks) {
            this.remarks = remarks == null ? "" : remarks.trim();
        }
    }
}
