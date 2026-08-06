package com.ddicg.erp.web.rest.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;
    private final Map<String, Object> properties;

    /**
     * Tạo exception với ErrorCode, dùng message mặc định.
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getTitle());
        this.errorCode = errorCode;
        this.detail = errorCode.getTitle();
        this.properties = new HashMap<>();
    }

    /**
     * Tạo exception với ErrorCode và custom detail message.
     */
    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
        this.detail = detail;
        this.properties = new HashMap<>();
    }

    /**
     * Tạo exception với ErrorCode, detail và cause.
     */
    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
        this.detail = detail;
        this.properties = new HashMap<>();
    }

    /**
     * @deprecated Dùng constructor với ErrorCode thay thế
     */
    @Deprecated
    public BusinessException(String msg) {
        super(msg);
        this.errorCode = ErrorCode.INTERNAL_ERROR;
        this.detail = msg;
        this.properties = new HashMap<>();
    }

    /**
     * @deprecated Dùng constructor với ErrorCode thay thế
     */
    @Deprecated
    public BusinessException(String errorCodeString, String msg) {
        super(msg);
        this.errorCode = findErrorCodeByString(errorCodeString);
        this.detail = msg;
        this.properties = new HashMap<>();
    }

    /**
     * @deprecated Dùng constructor với ErrorCode thay thế
     */
    @Deprecated
    public BusinessException(String errorCodeString, String msg, Throwable ex) {
        super(msg, ex);
        this.errorCode = findErrorCodeByString(errorCodeString);
        this.detail = msg;
        this.properties = new HashMap<>();
    }

    /**
     * Thêm property vào exception (fluent API).
     * VD: throw new BusinessException(INSUFFICIENT_STOCK, "Không đủ hàng")
     * .with("availableStock", 5)
     * .with("requestedQuantity", 10);
     */
    public BusinessException with(String key, Object value) {
        this.properties.put(key, value);
        return this;
    }

    public String getCode() {
        return errorCode.getCode();
    }

    public String getTitle() {
        return errorCode.getTitle();
    }

    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }

    public URI getType() {
        return errorCode.getType();
    }

    public boolean hasProperties() {
        return !properties.isEmpty();
    }

    private ErrorCode findErrorCodeByString(String codeString) {
        if (codeString == null || codeString.isEmpty()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        for (ErrorCode code : ErrorCode.values()) {
            if (code.getCode().equals(codeString) || code.name().equals(codeString)) {
                return code;
            }
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}
