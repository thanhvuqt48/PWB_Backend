package com.fpt.producerworkbench.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // ===== Lỗi chung (1xxx) =====
    UNCATEGORIZED_EXCEPTION(1001, "Lỗi không xác định.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_PARAMETER_FORMAT(1002, "Định dạng tham số không hợp lệ.", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(1003, "Lỗi xác thực dữ liệu.", HttpStatus.BAD_REQUEST),
    BAD_REQUEST(1004, "Yêu cầu không hợp lệ.", HttpStatus.BAD_REQUEST),
    INVALID_KEY(1001, "Key không hợp lệ.", HttpStatus.BAD_REQUEST),
    EXPIRED_TOKEN(401, "Token hết hạn.", HttpStatus.UNAUTHORIZED),
    TOKEN_CREATION_FAIL(400, "Tạo token thất bại.", HttpStatus.BAD_REQUEST),
    URL_GENERATION_FAILED(1012, "Không thể tạo URL. Vui lòng thử lại.", HttpStatus.INTERNAL_SERVER_ERROR),

    // ===== Lỗi Xác thực & Phân quyền (2xxx) =====
    UNAUTHENTICATED(2001, "Yêu cầu xác thực. Vui lòng đăng nhập.", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(2002, "Không có quyền truy cập tài nguyên này.", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS(2003, "Email hoặc mật khẩu không chính xác.", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(2004, "Token không hợp lệ hoặc đã bị thay đổi.", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(2005, "Token đã hết hạn. Vui lòng đăng nhập lại.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(2006, "Tài khoản đã bị khóa.", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(2007, "Tài khoản đã bị vô hiệu hóa.", HttpStatus.FORBIDDEN),
    EMAIL_NOT_VERIFIED(2008, "Email chưa được xác thực.", HttpStatus.FORBIDDEN),
    UNAUTHORIZED(1007, "Bạn không có quyền truy cập tài nguyên này.", HttpStatus.FORBIDDEN),


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
    DELETE_FAILED(4004, "Xóa tài nguyên thất bại.", HttpStatus.INTERNAL_SERVER_ERROR),

    // ===== Lỗi liên quan đến Dự án (5xxx) =====
    CLIENT_ALREADY_EXISTS(5001, "Dự án này đã có khách hàng.", HttpStatus.BAD_REQUEST),
    CLIENT_EXISTED(5002, "Một người dùng khác vừa chấp nhận vai trò khách hàng cho dự án này.", HttpStatus.BAD_REQUEST),
    INVITATION_EXPIRED(5003, "Mã mời đã hết hạn.", HttpStatus.BAD_REQUEST),
    INVITATION_NOT_ACCEPTED(5004, "Lời mời này không dành cho bạn.", HttpStatus.BAD_REQUEST),
    INVITATION_EXPIRED_OR_NOT_FOUND(5005, "Mã mời đã được sử dụng hoặc hết hạn.", HttpStatus.NOT_FOUND),
    PROJECT_EXISTED(5006, "Một dự án với tên này đã tồn tại.", HttpStatus.BAD_REQUEST),
    INVITATION_NOT_REJECTABLE(5007, "Không thể từ chối lời mời này.", HttpStatus.BAD_REQUEST),
    INVITATION_NOT_CANCELABLE(5008, "Chỉ có thể hủy lời mời đang chờ.", HttpStatus.NOT_FOUND),
    PROJECT_NOT_FOUND(5009, "Không tìm thấy project.", HttpStatus.NOT_FOUND),
    INVITATION_SELF_NOT_ALLOWED(5010, "Không thể mời chính bạn vào dự án.", HttpStatus.BAD_REQUEST),
    USER_ALREADY_MEMBER(5011, "Người dùng này đã là thành viên của dự án.", HttpStatus.BAD_REQUEST),

    // ===== 5xxx: Hợp đồng / Ký số (MỚI) =====
    CONTRACT_NOT_FOUND(5001, "Không tìm thấy hợp đồng.", HttpStatus.NOT_FOUND),
    CONTRACT_ALREADY_EXISTS(5002, "Dự án này đã có hợp đồng. Mỗi dự án chỉ được phép có một hợp đồng.", HttpStatus.CONFLICT),
    CONTRACT_FILLED_PDF_NOT_FOUND(5003, "Chưa có file hợp đồng đã soạn (FILLED) để mời ký.", HttpStatus.BAD_REQUEST),
    SIGNERS_REQUIRED(5004, "Thiếu danh sách người ký.", HttpStatus.BAD_REQUEST),
    SIGNER_EMAIL_REQUIRED(5005, "Thiếu email người ký.", HttpStatus.BAD_REQUEST),
    ROLE_ID_REQUIRED(5006, "Thiếu roleId cho người ký (Field Invite yêu cầu roleId).", HttpStatus.BAD_REQUEST),
    PDF_BASE64_INVALID(5007, "pdfBase64 không hợp lệ.", HttpStatus.BAD_REQUEST),

    // ===== 6xxx: Storage (MỚI) =====
    STORAGE_READ_FAILED(6001, "Không đọc được file từ storage.", HttpStatus.INTERNAL_SERVER_ERROR),

    // ===== 7xxx: Tích hợp SignNow (MỚI) =====
    SIGNNOW_UPLOAD_FAILED(7001, "Upload tài liệu lên SignNow thất bại.", HttpStatus.BAD_GATEWAY),
    SIGNNOW_INVITE_FAILED(7002, "Tạo lời mời ký trên SignNow thất bại.", HttpStatus.BAD_GATEWAY),
    SIGNNOW_DOC_HAS_NO_FIELDS(7003, "Tài liệu chưa có field/role.", HttpStatus.BAD_GATEWAY),
    SIGNNOW_DOC_ID_NOT_FOUND(7004, "Không tìm thấy tài liệu.", HttpStatus.NOT_FOUND),
    SIGNNOW_DOWNLOAD_FAILED(7005, "Tải thất bại.", HttpStatus.BAD_REQUEST),
    SIGNNOW_DOC_NOT_COMPLETED(7006, "Hợp đồng chưa hoàn tất ký.", HttpStatus.BAD_REQUEST),
    CONTRACT_DOC_NOT_FOUND(7007, "Không tìm thấy hợp đồng.", HttpStatus.BAD_REQUEST),
    ALREADY_SIGNED_FINAL(7008, "Hợp đồng đã có bản ký cuối. Không thể lưu thêm.", HttpStatus.CONFLICT),
    INVITE_NOT_ALLOWED_ALREADY_COMPLETED(7009, "Hợp đồng đã hoàn tất ký. Không thể mời ký lại.", HttpStatus.CONFLICT),
    CLIENT_NOT_FOUND(7010, "Dự án chưa có khách hàng. Vui lòng mời khách hàng tham gia dự án trước.", HttpStatus.BAD_REQUEST),
    CONTRACT_ALREADY_DECLINED(7011, "Hợp đồng đã bị từ chối trước đó. Không thể từ chối lại.", HttpStatus.CONFLICT),
    CONTRACT_ALREADY_COMPLETED(7012, "Hợp đồng đã hoàn tất ký. Không thể từ chối.", HttpStatus.CONFLICT),
    CONTRACT_NOT_DECLINED(7013, "Hợp đồng chưa bị từ chối.", HttpStatus.BAD_REQUEST),

    // ===== Lỗi Hệ thống / Máy chủ (9xxx) =====
    INTERNAL_SERVER_ERROR(9001, "Đã có lỗi xảy ra ở phía máy chủ.", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR(9002, "Lỗi truy vấn cơ sở dữ liệu.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_DOB(400, "Ngày sinh phải lớn hơn 1950 và nhỏ hơn ngày hiện tại", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(400, "Mật khẩu phải tối thiểu {min} kí tự", HttpStatus.BAD_REQUEST),
    PASSWORD_EXISTED(409, "Mật khẩu đã tồn tại", HttpStatus.CONFLICT),
    CONFIRM_PASSWORD_INVALID(400, "Mật khẩu xác nhận không khớp", HttpStatus.BAD_REQUEST)
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
