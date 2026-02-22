package dev.by1337.baf;

import dev.by1337.baf.command.AirdropCommands;
import dev.by1337.baf.command.AirdropContext;
import dev.by1337.baf.component.ComponentHolder;
import dev.by1337.cmd.Command;
import dev.by1337.plc.PapiResolver;
import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.plc.PlaceholderResolver;
import dev.by1337.plc.Placeholders;
import org.bukkit.World;

public class Airdrop implements PlaceholderApplier {
    private static final PlaceholderResolver<Airdrop> PLACEHOLDERS = Placeholders.<Airdrop>create()
            .withContext("x", ctx -> ctx.x)
            .withContext("y", ctx -> ctx.y)
            .withContext("z", ctx -> ctx.z)
            .withContext("world", ctx -> ctx.world.getName())
            .of("ctx", (key, ctx) -> {
                var arr = key.replace(" ", "").split("\\.", 2);
                if (arr.length != 2) return "<name>.<key>";
                var v = ctx.components.get(arr[0]);
                if (v == null) return "unknown node " + arr[0];
                var obj = v.getPlaceholder(arr[1]);
                if (obj == null) return "unknown key " + arr[1];
                return String.valueOf(obj);
            }).and(PapiResolver.get().map(v -> null));
    private final AirdropConfig config;
    private final AirdropManager manager;
    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private boolean removed;
    private final ComponentHolder components = new ComponentHolder(this);

    public Airdrop(AirdropConfig config, AirdropManager manager, World world, int x, int y, int z) {
        this.config = config;
        this.manager = manager;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Command<AirdropContext> getCommands() {
        return AirdropCommands.getCommands();
    }

    @Override
    public String setPlaceholders(String s) {
        return PLACEHOLDERS.setPlaceholders(s, this);
    }

    public void markRemoved() {
        if (removed) return;
        removed = true;
        manager.removeAirdrop(this);
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
}
