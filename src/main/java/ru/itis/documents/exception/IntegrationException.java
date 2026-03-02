package ru.itis.documents.exception;

public class IntegrationException extends RuntimeException {

    private final String code;        // машинный код ошибки
    private final String userMessage; // сообщение для пользователя
    private final Object details;     // любые детали (map/string/etc)
    private final int httpStatus;     // какой статус отдавать (400/429/503...)

    public IntegrationException(String code, String userMessage, Object details, int httpStatus, Throwable cause) {
        super(userMessage, cause);
        this.code = code;
        this.userMessage = userMessage;
        this.details = details;
        this.httpStatus = httpStatus;
    }

    public IntegrationException(String code, String userMessage, Object details, int httpStatus) {
        this(code, userMessage, details, httpStatus, null);
    }

    public String getCode() {
        return code;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public Object getDetails() {
        return details;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}