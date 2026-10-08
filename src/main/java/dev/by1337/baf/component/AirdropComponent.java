package dev.by1337.baf.component;


import dev.by1337.edsl.context.EventContext;

public interface AirdropComponent {
    NodeRegistry REGISTRY = new NodeRegistry();

    boolean call(String event, EventContext ctx);

    void markRemoved(EventContext ctx);

    boolean isRemoved();

    String name();

    Object getPlaceholder(String key);

    void onCreate(EventContext ctx);

    void onRemove(EventContext ctx);
}
