package org.codeart.validation.exception;

import org.codeart.validation.dto.ParamError;
import org.codeart.validation.dto.R;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Method for validating RequestBody field errors
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidationExceptions(MethodArgumentNotValidException ex) {
        R<Void> resp = R.fail(HttpStatus.BAD_REQUEST.value(), "Validation Failed");
        List<ParamError> errors = ex.getBindingResult().getAllErrors().stream().map(error -> {
            String paramName = ((FieldError) error).getField();
            String reason = error.getDefaultMessage();
            return new ParamError(paramName, reason);
        }).collect(Collectors.toList());
        resp.setErrors(errors);
        return resp;
    }

    // Method for validating RequestParam/PathVariable errors
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException ex) {
        R<Void> resp = R.fail(HttpStatus.BAD_REQUEST.value(), "Constraint Violation");
        List<ParamError> errors = ex.getConstraintViolations().stream().map(cv -> {
            String paramName = cv.getPropertyPath().toString().split("\\.")[1];
            String reason = cv.getMessage();
            return new ParamError(paramName, reason);
        }).collect(Collectors.toList());
        resp.setErrors(errors);
        return resp;
    }
}
