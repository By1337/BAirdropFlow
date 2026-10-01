package dev.by1337.baf.component.impl.schematic;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.component.ComponentHolder;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.schematic.Schematic;
import dev.by1337.baf.schematic.SchematicWriter;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

public class SchematicComponent extends AbstractComponent {
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
            SchematicWriter writer = new SchematicWriter(airdrop.world(), new Vec3i(airdrop.x(), airdrop.y(), airdrop.z()));
            schematic.set(writer);
            writer.writeSchematic(config.schematic);
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " remove {", "}")) {
            schematic.cleanup();
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
                ;

        public static final YamlDecoder<Config> DECODER = Schematic.DECODER.map(Config::new);
        private final Schematic schematic;

        public Config(Schematic schematic) {
            this.schematic = schematic;
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
