package dev.by1337.baf.command;

import dev.by1337.baf.Airdrop;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public record AirdropContext(Airdrop airdrop, @Nullable Player player) {

    public AirdropContext(Airdrop airdrop) {
        this(airdrop, null);
    }

}
