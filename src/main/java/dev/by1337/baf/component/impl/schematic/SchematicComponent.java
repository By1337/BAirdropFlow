package dev.by1337.baf.component.impl.schematic;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.config.value.Vec3iValue;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.schematic.Schematic;
import dev.by1337.baf.schematic.SchematicWriter;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

public class SchematicComponent extends AbstractComponent implements dev.by1337.baf.component.system.TimerSystem.Tickable {
    private java.util.List<dev.by1337.core.util.misc.Pair<Vec3i, org.bukkit.block.data.BlockData>> pending = java.util.List.of();
    private int index, age;
    private boolean complete;
    private Vec3i position;
    private final Config config;
    private final Airdrop airdrop;
    private final CleanerSlot<SchematicWriter> schematic;

    private SchematicComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.config = config;
        this.airdrop = airdrop;
        schematic = new CleanerSlot<>(SchematicWriter::undoSchematics, airdrop.cleaner());
    }

    @Override
    public void onCreate(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " create {", "}")) {
            position = new Vec3i(airdrop.x(), airdrop.y(), airdrop.z()).add(config.offsets.getVector(airdrop));
            SchematicWriter writer = new SchematicWriter(airdrop.world(), position);
            schematic.set(writer);
            index = age = 0;
            complete = false;
            if (config.animation.interval == 0) {
                writer.writeSchematic(config.schematic);
            } else {
                var blocks = new java.util.ArrayList<>(config.schematic.schematicFile().blocks().stream()
                        .filter(p -> !config.schematic.no_place().contains(p.getRight().getMaterial())).toList());
                if (config.animation.shuffle) java.util.Collections.shuffle(blocks);
                blocks.sort(java.util.Comparator.comparingInt(p -> p.getLeft().y()));
                pending = blocks;
            }
            config.onCreate.handle(ctx);
            if (!isRemoved() && config.animation.interval == 0) completed(ctx);
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " remove {", "}")) {
            pending = java.util.List.of();
            schematic.cleanup();
            config.onRemove.handle(ctx);
        }
    }

    @Override
    public void onSyncTick(EventContext ctx) {
        if (isRemoved() || complete || schematic.get() == null || config.animation.interval == 0) return;
        if (age++ % config.animation.interval != 0) return;
        for (int count = 0; count < config.animation.blocksPerStep && index < pending.size(); count++) {
            var block = pending.get(index++);
            if (schematic.get().writeBlock(config.schematic, block.getLeft(), block.getRight()) && !config.animation.sound.isBlank()) {
                var at = position.add(block.getLeft());
                airdrop.world().playSound(new org.bukkit.Location(airdrop.world(), at.x() + .5, at.y() + .5, at.z() + .5),
                        config.animation.sound, (float) config.animation.volume, (float) config.animation.pitch);
            }
        }
        if (index == pending.size()) completed(ctx);
    }

    private void completed(EventContext ctx) {
        complete = true;
        pending = java.util.List.of();
        config.onComplete.handle(ctx);
    }

    public record Animation(int interval, int blocksPerStep, boolean shuffle, String sound, double volume,
                            double pitch) {
        public static final YamlDecoder<Animation> DECODER = RecordYamlDecoder.mapOf(Animation::new,
                YamlDecoder.INT.fieldOf("interval_ticks", 0), YamlDecoder.INT.fieldOf("blocks_per_step", 1),
                YamlDecoder.BOOL.fieldOf("shuffle_layer", true), YamlDecoder.STRING.fieldOf("sound", ""),
                YamlDecoder.DOUBLE.fieldOf("volume", .8D), YamlDecoder.DOUBLE.fieldOf("pitch", 1.2D));

        public Animation {
            if (interval < 0 || interval > 12000 || blocksPerStep < 1 || blocksPerStep > 4096
                    || !Double.isFinite(volume) || volume < 0 || volume > 16
                    || !Double.isFinite(pitch) || pitch < .01 || pitch > 2)
                throw new IllegalArgumentException("Invalid schematic animation settings");
        }
    }

    public static class Config implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Схематика", "Вставляет схематику в мир")
                .inputs("create", "remove")
                .vector("offsets", "Смещение", "0;0;0", false)
                .text("path", "Путь до схематики", "../WorldEdit/schematics/asteroid.schem", true)
                .materials("no_replace", "Блоки, которые нельзя заменять")
                .materials("no_place", "Блоки, которые не вставлять")
                .materialMap("replace", "Замена блоков")
                .object("animation", "Постепенная вставка снизу вверх", NodeSchema.of("Анимация", "")
                        .integer("interval_ticks", "Интервал (0 — сразу)", 0, 0, false)
                        .integer("blocks_per_step", "Блоков за шаг", 1, 1, false)
                        .bool("shuffle_layer", "Перемешивать внутри слоя", true)
                        .text("sound", "Звук установки", "", false)
                        .number("volume", "Громкость", .8, 0, 16D).number("pitch", "Тон", 1.2, .01, 2D))
                .events("on_create", "on_complete", "on_remove");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                Schematic.DECODER.fieldOf(),
                Vec3iValue.DECODER.fieldOf("offsets", new Vec3iValue(Vec3i.ZERO)),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER),
                Animation.DECODER.fieldOf("animation", new Animation(0, 1, true, "", .8, 1.2)),
                HANDLER.fieldOf("on_complete", EMPTY_HANDLER)
        );
        private final Animation animation;
        private final EventHandler onComplete;
        private final Schematic schematic;
        private final Vec3iValue offsets;
        private final EventHandler onCreate;
        private final EventHandler onRemove;

        public Config(Schematic schematic, EventHandler onCreate, EventHandler onRemove) {
            this(schematic, new Vec3iValue(Vec3i.ZERO), onCreate, onRemove);
        }

        public Config(Schematic schematic, Vec3iValue offsets, EventHandler onCreate, EventHandler onRemove) {
            this(schematic, offsets, onCreate, onRemove, new Animation(0, 1, true, "", .8, 1.2), EMPTY_HANDLER);
        }

        public Config(Schematic schematic, Vec3iValue offsets, EventHandler onCreate, EventHandler onRemove,
                      Animation animation, EventHandler onComplete) {
            this.animation = animation;
            this.onComplete = onComplete;
            this.schematic = schematic;
            this.offsets = offsets;
            this.onCreate = onCreate;
            this.onRemove = onRemove;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new SchematicComponent(this, name, airdrop);
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
