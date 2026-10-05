package dev.by1337.baf;

import dev.by1337.baf.command.AirdropCommands;
import dev.by1337.baf.command.LootCommands;
import dev.by1337.baf.component.AirdropComponents;
import dev.by1337.baf.component.system.AsyncTimerSystem;
import dev.by1337.baf.component.system.TimerSystem;
import dev.by1337.baf.config.Config;
import dev.by1337.baf.generator.Generators;
import dev.by1337.baf.loot.menu.LootMenu;
import dev.by1337.baf.particle.ParticleGraphs;
import dev.by1337.baf.spawner.AirdropSpawnerBoot;
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
import dev.by1337.core.command.bcmd.argument.ArgumentChoice;
import dev.by1337.core.command.bcmd.requires.RequiresPermission;
import dev.by1337.core.plugin.BasePlugin;
import dev.by1337.core.util.io.ResourceUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.File;

public class BAirdropFlow extends BasePlugin {
    AirdropManager manager;
    ParticleGraphs particleGraphs;
    Config config;
    WorldDataContainer<WorldData> worldData;
    WorldDataContainer<FakeBlockSetter> fakeBlocks;
    WorkflowRepository workflows;
    AirdropWeb web;

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
        messages.setDebug(config.debug);
        BAF.manager = manager = new AirdropManager(this, messages);
        if (!new File(getDataFolder(), "particles").exists()) {
            ResourceUtil.saveIfNotExist("particles/example.yml", this);
            ResourceUtil.saveIfNotExist("particles/explosion.yml", this);
        }
        particleGraphs = new ParticleGraphs(this);
        particleGraphs.load();
        ResourceUtil.saveIfNotExist("workflow/default.yml", this);
        workflows = new WorkflowRepository(new WorkflowFiles(getDataFolder().toPath().resolve("workflow"),
                getDataFolder().toPath().resolve("backups/workflow")), Workflow.CODEC, log::warn);
        try {
            workflows.load();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Не удалось загрузить аирдропы", e);
        }
        ResourceUtil.saveIfNotExist("menu/lool_table.yml", this);
        MenuSubLoader menus = new MenuSubLoader(new File(getDataFolder(), "menu"), this, BMenu.menuLoader());
        menus.menuCodecRegistry().register("baf:loot", LootMenu.Config.CODEC);
        BMenu.menuLoader().registerSubLoader(this, menus);
        menus.loadMenus();
        addCloseHook(() -> BMenu.menuLoader().unregisterSubLoader(this));
        addCloseHook(() -> Bukkit.getOnlinePlayers().forEach(player -> {
            if (BMenu.menuLoader().getOpenedMenu(player) instanceof LootMenu) player.closeInventory();
        }));

    }

    private void startWorkflow(String key) {
        Workflow workflow = workflows.get(key);
        if (workflow == null) throw new CommandMsgError("Аирдроп не загружен или имеет ошибки: " + key);
        manager.addTicking(workflow.createSpawner());
    }

    @Override
    public void onEnable() {
        super.onEnable();
        worldData = new WorldDataContainer<>(this, w -> new WorldData(w, this));
        addCloseHook(worldData::close);
        fakeBlocks = new WorldDataContainer<>(this, w -> new FakeBlockSetter(this, w));
        addCloseHook(fakeBlocks::close);
        addCloseHook(manager::disable);
        manager.enable();
        addCloseHook(new TimerSystem(this, manager).start());
        addCloseHook(new AsyncTimerSystem(this, manager).start());
        if (config.web.enabled()) {
            web = new AirdropWeb(workflows, config.web, action -> Bukkit.getScheduler().runTask(this, action));
            addCloseHook(web);
        }
        registerCommand(new Command<CommandSender>("baf").executor(sender -> startWorkflow("default.yml"))
                .sub(LootCommands.create())
                .sub(new Command<CommandSender>("reload").requires(new RequiresPermission<>("baf.admin"))
                        .executor((s) -> {
                            onDisable();
                            onLoad();
                            onEnable();
                            s.sendMessage("reloaded!");
                        }))
                .sub(new Command<CommandSender>("start").requires(new RequiresPermission<>("baf.admin"))
                        .executor(new ArgumentChoice<>("workflow", workflows::keys), (sender, key) -> {
                            if (key == null) throw new CommandMsgError("/baf start <файл аирдропа>");
                            startWorkflow(key);
                        }))
                .sub(new Command<CommandSender>("web").requires(new RequiresPermission<>("baf.web.admin"))
                        .executor(sender -> {
                            if (web == null) throw new CommandMsgError("Включите web.enabled в config.yml");
                            web.link().whenComplete((link, error) -> Bukkit.getScheduler().runTask(this, () -> {
                                if (error != null)
                                    sender.sendMessage("Не удалось получить ссылку: " + error.getMessage() + " Возможно нужен VPN");
                                else
                                    sender.sendMessage(net.kyori.adventure.text.Component.text("Открыть редактор аирдропов")
                                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(link))
                                            .hoverEvent(net.kyori.adventure.text.Component.text(link)));
                            }));
                        }))
                .sub(new Command<CommandSender>("particle_web").requires(new RequiresPermission<>("baf.web.admin"))
                        .executor(sender -> sender.sendMessage(net.kyori.adventure.text.Component.text("Открыть редактор частиц")
                                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl("https://particle.bdev.space/"))
                                .hoverEvent(net.kyori.adventure.text.Component.text("https://particle.bdev.space/"))))), null);
    }
}
