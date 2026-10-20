package com.omsai.coaching.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central error handling. Users receive friendly pages without any
 * technical detail or stack traces.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView notFound() {
        return new ModelAndView("error/404");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView badId(MethodArgumentTypeMismatchException ex) {
        LOG.debug("Bad request parameter: {}", ex.getMessage());
        return new ModelAndView("error/404");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView integrity(DataIntegrityViolationException ex) {
        LOG.warn("Data integrity violation: {}", ex.getMessage());
        ModelAndView mav = new ModelAndView("error/error");
        mav.setStatus(HttpStatus.CONFLICT);
        mav.addObject("errorMessage",
                "That change conflicts with existing data - a record with the same details may already exist.");
        return mav;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception ex) {
        LOG.error("Unhandled exception", ex);
        ModelAndView mav = new ModelAndView("error/error");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        mav.addObject("errorMessage", "Something went wrong. Please try again or contact the system administrator.");
        return mav;
    }
}
