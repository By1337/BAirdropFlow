package dev.by1337.baf.component.impl;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.component.system.TimerSystem;
import dev.by1337.baf.config.value.*;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.util.DurationFormatter;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
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
    private double radius;
    private float configuredProgress;
    private boolean progressFromTime;
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
            readAppearance();
            if (!readTime(ctx)) return;
            initialTime = currentTime;
            bossBar.set(BossBar.bossBar(MiniMessage.deserialize(config.title.getString(airdrop)),
                    progress(), config.color.get(airdrop), config.overlay.get(airdrop), flags()));
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
        if (bar == null) return false;
        readAppearance();
        if (!readTime(ctx)) return false;
        bar.color(config.color.get(airdrop));
        bar.overlay(config.overlay.get(airdrop));
        bar.flags(flags());
        bar.name(MiniMessage.deserialize(config.title.getString(airdrop)));
        bar.progress(progress());
        updateViewers(bar);
        return true;
    }

    private Set<BossBar.Flag> flags() {
        return config.flags.stream().map(value -> value.get(airdrop)).collect(java.util.stream.Collectors.toSet());
    }

    private void readAppearance() {
        radius = config.radius.getDouble(airdrop);
        configuredProgress = (float) config.progress.getDouble(airdrop);
        progressFromTime = config.progressFromTime.getBoolean(airdrop);
    }

    private boolean readTime(EventContext ctx) {
        try {
            var time = config.time.get(airdrop);
            if (time.isEmpty()) {
                if (progressFromTime) throw new IllegalArgumentException("progress_from_time requires a time source");
                return true;
            }
            currentTime = Math.max(0, time.getAsDouble());
            formattedTime = DurationFormatter.formatSeconds((long) currentTime);
            return true;
        } catch (IllegalArgumentException error) {
            log.warn("Bossbar node {}: invalid time source '{}': {}; removing node", name, config.time, error.getMessage());
            markRemoved(ctx);
            return false;
        }
    }

    private float progress() {
        if (!progressFromTime) return configuredProgress;
        return initialTime <= 0 ? 0 : (float) Math.min(1, currentTime / initialTime);
    }

    private void updateViewers(BossBar bar) {
        Set<Player> nearby = new HashSet<>();
        double radiusSquared = radius * radius;
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
                StringValue.DECODER.fieldOf("title", new StringValue("<gold>Аирдроп")),
                DoubleValue.DECODER.fieldOf("radius", new DoubleValue(64)),
                Parameter.enumDecoder(BossBar.Color.class).fieldOf("color", Parameter.constant(BossBar.Color.GREEN)),
                Parameter.enumDecoder(BossBar.Overlay.class).fieldOf("overlay", Parameter.constant(BossBar.Overlay.PROGRESS)),
                Parameter.enumDecoder(BossBar.Flag.class).listOf().fieldOf("flags", List.of()),
                DoubleValue.DECODER.fieldOf("progress", new DoubleValue(1)),
                TimeValue.DECODER.fieldOf("time", new TimeValue("")),
                BooleanValue.DECODER.fieldOf("progress_from_time", new BooleanValue(false)),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER)
        );

        private final StringValue title;
        private final DoubleValue radius;
        private final Parameter<BossBar.Color> color;
        private final Parameter<BossBar.Overlay> overlay;
        private final List<Parameter<BossBar.Flag>> flags;
        private final DoubleValue progress;
        private final TimeValue time;
        private final BooleanValue progressFromTime;
        private final EventHandler onCreate;
        private final EventHandler onRemove;

        public Config(String title, double radius, BossBar.Color color, BossBar.Overlay overlay, List<BossBar.Flag> flags,
                      float progress, String time, boolean progressFromTime,
                      EventHandler onCreate, EventHandler onRemove) {
            this(new StringValue(title), new DoubleValue(radius), Parameter.constant(color), Parameter.constant(overlay),
                    flags.stream().map(Parameter::constant).toList(), new DoubleValue(progress), new TimeValue(time),
                    new BooleanValue(progressFromTime), onCreate, onRemove);
        }

        public Config(StringValue title, DoubleValue radius, Parameter<BossBar.Color> color,
                      Parameter<BossBar.Overlay> overlay, List<Parameter<BossBar.Flag>> flags,
                      DoubleValue progress, TimeValue time, BooleanValue progressFromTime,
                      EventHandler onCreate, EventHandler onRemove) {
            if (progressFromTime.isConstant() && progressFromTime.getBoolean(null) && time.isConstant() && time.get(null).isEmpty())
                throw new IllegalArgumentException("Bossbar progress_from_time requires a time source");
            this.title = title;
            this.radius = radius.checked(v -> v >= 0, "Bossbar radius must be non-negative");
            this.color = color;
            this.overlay = overlay;
            this.flags = List.copyOf(flags);
            this.progress = progress.checked(v -> v >= 0 && v <= 1, "Bossbar progress must be between 0 and 1");
            this.time = time;
            this.progressFromTime = progressFromTime;
            this.onCreate = onCreate;
            this.onRemove = onRemove;
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
