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
        AirdropComponent.REGISTRY.register("default:__init__", InitComponent.Config.DECODER);
        AirdropComponent.REGISTRY.register("default:__destroy__", DestroyComponent.Config.DECODER);
        AirdropComponent.REGISTRY.register("tick:timer", TimerComponent.Config.DECODER);
        AirdropComponent.REGISTRY.register("loot:looter", LooterComponent.Config.DECODER);
        AirdropComponent.REGISTRY.register("block:break", BreakBlockComponent.Config.DECODER);
        if (hasClass("eu.decentsoftware.holograms.api.DHAPI")) {
            AirdropComponent.REGISTRY.register("dh:hologram", DHHologramComponent.Config.DECODER);
        }
        AirdropComponent.REGISTRY.register("wg:region", WgRegionComponent.Config.DECODER);
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
