package dev.by1337.baf.component.impl.wg;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.managers.RemovalStrategy;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import dev.by1337.baf.Airdrop;
import dev.by1337.baf.command.AirdropContext;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.yaml.codec.RecordYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;
import org.bukkit.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class WgRegionComponent extends AbstractComponent {
    private final Config config;
    private final String randName = UUID.randomUUID().toString();
    private final CleanerSlot<World> regionRemover;

    private WgRegionComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.config = config;
        regionRemover = new CleanerSlot<>(this::removeRegion, airdrop.cleaner());
    }

    @Override
    public void onCreate(AirdropContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " create {", "}")){
            WGUtil.setRegion(
                    ctx.airdrop().world(),
                    config.flags,
                    new ProtectedCuboidRegion(
                            randName,
                            BlockVector3.at(
                                    ctx.airdrop().x() - config.size.x(),
                                    ctx.airdrop().y() - config.size.y(),
                                    ctx.airdrop().z() - config.size.z()
                            ),
                            BlockVector3.at(
                                    ctx.airdrop().x() + config.size.x(),
                                    ctx.airdrop().y() + config.size.y(),
                                    ctx.airdrop().z() + config.size.z()
                            )
                    )
            );
            regionRemover.set(ctx.airdrop().world());
        }
    }

    @Override
    public void onRemove(AirdropContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " remove {", "}")){
            regionRemover.cleanup();
        }
    }

    private void removeRegion(World world) {
        WGUtil.removeRegion(randName, world);
    }

    public static class Config implements ComponentFactory {
        public static final YamlCodec<Config> CODEC = RecordYamlCodecBuilder.mapOf(
                Config::new,
                Vec3i.CODEC.fieldOf("size", v -> v.size, new Vec3i(1, 1, 1)),
                YamlCodec.STRINGS.fieldOf("flags", v -> v.flags, List.of())
        );
        private final Vec3i size;
        private final List<String> flags;

        public Config(Vec3i size, List<String> flags) {
            this.size = size;
            this.flags = flags;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new WgRegionComponent(this, name, airdrop);
        }

        @Override
        public YamlCodec<? extends ComponentFactory> codec() {
            return CODEC;
        }
    }

    private static class WGUtil {
        public static final UUID RG_OWNER = UUID.nameUUIDFromBytes("BAirDropFlow".getBytes(StandardCharsets.UTF_8));
        private static final Logger LOGGER = LoggerFactory.getLogger("BAirDropFlow");
        private static final RegionContainer CONTAINER = WorldGuard.getInstance().getPlatform().getRegionContainer();

        public static void removeRegion(String id, World world) {
            com.sk89q.worldguard.protection.managers.RegionManager regions = CONTAINER.get(BukkitAdapter.adapt(world));
            if (regions.hasRegion(id))
                regions.removeRegion(id, RemovalStrategy.REMOVE_CHILDREN);
        }

        @SuppressWarnings("unchecked")
        public static <T> ProtectedRegion setRegion(World world, List<String> flags, ProtectedRegion rg) {
            com.sk89q.worldguard.protection.managers.RegionManager regions = CONTAINER.get(BukkitAdapter.adapt(world));


            for (String flag : flags) {
                try {
                    String name = flag.split(" ")[0];
                    String val = flag.substring(name.length() + 1);
                    Flag<T> f = (Flag<T>) WorldGuard.getInstance().getFlagRegistry().get(name);
                    if (f == null) {
                        LOGGER.error("unknown flag {}", flag);
                        continue;
                    }
                    rg.setFlag(f, f.parseInput(FlagContext.create().setInput(
                            "-e".equals(val) ? "" : val
                    ).build()));
                } catch (Exception e) {
                    LOGGER.error("failed to set flag! {}", flag, e);
                }
            }
            rg.getOwners().addPlayer(RG_OWNER);
            regions.addRegion(rg);
            return rg;
        }
    }
}
