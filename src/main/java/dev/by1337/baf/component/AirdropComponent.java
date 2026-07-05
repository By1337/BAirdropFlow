package dev.by1337.baf.component;


import dev.by1337.baf.registry.DecoderRegistry;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.YamlDecoder;

public interface AirdropComponent {
    DecoderRegistry<ComponentFactory> REGISTRY = new DecoderRegistry<>();

    boolean call(String event, EventContext ctx);
    void markRemoved(EventContext ctx);

    boolean isRemoved();
    String name();
    Object getPlaceholder(String key);

    void onCreate(EventContext ctx);

    void onRemove(EventContext ctx);
}
