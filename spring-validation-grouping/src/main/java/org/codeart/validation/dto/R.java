package org.codeart.validation.dto;

import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.List;

@Data
public class R<T> {
    private Integer code = NORMAL_CODE;
    private String msg = "";
    private T data;
    private List<ParamError> errors;

    public static final String SUCCESS = "Operation Successful";
    public static final String FAILURE = "Operation Failed";
    public static final Integer ERROR_CODE = 500;
    public static final Integer NORMAL_CODE = 200;

    public R() {
    }

    public R(T data) {
        this.data = data;
    }

    public R(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return ok(HttpStatus.OK.value(), SUCCESS, null);
    }

    public static <T> R<T> ok(Integer code, String msg, T data) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

    public static <T> R<T> ok(T data) {
        return ok(NORMAL_CODE, SUCCESS, data);
    }

    public static <T> R<T> fail(Integer code, String msg) {
        return ok(code, msg, null);
    }

    public static <T> R<T> fail(Integer code) {
        return ok(code, FAILURE, null);
    }

    public static <T> R<T> fail(String msg) {
        return ok(ERROR_CODE, msg, null);
    }

    public static <T> R<T> fail() {
        return ok(ERROR_CODE, FAILURE, null);
    }
}
