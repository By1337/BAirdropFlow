package dev.by1337.baf.vote;

import dev.by1337.baf.BAF;
import dev.by1337.bmenu.command.ExecuteContext;
import dev.by1337.bmenu.loader.MenuConfig;
import dev.by1337.bmenu.menu.DefaultMenu;
import dev.by1337.bmenu.menu.Menu;
import dev.by1337.bmenu.menu.command.MenuCommands;
import dev.by1337.cmd.Command;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.cmd.argument.ArgumentString;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import dev.by1337.yaml.codec.YamlCodec;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class VoteMenu extends DefaultMenu {
    private static final class Commands {
        private static final Command<ExecuteContext> VALUE = new Command<ExecuteContext>("root")
                .and(MenuCommands.getCommands())
                .sub(new Command<ExecuteContext>("[vote]").executor(new ArgumentString<>("option"), (ctx, option) -> {
                    if (!(ctx.menu instanceof VoteMenu menu)) return;
                    if (option == null || !menu.voting.vote(menu.session.token(), menu.viewer.getUniqueId(), option))
                        throw new CommandMsgError("Голосование завершилось или вариант недоступен");
                    menu.viewer.sendMessage(MiniMessage.deserialize(menu.session.text(menu.session.config().messages().voted(), menu.viewer.getUniqueId())));
                    menu.refresh();
                }));
    }

    private final VotingService voting;
    private final VotingService.Session session;
    private int age;

    public VoteMenu(MenuConfig config, Player viewer, @Nullable Menu previousMenu, VotingService voting) {
        super(config, viewer, previousMenu);
        this.voting = voting;
        session = voting.active();
        if (session == null) throw new CommandMsgError("Сейчас голосование не проводится");
        addPlaceholderResolver(session.placeholders(viewer.getUniqueId()).bindCtx(session));
    }

    public VotingService.Session session() {
        return session;
    }

    @Override
    public void open(boolean reopen) {
        if (voting.active() != session) throw new CommandMsgError("Голосование уже завершилось");
        super.open(reopen);
    }

    @Override
    public void tick() {
        if (voting.active() != session) {
            close();
            return;
        }
        if (++age % 20 == 0) refresh();
        super.tick();
    }

    @Override
    public Command<ExecuteContext> getCommands() {
        return Commands.VALUE;
    }

    public static final YamlCodec<MenuConfig> CODEC = MenuConfig.createCodecFor((config, viewer, previous) ->
            new VoteMenu(config, viewer, previous, BAF.globalSpawners().voting()));
}
