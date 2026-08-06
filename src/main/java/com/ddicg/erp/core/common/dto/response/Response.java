package com.ddicg.erp.core.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response<T> {

    private ApiStatus status;
    private T data;

    public Response() {}

    public Response(ApiStatus status, T data) {
        this.status = status;
        this.data = data;
    }

    public ApiStatus getStatus() { return status; }
    public void setStatus(ApiStatus status) { this.status = status; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public static <T> ResponseBuilder<T> builder() {
        return new ResponseBuilder<T>();
    }

    public static class ResponseBuilder<T> {
        private ApiStatus status;
        private T data;

        ResponseBuilder() {}

        public ResponseBuilder<T> status(ApiStatus status) {
            this.status = status;
            return this;
        }

        public ResponseBuilder<T> data(T data) {
            this.data = data;
            return this;
        }

        public Response<T> build() {
            return new Response<T>(this.status, this.data);
        }
    }

    public static <T> Response<T> ok(T data) {
        final ApiStatus status = new ApiStatus(HttpStatus.OK.value());
        return Response.<T>builder()
                .status(status)
                .data(data)
                .build();
    }

    public static <T> Response<T> ok(T data, String message) {
        final ApiStatus status = new ApiStatus(message, HttpStatus.OK.value());
        return Response.<T>builder()
                .status(status)
                .data(data)
                .build();
    }

    public static <T> Response<T> ok(String message) {
        final ApiStatus status = new ApiStatus(message, HttpStatus.OK.value());
        return Response.<T>builder()
                .status(status)
                .build();
    }

    public static <T> Response<T> created(T data) {
        final ApiStatus status = new ApiStatus(HttpStatus.CREATED.value());
        return Response.<T>builder()
                .status(status)
                .data(data)
                .build();
    }

    public static <T> Response<T> noContent() {
        final ApiStatus status = new ApiStatus(HttpStatus.NO_CONTENT.value());
        return Response.<T>builder()
                .status(status)
                .build();
    }

    public static <T> Response<T> found(String url) {
        final ApiStatus status = new ApiStatus(url, HttpStatus.FOUND.value());
        return Response.<T>builder()
                .status(status)
                .data(null)
                .build();
    }

    public static <T> Response<T> loginResponse(HttpStatus httpStatus, T data) {
        final ApiStatus status = new ApiStatus(httpStatus.value());
        return Response.<T>builder()
                .status(status)
                .data(data)
                .build();
    }

    public static <T> Response<T> fail(ApiStatus status) {
        return Response.<T>builder()
                .status(status)
                .build();
    }
}
