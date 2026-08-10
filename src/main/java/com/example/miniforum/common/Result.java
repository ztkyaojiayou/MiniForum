package com.example.miniforum.common;

/**
 * 统一 API 响应体
 * <p>
 * 所有接口统一返回该结构：code / message / data
 * 避免各接口返回结构不一致，便于前端统一处理。
 *
 * @param <T> 数据类型
 */
public class Result<T> {

    /** 业务状态码：0 表示成功 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功（无数据） */
    public static <T> Result<T> success() {
        return new Result<>(0, "success", null);
    }

    /** 成功（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    /** 成功（带数据和提示信息） */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(0, message, data);
    }

    /** 失败 */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
