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
    INVALID_SIGNATURE(1005, "Chữ ký không hợp lệ.", HttpStatus.BAD_REQUEST),
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

    FORBIDDEN(2009, "Hành động không được phép.", HttpStatus.FORBIDDEN),

    // ===== Lỗi liên quan đến Người dùng (User) (3xxx) =====
    USER_EXISTED(3001, "Người dùng với email này đã tồn tại.", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(3002, "Không tìm thấy người dùng.", HttpStatus.NOT_FOUND),
    INVALID_OLD_PASSWORD(3003, "Mật khẩu cũ không chính xác.", HttpStatus.BAD_REQUEST),
    OTP_INVALID(3004, "Mã OTP không hợp lệ.", HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(3005, "Mã OTP đã hết hạn.", HttpStatus.BAD_REQUEST),
    ACCOUNT_ALREADY_VERIFIED(3006, "Tài khoản này đã được xác thực trước đó.", HttpStatus.BAD_REQUEST),

    // ===== Lỗi liên quan đến Tài nguyên chung (4xxx) =====
    USER_INACTIVE(3007, "Tài khoản chưa được kích hoạt.", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(4001, "Không tìm thấy tài nguyên được yêu cầu.", HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE(4002, "Tài nguyên đã tồn tại.", HttpStatus.CONFLICT),
    UPLOAD_FAILED(4003, "Upload file thất bại.", HttpStatus.INTERNAL_SERVER_ERROR),
    DELETE_FAILED(4004, "Xóa tài nguyên thất bại.", HttpStatus.INTERNAL_SERVER_ERROR),
    PORTFOLIO_ALREADY_EXISTS(4005, "Bạn đã có portfolio. Mỗi người dùng chỉ được có một portfolio.", HttpStatus.CONFLICT),
    PORTFOLIO_NOT_FOUND(4006, "Không tìm thấy portfolio.", HttpStatus.NOT_FOUND),
    PORTFOLIO_NOT_PUBLIC(4007, "Portfolio này không công khai.", HttpStatus.FORBIDDEN),

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
    CONTRACT_FILLED_PDF_NOT_FOUND(5002, "Chưa có file hợp đồng đã soạn (FILLED) để mời ký.", HttpStatus.BAD_REQUEST),
    SIGNERS_REQUIRED(5003, "Thiếu danh sách người ký.", HttpStatus.BAD_REQUEST),
    SIGNER_EMAIL_REQUIRED(5004, "Thiếu email người ký.", HttpStatus.BAD_REQUEST),
    ROLE_ID_REQUIRED(5005, "Thiếu roleId cho người ký (Field Invite yêu cầu roleId).", HttpStatus.BAD_REQUEST),
    PDF_BASE64_INVALID(5006, "pdfBase64 không hợp lệ.", HttpStatus.BAD_REQUEST),

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
    INVITE_ALREADY_SENT(7017, "Lời mời ký đã được gửi cho hợp đồng này. Không thể gửi lại lời mời lần thứ 2.", HttpStatus.CONFLICT),
    CLIENT_NOT_FOUND(7010, "Dự án chưa có khách hàng. Vui lòng mời khách hàng tham gia dự án trước.", HttpStatus.BAD_REQUEST),
    CONTRACT_ALREADY_DECLINED(7011, "Hợp đồng đã bị từ chối trước đó. Không thể từ chối lại.", HttpStatus.CONFLICT),
    CONTRACT_ALREADY_COMPLETED(7012, "Hợp đồng đã hoàn tất ký. Không thể từ chối.", HttpStatus.CONFLICT),
    CONTRACT_NOT_DECLINED(7013, "Hợp đồng chưa bị từ chối.", HttpStatus.BAD_REQUEST),
    MILESTONES_TOTAL_NOT_ENOUGH(7014, "Tổng số tiền các cột mốc nhỏ hơn tổng số tiền trước thuế.", HttpStatus.BAD_REQUEST),
    MILESTONES_TOTAL_EXCEEDS(7015, "Tổng số tiền các cột mốc vượt quá tổng số tiền trước thuế.", HttpStatus.BAD_REQUEST),
    PRODUCT_COUNT_REQUIRED(7016, "Thiếu số lượng sản phẩm (tổng từ bảng hạng mục phải > 0).", HttpStatus.BAD_REQUEST),
    MILESTONES_PRODUCT_TOTAL_NOT_ENOUGH(7017, "Tổng số lượng sản phẩm ở các cột mốc BỊ THIẾU so với tổng số lượng hạng mục.", HttpStatus.BAD_REQUEST),
    MILESTONES_PRODUCT_TOTAL_EXCEEDS(7018, "Tổng số lượng sản phẩm ở các cột mốc BỊ DƯ so với tổng số lượng hạng mục.", HttpStatus.BAD_REQUEST),

    // ===== Lỗi Hệ thống / Máy chủ (9xxx) =====
    INTERNAL_SERVER_ERROR(9001, "Đã có lỗi xảy ra ở phía máy chủ.", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR(9002, "Lỗi truy vấn cơ sở dữ liệu.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_DOB(400, "Ngày sinh phải lớn hơn 1950 và nhỏ hơn ngày hiện tại", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(400, "Mật khẩu phải tối thiểu {min} kí tự", HttpStatus.BAD_REQUEST),
    PASSWORD_EXISTED(409, "Mật khẩu đã tồn tại", HttpStatus.CONFLICT),
    CONFIRM_PASSWORD_INVALID(400, "Mật khẩu xác nhận không khớp", HttpStatus.BAD_REQUEST),

    // Agora RTC/RTM Token - 6xxx
    AGORA_TOKEN_GENERATION_FAILED(6001, "Lỗi khi tạo Agora token", HttpStatus.INTERNAL_SERVER_ERROR),
    AGORA_CONFIG_MISSING(6002, "Cấu hình Agora bị thiếu", HttpStatus.INTERNAL_SERVER_ERROR),
    AGORA_CHANNEL_INVALID(6003, "Tên kênh Agora không hợp lệ", HttpStatus.BAD_REQUEST),
    AGORA_TOKEN_INVALID(6004, "Định dạng mã thông báo Agora không hợp lệ", HttpStatus.BAD_REQUEST),
    MESSAGE_NOT_PART_OF_STORY(400, "Tin nhắn không thuộc đoạn trò chuyện này", HttpStatus.BAD_REQUEST),
    CONVERSATION_NOT_FOUND(404, "Đoạn trò chuyện không tồn tại", HttpStatus.NOT_FOUND),
    MEDIA_URL_NOT_BLANK(400, "URL của file đính kèm không được để trống", HttpStatus.BAD_REQUEST),
    MESSAGE_NOT_FOUND(404, "Tin nhắn không tồn tại", HttpStatus.NOT_FOUND),
    AGORA_TOKEN_EXPIRED(6005, "Tên kênh Agora không hợp lệ", HttpStatus.UNAUTHORIZED),
    AGORA_UID_INVALID(6006, "Agora UID không hợp lệ", HttpStatus.BAD_REQUEST),
    // ========== File Related (7xxx) ==========
    FILE_NOT_FOUND(7001, "Không tìm thấy file", HttpStatus.NOT_FOUND),
    PARTICIPANT_INVALID(400, "Một hoặc nhiều người dùng không tồn tại", HttpStatus.BAD_REQUEST),

    TRANSACTION_NOT_FOUND(8002, "Không tìm thấy giao dịch.", HttpStatus.NOT_FOUND),
    NO_ACCESS(8003, "Không có quyền truy cập.", HttpStatus.FORBIDDEN),
    FILE_NOT_IN_PROJECT(7002, "File không thuộc về dự án này", HttpStatus.FORBIDDEN),
    INVALID_FILE_FORMAT(7003, "Định dạng file không hợp lệ", HttpStatus.BAD_REQUEST),

    // ========== Playback Related (8xxx) ==========
    CONTRACT_NOT_READY_FOR_PAYMENT(8006, "Hợp đồng chưa sẵn sàng để thanh toán.", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_TYPE(8008, "Loại thanh toán không hợp lệ.", HttpStatus.BAD_REQUEST),
    PAYMENT_LINK_CREATION_FAILED(8007, "Tạo link thanh toán thất bại.", HttpStatus.INTERNAL_SERVER_ERROR),

    PROJECT_NOT_FUNDED(8004, "Dự án chưa được thanh toán.", HttpStatus.BAD_REQUEST),
    PROJECT_ALREADY_FUNDED(8005, "Dự án đã được thanh toán.", HttpStatus.BAD_REQUEST),

    MILESTONE_NOT_FOUND(8009, "Không tìm thấy milestone.", HttpStatus.NOT_FOUND),
    MILESTONE_TITLE_DUPLICATE(8010, "Tên cột mốc đã tồn tại trong dự án này. Vui lòng chọn tên khác.", HttpStatus.BAD_REQUEST),
    CANNOT_CREATE_MILESTONE_FOR_MILESTONE_PAYMENT_TYPE(8011, "Hợp đồng có loại thanh toán MILESTONE không được phép tạo cột mốc. Chỉ hợp đồng có loại thanh toán FULL mới được tạo cột mốc.", HttpStatus.BAD_REQUEST),
    EDIT_COUNT_EXCEEDS_CONTRACT_LIMIT(8012, "Số lượt chỉnh sửa không được vượt quá số lượng trong dự án.", HttpStatus.BAD_REQUEST),
    PRODUCT_COUNT_EXCEEDS_CONTRACT_LIMIT(8013, "Số lượng sản phẩm không được vượt quá số lượng trong dự án.", HttpStatus.BAD_REQUEST),
    MILESTONE_AMOUNT_EXCEEDS_CONTRACT_TOTAL(8014, "Tổng số tiền các cột mốc không được vượt quá tổng số tiền trong dự án.", HttpStatus.BAD_REQUEST),
    EDIT_COUNT_TOTAL_EXCEEDS_CONTRACT_LIMIT(8015, "Tổng số lượt chỉnh sửa của tất cả cột mốc không được vượt quá số lượng trong dự án.", HttpStatus.BAD_REQUEST),
    PRODUCT_COUNT_TOTAL_EXCEEDS_CONTRACT_LIMIT(8016, "Tổng số lượng sản phẩm của tất cả cột mốc không được vượt quá số lượng trong dự án.", HttpStatus.BAD_REQUEST),
    MEMBER_ALREADY_IN_MILESTONE(8017, "Thành viên đã có trong cột mốc này.", HttpStatus.BAD_REQUEST),
    MONEY_SPLIT_NOT_FOUND(8018, "Không tìm thấy phân chia tiền.", HttpStatus.NOT_FOUND),
    MONEY_SPLIT_ALREADY_APPROVED(8019, "Phân chia tiền đã được chấp nhận.", HttpStatus.BAD_REQUEST),
    MONEY_SPLIT_ALREADY_REJECTED(8020, "Phân chia tiền đã bị từ chối.", HttpStatus.BAD_REQUEST),
    MONEY_SPLIT_TOTAL_EXCEEDS_MILESTONE(8021, "Tổng số tiền phân chia và chi phí vượt quá số tiền của cột mốc.", HttpStatus.BAD_REQUEST),
    MONEY_SPLIT_CANNOT_APPROVE_OWN(8022, "Bạn không thể chấp nhận phân chia tiền của chính mình.", HttpStatus.BAD_REQUEST),
    MONEY_SPLIT_CANNOT_REJECT_OWN(8023, "Bạn không thể từ chối phân chia tiền của chính mình.", HttpStatus.BAD_REQUEST),
    MONEY_SPLIT_ONLY_MEMBER_CAN_APPROVE(8024, "Chỉ thành viên được phân chia tiền mới có thể chấp nhận hoặc từ chối.", HttpStatus.FORBIDDEN),
    EXPENSE_NOT_FOUND(8025, "Không tìm thấy chi phí.", HttpStatus.NOT_FOUND),
    MONEY_SPLIT_CANNOT_UPDATE_APPROVED(8026, "Không thể chỉnh sửa phân chia tiền đã được chấp nhận.", HttpStatus.BAD_REQUEST),
    MONEY_SPLIT_CANNOT_UPDATE_REJECTED(8027, "Không thể chỉnh sửa phân chia tiền đã bị từ chối. Vui lòng tạo mới.", HttpStatus.BAD_REQUEST),
    MONEY_SPLIT_CANNOT_DELETE_APPROVED(8028, "Không thể xóa phân chia tiền đã được chấp nhận. Chỉ có thể xóa phân chia tiền đang chờ phản hồi hoặc đã bị từ chối.", HttpStatus.BAD_REQUEST),
    CONTRACT_NOT_COMPLETED_FOR_MILESTONE(8029, "Hợp đồng chưa được hoàn thành. Chỉ có thể tạo hoặc sử dụng cột mốc khi hợp đồng đã được hoàn thành.", HttpStatus.BAD_REQUEST),
    MILESTONE_HAS_APPROVED_MONEY_SPLIT(8030, "Cột mốc đã có phân chia tiền được chấp nhận. Không thể xóa.", HttpStatus.BAD_REQUEST),
    MILESTONE_MEMBER_NOT_FOUND(8031, "Không tìm thấy thành viên cột mốc.", HttpStatus.NOT_FOUND),
    MILESTONE_MEMBER_HAS_APPROVED_MONEY_SPLIT(8032, "Không thể xóa thành viên vì đã có phân chia tiền hoàn tất.", HttpStatus.BAD_REQUEST),
    PROJECT_MEMBER_HAS_MONEY_SPLIT(8033, "Thành viên đã được phân chia tiền trong cột mốc. Không thể xóa.", HttpStatus.BAD_REQUEST),
    PROJECT_OWNER_CANNOT_BE_MODIFIED(8034, "Không thể chỉnh sửa hoặc xóa chủ dự án.", HttpStatus.BAD_REQUEST),
    PROJECT_CLIENT_CANNOT_BE_MODIFIED(8035, "Không thể chỉnh sửa hoặc xóa khách hàng của dự án.", HttpStatus.BAD_REQUEST),
    PROJECT_CLIENT_CONTRACT_COMPLETED(8036, "Không thể xóa khách hàng vì hợp đồng của dự án đã hoàn thành.", HttpStatus.BAD_REQUEST),
    PLAYBACK_CONTROL_DENIED(8001, "Bạn không có quyền điều khiển phát lại", HttpStatus.FORBIDDEN),
    NO_FILE_PLAYING(8002, "Hiện tại không có file nào đang phát", HttpStatus.BAD_REQUEST),
    INVALID_PLAYBACK_POSITION(8003, "Vị trí phát lại không hợp lệ", HttpStatus.BAD_REQUEST),
    PROJECT_MEMBER_NOT_FOUND(8010, "Không tìm thấy thành viên dự án.", HttpStatus.NOT_FOUND),

    // ========== Chat Related (9xxx) ==========
    CHAT_MESSAGE_NOT_FOUND(9001, "Không tìm thấy tin nhắn chat", HttpStatus.NOT_FOUND),
    CANNOT_DELETE_MESSAGE(9002, "Bạn không có quyền xóa tin nhắn này", HttpStatus.FORBIDDEN),
    MESSAGE_TOO_LONG(9003, "Tin nhắn vượt quá độ dài tối đa", HttpStatus.BAD_REQUEST),

    SESSION_NOT_FOUND(5009, "Không tìm thấy phiên", HttpStatus.NOT_FOUND),
    SESSION_NOT_ACTIVE(5010, "Phiên không hoạt động", HttpStatus.BAD_REQUEST),
    SESSION_ALREADY_ACTIVE(5012, "Phiên đã hoạt động", HttpStatus.BAD_REQUEST),
    SESSION_ALREADY_ENDED(5013, "Phiên đã kết thúc", HttpStatus.BAD_REQUEST),
    SESSION_ALREADY_STARTED(5014, "Phiên đã bắt đầu", HttpStatus.BAD_REQUEST),
    CAN_ONLY_CANCEL_SCHEDULED_SESSION(5016, "Chỉ có thể hủy phiên đã lên lịch", HttpStatus.BAD_REQUEST),
    MUST_REQUEST_JOIN_FIRST(5017, "Bạn phải gửi yêu cầu tham gia và được phê duyệt trước", HttpStatus.FORBIDDEN),
    CANNOT_REMOVE_HOST(5106, "Không thể xóa người chủ trì phiên", HttpStatus.BAD_REQUEST),


    PARTICIPANT_NOT_FOUND(5101, "Không tìm thấy người tham gia trong phiên", HttpStatus.NOT_FOUND),
    USER_ALREADY_INVITED(5102, "Người dùng đã được mời vào phiên này", HttpStatus.CONFLICT),
    USER_NOT_IN_PROJECT(5103, "Người dùng không phải là thành viên của dự án này", HttpStatus.FORBIDDEN),
    INVITATION_DECLINED(5104, "Lời mời đã bị từ chối", HttpStatus.BAD_REQUEST),
    ONLY_HOST_CAN_PERFORM_ACTION(5105, "Chỉ người chủ trì phiên mới có thể thực hiện hành động này", HttpStatus.FORBIDDEN),

    // Project errors (4xxx)
    ONLY_PROJECT_OWNER_CAN_CREATE_SESSION(4002, "Chỉ chủ sở hữu dự án mới có thể tạo phiên", HttpStatus.FORBIDDEN),
    PROJECT_ALREADY_HAS_ACTIVE_SESSION(4003, "Dự án đã có phiên hoạt động", HttpStatus.BAD_REQUEST),
    MAX_CONCURRENT_SESSIONS_REACHED(4004, "Dự án đã đạt giới hạn tối đa 3 phiên đồng thời", HttpStatus.BAD_REQUEST),

    // Session Update/Delete errors (5300-5399)
    SCHEDULED_START_MUST_BE_FUTURE(5301, "Thời gian bắt đầu phải là thời gian tương lai", HttpStatus.BAD_REQUEST),
    CAN_ONLY_UPDATE_SCHEDULED_SESSION(5304, "Chỉ có thể cập nhật phiên đã lên lịch", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_ACTIVE_SESSION(5306, "Không thể xóa phiên đang hoạt động. Vui lòng kết thúc phiên trước", HttpStatus.BAD_REQUEST),
    CAN_ONLY_DELETE_SCHEDULED_ENDED_OR_CANCELLED_SESSION(5307, "Chỉ có thể xóa phiên đã lên lịch, đã kết thúc hoặc đã hủy", HttpStatus.BAD_REQUEST),
    CAN_ONLY_INVITE_TO_SCHEDULED_SESSION(5310, "Chỉ có thể mời thêm thành viên vào phiên đã lên lịch", HttpStatus.BAD_REQUEST),
    CAN_ONLY_INVITE_TO_PRIVATE_SESSION(5311, "Chỉ có thể mời thêm thành viên vào phiên riêng tư", HttpStatus.BAD_REQUEST),
    MEMBER_IDS_AND_ROLES_MUST_MATCH(5312, "Số lượng thành viên và vai trò phải khớp nhau", HttpStatus.BAD_REQUEST),
    USER_NOT_IN_PROJECTS(5313, "Người dùng không phải thành viên của dự án", HttpStatus.BAD_REQUEST),
    ANONYMOUS_MEMBER_CANNOT_ACCESS_SESSION(5308, "Thành viên ẩn danh không thể truy cập phiên", HttpStatus.FORBIDDEN),
    NOT_PROJECT_MEMBER(5309, "Bạn không phải là thành viên của dự án này", HttpStatus.FORBIDDEN),

    // Join Request errors (5200-5299)
    JOIN_REQUEST_NOT_FOUND(5201, "Không tìm thấy yêu cầu tham gia hoặc đã hết hạn", HttpStatus.NOT_FOUND),
    JOIN_REQUEST_EXPIRED(5202, "Yêu cầu tham gia đã hết hạn", HttpStatus.BAD_REQUEST),
    DUPLICATE_JOIN_REQUEST(5203, "Bạn đã có yêu cầu tham gia đang chờ xử lý", HttpStatus.CONFLICT),
    REQUEST_ALREADY_PROCESSED(5204, "Yêu cầu đã được xử lý", HttpStatus.CONFLICT),
    OWNER_BYPASS_APPROVAL(5205, "Chủ phòng không cần phê duyệt để tham gia", HttpStatus.BAD_REQUEST),

    INVALID_FILE_KEY(1011, "Key của file không hợp lệ.", HttpStatus.BAD_REQUEST)
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
