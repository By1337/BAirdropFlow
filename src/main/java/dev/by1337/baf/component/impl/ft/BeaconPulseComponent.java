package dev.by1337.baf.component.impl.ft;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.config.value.DoubleValue;
import dev.by1337.baf.config.value.IntValue;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class BeaconPulseComponent extends FtComponent {
    private final Config config;

    private BeaconPulseComponent(Config c, String name, Airdrop air) {
        super(c.common, name, air);
        config = c;
    }

    @Override
    protected void tick(EventContext ctx) {
        if (age % config.interval.getInt(airdrop) != 0 || !FtEntities.loaded(origin)) return;
        double radius = config.radius.getDouble(airdrop);
        for (var player : origin.getNearbyPlayers(radius, 200)) {
            double dx = player.getX() - origin.getX(), dz = player.getZ() - origin.getZ();
            double distance = Math.hypot(dx, dz);
            int duration = (int) (config.effectTicks.getInt(airdrop) * Math.max(0, 1 - distance / radius));
            if (duration <= 0) continue;
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, duration, 5));
        }
        origin.getWorld().playSound(origin, "entity.lightning_bolt.thunder", 1, 1);
    }

    public record Config(FtSettings common, IntValue interval, DoubleValue radius,
                         IntValue effectTicks) implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = FtSettings.schema("FT: пульс маяка", "Glowing/Hunger и звук вокруг apex; длительность уменьшается по расстоянию XZ.")
                .integer("interval_ticks", "Период", 600, 1, false).number("radius", "Радиус XZ", 15, .1, 128D)
                .integer("effect_ticks", "Максимальная длительность эффектов", 80, 1, false);
        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(Config::new, FtSettings.DECODER.fieldOf(),
                IntValue.DECODER.fieldOf("interval_ticks", new IntValue(600)), DoubleValue.DECODER.fieldOf("radius", new DoubleValue(15)),
                IntValue.DECODER.fieldOf("effect_ticks", new IntValue(80)));

        public Config {
            interval = interval.checked(v -> v > 0, "Invalid interval_ticks");
            radius = radius.checked(v -> v > 0 && v <= 128, "Invalid radius");
            effectTicks = effectTicks.checked(v -> v > 0 && v <= 12000, "Invalid effect_ticks");
        }

        public AirdropComponent create(Airdrop air, String name) {
            return new BeaconPulseComponent(this, name, air);
        }

        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
