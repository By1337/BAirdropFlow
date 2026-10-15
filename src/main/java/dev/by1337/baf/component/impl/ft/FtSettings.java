package dev.by1337.baf.component.impl.ft;

import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.config.value.IntValue;
import dev.by1337.baf.config.value.StringValue;
import dev.by1337.baf.config.value.Vec3dValue;
import dev.by1337.baf.util.Vec3d;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.EventHandler;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

record FtSettings(Vec3dValue offsets, IntValue ticks, IntValue lifetime, IntValue maxEntities, StringValue trail,
                  IntValue trailTicks,
                  EventHandler create, EventHandler remove, EventHandler land, EventHandler death,
                  EventHandler impact, EventHandler finish) {
    static final YamlDecoder<FtSettings> DECODER = RecordYamlDecoder.mapOf(FtSettings::new,
            Vec3dValue.DECODER.fieldOf("offsets", new Vec3dValue(new Vec3d(0, 0, 0))),
            IntValue.DECODER.fieldOf("ticks", new IntValue(-1)),
            IntValue.DECODER.fieldOf("entity_lifetime_ticks", new IntValue(1200)),
            IntValue.DECODER.fieldOf("max_entities", new IntValue(256)),
            StringValue.DECODER.fieldOf("trail_animation", new StringValue("")),
            IntValue.DECODER.fieldOf("trail_ticks", new IntValue(80)),
            ComponentFactory.HANDLER.fieldOf("on_create", ComponentFactory.EMPTY_HANDLER),
            ComponentFactory.HANDLER.fieldOf("on_remove", ComponentFactory.EMPTY_HANDLER),
            ComponentFactory.HANDLER.fieldOf("on_land", ComponentFactory.EMPTY_HANDLER),
            ComponentFactory.HANDLER.fieldOf("on_death", ComponentFactory.EMPTY_HANDLER),
            ComponentFactory.HANDLER.fieldOf("on_impact", ComponentFactory.EMPTY_HANDLER),
            ComponentFactory.HANDLER.fieldOf("on_finish", ComponentFactory.EMPTY_HANDLER));

    FtSettings {
        trailTicks = trailTicks.checked(v -> v >= 0 && v <= 1200000, "Invalid trail_ticks");
        ticks = ticks.checked(v -> v == -1 || v > 0, "ticks must be -1 or positive");
        lifetime = lifetime.checked(v -> v > 0 && v <= 1200000, "entity_lifetime_ticks must be 1..1200000");
        maxEntities = maxEntities.checked(v -> v > 0 && v <= 4096, "max_entities must be 1..4096");
    }

    static NodeSchema schema(String title, String description) {
        return NodeSchema.of(title, description)
                .vector("offsets", "Смещение", "0;0;0", true)
                .integer("ticks", "Длительность (-1 — до удаления)", -1, -1, false)
                .integer("entity_lifetime_ticks", "Максимальное время жизни сущности", 1200, 1, false)
                .integer("max_entities", "Лимит сущностей", 256, 1, false)
                .text("trail_animation", "Particle graph на сущности", "", false)
                .integer("trail_ticks", "Время следа на каждой сущности", 80, 0, false)
                .placeholder("alive", "Живые сущности", "Количество объектов под управлением компонента")
                .placeholder("killed", "Убито мобов", "Только реальные смерти, без очистки/выгрузки")
                .placeholder("x", "X последнего события", "Мировая координата")
                .placeholder("y", "Y последнего события", "Мировая координата")
                .placeholder("z", "Z последнего события", "Мировая координата")
                .events("on_create", "on_remove", "on_land", "on_death", "on_impact", "on_finish")
                .inputs("create", "remove");
    }
}
