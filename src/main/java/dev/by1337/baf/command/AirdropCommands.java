package dev.by1337.baf.command;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.command.argument.OptionsArgument;
import dev.by1337.baf.util.OptionsHolder;
import dev.by1337.cmd.Command;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.cmd.argument.ArgumentCommand;
import dev.by1337.cmd.argument.ArgumentString;
import dev.by1337.cmd.argument.ArgumentStrings;
import dev.by1337.core.command.bcmd.argument.ArgumentInt;
import dev.by1337.edsl.command.DefaultEventCommands;
import dev.by1337.edsl.context.EventContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class AirdropCommands {
    private static Command<EventContext> COMMANDS;


    public static Command<EventContext> getCommands() {
        if (COMMANDS == null) resetCommands();
        return COMMANDS;
    }

    public static void resetCommands() {
        COMMANDS = new Command<EventContext>("root")
                .and(DefaultEventCommands.createCommands())
                .and(nodeControl())
                .and(argumentControl())
                .and(world())
        ;
    }


    static Command<EventContext> argumentControl() {
        return new Command<EventContext>("root")
                .sub(new Command<EventContext>("[set_arg]").executor(
                        new ArgumentString<>("key"),
                        new ArgumentStrings<>("value"),
                        (ctx, key, value) -> {
                            var drop = ctx.get(Airdrop.class);
                            if (drop == null) throw new CommandMsgError("set_arg requires an airdrop");
                            if (key == null || value == null)
                                throw new CommandMsgError("Use set_arg: <key> <value>");
                            drop.args().put(key, value);
                        }));
    }

    private static Command<EventContext> nodeControl() {
        return new Command<EventContext>("root")
                .sub(new Command<EventContext>("[create]")
                        .argument(new ArgumentString<>("node"))
                        .argument(new OptionsArgument<>("flags"))
                        .executor((ctx, args) -> {
                            OptionsHolder flags = (OptionsHolder) args.getOrDefault("flags", new OptionsHolder());
                            String node = (String) args.getOrThrow("node", "Use: [create] <node>");
                            var drop = ctx.get(Airdrop.class);
                            // if (drop.isRemoved()) {
                            //     throw new CommandMsgError("Airdrop is removed!");
                            // }
                            var factory = drop.config().nodes().get(node);
                            if (factory == null) {
                                throw new CommandMsgError("Unknown node " + node);
                            }
                            String name;
                            if (flags.getOrDefault("anon", "false").equals("true")) {
                                name = UUID.randomUUID() + "$" + node;
                            } else {
                                name = flags.getOrDefault("name", node);
                            }
                            if (drop.components().get(name) != null) {
                                throw new CommandMsgError("Node " + name + " already exist!");
                            }
                            drop.components().add(factory.create(drop, name), ctx);
                            if (drop.isRemoved()) {
                                var c = drop.components().get(name);
                                if (c != null) {
                                    c.markRemoved(ctx);
                                }
                            }
                        })
                )
                .sub(new Command<EventContext>("[remove]")
                        .aliases("[rm]")
                        .argument(new ArgumentString<>("node"))
                        .executor((ctx, args) -> {
                            String node = (String) args.getOrThrow("node", "Use: [remove] <node>");
                            var drop = ctx.get(Airdrop.class);
                            var c = drop.components().get(node);
                            if (c != null) {
                                c.markRemoved(ctx);
                            }
                        })
                )
                .sub(new Command<EventContext>("[call]")
                        .argument(new ArgumentString<>("event"))
                        .argument(new ArgumentString<>("node"))
                        .executor((ctx, args) -> {
                            String event = (String) args.getOrThrow("event", "Use: [call] <event> <node>");
                            String node = (String) args.getOrThrow("node", "Use: [call] <event> <node>");
                            var drop = ctx.get(Airdrop.class);
                            var c = drop.components().get(node);
                            if (c == null) {
                                throw new CommandMsgError("Unknown node " + node);
                            }
                            if (!c.call(event, ctx)) {
                                throw new CommandMsgError("Unknown event " + event + " for node " + node);
                            }
                        })
                )
                .sub(new Command<EventContext>("[call?]")
                        .argument(new ArgumentString<>("event"))
                        .argument(new ArgumentString<>("node"))
                        .executor((ctx, args) -> {
                            String event = (String) args.getOrThrow("event", "Use: [call] <event> <node>");
                            String node = (String) args.getOrThrow("node", "Use: [call] <event> <node>");
                            var drop = ctx.get(Airdrop.class);
                            var c = drop.components().get(node);
                            if (c != null) {
                                if (!c.call(event, ctx)) {
                                    throw new CommandMsgError("Unknown event " + event + " for node " + node);
                                }
                            }
                        })
                ).sub(new Command<EventContext>("[destroy]")
                        .executor((ctx, args) -> {
                            ctx.get(Airdrop.class).markRemoved(ctx);
                        })
                )
                ;
    }

    private static Command<EventContext> world() {
        return new Command<EventContext>("root")
                .sub(new Command<EventContext>("[nearby]").executor(
                        new ArgumentInt<>("radius"),
                        new ArgumentCommand<>("cmd", AirdropCommands::getCommands),
                        (ctx, r, c) -> {
                            if (r == null || c == null){
                                throw new CommandMsgError("use [nearby] <radius> <command>");
                            }
                            var air = ctx.get(Airdrop.class);
                            for (Player player : air.location().getNearbyPlayers(r)) {
                                try (var ignored = ctx.target(player)){
                                    c.run(ctx, s -> s);
                                }
                            }
                        })
                )
                .sub(new Command<EventContext>("[setblock]")
                        .argument(new ArgumentInt<>("x"))
                        .argument(new ArgumentInt<>("y"))
                        .argument(new ArgumentInt<>("z"))
                        .argument(new ArgumentString<>("block"))
                        .executor((ctx, args) -> {
                            int x = (int) args.getOrThrow("x", "Use: [setblock] <x> <y> <z> <block>");
                            int y = (int) args.getOrThrow("y", "Use: [setblock] <x> <y> <z> <block>");
                            int z = (int) args.getOrThrow("z", "Use: [setblock] <x> <y> <z> <block>");
                            String block = (String) args.getOrThrow("block", "Use: [setblock] <x> <y> <z> <block>");
                            ctx.get(Airdrop.class).world().getBlockAt(
                                    x, y, z
                            ).setBlockData(Bukkit.createBlockData(block), false);
                        })
                )
                ;
    }
}
