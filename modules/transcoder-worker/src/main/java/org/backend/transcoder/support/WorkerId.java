package org.backend.transcoder.support;

public final class WorkerId {
    private static final String ID;

    static {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isBlank()) hostname = "worker-unknown";
        ID = hostname;
    }

    private WorkerId() {}
    public static String get() { return ID; }
}