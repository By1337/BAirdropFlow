package dev.by1337.baf.component;

import dev.by1337.edsl.context.EventContext;
import org.bukkit.Bukkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ComponentHolder {
    private static final Logger log = LoggerFactory.getLogger("BAirdropFlow");
    private final Map<String, AirdropComponent> byName = new HashMap<>();
    private final List<AirdropComponent> list = new CopyOnWriteArrayList<>();

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

    public void forEach(Consumer<AirdropComponent> c) {
        list.forEach(c);
    }

    public void add(AirdropComponent component, EventContext ctx) {
        reqMain("async add");
        if (byName.containsKey(component.name())) {
            throw new IllegalArgumentException("component " + component.name() + " already exist!");
        }
        byName.put(component.name(), component);
        list.add(component);
        component.onCreate(ctx);
    }

    public void remove(AirdropComponent component, EventContext ctx) {
        reqMain("async remove");
        list.remove(component);
        byName.remove(component.name());
        component.onRemove(ctx);
    }

    private void reqMain(String msg) {
        if (!Bukkit.isPrimaryThread() && !Bukkit.isStopping()) {
            throw new IllegalStateException(msg);
        }
    }
}
