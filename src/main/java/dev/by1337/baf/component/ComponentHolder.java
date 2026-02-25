package dev.by1337.baf.component;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.command.AirdropContext;
import org.bukkit.Bukkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ComponentHolder {
    private static final Logger log = LoggerFactory.getLogger(ComponentHolder.class);
    private final Map<String, AirdropComponent> byName = new HashMap<>();
    private final Map<Class<? extends AirdropComponent>, Set<AirdropComponent>> byType = new HashMap<>();

    public ComponentHolder() {
    }

    public AirdropComponent get(String name) {
        reqMain("async get");
        return byName.get(name);
    }

    public List<AirdropComponent> all() {
        reqMain("async all");
        return new ArrayList<>(byName.values());
    }

    public <T extends AirdropComponent> List<T> allOf(Class<T> t) {
        reqMain("async allOf");
        var set = byType.get(t);
        if (set == null || set.isEmpty()) return List.of();
        List<T> result = new ArrayList<>(set.size());
        for (AirdropComponent c : set) {
            // noinspection unchecked
            result.add((T) c);
        }
        return result;
    }

    public void add(AirdropComponent component, AirdropContext ctx) {
        reqMain("async add");
        if (byName.containsKey(component.name())) {
            throw new IllegalArgumentException("component " + component.name() + " already exist!");
        }
        byName.put(component.name(), component);
        byType.computeIfAbsent(component.getClass(), k -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(component);
        if (!ctx.call(component, AirdropComponent::onCreate)){
            component.markRemoved(ctx);
        }
    }

    public void remove(AirdropComponent component, AirdropContext ctx) {
        reqMain("async remove");
        byName.remove(component.name());
        var set = byType.get(component.getClass());
        if (set != null) {
            set.remove(component);
        }
        ctx.call(component, AirdropComponent::onRemove);
    }

    private void reqMain(String msg) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(msg);
        }
    }
}
