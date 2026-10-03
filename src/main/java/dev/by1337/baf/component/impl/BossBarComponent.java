package dev.by1337.baf.component.impl;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.component.system.TimerSystem;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.util.DurationFormatter;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.codec.YamlCodec;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BossBarComponent extends AbstractComponent implements TimerSystem.Tickable {
    private static final Logger log = LoggerFactory.getLogger(BossBarComponent.class);
    private final Config config;
    private final Airdrop airdrop;
    private final Location location;
    private final Set<Player> viewers = new HashSet<>();
    private final CleanerSlot<BossBar> bossBar;
    private double initialTime;
    private double currentTime;
    private String formattedTime = DurationFormatter.formatSeconds(0);

    private BossBarComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.config = config;
        this.airdrop = airdrop;
        location = airdrop.location();
        bossBar = new CleanerSlot<>(this::hideBossBar, airdrop.cleaner());
        exportPlaceholder("time", () -> formattedTime);
    }

    @Override
    public void onCreate(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_create {", "}")) {
            if (!readTime(ctx)) return;
            initialTime = currentTime;
            bossBar.set(BossBar.bossBar(MiniMessage.deserialize(ctx.setPlaceholders(config.title)),
                    progress(), config.color, config.overlay, config.flags));
            updateViewers(bossBar.get());
            config.onCreate.handle(ctx);
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_remove {", "}")) {
            bossBar.cleanup();
            config.onRemove.handle(ctx);
        }
    }

    @Override
    public void onSyncTick(EventContext ctx) {
        if (!isRemoved()) update(ctx);
    }

    private boolean update(EventContext ctx) {
        BossBar bar = bossBar.get();
        if (bar == null || !readTime(ctx)) return false;
        bar.name(MiniMessage.deserialize(ctx.setPlaceholders(config.title)));
        bar.progress(progress());
        updateViewers(bar);
        return true;
    }

    private boolean readTime(EventContext ctx) {
        if (config.time.isBlank()) return true;
        String value = ctx.setPlaceholders(config.time);
        try {
            double seconds = Double.parseDouble(value.trim());
            if (!Double.isFinite(seconds)) throw new NumberFormatException("not finite");
            currentTime = Math.max(0, seconds);
            formattedTime = DurationFormatter.formatSeconds((long) currentTime);
            return true;
        } catch (NumberFormatException error) {
            log.warn("Bossbar node {}: time '{}' resolved to '{}', expected seconds; removing node", name, config.time, value);
            markRemoved(ctx);
            return false;
        }
    }

    private float progress() {
        if (!config.progressFromTime) return config.progress;
        return initialTime <= 0 ? 0 : (float) Math.min(1, currentTime / initialTime);
    }

    private void updateViewers(BossBar bar) {
        Set<Player> nearby = new HashSet<>();
        double radiusSquared = config.radius * config.radius;
        for (Player player : airdrop.world().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radiusSquared) nearby.add(player);
        }
        var iterator = viewers.iterator();
        while (iterator.hasNext()) {
            Player player = iterator.next();
            if (!nearby.contains(player)) {
                player.hideBossBar(bar);
                iterator.remove();
            }
        }
        for (Player player : nearby) if (viewers.add(player)) player.showBossBar(bar);
    }

    private void hideBossBar(BossBar bar) {
        for (Player player : viewers) {
            try {
                player.hideBossBar(bar);
            } catch (Exception error) {
                log.error("Failed to hide bossbar {} for player {}", name, player.getUniqueId(), error);
            }
        }
        viewers.clear();
    }

    public static class Config implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Боссбар", "Боссбар в радиусе аирдропа. Текст, время и видимость обновляются каждый тик.")
                .text("title", "Заголовок (MiniMessage)", "<gold>Аирдроп", false)
                .number("radius", "Радиус видимости, блоков", 64, 0, null)
                .choice("color", "Цвет", "green", names(BossBar.Color.values()))
                .choice("overlay", "Деления шкалы", "progress", names(BossBar.Overlay.values()))
                .strings("flags", "Флаги: darken_screen, play_boss_music, create_world_fog")
                .number("progress", "Прогресс (0–1)", 1, 0, 1D)
                .text("time", "Источник времени, секунд (необязательно)", "", false)
                .bool("progress_from_time", "Считать прогресс от времени", false)
                .placeholder("time", "Отформатированное время", "Время из поля time в формате lang.yml; без источника — 0 секунд.")
                .events("on_create", "on_remove")
                .inputs("create", "remove");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                YamlDecoder.STRING.fieldOf("title", "<gold>Аирдроп"),
                YamlDecoder.DOUBLE.fieldOf("radius", 64D),
                enumDecoder(BossBar.Color.class).fieldOf("color", BossBar.Color.GREEN),
                enumDecoder(BossBar.Overlay.class).fieldOf("overlay", BossBar.Overlay.PROGRESS),
                enumDecoder(BossBar.Flag.class).listOf().fieldOf("flags", List.of()),
                YamlDecoder.FLOAT.fieldOf("progress", 1F),
                YamlDecoder.STRING.fieldOf("time", ""),
                YamlDecoder.BOOL.fieldOf("progress_from_time", false),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER)
        );

        private final String title;
        private final double radius;
        private final BossBar.Color color;
        private final BossBar.Overlay overlay;
        private final Set<BossBar.Flag> flags;
        private final float progress;
        private final String time;
        private final boolean progressFromTime;
        private final EventHandler onCreate;
        private final EventHandler onRemove;

        public Config(String title, double radius, BossBar.Color color, BossBar.Overlay overlay, List<BossBar.Flag> flags,
                      float progress, String time, boolean progressFromTime,
                      EventHandler onCreate, EventHandler onRemove) {
            if (!Double.isFinite(radius) || radius < 0)
                throw new IllegalArgumentException("Bossbar radius must be finite and non-negative");
            if (!Float.isFinite(progress) || progress < 0 || progress > 1)
                throw new IllegalArgumentException("Bossbar progress must be between 0 and 1");
            if (progressFromTime && time.isBlank())
                throw new IllegalArgumentException("Bossbar progress_from_time requires a time source");
            this.title = title;
            this.radius = radius;
            this.color = color;
            this.overlay = overlay;
            this.flags = Set.copyOf(flags);
            this.progress = progress;
            this.time = time;
            this.progressFromTime = progressFromTime;
            this.onCreate = onCreate;
            this.onRemove = onRemove;
        }

        private static <E extends Enum<E>> YamlDecoder<E> enumDecoder(Class<E> type) {
            var codec = YamlCodec.fromEnum(type);
            return (ctx, yaml) -> codec.decode(yaml);
        }

        private static String[] names(Enum<?>[] values) {
            return Arrays.stream(values).map(value -> value.name().toLowerCase(Locale.ROOT)).toArray(String[]::new);
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new BossBarComponent(this, name, airdrop);
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
