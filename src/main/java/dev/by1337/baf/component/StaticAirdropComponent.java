/*
package dev.by1337.baf.component;

import dev.by1337.baf.command.AirdropContext;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class StaticAirdropComponent implements AirdropComponent {
    private final Map<String, Consumer<AirdropContext>> listeners;
    protected final String name;

    public StaticAirdropComponent(String name) {
        this.name = name;
        listeners = new HashMap<>();
    }
    protected void exportEvent(String name, Consumer<AirdropContext> c) {
        listeners.put(name, c);
    }

    @Override
    public boolean call(String event, AirdropContext ctx) {
        var v = listeners.get(event);
        if (v != null) {
            ctx.call(v);
            return true;
        }
        return false;
    }

    @Override
    public final void markRemoved(AirdropContext ctx) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean isRemoved() {
        return false;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public final Object getPlaceholder(String key) {
        return null;
    }

    @Override
    public final void onCreate(AirdropContext ctx) {
        //nop
    }

    @Override
    public final void onRemove(AirdropContext ctx) {
        //nop
    }
}
*/
