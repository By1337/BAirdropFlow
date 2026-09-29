package dev.by1337.baf.component;

import dev.by1337.baf.component.impl.DestroyComponent;
import dev.by1337.baf.component.impl.InitComponent;
import dev.by1337.baf.component.impl.TimerComponent;
import dev.by1337.baf.component.impl.block.BreakBlockComponent;
import dev.by1337.baf.component.impl.dh.DHHologramComponent;
import dev.by1337.baf.component.impl.loot.LooterComponent;
import dev.by1337.baf.component.impl.wg.WgRegionComponent;

public class AirdropComponents {

    public static void boot(){
        AirdropComponent.REGISTRY.clear();
        AirdropComponent.REGISTRY.register("default:__init__", InitComponent.Config.DECODER, InitComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("default:__destroy__", DestroyComponent.Config.DECODER, DestroyComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("tick:timer", TimerComponent.Config.DECODER, TimerComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("loot:looter", LooterComponent.Config.DECODER, LooterComponent.Config.WEB_SCHEMA);
        AirdropComponent.REGISTRY.register("block:break", BreakBlockComponent.Config.DECODER, BreakBlockComponent.Config.WEB_SCHEMA);
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
