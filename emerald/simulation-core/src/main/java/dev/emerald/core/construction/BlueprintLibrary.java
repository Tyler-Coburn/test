package dev.emerald.core.construction;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime registry of known blueprints. Not persisted: rebuilt from code and resources at startup. */
public final class BlueprintLibrary {
    private final Map<String, Blueprint> blueprints = new ConcurrentHashMap<>();
    private final Map<String, String> sources = new ConcurrentHashMap<>();

    public static BlueprintLibrary withDefaults() {
        BlueprintLibrary lib = new BlueprintLibrary();
        lib.register(Blueprints.fallbackHut(), "built-in fallback");
        return lib;
    }

    public void register(Blueprint blueprint, String source) {
        blueprints.put(blueprint.id(), blueprint);
        sources.put(blueprint.id(), source);
    }

    public Optional<Blueprint> get(String id) {
        return Optional.ofNullable(blueprints.get(id));
    }

    public String source(String id) {
        return sources.getOrDefault(id, "unknown");
    }
}
