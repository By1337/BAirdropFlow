package dev.by1337.baf;

import dev.by1337.baf.command.AirdropCommands;
import dev.by1337.baf.command.AirdropContext;
import dev.by1337.baf.component.ComponentHolder;
import dev.by1337.baf.ref.AirdropCleaner;
import dev.by1337.cmd.Command;
import dev.by1337.plc.PapiResolver;
import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.plc.PlaceholderResolver;
import dev.by1337.plc.Placeholders;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Random;

public class Airdrop implements PlaceholderApplier {
    private static final Random RANDOM = new Random();
    public static final PlaceholderResolver<AirdropContext> PLACEHOLDERS = Placeholders.<AirdropContext>create()
            .withContext("x", ctx -> ctx.airdrop().x)
            .withContext("y", ctx -> ctx.airdrop().y)
            .withContext("z", ctx -> ctx.airdrop().z)
            .withContext("world", ctx -> ctx.airdrop().world.getName())
            .of("ctx", (key, ctx) -> {
                var arr = key.replace(" ", "").split("\\.", 2);
                if (arr.length != 2) return "<name>.<key>";
                var v = ctx.airdrop().components.get(arr[0]);
                if (v == null) return "unknown node " + arr[0];
                var obj = v.getPlaceholder(arr[1]);
                if (obj == null) return "unknown key " + arr[1];
                return String.valueOf(obj);
            }).and(PapiResolver.get().map(AirdropContext::player));
    private final AirdropContext selfContext = new AirdropContext(this);
    private final AirdropConfig config;
    private final AirdropManager manager;
    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private final Location location;
    private boolean removed;
    private final ComponentHolder components = new ComponentHolder();
    private final AirdropCleaner cleaner;

    public Airdrop(AirdropConfig config, AirdropManager manager, Location location) {
        cleaner = new AirdropCleaner();
        this.config = config;
        this.manager = manager;
        this.location = location;
        this.world = location.getWorld();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
    }

    public Command<AirdropContext> getCommands() {
        return AirdropCommands.getCommands();
    }

    public static String setPlaceholders(String s, AirdropContext context) {
        return PLACEHOLDERS.setPlaceholders(s, context);
    }

    @Override
    public String setPlaceholders(String s) {
        return PLACEHOLDERS.setPlaceholders(s, selfContext);
    }

    public void markRemoved(AirdropContext ctx) {
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

    public AirdropConfig config() {
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
}
