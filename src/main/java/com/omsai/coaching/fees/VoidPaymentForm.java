package com.omsai.coaching.fees;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form backing the void-a-payment workflow. A reason is mandatory.
 */
public class VoidPaymentForm {

    @NotBlank(message = "A reason is required to void a payment.")
    @Size(max = 2000, message = "Reason can be at most 2000 characters.")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
