package org.backend.admin.exception;

public class UploadNotCompletedException extends AdminApiException {
    public UploadNotCompletedException() {
        super(ErrorCode.UPLOAD_NOT_COMPLETED);
    }

    public UploadNotCompletedException(String message) {
        super(ErrorCode.UPLOAD_NOT_COMPLETED, message);
    }
}