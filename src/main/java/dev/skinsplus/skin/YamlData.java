package dev.skinsplus.skin;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class YamlData {
    private YamlData() {
    }

    static Map<String, Object> load(Path file, SkinLogger logger) {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            Object loaded = yaml().load(reader);
            if (!(loaded instanceof Map<?, ?> rawMap)) {
                return new LinkedHashMap<>();
            }
            return stringMap(rawMap);
        } catch (IOException | RuntimeException exception) {
            logger.warning("Failed to load " + file.getFileName() + ": " + exception.getMessage());
            return new LinkedHashMap<>();
        }
    }

    static void save(Path file, Map<String, Object> data, SkinLogger logger, String name) {
        try {
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) {
                logger.fine("Skipped saving " + name + " because the plugin data folder does not exist.");
                return;
            }
            try (Writer writer = Files.newBufferedWriter(file)) {
                yaml().dump(data, writer);
            }
        } catch (IOException | RuntimeException exception) {
            logger.warning("Failed to save " + name + ": " + exception.getMessage());
        }
    }

    static Map<String, Object> section(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value instanceof Map<?, ?> rawMap) {
            return stringMap(rawMap);
        }
        return new LinkedHashMap<>();
    }

    static String string(Map<String, Object> root, String key, String fallback) {
        Object value = root.get(key);
        return value == null ? fallback : value.toString();
    }

    static long longValue(Map<String, Object> root, String key, long fallback) {
        Object value = root.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static boolean bool(Map<String, Object> root, String key, boolean fallback) {
        Object value = root.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }

    static Map<String, Object> stringMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return result;
    }

    private static Yaml yaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        return new Yaml(new SafeConstructor(loaderOptions), new Representer(dumperOptions), dumperOptions, loaderOptions);
    }
}
