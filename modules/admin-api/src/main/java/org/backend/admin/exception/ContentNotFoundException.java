package org.backend.admin.exception;

public class ContentNotFoundException extends AdminApiException {
    public ContentNotFoundException() {
        super(ErrorCode.CONTENT_NOT_FOUND);
    }
}