package dev.by1337.baf.component.impl.dh;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.command.AirdropContext;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.handler.ComponentHandler;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.util.Vec3d;
import dev.by1337.yaml.codec.RecordYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;
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

    public void onUpdate(AirdropContext ctx) {
        System.out.println("DHHologramComponent.onUpdate");
        var holo = hologram.get();
        Objects.requireNonNull(holo, "hologram is not created!");
        DHAPI.setHologramLines(holo, lines());
        config.update.handle(ctx);
    }

    @Override
    public void onCreate(AirdropContext ctx) {
        System.out.println("DHHologramComponent.onCreate");
        hologram.set(DHAPI.createHologram(UUID.randomUUID().toString(), new Location(
                airdrop.world(),
                airdrop.x() + config.offsets.x(),
                airdrop.y() + config.offsets.y(),
                airdrop.z() + config.offsets.z()
        ), lines()));
        config.create.handle(ctx);
    }

    private List<String> lines() {
        List<String> result = new ArrayList<>();
        for (String line : config.lines) {
            result.add(airdrop.setPlaceholders(line));
        }
        return result;
    }

    @Override
    public void onRemove(AirdropContext ctx) {
        System.out.println("DHHologramComponent.onRemove");
        hologram.cleanup();
        config.remove.handle(ctx);
    }

    public static class Config implements ComponentFactory {
        public static final YamlCodec<Config> CODEC = RecordYamlCodecBuilder.mapOf(
                Config::new,
                Vec3d.CODEC.fieldOf("offsets", v -> v.offsets, new Vec3d(0, 0, 0)),
                YamlCodec.STRINGS.fieldOf("lines", v -> v.lines, List.of()),
                ComponentHandler.CODEC.fieldOf("on_create", v -> v.create, ComponentHandler.EMPTY),
                ComponentHandler.CODEC.fieldOf("on_update", v -> v.update, ComponentHandler.EMPTY),
                ComponentHandler.CODEC.fieldOf("on_remove", v -> v.remove, ComponentHandler.EMPTY)
        );
        private final Vec3d offsets;
        private final List<String> lines;
        private final ComponentHandler create;
        private final ComponentHandler update;
        private final ComponentHandler remove;

        public Config(Vec3d offsets, List<String> lines, ComponentHandler create, ComponentHandler update, ComponentHandler remove) {
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
        public YamlCodec<? extends ComponentFactory> codec() {
            return CODEC;
        }

    }
}
