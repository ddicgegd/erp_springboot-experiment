package com.ddicg.erp.service.dto.response.ResponseConfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response<T> {

    ApiStatus status;

    T data;

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
