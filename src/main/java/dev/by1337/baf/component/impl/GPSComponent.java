package dev.by1337.baf.component.impl;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.BAF;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.config.value.BooleanValue;
import dev.by1337.baf.config.value.StringValue;
import dev.by1337.baf.gps.GPSRegistry;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

public class GPSComponent extends AbstractComponent {
    private final Config config;
    private final Airdrop airdrop;
    private final GPSRegistry registry;
    private final CleanerSlot<GPSRegistry.GPSEntry> entry;

    private GPSComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.config = config;
        this.airdrop = airdrop;
        registry = BAF.gps();
        entry = new CleanerSlot<>(value -> registry.remove(value.id()), airdrop.cleaner());
        exportPlaceholder("gps_id", () -> {
            var v = entry.get();
            if (v == null) return "-1";
            return Long.toString(v.id());
        });
    }

    @Override
    public void onCreate(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " create {", "}")) {
            var value = new GPSRegistry.GPSEntry(Airdrop.PLACEHOLDERS.bindCtx(airdrop),
                    config.gps_line().source(), config.publish_location().getBoolean(airdrop) ? airdrop.location() : null);
            entry.set(value);
            registry.publish(value);
            config.on_create().handle(ctx);
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " remove {", "}")) {
            entry.cleanup();
            config.on_remove().handle(ctx);
        }
    }

    public record Config(BooleanValue publish_location, StringValue gps_line,
                         EventHandler on_create, EventHandler on_remove) implements ComponentFactory {
        public Config(boolean publishLocation, String gpsLine, EventHandler onCreate, EventHandler onRemove) {
            this(new BooleanValue(publishLocation), new StringValue(gpsLine), onCreate, onRemove);
        }

        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("GPS", "Публикует аирдроп в GPS. Запись удаляется при снятии ноды или очистке аирдропа.")
                .placeholder("gps_id", "gps id", "id в /gps <id>")
                .bool("publish_location", "Публиковать координаты", true)
                .text("gps_line", "Строка GPS", "", false)
                .inputs("create", "remove")
                .events("on_create", "on_remove");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                BooleanValue.DECODER.fieldOf("publish_location", new BooleanValue(true)),
                StringValue.DECODER.fieldOf("gps_line", new StringValue("")),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER)
        );

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new GPSComponent(this, name, airdrop);
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
