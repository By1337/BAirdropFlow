package dev.by1337.baf;

import dev.by1337.baf.interact.InteractManager;
import dev.by1337.baf.component.system.ParticleGraphs;
import dev.by1337.baf.loot.LootTables;
import dev.by1337.baf.world.WorldData;
import dev.by1337.baf.world.WorldDataContainer;
import dev.by1337.baf.world.fake.FakeBlockSetter;
import dev.by1337.edsl.MessageManager;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.plc.PlaceholderResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class BAF {
    public static boolean RUNNING_IN_IDE = Boolean.getBoolean("baf.ide");
    static AirdropManager manager;
    static BAirdropFlow plugin;

    public static AirdropManager manager() {
        return manager;
    }

    public static LootTables lootTables() {
        return manager.lootTables();
    }

    public static InteractManager blockInteract() {
        return manager.interactManager();
    }

    public static EventContext newContext(PlaceholderResolver<EventContext> p) {
        return manager.newContext(p);
    }

    public static MessageManager.Builder newContextBuilder(final Object... args) {
        return manager.newContextBuilder(args);
    }

    public static EventContext newContext() {
        return manager.newContextBuilder().build();
    }


    public static EventContext newContext(@Nullable Player player) {
        return manager.newContext(player);
    }

    public static EventContext newContext(Airdrop airdrop) {
        return manager.newContext(airdrop);
    }

    public static EventContext newContext(Airdrop airdrop, @Nullable Player player) {
        return manager.newContext(airdrop, player);
    }

    public static WorldDataContainer<WorldData> worldData() {
        return plugin.worldData;
    }
    public static Plugin plugin(){
        return plugin;
    }
    public static ParticleGraphs particleGraphs() {
        return plugin.particleGraphs;
    }
    public static WorldDataContainer<FakeBlockSetter> fakeBlocks() {
        return plugin.fakeBlocks;
    }

}
