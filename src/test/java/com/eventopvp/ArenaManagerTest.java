package com.eventopvp;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Comprueba el guardado/lectura de coordenadas de spawn en arenas.yml. */
class ArenaManagerTest {

    @Test
    void fmtYParseConservanLasCoordenadas() {
        Location original = new Location(null, 12.34, 65.5, -7.25, 90f, -12.5f);
        Location back = ArenaManager.parse(null, ArenaManager.fmt(original));
        assertNotNull(back);
        assertEquals(12.34, back.getX(), 0.001);
        assertEquals(65.5, back.getY(), 0.001);
        assertEquals(-7.25, back.getZ(), 0.001);
        assertEquals(90f, back.getYaw(), 0.06);
        assertEquals(-12.5f, back.getPitch(), 0.06);
    }

    @Test
    void parseDevuelveNullSiElTextoNoEsValido() {
        assertNull(ArenaManager.parse(null, "esto no es una ubicacion"));
        assertNull(ArenaManager.parse(null, "1,2"));
        assertNull(ArenaManager.parse(null, ""));
    }
}
