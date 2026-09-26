package dev.by1337.baf;

import dev.by1337.baf.component.AirdropComponents;
import dev.by1337.baf.command.AirdropCommands;
import dev.by1337.baf.command.LootCommands;
import dev.by1337.baf.loot.menu.LootMenu;
import dev.by1337.bmenu.BMenu;
import dev.by1337.bmenu.loader.MenuSubLoader;
import dev.by1337.baf.component.system.TimerSystem;
import dev.by1337.baf.config.Config;
import dev.by1337.baf.generator.Generators;
import dev.by1337.baf.spawner.AirdropSpawnerBoot;
import dev.by1337.baf.world.WorldDataContainer;
import dev.by1337.core.util.io.ResourceUtil;
import dev.by1337.core.plugin.BasePlugin;
import dev.by1337.cmd.Command;
import dev.by1337.yaml.YamlMap;
import org.bukkit.command.CommandSender;
import org.bukkit.Bukkit;
import java.io.File;

public class BAirdropFlow extends BasePlugin {
    AirdropManager manager;
    TimerSystem timerSystem;
    Config config;
    WorldDataContainer worldData;

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
        timerSystem = new TimerSystem(this, manager);
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

    @Override
    public void onEnable() {
        super.onEnable();
        worldData = new WorldDataContainer(this);
        addCloseHook(worldData::close);
        addCloseHook(manager::disable);
        manager.enable();
        addCloseHook(timerSystem);
        timerSystem.start();
        registerCommand(new Command<CommandSender>("baf").executor(sender -> {
            YamlMap map = ResourceUtil.load("workflow/default.yml", this);
            Workflow workflow = Workflow.CODEC.decode(map.get()).getOrThrow();
            manager.addTicking(workflow.createSpawner());
        }).sub(LootCommands.create()), null);
    }
}
