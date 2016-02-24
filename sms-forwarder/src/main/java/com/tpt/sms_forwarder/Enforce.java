package com.tpt.sms_forwarder;

/**
 * Created meifakun 14-2-17
 */
public class Enforce {
    public static String NotNullOrEmpty(String s, String name) throws IllegalArgumentException{
        if(null == s || 0 == s.length()) {
            throw new IllegalArgumentException("'"+name+"' is null or empty");
        }
        return s;
    }

    public static String ThrowIfNullOrEmpty(String s, String msg) {
        if(null == s || 0 == s.length()) {
            throw new IllegalArgumentException(msg);
        }
        return s;
    }

    public static <T> T NotNull(T o, String name) {
        if(null == o) {
            throw new IllegalArgumentException("'"+name+"' is null or empty");
        }
        return o;
    }

    public static String DefaultIfNullOrEmpty(String s, String defaultValue) {
        if(null == s || 0 == s.length()) {
            return defaultValue;
        }
        return s;
    }

    public static <T> T Default(T o, T defaultValue) {
        return (null == o)?defaultValue: o;
    }
}
