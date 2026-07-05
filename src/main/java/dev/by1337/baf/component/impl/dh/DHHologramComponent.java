package dev.by1337.baf.component.impl.dh;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentEvent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.util.Vec3d;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DHHologramComponent extends AbstractComponent {
    private final Config config;
    private final Airdrop airdrop;
    private final CleanerSlot<Hologram> hologram;

    private DHHologramComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.airdrop = airdrop;
        this.config = config;
        hologram = new CleanerSlot<>(h -> DHAPI.removeHologram(h.getName()), airdrop.cleaner());
        exportEvent("update", this::onUpdate);
    }

    @ComponentEvent("update")
    public void onUpdate(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " update {", "}")) {
            var holo = hologram.get();
            Objects.requireNonNull(holo, "hologram is not created!");
            DHAPI.setHologramLines(holo, lines(ctx));
            config.update.handle(ctx);
        }
    }

    @Override
    @ComponentEvent("create")
    public void onCreate(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " create {", "}")) {
            hologram.set(DHAPI.createHologram(UUID.randomUUID().toString(), new Location(
                    airdrop.world(),
                    airdrop.x() + config.offsets.x(),
                    airdrop.y() + config.offsets.y(),
                    airdrop.z() + config.offsets.z()
            ), lines(ctx)));
            config.create.handle(ctx);
        }
    }

    @Override
    @ComponentEvent("remove")
    public void onRemove(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " remove {", "}")) {
            hologram.cleanup();
            config.remove.handle(ctx);
        }
    }

    private List<String> lines(EventContext ctx) {
        List<String> result = new ArrayList<>();
        for (String line : config.lines) {
            result.add(ctx.setPlaceholders(line));
        }
        return result;
    }

    public static class Config implements ComponentFactory {
        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                Vec3d.DECODER.fieldOf("offsets", new Vec3d(0, 0, 0)),
                YamlDecoder.STRINGS.fieldOf("lines", List.of()),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_update", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER)
        );
        private final Vec3d offsets;
        private final List<String> lines;
        private final EventHandler create;
        private final EventHandler update;
        private final EventHandler remove;

        public Config(Vec3d offsets, List<String> lines, EventHandler create, EventHandler update, EventHandler remove) {
            this.offsets = offsets;
            this.lines = lines;
            this.create = create;
            this.update = update;
            this.remove = remove;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new DHHologramComponent(this, name, airdrop);
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }

    }
}
