package com.joshfouchey.smsarchive.util;

/**
 * Centralized input length limits. Keeps controllers consistent
 * and prevents oversized strings from reaching the database/LLM.
 */
public final class InputLimits {
    private InputLimits() {}

    public static final int USERNAME_MAX = 40;
    public static final int PASSWORD_MIN = 6;
    public static final int PASSWORD_MAX = 128;
    public static final int SEARCH_QUERY_MAX = 500;
    public static final int CONTACT_NAME_MAX = 100;
    public static final int CONVERSATION_NAME_MAX = 100;
    public static final int QA_QUESTION_MAX = 1000;

    /** Truncate to max length (safe, never throws). */
    public static String truncate(String input, int max) {
        if (input == null) return null;
        return input.length() <= max ? input : input.substring(0, max);
    }
}
