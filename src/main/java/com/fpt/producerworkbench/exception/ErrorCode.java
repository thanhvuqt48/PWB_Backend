package com.fpt.producerworkbench.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {

    // ===== Lỗi chung (1xxx) =====
    UNCATEGORIZED_EXCEPTION(1001, "Lỗi không xác định.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_PARAMETER_FORMAT(1002, "Định dạng tham số không hợp lệ.", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(1003, "Lỗi xác thực dữ liệu.", HttpStatus.BAD_REQUEST),
    BAD_REQUEST(1004, "Yêu cầu không hợp lệ.", HttpStatus.BAD_REQUEST),
    INVALID_KEY(1001, "Uncategorized error", HttpStatus.BAD_REQUEST),
    EXPIRED_TOKEN(401, "EXPIRED_TOKEN", HttpStatus.UNAUTHORIZED),
    TOKEN_CREATION_FAIL(400, "Failed to create token", HttpStatus.BAD_REQUEST),

    // ===== Lỗi Xác thực & Phân quyền (2xxx) =====
    UNAUTHENTICATED(2001, "Yêu cầu xác thực. Vui lòng đăng nhập.", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(2002, "Không có quyền truy cập tài nguyên này.", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS(2003, "Email hoặc mật khẩu không chính xác.", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(2004, "Token không hợp lệ hoặc đã bị thay đổi.", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(2005, "Token đã hết hạn. Vui lòng đăng nhập lại.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(2006, "Tài khoản đã bị khóa.", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(2007, "Tài khoản đã bị vô hiệu hóa.", HttpStatus.FORBIDDEN),
    EMAIL_NOT_VERIFIED(2008, "Email chưa được xác thực.", HttpStatus.FORBIDDEN),
    UNAUTHORIZED(1007, "You do not have permission", HttpStatus.FORBIDDEN),


    // ===== Lỗi liên quan đến Người dùng (User) (3xxx) =====
    USER_EXISTED(3001, "Người dùng với email này đã tồn tại.", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(3002, "Không tìm thấy người dùng.", HttpStatus.NOT_FOUND),
    INVALID_OLD_PASSWORD(3003, "Mật khẩu cũ không chính xác.", HttpStatus.BAD_REQUEST),
    OTP_INVALID(3004, "Mã OTP không hợp lệ.", HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(3005, "Mã OTP đã hết hạn.", HttpStatus.BAD_REQUEST),
    ACCOUNT_ALREADY_VERIFIED(3006, "Tài khoản này đã được xác thực trước đó.", HttpStatus.BAD_REQUEST),

    // ===== Lỗi liên quan đến Tài nguyên chung (4xxx) =====
    RESOURCE_NOT_FOUND(4001, "Không tìm thấy tài nguyên được yêu cầu.", HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE(4002, "Tài nguyên đã tồn tại.", HttpStatus.CONFLICT),
    UPLOAD_FAILED(4003, "Upload file thất bại.", HttpStatus.INTERNAL_SERVER_ERROR),

    // ===== Lỗi Hệ thống / Máy chủ (9xxx) =====
    INTERNAL_SERVER_ERROR(9001, "Đã có lỗi xảy ra ở phía máy chủ.", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR(9002, "Lỗi truy vấn cơ sở dữ liệu.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_DOB(400, "Date of birth must be greater than 1950 and less than current date", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(400, "Password must be at least {min} characters", HttpStatus.BAD_REQUEST),
    PASSWORD_EXISTED(409, "Password existed", HttpStatus.CONFLICT),
    CONFIRM_PASSWORD_INVALID(400, "Confirmed password is incorrect", HttpStatus.BAD_REQUEST)
    ;

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
