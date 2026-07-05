package dev.by1337.baf.spawner.impl;

import dev.by1337.baf.BAF;
import dev.by1337.baf.Workflow;
import dev.by1337.baf.command.AirdropCommands;
import dev.by1337.baf.entity.TickableEntity;
import dev.by1337.baf.spawner.AirdropSpawner;
import dev.by1337.baf.util.time.DurationParser;
import dev.by1337.baf.util.time.DurationRange;
import dev.by1337.cmd.Command;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.cmd.argument.ArgumentString;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.command.args.ArgumentParamsMap;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.plc.PlaceholderResolver;
import dev.by1337.plc.Placeholders;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class TimerSpawner implements AirdropSpawner {
    private static final Logger log = LoggerFactory.getLogger(TimerSpawner.class);
    static EventHandler EMPTY_HANDLER = EventHandler.nop();
    static YamlDecoder<EventHandler> HANDLER = EventHandler.decoder().commandRewriter(s -> switch (s) {
        case "make" -> "spawner:timer:make";
        case "set_value" -> "spawner:timer:set_value";
        default -> s;
    });

    public static final YamlDecoder<TimerSpawner> DECODER = RecordYamlDecoder.mapOf(
            TimerSpawner::new,
            DurationParser.MILLIS.fieldOf("time"),
            HANDLER.fieldOf("on_create", EMPTY_HANDLER),
            YamlDecoder.mapOf(DurationRange.DECODER, HANDLER)
                    .fieldOf("when"),
            HANDLER.fieldOf("on_timer_end", EMPTY_HANDLER)
    );


    private final long duration;
    private final EventHandler on_create;
    private final Map<DurationRange, EventHandler> when;
    private final EventHandler on_timer_end;

    public TimerSpawner(long duration, EventHandler onCreate, Map<DurationRange, EventHandler> when, EventHandler onTimerEnd) {
        this.duration = duration;
        on_create = onCreate;
        this.when = when;
        on_timer_end = onTimerEnd;
    }

    @Override
    public TickableEntity create(Workflow workflow) {
        return new Ticker(workflow);
    }

    private class Ticker implements TickableEntity {
        private final PlaceholderResolver<Ticker> PLACEHOLDERS = Placeholders.<Ticker>create()
                .of("value", (key, v) -> v.values.get(key))
                .withContext("time", (v) -> v.timeLeft / 1000)
                .withContext("x", (v) -> v.location.getBlockX())
                .withContext("y", (v) -> v.location.getBlockY())
                .withContext("z", (v) -> v.location.getBlockZ())
                ;
        private final Workflow workflow;
        private long timeLeft = duration;
        private int tick;
        private World world;
        private boolean removed;
        private Location location;
        private int generatorFailure = 0;
        private boolean init;
        private final Map<String, String> values = new HashMap<>();
        private final PlaceholderResolver<EventContext> placeholders = PLACEHOLDERS.bindCtx(this);

        private Ticker(Workflow workflow) {
            this.workflow = workflow;
        }

        @Override
        public void tick() {
            worldChecks();
            if (removed) return;
            if (!locationGen()) return;
            if (removed) return;
            if (!init) {
                init = true;
                on_create.handleAndClose(makeContext());
            }
            if (tick++ % 20 == 0) {
                timeLeft -= 1000;
                if (timeLeft > 0) {
                    for (Map.Entry<DurationRange, EventHandler> entry : when.entrySet()) {
                        if (entry.getKey().contains(timeLeft)) {
                            entry.getValue().handleAndClose(makeContext());
                        }
                    }
                } else {
                    removed = true;
                    on_timer_end.handleAndClose(makeContext());
                }
            }
        }

        private EventContext makeContext() {
            return BAF.newContextBuilder(this).placeholders(placeholders).build();
        }

        private boolean locationGen() {
            if (location == null) {
                location = workflow.generator.tryGenerate(world);
                if (location == null) {
                    if (generatorFailure++ >= 200) {
                        log.error("Failed to generate location for {} in {}", workflow.id(), workflow.generator);
                        removed = true;
                    }
                    return false;
                }
            }
            return true;
        }

        private void worldChecks() {
            if (world == null) {
                world = Bukkit.getWorld(workflow.world());
                if (world == null) {
                    log.error("Unknown world {}", workflow.world());
                    removed = true;
                }
            } else if (Bukkit.getWorld(workflow.world()) == null) {
                log.error("World is unloaded! {}", workflow.world());
                removed = true;
            }
        }

        @Override
        public boolean isRemoved() {
            return removed;
        }

        static {
            AirdropCommands.getCommands()
                    .sub(new Command<EventContext>("[spawner:timer:make]").executor(
                            new ArgumentParamsMap<>("args"),
                            (ctx, args) -> {
                                Ticker ticker = ctx.get(Ticker.class);
                                if (ticker == null) throw new CommandMsgError("has no ticker");
                                var air = ticker.workflow.make(ticker.location);
                                if (args != null) {
                                    air.args().putAll(args);
                                }
                                BAF.manager().addAirdrop(air);
                            }
                    )).sub(new Command<EventContext>("[spawner:timer:set_value]").executor(
                            new ArgumentString<>("key"),
                            new ArgumentString<>("value"),
                            (ctx, key, value) -> {
                                Ticker ticker = ctx.get(Ticker.class);
                                if (ticker == null) throw new CommandMsgError("has no ticker");
                                if (key == null || value == null) throw new CommandMsgError("Use set_value: <key> <value>");
                                ticker.values.put(key, value);
                            }
                    ));
        }
    }
}
