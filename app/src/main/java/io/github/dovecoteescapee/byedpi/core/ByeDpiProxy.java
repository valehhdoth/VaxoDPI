package io.github.dovecoteescapee.byedpi.core;

public final class ByeDpiProxy {
    static {
        System.loadLibrary("byedpi");
    }

    private ByeDpiProxy() {}

    public static native int jniCreateSocketWithCommandLine(String[] args);
    public static native int jniStartProxy(int fd);
    public static native int jniStopProxy(int fd);
}
