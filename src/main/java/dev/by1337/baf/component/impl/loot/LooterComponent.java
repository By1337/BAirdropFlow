package dev.by1337.baf.component.impl.loot;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.command.AirdropContext;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.handler.ComponentHandler;
import dev.by1337.baf.interact.InteractListener;
import dev.by1337.baf.loot.LootTable;
import dev.by1337.baf.loot.inventory.LootingInventory;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import dev.by1337.yaml.codec.RecordYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LooterComponent extends AbstractComponent {

    private static final Logger log = LoggerFactory.getLogger(LooterComponent.class);
    private final Config config;
    private final Airdrop airdrop;
    private final CleanerSlot<InteractListener<PlayerInteractEvent>> clickHolder;
    private final CleanerSlot<LootingInventory> inventoryHolder;

    private LooterComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.config = config;
        this.airdrop = airdrop;
        clickHolder = new CleanerSlot<>(InteractListener::unregister, airdrop.cleaner());
        inventoryHolder = new CleanerSlot<>(LootingInventory::close, airdrop.cleaner());
    }

    private void on(PlayerInteractEvent event) {
        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var inv = inventoryHolder.get();
        if (inv == null) return;
        Player pl = event.getPlayer();
        if (pl.getOpenInventory().getTopInventory() == inv.getInventory()) {
            return;
        }
        pl.openInventory(inv.getInventory());
        AirdropContext ctx = new AirdropContext(airdrop, pl);
        config.open.handle(ctx);
    }

    @Override
    public void onCreate(AirdropContext ctx) {
        clickHolder.set(airdrop.manager().interactManager().registerBlockClick(
                airdrop.world(),
                airdrop.x() + config.offsets.x(),
                airdrop.y() + config.offsets.y(),
                airdrop.z() + config.offsets.z(),
                LooterComponent.this::on
        ));
        inventoryHolder.set(new LootingInventory(
                54,
                MiniMessage.deserialize(airdrop.setPlaceholders(config.title)),
                airdrop.location().add(config.offsets.x(), config.offsets.y(), config.offsets.z()),
                airdrop.manager().plugin()
        ));
        LootTable lootTable = airdrop.manager().lootTables().get(config.lootTable);
        if (lootTable == null) {
            log.error("Unknown loot table {}", config.lootTable);
        } else {
            var inv = inventoryHolder.get();
            if (inv == null) return;
            inv.fill(lootTable.generate());
        }
        config.create.handle(ctx);
    }

    @Override
    public void onRemove(AirdropContext ctx) {
        config.remove.handle(ctx);
        clickHolder.cleanup();
        inventoryHolder.cleanup();
    }


    public static class Config implements ComponentFactory {
        public static final YamlCodec<Config> CODEC = RecordYamlCodecBuilder.mapOf(
                Config::new,
                Vec3i.CODEC.fieldOf("offsets", v -> v.offsets, new Vec3i(0, 0, 0)),
                YamlCodec.STRING.fieldOf("loot_table", v -> v.lootTable),
                ComponentHandler.CODEC.fieldOf("on_create", v -> v.create, ComponentHandler.EMPTY),
                ComponentHandler.CODEC.fieldOf("on_open", v -> v.open, ComponentHandler.EMPTY),
                ComponentHandler.CODEC.fieldOf("on_remove", v -> v.remove, ComponentHandler.EMPTY),
                YamlCodec.STRING.fieldOf("title", v -> v.title, "&7Title")
        );
        private final Vec3i offsets;
        private final String lootTable;
        private final ComponentHandler create;
        private final ComponentHandler open;
        private final ComponentHandler remove;
        private final String title;

        public Config(Vec3i offsets, String lootTable, ComponentHandler create, ComponentHandler open, ComponentHandler remove, String title) {
            this.offsets = offsets;
            this.lootTable = lootTable;
            this.create = create;
            this.open = open;
            this.remove = remove;
            this.title = title;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new LooterComponent(this, name, airdrop);
        }

        @Override
        public YamlCodec<? extends ComponentFactory> codec() {
            return CODEC;
        }
    }
}