package dev.by1337.baf.component.impl.ft;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.config.value.BooleanValue;
import dev.by1337.baf.config.value.IntValue;
import dev.by1337.baf.config.value.StringValue;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;

import java.util.List;

public final class FireworkComponent extends FtComponent {
    private final Config config;

    private FireworkComponent(Config c, String name, Airdrop air) {
        super(c.common, name, air);
        config = c;
    }

    @Override
    protected void start(EventContext ctx) {
        var colors = config.colors.stream().map(v -> color(v.getString(airdrop))).toList();
        var fades = config.fade.stream().map(v -> color(v.getString(airdrop))).toList();
        var effect = FireworkEffect.builder().withColor(colors).withFade(fades)
                .flicker(config.flicker.getBoolean(airdrop)).trail(config.trail.getBoolean(airdrop)).build();
        for (int i = 0; i < config.count.getInt(airdrop); i++)
            spawn(origin, Firework.class, false, firework -> {
                var meta = firework.getFireworkMeta();
                meta.addEffect(effect);
                meta.setPower(config.power.getInt(airdrop));
                firework.setFireworkMeta(meta);
            });
    }

    static Color color(String s) {
        return Color.fromRGB(Integer.parseInt(s.replace("#", ""), 16));
    }

    @Override
    protected void tick(EventContext ctx) {
        if (entities.size() == 0) finish(ctx);
    }

    public record Config(FtSettings common, IntValue count, IntValue power, BooleanValue flicker, BooleanValue trail,
                         List<StringValue> colors, List<StringValue> fade) implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = FtSettings.schema("FT: фейерверк", "Настоящий фейерверк без урона.")
                .integer("count", "Количество", 1, 1, false).integer("power", "Сила", 1, 0, false)
                .bool("flicker", "Мерцание", true).bool("trail", "След", true)
                .strings("colors", "Цвета #RRGGBB", List.of("#ff0000")).strings("fade", "Цвета затухания #RRGGBB", List.of("#ffff00"));
        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(Config::new, FtSettings.DECODER.fieldOf(),
                IntValue.DECODER.fieldOf("count", new IntValue(1)), IntValue.DECODER.fieldOf("power", new IntValue(1)),
                BooleanValue.DECODER.fieldOf("flicker", new BooleanValue(true)), BooleanValue.DECODER.fieldOf("trail", new BooleanValue(true)),
                StringValue.DECODER.listOf().fieldOf("colors", List.of(new StringValue("#ff0000"))),
                StringValue.DECODER.listOf().fieldOf("fade", List.of(new StringValue("#ffff00"))));

        public Config {
            count = count.checked(v -> v > 0 && v <= 256, "Invalid count");
            power = power.checked(v -> v >= 0 && v <= 4, "Invalid power");
            colors = List.copyOf(colors);
            fade = List.copyOf(fade);
            if (colors.isEmpty()) throw new IllegalArgumentException("colors cannot be empty");
            for (var value : colors) if (value.isConstant()) color(value.getString(null));
            for (var value : fade) if (value.isConstant()) color(value.getString(null));
        }

        public AirdropComponent create(Airdrop air, String name) {
            return new FireworkComponent(this, name, air);
        }

        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
