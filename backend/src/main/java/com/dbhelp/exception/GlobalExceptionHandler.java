package com.dbhelp.exception;

import com.dbhelp.common.ApiResponse;
import javax.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ApiResponse<Object>> handleSql(SQLException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail("数据库访问失败: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOthers(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("系统异常: " + ex.getMessage()));
    }
}
