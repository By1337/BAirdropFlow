package dev.by1337.baf.command;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.edsl.EventContext;
import dev.by1337.edsl.trace.EventTracer;
import dev.by1337.plc.PlaceholderApplier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AirdropContext implements PlaceholderApplier, EventContext, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger("BAirdropFlow");
    public static boolean ENABLE_TRACE = true;

    private final Airdrop airdrop;
    private final @Nullable Player player;
    private final EventTracer tracer;
    private StringBuilder out;

    public AirdropContext(Airdrop airdrop) {
        this.airdrop = airdrop;
        player = null;
        tracer = createTracer();
    }

    public AirdropContext(Airdrop airdrop, @Nullable Player player) {
        this.airdrop = airdrop;
        this.player = player;
        tracer = createTracer();
    }

    public AirdropContext(Airdrop airdrop, @Nullable Player player, EventTracer tracer) {
        this.airdrop = airdrop;
        this.player = player;
        this.tracer = tracer;
    }
    public AirdropContext whichPlayer(Player player){
        return new AirdropContext(airdrop, player, tracer);
    }

    private EventTracer createTracer() {
        if (!ENABLE_TRACE) return EventTracer.NOP;
        out = new StringBuilder();
        return EventTracer.performanceTracer(out);
    }

    @Override
    public String setPlaceholders(String s) {
        return Airdrop.setPlaceholders(s, this);
    }

    public <T extends AirdropComponent> boolean call(
            T component,
            BiConsumer<? super T, AirdropContext> c
    ) {
        try {
            c.accept(component, this);
        } catch (CommandMsgError e) {
            log.error("Failed to run command {}", e.getMessage());
        } catch (Exception | StackOverflowError e) {
            log.error("Failed to call event!", e);
            return false;
        }
        return true;
    }

    public boolean call(Consumer<AirdropContext> c) {
        try {
            c.accept(this);
        } catch (CommandMsgError e) {
            log.error("Failed to run command {}", e.getMessage());
        } catch (Exception | StackOverflowError e) {
            log.error("Failed to call event!", e);
            return false;
        }
        return true;
    }

    @Override
    public EventTracer tracer() {
        return tracer;
    }

    @Override
    public void close() {
        if (out != null){
            log.info("TRACE:\n{}", out);
        }
    }

    public Airdrop airdrop() {
        return airdrop;
    }

    public @Nullable Player player() {
        return player;
    }
}
