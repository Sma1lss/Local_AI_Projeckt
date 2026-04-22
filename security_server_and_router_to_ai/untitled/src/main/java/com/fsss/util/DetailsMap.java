package com.fsss.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DetailsMap {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private DetailsMap() {
    }

    public static DetailsMap create() {
        return new DetailsMap();
    }

    public DetailsMap add(String key, Object value) {
        if (key != null && value != null) {
            values.put(key, value);
        }
        return this;
    }

    public Map<String, Object> build() {
        return Map.copyOf(values);
    }
}
