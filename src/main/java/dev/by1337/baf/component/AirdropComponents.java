package dev.by1337.baf.component;

import dev.by1337.baf.component.impl.*;
import dev.by1337.baf.component.impl.block.BreakBlockComponent;
import dev.by1337.baf.component.impl.block.ClickBlockComponent;
import dev.by1337.baf.component.impl.dh.DHHologramComponent;
import dev.by1337.baf.component.impl.ft.*;
import dev.by1337.baf.component.impl.holo.HologramComponent;
import dev.by1337.baf.component.impl.loot.LooterComponent;
import dev.by1337.baf.component.impl.loot.SpreadingComponent;
import dev.by1337.baf.component.impl.schematic.SchematicComponent;
import dev.by1337.baf.component.impl.wg.WgRegionComponent;

public class AirdropComponents {

    public static void boot() {
        AirdropComponent.REGISTRY.clear();
        AirdropComponent.REGISTRY.register("default:__init__", InitComponent.Config.DECODER, InitComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("default:__destroy__", DestroyComponent.Config.DECODER, DestroyComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("default:commands", CommandsComponent.Config.DECODER, CommandsComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("tick:timer", TimerComponent.Config.DECODER, TimerComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("tick:timeout", TimeoutComponent.Config.DECODER, TimeoutComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("default:bossbar", BossBarComponent.Config.DECODER, BossBarComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("default:gps", GPSComponent.Config.DECODER, GPSComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("particle:animation", ParticleComponent.Config.DECODER, ParticleComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("loot:looter", LooterComponent.Config.DECODER, LooterComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("loot:spreading", SpreadingComponent.Config.DECODER, SpreadingComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("block:break", BreakBlockComponent.Config.DECODER, BreakBlockComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("block:click", ClickBlockComponent.Config.DECODER, ClickBlockComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("schematic:paster", SchematicComponent.Config.DECODER, SchematicComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("default:hologram", HologramComponent.Config.DECODER, HologramComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("ft:falling_blocks", FallingBlocksComponent.Config.DECODER, FallingBlocksComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("ft:tnt", TntComponent.Config.DECODER, TntComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("ft:firework", FireworkComponent.Config.DECODER, FireworkComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("ft:mobs", MobsComponent.Config.DECODER, MobsComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("ft:meteorites", MeteoriteComponent.Config.DECODER, MeteoriteComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("ft:geyser", GeyserComponent.Config.DECODER, GeyserComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("ft:strange_beacon", StrangeBeaconComponent.Config.DECODER, StrangeBeaconComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("ft:items", ItemsComponent.Config.DECODER, ItemsComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("ft:proximity_reward", ProximityRewardComponent.Config.DECODER, ProximityRewardComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("ft:beacon_pulse", BeaconPulseComponent.Config.DECODER, BeaconPulseComponent.Config.WEB_SCHEMA);
        if (hasClass("eu.decentsoftware.holograms.api.DHAPI")) {
            AirdropComponent.REGISTRY.register("dh:hologram", DHHologramComponent.Config.DECODER, DHHologramComponent.Config.WEB_SCHEMA);
        }
        AirdropComponent.REGISTRY.register("wg:region", WgRegionComponent.Config.DECODER, WgRegionComponent.Config.WEB_SCHEMA);
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name);
        } catch (ClassNotFoundException e) {
            return false;
        }
        return true;
    }
}
