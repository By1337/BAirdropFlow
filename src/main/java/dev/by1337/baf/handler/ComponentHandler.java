package dev.by1337.baf.handler;

import dev.by1337.baf.command.AirdropContext;
import dev.by1337.cmd.Command;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.HandlerCodecBuilder;
import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.yaml.YamlValue;
import dev.by1337.yaml.codec.YamlCodec;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComponentHandler implements EventHandler<AirdropContext> {
    public static final YamlCodec<ComponentHandler> CODEC;
    private static final Logger log = LoggerFactory.getLogger(ComponentHandler.class);

    private final EventHandler<AirdropContext> source;

    public ComponentHandler(EventHandler<AirdropContext> source) {
        this.source = source;
    }


    public boolean handle(AirdropContext entity) {
        return handle(entity, entity.airdrop(), entity.airdrop().getCommands());
    }

    @Override
    public boolean handle(AirdropContext entity, PlaceholderApplier placeholderApplier, Command<AirdropContext> command) {
        try {
            return source.handle(entity, placeholderApplier, command);
        } catch (Exception e) {
            log.error("failed to handle event!", e);
            return false;
        }
    }

    @Override
    public @Nullable YamlValue encode() {
        return source.encode();
    }

    static {
        HandlerCodecBuilder<AirdropContext> builder = new HandlerCodecBuilder<>();
        CODEC = builder.handlerCodec().map(
                ComponentHandler::new,
                c -> c.source
        );
    }
}
