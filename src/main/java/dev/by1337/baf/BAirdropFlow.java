package dev.by1337.baf;

import dev.by1337.baf.command.AirdropCommands;
import dev.by1337.baf.command.LootCommands;
import dev.by1337.baf.component.AirdropComponents;
import dev.by1337.baf.component.system.AsyncTimerSystem;
import dev.by1337.baf.component.system.TimerSystem;
import dev.by1337.baf.config.Config;
import dev.by1337.baf.generator.Generators;
import dev.by1337.baf.gps.GPSRegistry;
import dev.by1337.baf.loot.menu.LootMenu;
import dev.by1337.baf.particle.ParticleGraphs;
import dev.by1337.baf.spawner.AirdropSpawnerBoot;
import dev.by1337.baf.spawner.global.GlobalSpawners;
import dev.by1337.baf.util.DurationFormatter;
import dev.by1337.baf.web.AirdropWeb;
import dev.by1337.baf.workflow.WorkflowFiles;
import dev.by1337.baf.workflow.WorkflowRepository;
import dev.by1337.baf.world.WorldData;
import dev.by1337.baf.world.WorldDataContainer;
import dev.by1337.baf.world.fake.FakeBlockSetter;
import dev.by1337.bmenu.BMenu;
import dev.by1337.bmenu.loader.MenuSubLoader;
import dev.by1337.cmd.Command;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.cmd.argument.ArgumentString;
import dev.by1337.core.command.bcmd.argument.ArgumentChoice;
import dev.by1337.core.command.bcmd.argument.ArgumentPlayers;
import dev.by1337.core.command.bcmd.requires.RequiresPermission;
import dev.by1337.core.plugin.BasePlugin;
import dev.by1337.core.util.io.ResourceUtil;
import dev.by1337.core.util.text.minimessage.BMM;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.plc.Placeholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

public class BAirdropFlow extends BasePlugin {
    AirdropManager manager;
    ParticleGraphs particleGraphs;
    Config config;
    WorldDataContainer<WorldData> worldData;
    WorldDataContainer<FakeBlockSetter> fakeBlocks;
    WorkflowRepository workflows;
    dev.by1337.baf.launch.LaunchRegistry launches;
    AirdropWeb web;
    GPSRegistry gps;
    GlobalSpawners globalSpawners;
    MenuSubLoader menus;
    private boolean isReloading;

    @Override
    public void onLoad() {
        BAF.plugin = this;
        super.onLoad();
        getDataFolder().mkdirs();
        //AirdropCommands.resetCommands(); TimerSpawner в static блоке команды рерает
        Generators.boot();
        AirdropSpawnerBoot.boot();
        AirdropComponents.boot();

        config = safeDecode(Config.DECODER, "config.yml", this);
        messages.setCommands(AirdropCommands.getCommands());
        //messages.setDebug(config.debug);
        BAF.manager = manager = new AirdropManager(this, messages);
        if (!new File(getDataFolder(), "particles").exists()) {
            ResourceUtil.saveIfNotExist("particles/example.yml", this);
            ResourceUtil.saveIfNotExist("particles/explosion.yml", this);
            ResourceUtil.saveIfNotExist("particles/spreading_flame.yml", this);
        }
        particleGraphs = new ParticleGraphs(this);
        particleGraphs.load();
        ResourceUtil.saveIfNotExist("workflow/default.yml", this);
        workflows = new WorkflowRepository(new WorkflowFiles(getDataFolder().toPath().resolve("workflow"),
                getDataFolder().toPath().resolve("backups/workflow")), Workflow.CODEC, log::warn);
        launches = new dev.by1337.baf.launch.LaunchRegistry();
        workflows.bindRegistry(launches);
        ResourceUtil.saveIfNotExist("menu/lool_table.yml", this);
        ResourceUtil.saveIfNotExist("menu/vote.yml", this);
        menus = new MenuSubLoader(new File(getDataFolder(), "menu"), this, BMenu.menuLoader());
        menus.menuCodecRegistry().register("baf:loot", LootMenu.Config.CODEC);
        menus.menuCodecRegistry().register("baf:vote", dev.by1337.baf.vote.VoteMenu.CODEC);
        BMenu.menuLoader().registerSubLoader(this, menus);
        addCloseHook(() -> BMenu.menuLoader().unregisterSubLoader(this));
        addCloseHook(() -> Bukkit.getOnlinePlayers().forEach(player -> {
            if (BMenu.menuLoader().getOpenedMenu(player) instanceof LootMenu) player.closeInventory();
        }));

    }

    private void startEvent(String key) {
        String id = key;
        // Keep old filename-based commands working, but always launch through the registry.
        if (launches.get(id) == null) {
            Workflow workflow = workflows.get(key);
            if (workflow != null) id = workflow.id();
        }
        try { launches.start(id); }
        catch (IllegalArgumentException | IllegalStateException error) { throw new CommandMsgError(error.getMessage()); }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (!isReloading) {
            web.close();
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        try {
            workflows.load();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Не удалось загрузить аирдропы", e);
        }
        menus.loadMenus();

        gps = new GPSRegistry(this);
        addCloseHook(gps);
        worldData = new WorldDataContainer<>(this, w -> new WorldData(w, this));
        addCloseHook(worldData::close);
        fakeBlocks = new WorldDataContainer<>(this, w -> new FakeBlockSetter(this, w));
        addCloseHook(fakeBlocks::close);
        addCloseHook(manager::disable);
        manager.enable();
        addCloseHook(new TimerSystem(this, manager).start());
        addCloseHook(new AsyncTimerSystem(this, manager).start());
        if (!new File(getDataFolder(), "spawner").exists()) {
            ResourceUtil.saveIfNotExist("spawner/by_time.yml", this);
            ResourceUtil.saveIfNotExist("spawner/by_item.yml", this);
        }
        ResourceUtil.saveIfNotExist("spawner/vote.yml", this);
        globalSpawners = new GlobalSpawners();
        addCloseHook(globalSpawners::close);
        try {
            globalSpawners.load(getDataFolder().toPath().resolve("spawner"), launches, manager, log::warn);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Не удалось загрузить глобальные спавнеры", e);
        }
        globalSpawners.enableItems(this);
        if (web != null && !web.config().equals(config.web)) {
            web.close();
            web = null;
        }
        if (web == null) {
            web = new AirdropWeb(workflows, config.web, action -> Bukkit.getScheduler().runTask(this, action));
        } else {
            web.reload(workflows, action -> Bukkit.getScheduler().runTask(this, action));
        }

        registerCommand(new Command<CommandSender>("vote").executor(sender -> {
            if (!(sender instanceof Player player)) throw new CommandMsgError("Команда доступна игроку");
            var session = globalSpawners.voting().active();
            if (session == null) throw new CommandMsgError("Сейчас голосование не проводится");
            var menu = BMenu.menuLoader().create(session.config().menu(), player, null);
            if (!(menu instanceof dev.by1337.baf.vote.VoteMenu))
                throw new CommandMsgError("Меню голосования должно использовать provider: baf:vote");
            menu.open();
        }), null);
        registerCommand(new Command<CommandSender>("gps").executor(
                new ArgumentString<CommandSender>("id", null),
                (sender, id) -> {
                    if (!(sender instanceof Player player)) return;
                    if (id != null && !id.isBlank()) {
                        try {
                            long value = Long.parseLong(id);
                            if (gps.addWaypoint(player, value)) return;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    var lines = gps.getEntries();
                    if (lines.isEmpty()) {
                        long seconds = globalSpawners.getSecondsUntilNextEvent();
                        if (seconds < 0) sendMessage("gps_empty", player);
                        else sendMessage("gps_next_event", player, Placeholders.<EventContext>create()
                                .of("seconds", Long.toString(seconds))
                                .of("formated_time_left", DurationFormatter.formatSeconds(seconds)));
                        return;
                    }
                    int x = 0;
                    for (GPSRegistry.GPSEntry line : lines) {
                        player.sendMessage(Component.text(++x + ". ").append(BMM.deserialize(line.getLine(), player.locale())));
                        if (x > 25) return;
                    }
                }), null);
        registerCommand(new Command<CommandSender>("baf").executor(sender -> startEvent("default.yml"))
                .sub(LootCommands.create())
                .sub(new Command<CommandSender>("give").requires(new RequiresPermission<>("baf.admin"))
                        .executor(new ArgumentPlayers<>("player", true),
                                new ArgumentChoice<>("item", () -> globalSpawners.items().ids()),
                                (sender, players, id) -> {
                                    var item = globalSpawners.items().byId(id);
                                    if (players == null || players.isEmpty() || item == null)
                                        throw new CommandMsgError("/baf give <игрок> <предмет>");
                                    var player = players.getFirst();
                                    var stack = globalSpawners.items().getItemStack(item);
                                    player.getInventory().addItem(stack).values().forEach(leftover ->
                                            player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                                }))
                .sub(new Command<CommandSender>("reload").requires(new RequiresPermission<>("baf.admin"))
                        .executor((s) -> {
                            try {
                                isReloading = true;
                                onDisable();
                                onLoad();
                                onEnable();
                            } finally {
                                isReloading = false;
                            }
                            s.sendMessage("reloaded!");
                        }))
                .sub(new Command<CommandSender>("start").requires(new RequiresPermission<>("baf.admin"))
                        .executor(new ArgumentChoice<>("event", launches::ids), (sender, key) -> {
                            if (key == null) throw new CommandMsgError("/baf start <id события>");
                            startEvent(key);
                        }))
                .sub(new Command<CommandSender>("web").requires(new RequiresPermission<>("baf.web.admin"))
                        .executor(sender -> {
                            var future = web.link();
                            if (!future.isDone()) {
                                sender.sendMessage("Создание ссылки...");
                            }
                            future.whenComplete((link, error) -> Bukkit.getScheduler().runTask(this, () -> {
                                if (error != null)
                                    sender.sendMessage("Не удалось получить ссылку: " + error.getMessage() + " Возможно нужен VPN");
                                else
                                    sender.sendMessage(Component.text(link)
                                            .clickEvent(ClickEvent.openUrl(link))
                                            .hoverEvent(Component.text(link)));
                            }));
                        }))
                .sub(new Command<CommandSender>("particle_web").requires(new RequiresPermission<>("baf.web.admin"))
                        .executor(sender -> sender.sendMessage(net.kyori.adventure.text.Component.text("Открыть редактор частиц")
                                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl("https://particle.bdev.space/"))
                                .hoverEvent(net.kyori.adventure.text.Component.text("https://particle.bdev.space/"))))), null);
    }
}