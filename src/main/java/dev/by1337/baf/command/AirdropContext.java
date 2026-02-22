package dev.by1337.baf.command;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.plc.PlaceholderApplier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public record AirdropContext(Airdrop airdrop, @Nullable Player player) implements PlaceholderApplier {
    private static final Logger log = LoggerFactory.getLogger(AirdropContext.class);

    public AirdropContext(Airdrop airdrop) {
        this(airdrop, null);
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
        } catch (CommandMsgError e){
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
        } catch (CommandMsgError e){
            log.error("Failed to run command {}", e.getMessage());
        } catch (Exception | StackOverflowError e) {
            log.error("Failed to call event!", e);
            return false;
        }
        return true;
    }

    public void log(String s) {
        System.out.println(Integer.toHexString(System.identityHashCode(this)) + " " + s);
    }
}
