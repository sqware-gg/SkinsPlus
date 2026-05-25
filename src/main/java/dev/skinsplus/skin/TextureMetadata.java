package dev.skinsplus.skin;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record TextureMetadata(
        boolean hasSkin,
        Optional<String> skinUrl,
        Optional<String> skinModel,
        boolean hasCape,
        Optional<String> capeUrl
) {
    private static final Pattern URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MODEL = Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"");

    public static TextureMetadata from(SkinProperty property) {
        try {
            String json = new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8);
            Optional<String> skinBlock = textureBlock(json, "SKIN");
            Optional<String> capeBlock = textureBlock(json, "CAPE");
            return new TextureMetadata(
                    skinBlock.isPresent(),
                    skinBlock.flatMap(TextureMetadata::url),
                    skinBlock.flatMap(TextureMetadata::model),
                    capeBlock.isPresent(),
                    capeBlock.flatMap(TextureMetadata::url)
            );
        } catch (RuntimeException exception) {
            return new TextureMetadata(false, Optional.empty(), Optional.empty(), false, Optional.empty());
        }
    }

    public String summary() {
        String model = skinModel.orElse("classic");
        return hasCape ? "model=" + model + ", cape=yes" : "model=" + model + ", cape=no";
    }

    private static Optional<String> textureBlock(String json, String key) {
        int keyIndex = json.indexOf("\"" + key + "\"");
        if (keyIndex < 0) {
            return Optional.empty();
        }

        int start = json.indexOf('{', keyIndex);
        if (start < 0) {
            return Optional.empty();
        }

        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char character = json.charAt(i);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return Optional.of(json.substring(start, i + 1));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> url(String block) {
        Matcher matcher = URL.matcher(block);
        return matcher.find() ? Optional.of(unescapeJson(matcher.group(1))) : Optional.empty();
    }

    private static Optional<String> model(String block) {
        Matcher matcher = MODEL.matcher(block);
        return matcher.find() ? Optional.of(unescapeJson(matcher.group(1))) : Optional.empty();
    }

    private static String unescapeJson(String value) {
        return value.replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
