package dev.by1337.baf.command;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.command.argument.OptionsArgument;
import dev.by1337.baf.util.OptionsHolder;
import dev.by1337.cmd.Command;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.cmd.argument.ArgumentString;
import dev.by1337.cmd.argument.ArgumentStrings;
import dev.by1337.core.command.bcmd.argument.ArgumentComponents;
import dev.by1337.core.command.bcmd.argument.ArgumentInt;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import dev.by1337.edsl.command.DefaultEventCommands;
import dev.by1337.edsl.context.EventContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;

import java.util.UUID;

public class AirdropCommands {
    private static Command<EventContext> COMMANDS;


    public static Command<EventContext> getCommands() {
        if (COMMANDS == null) resetCommands();
        return COMMANDS;
    }
    public static void resetCommands(){
        COMMANDS = new Command<EventContext>("root")
                .and(DefaultEventCommands.createCommands())
                .and(nodeControl())
                .and(world())
        ;
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
                                if (c != null){
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

    private static Command<Void> voidOutput() {
        return new Command<Void>("root")
                .sub(new Command<Void>("[BROADCAST]")
                        .aliases("[broadcast]")
                        .aliases("[bc]")
                        .argument(new ArgumentComponents<>("msg"))
                        .executor((v, args) -> {
                                    Component msg = (Component) args.getOrDefault("msg", Component.empty());
                                    Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(msg));
                                }
                        )
                )
                .sub(new Command<Void>("[CONSOLE]")
                        .aliases("[console]")
                        .argument(new ArgumentStrings<>("cmd"))
                        .executor((v, args) -> {
                                    String cmd = (String) args.getOrThrow("cmd", "use: [console] <cmd>");
                                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd);
                                }
                        )
                )
                .sub(new Command<Void>("[TITLE_ALL]")
                        .aliases("[title_all]")
                        .argument(new ArgumentString<>("msg"))
                        .argument(new ArgumentInt<>("fadeIn"))
                        .argument(new ArgumentInt<>("stay"))
                        .argument(new ArgumentInt<>("fadeOut"))
                        .executor((v, args) -> {

                                    //todo хочу ArgumentComponent а тут \n
                                    String msg = (String) args.getOrThrow("msg", "Use [TITLE_ALL] <\"Title\\nSubTitle\"> <?fadeIn> <?stay> <?fadeOut>");
                                    int fadeIn = (int) args.getOrDefault("fadeIn", 10);
                                    int stay = (int) args.getOrDefault("stay", 70);
                                    int fadeOut = (int) args.getOrDefault("fadeOut", 20);

                                    String[] arr = msg.split("\\\\n", 2);
                                    Title title = Title.title(
                                            MiniMessage.deserialize(arr[0]),
                                            arr.length == 2 ? MiniMessage.deserialize(arr[1]) : Component.empty(),
                                            Title.Times.times(
                                                    Ticks.duration(fadeIn),
                                                    Ticks.duration(stay),
                                                    Ticks.duration(fadeOut)
                                            )
                                    );
                                    Bukkit.getOnlinePlayers().forEach(player -> player.showTitle(title));
                                }
                        )

                )
                ;

    }
}
