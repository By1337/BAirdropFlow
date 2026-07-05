package dev.by1337.baf.component;

import dev.by1337.baf.Airdrop;
import dev.by1337.edsl.EventHandler;
import dev.by1337.yaml.decoder.YamlDecoder;

public interface ComponentFactory {
    static EventHandler EMPTY_HANDLER = EventHandler.nop();
    static YamlDecoder<EventHandler> HANDLER = EventHandler.decoder();

    AirdropComponent create(Airdrop airdrop, String name);

    YamlDecoder<? extends ComponentFactory> codec();
}
