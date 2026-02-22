package dev.by1337.baf;

import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.impl.*;
import dev.by1337.baf.component.system.TimerSystem;
import dev.by1337.core.util.io.ResourceUtil;
import dev.by1337.yaml.YamlMap;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class BAirdropFlow extends JavaPlugin {
    private AirdropManager manager;
    private TimerSystem timerSystem;

    @Override
    public void onLoad() {
        AirdropComponent.REGISTRY.register("default:__init__", InitComponent.Config.CODEC);
        AirdropComponent.REGISTRY.register("default:__destroy__", DestroyComponent.Config.CODEC);
        AirdropComponent.REGISTRY.register("default:timer", TimerComponent.Config.CODEC);
        manager = new AirdropManager(this);
        timerSystem = new TimerSystem(this, manager);
    }

    @Override
    public void onEnable() {
        timerSystem.start();
    }

    @Override
    public void onDisable() {
        timerSystem.close();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (Player) sender;
        YamlMap map = ResourceUtil.load("workflow/default.yml", this);
        AirdropConfig cfg = AirdropConfig.CODEC.decode(map).getOrThrow();
        var loc = player.getLocation();
        Airdrop airdrop = new Airdrop(
                cfg, manager,
                loc.getWorld(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
        );
        manager.addAirdrop(airdrop);
        return true;
    }
}
