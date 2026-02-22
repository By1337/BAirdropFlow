package dev.by1337.baf.command;

import dev.by1337.baf.Airdrop;
import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.plc.PlaceholderResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public record AirdropContext(Airdrop airdrop, @Nullable Player player) implements PlaceholderApplier {
    public AirdropContext(Airdrop airdrop) {
        this(airdrop, null);
    }

    @Override
    public String setPlaceholders(String s) {
        return Airdrop.setPlaceholders(s, this);
    }
}
