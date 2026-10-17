package com.eventopvp;

import java.util.UUID;

final class Stats {
    final UUID id;
    final String name;
    int kills, deaths, streak, bestStreak, totemsBroken, totemsLost;

    Stats(UUID id, String name) {
        this.id = id;
        this.name = name;
    }
}
