/*
package dev.by1337.baf.command;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.cmd.Command;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.edsl.context.EventContext;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AirdropContext extends EventContext {
    private static final Logger log = LoggerFactory.getLogger("BAirdropFlow");
    public static boolean ENABLE_TRACE = true;

    private final Airdrop airdrop;

    public AirdropContext(Airdrop airdrop) {
        super(ENABLE_TRACE, null, Airdrop.PLACEHOLDERS);
        this.airdrop = airdrop;
        setLocation(airdrop.location());
    }

    public AirdropContext(Airdrop airdrop, @Nullable Player player) {
        super(ENABLE_TRACE, player, Airdrop.PLACEHOLDERS);
        this.airdrop = airdrop;
        setLocation(airdrop.location());
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


    public Airdrop airdrop() {
        return airdrop;
    }

    @Override
    public Command<AirdropContext> command() {
        return null;
    }

    @Override
    public Logger getLogger() {
        return log;
    }
}
*/
