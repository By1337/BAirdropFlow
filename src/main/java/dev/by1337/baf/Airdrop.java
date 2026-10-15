package dev.by1337.baf;

import dev.by1337.baf.command.AirdropCommands;
import dev.by1337.baf.component.ComponentHolder;
import dev.by1337.baf.ref.AirdropCleaner;
import dev.by1337.cmd.Command;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.plc.PlaceholderResolver;
import dev.by1337.plc.Placeholders;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

public class Airdrop implements PlaceholderApplier {
    private static final Random RANDOM = new Random();
    public static final PlaceholderResolver<Airdrop> PLACEHOLDERS = Placeholders.<Airdrop>create()
            .withContext("x", air -> air.x)
            .withContext("y", air -> air.y)
            .withContext("z", air -> air.z)
            .withContext("world", air -> air.world.getName())
            .of("ctx", (key, air) -> {
                var arr = key.replace(" ", "").split("\\.", 2);
                if (arr.length != 2) return "<name>.<key>";
                var v = air.components.get(arr[0]);
                if (v == null) return "unknown node " + arr[0];
                var obj = v.getPlaceholder(arr[1]);
                if (obj == null) return "unknown key " + arr[1];
                return String.valueOf(obj);
            })
            .of("args", (key, air) -> String.valueOf(air.args.get(key)));
    private final Workflow config;
    private final AirdropManager manager;
    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private final Location location;
    private boolean removed;
    private final ComponentHolder components = new ComponentHolder();
    private final AirdropCleaner cleaner;
    private final Map<String, String> args = new HashMap<>();
    private final Set<Player> nearbyPlayers = new HashSet<>();

    public Airdrop(Workflow config, AirdropManager manager, Location location) {
        cleaner = new AirdropCleaner();
        this.config = config;
        this.manager = manager;
        this.location = location;
        this.world = location.getWorld();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
    }

    public void tick(int radiusSq) {
        nearbyPlayers.clear();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radiusSq) nearbyPlayers.add(player);
        }
    }

    public void tick() {
        tick(config.nearbyRadius() * config.nearbyRadius());
    }

    public Map<String, String> args() {
        return args;
    }

    public Command<EventContext> getCommands() {
        return AirdropCommands.getCommands();
    }

    @Override
    public String setPlaceholders(String s) {
        return PLACEHOLDERS.setPlaceholders(s, this);
    }

    public void markRemoved(EventContext ctx) {
        if (removed) return;
        removed = true;
        manager.removeAirdrop(this, ctx);
    }

    public boolean isRemoved() {
        return removed;
    }

    public ComponentHolder components() {
        return components;
    }

    public Workflow config() {
        return config;
    }

    public World world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public AirdropCleaner cleaner() {
        return cleaner;
    }

    public AirdropManager manager() {
        return manager;
    }

    public Location location() {
        return location.clone();
    }

    public static Random random() {
        return RANDOM;
    }

    public Set<Player> nearbyPlayers() {
        return nearbyPlayers;
    }
}
