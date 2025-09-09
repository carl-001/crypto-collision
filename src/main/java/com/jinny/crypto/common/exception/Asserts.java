package com.jinny.crypto.common.exception;


import com.jinny.crypto.common.api.IErrorCode;

/**
 * 断言处理类，用于抛出各种API异常
 * Created by macro on 2020/2/27.
 */
public class Asserts {
    public static void fail(String message) {
        throw new ApiException(message);
    }

    public static void fail(IErrorCode errorCode) {
        throw new ApiException(errorCode);
    }

    // 新增：直接使用错误码和错误消息
    public static void fail(String message, int code) {
        throw new ApiException(message, code);
    }

}
