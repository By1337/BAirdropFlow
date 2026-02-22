package dev.by1337.baf.component;

import dev.by1337.baf.command.AirdropContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class AbstractComponent implements AirdropComponent {
    private static final Logger log = LoggerFactory.getLogger(AbstractComponent.class);
    private final Map<String, Consumer<AirdropContext>> listeners;
    protected final Map<String, Supplier<Object>> placeholders = new HashMap<>();
    protected final String name;
    private boolean removed;
    private final ComponentHolder owner;

    public AbstractComponent(String name, @NotNull ComponentHolder owner) {
        this.name = name;
        this.owner = owner;
        listeners = new HashMap<>();
        // exportEvent("create", this::onCreate);
        // exportEvent("remove", this::onRemove);
    }

    public abstract void onCreate(AirdropContext ctx);

    public abstract void onRemove(AirdropContext ctx);

    @Override
    public boolean call(String event, AirdropContext ctx) {
        var v = listeners.get(event);
        if (v != null) {
            ctx.call(v);
            return true;
        }
        return false;
    }

    protected void exportEvent(String name, Consumer<AirdropContext> c) {
        listeners.put(name, c);
    }

    protected void exportPlaceholder(String key, Supplier<Object> s) {
        placeholders.put(key, s);
    }

    @Override
    public Object getPlaceholder(String key) {
        var s = placeholders.get(key);
        return s == null ? null : s.get();
    }

    @Override
    public void markRemoved(AirdropContext ctx) {
        if (removed) {
            log.warn("duplicate markRemoved! {}", name);
            return;
        }
        removed = true;
        owner.remove(this, ctx);
    }

    @Override
    public boolean isRemoved() {
        return removed;
    }

    @Override
    public String name() {
        return name;
    }
}
