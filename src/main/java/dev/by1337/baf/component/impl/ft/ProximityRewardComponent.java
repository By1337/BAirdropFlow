package dev.by1337.baf.component.impl.ft;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class ProximityRewardComponent extends FtComponent {
    private record Recipient(Player player, double distance) {}

    private final Config config;
    private String amount = "0", distance = "0";
    private int players;

    private ProximityRewardComponent(Config config, String name, Airdrop air) {
        super(config.common, name, air);
        this.config = config;
        exportPlaceholder("amount", () -> amount);
        exportPlaceholder("distance", () -> distance);
        exportPlaceholder("players", () -> players);
    }

    @Override
    protected void tick(EventContext ctx) {
        if (age % config.interval != 0) return;
        List<Recipient> recipients = origin.getWorld().getNearbyPlayers(origin, config.radius).stream()
                .filter(p -> p.isOnline() && p.getWorld().equals(origin.getWorld()))
                .map(p -> new Recipient(p, p.getLocation().distance(origin)))
                .filter(p -> p.distance <= config.radius).toList();
        players = recipients.size();
        for (Recipient recipient : recipients) {
            amount = BigDecimal.valueOf(reward(config.min, config.max, config.radius, recipient.distance, players))
                    .setScale(2, RoundingMode.HALF_UP).toPlainString();
            distance = BigDecimal.valueOf(recipient.distance).setScale(1, RoundingMode.HALF_UP).toPlainString();
            emit(config.reward, origin, null, recipient.player);
            if (isRemoved()) return;
        }
    }

    static double reward(double min, double max, double radius, double distance, int players) {
        return (min + (max - min) * Math.max(0, 1 - distance / radius)) / players;
    }

    public record Config(FtSettings common, int interval, double radius, double min, double max,
                         EventHandler reward) implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = FtSettings.schema("FT: награда рядом", "Доля по расстоянию и числу игроков. Команда выплаты задаётся в on_reward. Собственный поиск получателей только при выплате; nearby_radius workflow влияет на видимость.")
                .integer("interval_ticks", "Период выплаты", 50, 1, false)
                .number("radius", "Радиус выплат", 20, .1, 128D)
                .number("min_reward", "На границе", 10000, 0, null)
                .number("max_reward", "В центре", 90000, 0, null)
                .placeholder("amount", "Сумма", "Доля текущего игрока, два десятичных знака")
                .placeholder("distance", "Расстояние", "Расстояние текущего игрока до центра")
                .placeholder("players", "Игроков", "Число получателей")
                .events("on_reward");
        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(Config::new,
                FtSettings.DECODER.fieldOf(), YamlDecoder.INT.fieldOf("interval_ticks", 50),
                YamlDecoder.DOUBLE.fieldOf("radius", 20D), YamlDecoder.DOUBLE.fieldOf("min_reward", 10000D),
                YamlDecoder.DOUBLE.fieldOf("max_reward", 90000D), HANDLER.fieldOf("on_reward", EMPTY_HANDLER));

        public Config {
            if (interval < 1 || interval > 1200000 || !Double.isFinite(radius) || radius <= 0 || radius > 128
                    || !Double.isFinite(min) || !Double.isFinite(max) || min < 0 || max < min)
                throw new IllegalArgumentException("Invalid proximity reward settings");
        }

        public AirdropComponent create(Airdrop air, String name) {
            return new ProximityRewardComponent(this, name, air);
        }

        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
