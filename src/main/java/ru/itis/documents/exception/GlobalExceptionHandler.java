package ru.itis.documents.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.itis.documents.dto.ApiErrorResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 7.3: логирование интеграции
    @ExceptionHandler(IntegrationException.class)
    public Object handleIntegration(IntegrationException ex, HttpServletRequest request) {
        int status = ex.getHttpStatus() > 0 ? ex.getHttpStatus() : 503;

        log.warn("Integration error: code={} status={} method={} uri={} msg={}",
                ex.getCode(), status, request.getMethod(), request.getRequestURI(), ex.getUserMessage(), ex);

        if (isAjax(request)) {
            ApiErrorResponse body = new ApiErrorResponse(ex.getCode(), ex.getUserMessage(), ex.getDetails());
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
        }

        ModelAndView mav = new ModelAndView("error/integration");
        mav.setStatus(org.springframework.http.HttpStatusCode.valueOf(status));
        mav.addObject("title", "Ошибка внешнего сервиса");
        mav.addObject("message", ex.getUserMessage());
        mav.addObject("code", ex.getCode());
        mav.addObject("details", ex.getDetails());
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    // 7.3: логирование интеграции (Perenual и т.п.)
    @ExceptionHandler(ExternalApiUnavailableException.class)
    public Object handleExternalApiUnavailable(ExternalApiUnavailableException ex, HttpServletRequest request) {
        int status = (ex.getStatusCode() == null || ex.getStatusCode() <= 0) ? 503 : ex.getStatusCode();

        String service = (ex.getService() == null || ex.getService().isBlank()) ? "external" : ex.getService().trim();
        String code = service.toUpperCase() + "_UNAVAILABLE";

        log.warn("External API unavailable: service={} status={} method={} uri={} msg={}",
                service, status, request.getMethod(), request.getRequestURI(), ex.getUserMessage(), ex);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("service", service);
        details.put("status", status);

        if (isAjax(request)) {
            ApiErrorResponse body = new ApiErrorResponse(code, ex.getUserMessage(), details);
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
        }

        ModelAndView mav = new ModelAndView("error/integration");
        mav.setStatus(org.springframework.http.HttpStatusCode.valueOf(status));
        mav.addObject("title", "Ошибка внешнего сервиса");
        mav.addObject("message", ex.getUserMessage());
        mav.addObject("code", code);
        mav.addObject("details", details);
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    // 7.1: 404
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Object handleNotFound(Exception ex, HttpServletRequest request) {
        if (isAjax(request)) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("NOT_FOUND", "Страница не найдена", detailsOf(request, null)));
        }

        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(org.springframework.http.HttpStatus.NOT_FOUND);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    // 7.3: логирование доступа
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        String user = (request.getUserPrincipal() == null) ? "anonymous" : request.getUserPrincipal().getName();
        log.warn("Access denied: user={} method={} uri={}", user, request.getMethod(), request.getRequestURI(), ex);

        if (isAjax(request)) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("FORBIDDEN", "Доступ запрещён", detailsOf(request, null)));
        }

        ModelAndView mav = new ModelAndView("error/403");
        mav.setStatus(org.springframework.http.HttpStatus.FORBIDDEN);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    // 7.1: валидация (400)
    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public Object handleValidation(Exception ex, HttpServletRequest request) {
        List<String> errors = extractValidationErrors(ex);

        if (isAjax(request)) {
            Map<String, Object> details = detailsOf(request, Map.of("errors", errors));
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("VALIDATION_ERROR", "Некорректные данные", details));
        }

        ModelAndView mav = new ModelAndView("error/400");
        mav.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("errors", errors);
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    // 7.3: неожиданные ошибки в лог
    @ExceptionHandler(Exception.class)
    public Object handleAny(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error: method={} uri={} qs={}",
                request.getMethod(), request.getRequestURI(), request.getQueryString(), ex);

        if (isAjax(request)) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("INTERNAL_ERROR", "Ошибка сервера", detailsOf(request, null)));
        }

        ModelAndView mav = new ModelAndView("error/500");
        mav.setStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("backUrl", safeBackUrl(request));
        return mav;
    }

    private static boolean isAjax(HttpServletRequest request) {
        // 7.2: /api/** всегда JSON
        String uri = request.getRequestURI();
        if (uri != null && (uri.equals("/api") || uri.startsWith("/api/"))) return true;

        String xrw = request.getHeader("X-Requested-With");
        if (xrw != null && xrw.equalsIgnoreCase("XMLHttpRequest")) return true;

        String accept = request.getHeader("Accept");
        if (accept != null && accept.toLowerCase().contains("application/json")) return true;

        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }

    private static String safeBackUrl(HttpServletRequest request) {
        String ref = request.getHeader("Referer");
        return (ref == null || ref.isBlank()) ? "/" : ref;
    }

    private static Map<String, Object> detailsOf(HttpServletRequest request, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", request.getRequestURI());
        details.put("method", request.getMethod());
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            details.put("query", request.getQueryString());
        }
        if (extra != null) details.putAll(extra);
        return details;
    }

    private static List<String> extractValidationErrors(Exception ex) {
        if (ex instanceof BindException be) {
            return be.getBindingResult().getAllErrors().stream()
                    .map(err -> (err instanceof FieldError fe)
                            ? fe.getField() + ": " + fe.getDefaultMessage()
                            : err.getDefaultMessage())
                    .collect(Collectors.toList());
        }

        if (ex instanceof MethodArgumentNotValidException manv) {
            return manv.getBindingResult().getAllErrors().stream()
                    .map(err -> (err instanceof FieldError fe)
                            ? fe.getField() + ": " + fe.getDefaultMessage()
                            : err.getDefaultMessage())
                    .collect(Collectors.toList());
        }

        if (ex instanceof ConstraintViolationException cve) {
            List<String> out = new ArrayList<>();
            cve.getConstraintViolations().forEach(v -> out.add(v.getPropertyPath() + ": " + v.getMessage()));
            return out;
        }

        return List.of("Некорректные данные");
    }
}