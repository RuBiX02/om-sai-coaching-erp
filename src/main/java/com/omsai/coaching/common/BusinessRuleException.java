package com.omsai.coaching.common;

/**
 * Thrown by services when a business rule is violated. The message is
 * intended to be shown to the administrator as feedback.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
