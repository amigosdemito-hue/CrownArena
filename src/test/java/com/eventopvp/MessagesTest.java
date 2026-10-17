package com.eventopvp;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida messages.yml: que el YAML sea correcto y que cada mensaje sea
 * MiniMessage válido y solo use placeholders conocidos (modo estricto).
 */
class MessagesTest {

    /** Todos los placeholders que el plugin inyecta en algún mensaje. */
    private static final String[] PLACEHOLDERS = {
            "seconds", "minutes", "players", "arena", "lobby", "n", "state", "time",
            "date", "left", "stat", "value", "pos", "player", "kills", "deaths",
            "totems", "streak", "best", "events", "wins", "killer", "victim",
            "weapon", "attacker", "wave", "kills_name", "deaths_name"
    };

    @Test
    void todosLosMensajesSonMiniMessageValido() {
        InputStream in = getClass().getResourceAsStream("/messages.yml");
        assertNotNull(in, "messages.yml no está en el jar");

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));

        assertTrue(yaml.getInt("messages-version") >= 1, "falta messages-version");

        // En modo estricto, cualquier etiqueta desconocida (typo o placeholder
        // que el código no rellena) provoca un error -> falla el test.
        MiniMessage mm = MiniMessage.builder().strict(true).build();
        TagResolver all = TagResolver.resolver(Arrays.stream(PLACEHOLDERS)
                .map(n -> Placeholder.unparsed(n, "X"))
                .toArray(TagResolver[]::new));

        int checked = 0;
        for (String key : yaml.getKeys(false)) {
            if (key.equals("messages-version")) continue;
            String value = yaml.getString(key);
            assertNotNull(value, "mensaje nulo: " + key);
            assertFalse(value.isBlank(), "mensaje vacío: " + key);
            assertDoesNotThrow(() -> mm.deserialize(value, all), "MiniMessage inválido en: " + key);
            checked++;
        }
        assertTrue(checked > 80, "se esperaban más mensajes, encontrados: " + checked);
    }
}
