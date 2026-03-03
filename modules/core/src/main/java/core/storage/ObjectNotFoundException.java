package core.storage;

public class ObjectNotFoundException extends RuntimeException {
    public ObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}