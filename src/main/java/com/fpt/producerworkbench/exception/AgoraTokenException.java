package com.fpt.producerworkbench.exception;

import lombok.Getter;

@Getter
public class AgoraTokenException extends AppException {

    public AgoraTokenException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AgoraTokenException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public AgoraTokenException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
