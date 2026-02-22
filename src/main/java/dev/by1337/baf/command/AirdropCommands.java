package dev.by1337.baf.command;

import dev.by1337.cmd.Command;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.cmd.argument.ArgumentString;
import dev.by1337.cmd.argument.ArgumentStrings;
import dev.by1337.core.command.bcmd.argument.ArgumentComponents;
import dev.by1337.core.command.bcmd.argument.ArgumentDouble;
import dev.by1337.core.command.bcmd.argument.ArgumentInt;
import dev.by1337.core.command.bcmd.argument.ArgumentSound;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class AirdropCommands {
    private static final Command<AirdropContext> COMMANDS;


    public static Command<AirdropContext> getCommands() {
        return COMMANDS;
    }

    static {
        COMMANDS = new Command<AirdropContext>("root")
                .and(voidOutput().map(v -> null))
                .and(playerOutput().map(AirdropContext::player))
                .and(nodeControl())
                .and(world())
        ;
    }

    private static Command<AirdropContext> nodeControl() {
        return new Command<AirdropContext>("root")
                .sub(new Command<AirdropContext>("[create]")
                        .argument(new ArgumentString<>("node"))
                        .executor((ctx, args) -> {
                            String node = (String) args.getOrThrow("node", "Use: [create] <node>");
                            var drop = ctx.airdrop();
                            if (drop.components().get(node) != null) {
                                throw new CommandMsgError("Node " + node + " already exist!");
                            }
                            var factory = drop.config().nodes().get(node);
                            if (factory == null) {
                                throw new CommandMsgError("Unknown node " + node);
                            }
                            drop.components().add(factory.create(drop, node));
                        })
                )
                .sub(new Command<AirdropContext>("[remove]")
                        .argument(new ArgumentString<>("node"))
                        .executor((ctx, args) -> {
                            String node = (String) args.getOrThrow("node", "Use: [remove] <node>");
                            var drop = ctx.airdrop();
                            var c = drop.components().get(node);
                            if (c == null) {
                                throw new CommandMsgError("Unknown node " + node);
                            }
                            c.markRemoved();
                        })
                )
                .sub(new Command<AirdropContext>("[call]")
                        .argument(new ArgumentString<>("event"))
                        .argument(new ArgumentString<>("node"))
                        .executor((ctx, args) -> {
                            String event = (String) args.getOrThrow("event", "Use: [call] <event> <node>");
                            String node = (String) args.getOrThrow("node", "Use: [call] <event> <node>");
                            var drop = ctx.airdrop();
                            var c = drop.components().get(node);
                            if (c == null) {
                                throw new CommandMsgError("Unknown node " + node);
                            }
                            if (!c.call(event, ctx)) {
                                throw new CommandMsgError("Unknown event " + event + " for node " + node);
                            }
                        })
                ).sub(new Command<AirdropContext>("[destroy]")
                        .executor((ctx, args) -> {
                            ctx.airdrop().markRemoved();
                        })
                )
                ;
    }

    private static Command<AirdropContext> world() {
        return new Command<AirdropContext>("root")
                .sub(new Command<AirdropContext>("[setblock]")
                        .argument(new ArgumentInt<>("x"))
                        .argument(new ArgumentInt<>("y"))
                        .argument(new ArgumentInt<>("z"))
                        .argument(new ArgumentString<>("block"))
                        .executor((ctx, args) -> {
                            int x = (int) args.getOrThrow("x", "Use: [setblock] <x> <y> <z> <block>");
                            int y = (int) args.getOrThrow("y", "Use: [setblock] <x> <y> <z> <block>");
                            int z = (int) args.getOrThrow("z", "Use: [setblock] <x> <y> <z> <block>");
                            String block = (String) args.getOrThrow("block", "Use: [setblock] <x> <y> <z> <block>");
                            ctx.airdrop().world().getBlockAt(
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
                                            Title.Times.of(
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

    private static Command<Player> playerOutput() {
        return new Command<Player>("root")
                .sub(new Command<Player>("[PLAYER]")
                        .aliases("[player]")
                        .argument(new ArgumentStrings<>("cmd"))
                        .executor((v, args) -> {
                                    if (v == null) throw new CommandMsgError("only with player");
                                    String cmd = (String) args.getOrThrow("cmd", "use: [player] <cmd>");
                                    v.performCommand(cmd);
                                }
                        )
                )
                .sub(new Command<Player>("[CHAT]")
                        .aliases("[chat]")
                        .argument(new ArgumentStrings<>("cmd"))
                        .executor((v, args) -> {
                                    if (v == null) throw new CommandMsgError("only with player");
                                    String cmd = (String) args.getOrThrow("cmd", "use: [chat] <cmd>");
                                    v.chat(cmd);
                                }
                        )
                )
                .sub(new Command<Player>("[sound]")
                        .aliases("[SOUND]")
                        .argument(new ArgumentSound<>("sound"))
                        .argument(new ArgumentDouble<>("volume"))
                        .argument(new ArgumentDouble<>("pitch"))
                        .executor((v, args) -> {
                                    if (v == null) throw new CommandMsgError("only with player");
                                    float volume = ((Number) args.getOrDefault("volume", 1F)).floatValue();
                                    float pitch = ((Number) args.getOrDefault("pitch", 1F)).floatValue();
                                    Sound sound = (Sound) args.getOrThrow("sound", "use [sound] <sound> <?volume> <?pitch>");
                                    v.playSound(v.getLocation(), sound, volume, pitch);
                                }
                        )
                )
                .sub(new Command<Player>("[MESSAGE]")
                        .aliases("[message]")
                        .argument(new ArgumentComponents<>("msg"))
                        .executor((v, args) -> {
                                    if (v == null) throw new CommandMsgError("only with player");
                                    v.sendMessage((Component) args.getOrDefault("msg", Component.empty()));
                                }
                        )
                )
                .sub(new Command<Player>("[ACTION_BAR]")
                        .aliases("[action_bar]")
                        .argument(new ArgumentComponents<>("msg"))
                        .executor((v, args) -> {
                                    if (v == null) throw new CommandMsgError("only with player");
                                    Component msg = (Component) args.getOrDefault("msg", Component.empty());
                                    v.sendActionBar(msg);
                                }
                        )

                )
                .sub(new Command<Player>("[ACTION_BAR_ALL]")
                        .aliases("[action_bar_all]")
                        .argument(new ArgumentComponents<>("msg"))
                        .executor((v, args) -> {
                                    if (v == null) throw new CommandMsgError("only with player");
                                    Component msg = (Component) args.getOrDefault("msg", Component.empty());
                                    Bukkit.getOnlinePlayers().forEach(player -> player.sendActionBar(msg));
                                }
                        )
                )
                .sub(new Command<Player>("[TITLE]")
                        .aliases("[title]")
                        .argument(new ArgumentString<>("msg"))
                        .argument(new ArgumentInt<>("fadeIn"))
                        .argument(new ArgumentInt<>("stay"))
                        .argument(new ArgumentInt<>("fadeOut"))

                        .executor((v, args) -> {
                                    if (v == null) throw new CommandMsgError("only with player");
                                    //todo хочу ArgumentComponent а тут \n
                                    String msg = (String) args.getOrThrow("msg", "Use [TITLE] <\"Title\\nSubTitle\"> <?fadeIn> <?stay> <?fadeOut>");
                                    int fadeIn = (int) args.getOrDefault("fadeIn", 10);
                                    int stay = (int) args.getOrDefault("stay", 70);
                                    int fadeOut = (int) args.getOrDefault("fadeOut", 20);

                                    String[] arr = msg.split("\\\\n", 2);
                                    v.showTitle(Title.title(
                                            MiniMessage.deserialize(arr[0]),
                                            arr.length == 2 ? MiniMessage.deserialize(arr[1]) : Component.empty(),
                                            Title.Times.of(
                                                    Ticks.duration(fadeIn),
                                                    Ticks.duration(stay),
                                                    Ticks.duration(fadeOut)
                                            )
                                    ));
                                }
                        )
                )
                ;
    }
}
