package com.dbhelp.common;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON 友好 {@link Map} 构建工具：通用键值对及常见 {@code success}/{@code message} 响应。
 */
public final class JsonMaps {

    private JsonMaps() {
    }

    /**
     * 按 key、value、key、value… 交替组装为可变 HashMap。
     *
     * @throws IllegalArgumentException 参数个数不是偶数时
     */
    public static Map<String, Object> of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must have even length (key, value pairs)");
        }
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    /** {@code { "success": true }} */
    public static Map<String, Object> success() {
        return of("success", true);
    }

    /** {@code { "success": true, "message": message }} */
    public static Map<String, Object> success(String message) {
        return of("success", true, "message", message);
    }

    /**
     * {@code success} + {@code message}，再追加任意偶数个键值对。
     */
    public static Map<String, Object> success(String message, Object... moreKeyValues) {
        if (moreKeyValues.length % 2 != 0) {
            throw new IllegalArgumentException("moreKeyValues must have even length");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("success", true);
        map.put("message", message);
        putPairs(map, moreKeyValues);
        return map;
    }

    /**
     * {@code success: true} 与任意偶数个额外字段（无默认 {@code message}）。
     */
    public static Map<String, Object> successExtra(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must have even length");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("success", true);
        putPairs(map, keyValues);
        return map;
    }

    /** {@code { "success": false, "message": message }} */
    public static Map<String, Object> failure(String message) {
        return of("success", false, "message", message);
    }

    private static void putPairs(Map<String, Object> target, Object[] keyValues) {
        for (int i = 0; i < keyValues.length; i += 2) {
            target.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
    }
}
