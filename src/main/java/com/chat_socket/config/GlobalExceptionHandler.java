package com.chat_socket.config;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.FriendPermissionException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.exception.SignInException;
import com.chat_socket.exception.UnAuthorizedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.badRequest()
                .body(new BaseResponse<>(errors, "Validation failed", HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnhandledException(Exception ex) {
        return ResponseEntity.internalServerError()
                .body(new BaseResponse<>(
                        ex.getMessage(), "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    @ExceptionHandler(SignInException.class)
    ResponseEntity<Object> handleSignInException(SignInException ex) {
        return ResponseEntity.badRequest()
                .body(new BaseResponse<>(null, ex.getMessage(), HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Object> handleNotFoundException(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseResponse<>(null, ex.getMessage(), HttpStatus.NOT_FOUND.value()));
    }

    @ExceptionHandler(UnAuthorizedException.class)
    ResponseEntity<Object> handleUnAuthorizedException(UnAuthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new BaseResponse<>(null, ex.getMessage(), HttpStatus.UNAUTHORIZED.value()));
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<Object> handleForbiddenException(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new BaseResponse<>(null, ex.getMessage(), HttpStatus.FORBIDDEN.value()));
    }

    @ExceptionHandler(FriendPermissionException.class)
    ResponseEntity<Object> handleFriendPermissionException(FriendPermissionException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new BaseResponse<>(
                        Map.of("notFriends", ex.getNotFriends()), ex.getMessage(), HttpStatus.FORBIDDEN.value()));
    }
}
