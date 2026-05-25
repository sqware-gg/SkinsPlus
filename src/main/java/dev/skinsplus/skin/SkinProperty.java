package dev.skinsplus.skin;

public record SkinProperty(String value, String signature) {
    public boolean signed() {
        return signature != null && !signature.isBlank();
    }
}
