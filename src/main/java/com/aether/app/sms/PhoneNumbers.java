package com.aether.app.sms;

/**
 * Normalizes user-entered phone strings toward E.164 (US-centric when no country code is present).
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /**
     * @return E.164-like string starting with {@code +}, or null if not usable
     */
    public static String normalizeToE164(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("+")) {
            String digits = trimmed.substring(1).replaceAll("\\D", "");
            return digits.isEmpty() ? null : "+" + digits;
        }
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() == 10) {
            return "+1" + digits;
        }
        if (digits.length() == 11 && digits.startsWith("1")) {
            return "+" + digits;
        }
        return "+" + digits;
    }
}
