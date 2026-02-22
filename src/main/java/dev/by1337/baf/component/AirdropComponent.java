package dev.by1337.baf.component;

import dev.by1337.baf.command.AirdropContext;

public interface AirdropComponent {
    ComponentRegistry REGISTRY = new ComponentRegistry();

    boolean call(String event, AirdropContext ctx);
    void markRemoved();

    boolean isRemoved();
    String name();
    Object getPlaceholder(String key);

    void onCreate(AirdropContext ctx);

    void onRemove(AirdropContext ctx);
}
