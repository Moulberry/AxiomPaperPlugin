package com.moulberry.axiom.integration.prism;

final class PrismActionKey {
    static final int MAX_DATABASE_LENGTH = 25;

    private PrismActionKey() {
    }

    static void validateRegistryKey(String key) {
        if (key == null || !key.matches("[a-z0-9]([a-z0-9-]*[a-z0-9])?")) {
            throw new IllegalArgumentException(
                "Prism action key must be lowercase, hyphenated, and must not start or end with a hyphen: " + key
            );
        }
    }

    static void validateWritableKey(String key) {
        validateRegistryKey(key);
        if (key.length() > MAX_DATABASE_LENGTH) {
            throw new IllegalArgumentException(
                "Prism action key must be at most " + MAX_DATABASE_LENGTH + " characters: " + key
            );
        }
    }
}
