package dev.by1337.baf.component.impl.holo;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.component.system.TimerSystem;
import dev.by1337.baf.config.value.StringValue;
import dev.by1337.baf.config.value.Vec3dValue;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.util.Vec3d;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class HologramComponent extends AbstractComponent implements TimerSystem.Tickable {
    private final Config config;
    private final Airdrop airdrop;
    private final CleanerSlot<Hologram> hologram;

    private HologramComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.airdrop = airdrop;
        this.config = config;
        hologram = new CleanerSlot<>(Hologram::close, airdrop.cleaner());
        exportEvent("update", this::onUpdate);
    }

    public void onUpdate(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " update {", "}")) {
            var holo = hologram.get();
            Objects.requireNonNull(holo, "hologram is not created!");
            holo.setLines(lines());
            syncViewers(holo);
            config.update.handle(ctx);
        }
    }

    @Override
    public void onCreate(EventContext ctx) {
        var offsets = config.offsets.getVector(airdrop);
        try (var ignored = ctx.tracer().enter(name + " create {", "}")) {
            hologram.set(new Hologram(
                    airdrop.world(),
                    airdrop.x() + offsets.x(),
                    airdrop.y() + offsets.y(),
                    airdrop.z() + offsets.z()
            ));
            hologram.get().setLines(lines());
            syncViewers(hologram.get());
            config.create.handle(ctx);
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " remove {", "}")) {
            hologram.cleanup();
            config.remove.handle(ctx);
        }
    }

    @Override
    public void onSyncTick(EventContext ctx) {
        var holo = hologram.get();
        if (!isRemoved() && holo != null) syncViewers(holo);
    }

    private void syncViewers(Hologram holo) {
        Set<Player> nearby = airdrop.nearbyPlayers();
        holo.sync(nearby);
    }

    private List<String> lines() {
        List<String> result = new ArrayList<>();
        for (StringValue line : config.lines) {
            result.add(line.getString(airdrop));
        }
        return result;
    }

    public static class Config implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Голограмма", "Голограмма на ArmorStand в радиусе аирдропа. Шаг строк 0.3 блока. Вызов update обновляет строки.")
                .vector("offsets", "Смещение", "0;0;0", true)
                .strings("lines", "Строки голограммы")
                .events("on_create", "on_update", "on_remove")
                .inputs("create", "remove", "update");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                Vec3dValue.DECODER.fieldOf("offsets", new Vec3dValue(new Vec3d(0, 0, 0))),
                StringValue.DECODER.listOf().fieldOf("lines", List.of()),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_update", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER)
        );
        private final Vec3dValue offsets;
        private final List<StringValue> lines;
        private final EventHandler create;
        private final EventHandler update;
        private final EventHandler remove;

        public Config(Vec3d offsets, List<String> lines, EventHandler create, EventHandler update, EventHandler remove) {
            this(new Vec3dValue(offsets), lines.stream().map(StringValue::new).toList(), create, update, remove);
        }

        public Config(Vec3dValue offsets, List<StringValue> lines, EventHandler create, EventHandler update, EventHandler remove) {
            this.offsets = offsets;
            this.lines = List.copyOf(lines);
            this.create = create;
            this.update = update;
            this.remove = remove;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new HologramComponent(this, name, airdrop);
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }

    }
}
