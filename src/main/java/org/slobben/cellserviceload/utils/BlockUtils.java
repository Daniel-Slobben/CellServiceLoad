package org.slobben.cellserviceload.utils;

public final class BlockUtils {
    private static final String SPLIT_CHAR = "/";

    public static String getKey(int x, int y) {
        return x + SPLIT_CHAR + y;
    }
}
