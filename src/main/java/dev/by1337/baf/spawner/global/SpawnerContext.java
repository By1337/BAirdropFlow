package dev.by1337.baf.spawner.global;

import dev.by1337.baf.launch.LaunchRegistry;
import dev.by1337.baf.vote.VotingService;

public record SpawnerContext(LaunchRegistry launches, VotingService voting) {}
