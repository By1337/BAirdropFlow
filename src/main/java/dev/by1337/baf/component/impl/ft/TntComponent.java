package dev.by1337.baf.component.impl.ft;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.config.value.BooleanValue;
import dev.by1337.baf.config.value.IntValue;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.entity.TNTPrimed;

public final class TntComponent extends FtComponent {
    private final Config config;

    private TntComponent(Config config, String name, Airdrop air) {
        super(config.common, name, air);
        this.config = config;
    }

    @Override
    protected void start(EventContext ctx) {
        int fuse = config.fuse.getInt(airdrop);
        if (fuse >= lifetime) throw new IllegalArgumentException("entity_lifetime_ticks must exceed fuse_ticks");
        spawn(origin, TNTPrimed.class, config.damage.getBoolean(airdrop), tnt -> {
            tnt.setFuseTicks(fuse);
            tnt.setIsIncendiary(false);
            tnt.setYield(4);
        });
    }

    @Override
    protected void tick(EventContext ctx) {
        if (entities.size() == 0) finish(ctx);
    }

    public record Config(FtSettings common, IntValue fuse, BooleanValue damage) implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = FtSettings.schema("FT: TNT маяка", "Не разрушает блоки. Урон выключен по умолчанию; WorldGuard не обходится.")
                .integer("fuse_ticks", "Время до взрыва", 100, 1, false).bool("damage", "Разрешить обычный урон TNT", false);
        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(Config::new, FtSettings.DECODER.fieldOf(),
                IntValue.DECODER.fieldOf("fuse_ticks", new IntValue(100)), BooleanValue.DECODER.fieldOf("damage", new BooleanValue(false)));

        public Config {
            fuse = fuse.checked(v -> v > 0 && v < 1200000, "Invalid fuse_ticks");
        }

        public AirdropComponent create(Airdrop air, String name) {
            return new TntComponent(this, name, air);
        }

        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
