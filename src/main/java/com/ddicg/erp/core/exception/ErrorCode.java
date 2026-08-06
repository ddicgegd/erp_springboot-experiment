package com.ddicg.erp.core.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    PRODUCT_NOT_FOUND("Sản phẩm không tồn tại", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND("Danh mục không tồn tại", HttpStatus.NOT_FOUND),
    INVALID_REQUEST("Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHORIZED("Chưa xác thực", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("Không có quyền", HttpStatus.FORBIDDEN),
    INSUFFICIENT_STOCK("Không đủ hàng", HttpStatus.BAD_REQUEST),
    ATTRIBUTES_NOT_FOUND("Thuộc tính không tồn tại", HttpStatus.NOT_FOUND),
    INTERNAL_ERROR("Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_STATUS_TRANSITION("Chuyển trạng thái không hợp lệ", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("Lỗi xác thực", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND("Người dùng không tồn tại", HttpStatus.NOT_FOUND),
    ORDER_NOT_FOUND("Đơn hàng không tồn tại", HttpStatus.NOT_FOUND),
    ATTRIBUTES_OUT_OF_STOCK("Thuộc tính hết hàng", HttpStatus.BAD_REQUEST),
    INVALID_FORMAT("Định dạng không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS("Thông tin đăng nhập không hợp lệ", HttpStatus.UNAUTHORIZED),
    CATEGORY_ALREADY_EXISTS("Danh mục đã tồn tại", HttpStatus.CONFLICT),
    ACCESS_DENIED("Từ chối truy cập", HttpStatus.FORBIDDEN),
    INVALID_QUANTITY("Số lượng không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_PRICE("Giá không hợp lệ", HttpStatus.BAD_REQUEST),
    PRODUCT_OUT_OF_STOCK("Sản phẩm hết hàng", HttpStatus.BAD_REQUEST),
    REGISTRATION_INFO_MISMATCH("Thông tin đăng ký không khớp", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }

    public String getTitle() {
        return message;
    }

    public String getCode() {
        return this.name();
    }

    public HttpStatus getHttpStatus() {
        return status;
    }

    public java.net.URI getType() {
        return java.net.URI.create("about:blank");
    }
}
