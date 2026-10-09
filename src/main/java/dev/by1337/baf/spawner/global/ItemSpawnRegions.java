package dev.by1337.baf.spawner.global;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

final class ItemSpawnRegions {
    private ItemSpawnRegions() {
    }

    static boolean isOutside(Block support, int radius, int height) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false;
        var manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(support.getWorld()));
        if (manager == null) return false;
        var area = new ProtectedCuboidRegion("baf_item_check",
                BlockVector3.at(support.getX() - radius, support.getY(), support.getZ() - radius),
                BlockVector3.at(support.getX() + radius, support.getY() + height, support.getZ() + radius));
        return manager.getApplicableRegions(area).size() == 0;
    }
}
