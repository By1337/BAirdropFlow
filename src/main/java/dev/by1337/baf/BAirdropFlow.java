package dev.by1337.baf;

import dev.by1337.baf.component.AirdropComponents;
import dev.by1337.baf.component.system.TimerSystem;
import dev.by1337.baf.config.Config;
import dev.by1337.baf.generator.Generators;
import dev.by1337.baf.spawner.AirdropSpawnerBoot;
import dev.by1337.baf.world.WorldDataContainer;
import dev.by1337.core.util.io.ResourceUtil;
import dev.by1337.yaml.YamlMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class BAirdropFlow extends JavaPlugin {
    AirdropManager manager;
    TimerSystem timerSystem;
    Config config;
    WorldDataContainer worldData;

    @Override
    public void onLoad() {
        BAF.plugin = this;
        getDataFolder().mkdirs();
        //AirdropCommands.resetCommands(); TimerSpawner в static блоки команды рерает
        Generators.boot();
        AirdropSpawnerBoot.boot();
        AirdropComponents.boot();

        var res = Config.DECODER.decode(ResourceUtil.load("config.yml", this).get(), this);
        config = res.result();
        if (config == null) {
            throw new RuntimeException(res.error());
        } else if (res.hasError()) {
            getSLF4JLogger().error("Failed to load cfg\n{}", res.error());
        }
        config.eventCtx.setDebug(true);
        BAF.manager = manager = new AirdropManager(this, config);
        timerSystem = new TimerSystem(this, manager);

    }

    @Override
    public void onEnable() {
        manager.enable();
        timerSystem.start();
        worldData = new WorldDataContainer(this);
    }

    @Override
    public void onDisable() {
        manager.disable();
        timerSystem.close();
        worldData.close();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        YamlMap map = ResourceUtil.load("workflow/default.yml", this);
        Workflow workflow = Workflow.CODEC.decode(map.get()).getOrThrow();
        BAF.manager().addTicking(workflow.createSpawner());
        var testGenerator = workflow.generator();
        Player player = (Player) sender;
        Location loc;
        int x = 0;
        while ((loc = testGenerator.tryGenerate(player.getWorld())) == null) {
            System.out.println(x++);
        }
        player.teleport(loc);
        player.sendMessage(Component.text(x));

        return true;
    }
}
