package dev.by1337.baf.component.impl;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.BAF;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.component.system.TimerSystem;
import dev.by1337.baf.util.Vec3d;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.particle.ParticleRender;
import dev.by1337.particle.flow.emit.EmitterGraph;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class ParticleComponent extends AbstractComponent implements TimerSystem.Tickable {
    private static final double VIEW_DISTANCE_SQUARED = 30 * 30;
    private final Config config;
    private final Location location;
    private EmitterGraph graph;
    private int tick;

    private ParticleComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.config = config;
        this.location = airdrop.location().add(config.offsets.x(), config.offsets.y(), config.offsets.z());
    }

    @Override
    public void onCreate(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_create {", "}")) {
            graph = BAF.particleGraphs().get(config.animation);
            if (graph == null) {
                BAF.plugin().getSLF4JLogger().warn("Unknown particle animation '{}' in node {}", config.animation, name);
                markRemoved(ctx);
                return;
            }
            config.onCreate.handle(ctx);
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_remove {", "}")) {
            config.onRemove.handle(ctx);
        }
    }

    @Override
    public void onSyncTick(EventContext ctx) {
        if (config.ticks != -1 && tick >= config.ticks) {
            try (var ignored = ctx.tracer().enter(name + " on_timer_end {", "}")) {
                config.onTimerEnd.handle(ctx);
                if (!isRemoved()) markRemoved(ctx);
            }
            return;
        }
        World world = location.getWorld();
        if (world != null) {
            var particles = graph.make(tick);

            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(location) <= VIEW_DISTANCE_SQUARED) {
                    ParticleRender.render(player, particles, location.getX(), location.getY(), location.getZ());
                }
            }
        }
        tick++;
    }

    public static class Config implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Частицы", "Проигрывает анимацию из папки particles в течение заданного числа тиков.")
                .text("animation", "ID анимации", "example", true)
                .vector("offsets", "Смещение", "0;0;0", true)
                .integer("ticks", "Время жизни, тиков (-1 — бесконечно)", 100, -1, false)
                .events("on_create", "on_remove", "on_timer_end")
                .inputs("create", "remove");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                YamlDecoder.STRING.fieldOf("animation"),
                Vec3d.DECODER.fieldOf("offsets", new Vec3d(0, 0, 0)),
                YamlDecoder.INT.fieldOf("ticks", 100),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER),
                HANDLER.fieldOf("on_timer_end", EMPTY_HANDLER)
        );
        private final String animation;
        private final Vec3d offsets;
        private final int ticks;
        private final EventHandler onCreate;
        private final EventHandler onRemove;
        private final EventHandler onTimerEnd;

        public Config(String animation, Vec3d offsets, int ticks, EventHandler onCreate, EventHandler onRemove, EventHandler onTimerEnd) {
            if (animation == null || animation.isBlank())
                throw new IllegalArgumentException("Particle animation ID is empty");
            if (ticks < 1 && ticks != -1)
                throw new IllegalArgumentException("Particle lifetime must be positive or -1");
            this.animation = animation;
            this.offsets = offsets;
            this.ticks = ticks;
            this.onCreate = onCreate;
            this.onRemove = onRemove;
            this.onTimerEnd = onTimerEnd;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new ParticleComponent(this, name, airdrop);
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
