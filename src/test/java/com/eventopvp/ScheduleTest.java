package com.eventopvp;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Comprueba el cálculo del próximo inicio quincenal. */
class ScheduleTest {
    private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
    private static final LocalDate FIRST = LocalDate.of(2026, 10, 24);
    private static final LocalTime TIME = LocalTime.of(20, 0);

    private static ZonedDateTime t(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE);
    }

    @Test
    void antesDelPrimerEventoDevuelveElPrimero() {
        assertEquals(t(2026, 10, 24, 20, 0),
                EventManager.computeNextStart(t(2026, 10, 15, 10, 0), FIRST, TIME, 15));
    }

    @Test
    void elMismoDiaAntesDeLaHoraCuentaEseDia() {
        assertEquals(t(2026, 10, 24, 20, 0),
                EventManager.computeNextStart(t(2026, 10, 24, 19, 59), FIRST, TIME, 15));
    }

    @Test
    void dentroDelMinutoProgramadoSigueSiendoHoy() {
        assertEquals(t(2026, 10, 24, 20, 0),
                EventManager.computeNextStart(t(2026, 10, 24, 20, 0), FIRST, TIME, 15));
    }

    @Test
    void elDiaDelEventoPeroYaPasadaLaHoraSaltaAlSiguienteCiclo() {
        assertEquals(t(2026, 11, 8, 20, 0),
                EventManager.computeNextStart(t(2026, 10, 24, 20, 30), FIRST, TIME, 15));
    }

    @Test
    void saltarVariosCiclosUsaElUltimoYSumaElIntervalo() {
        // 24 oct + (38/15)*15 = 24 oct + 30 = 23 nov; ya pasada → +15 días = 8 dic
        assertEquals(t(2026, 12, 8, 20, 0),
                EventManager.computeNextStart(t(2026, 12, 1, 9, 0), FIRST, TIME, 15));
    }

    @Test
    void cadaDiaUnoSumaUnDia() {
        assertEquals(t(2026, 11, 8, 20, 0),
                EventManager.computeNextStart(t(2026, 11, 7, 21, 0), FIRST, TIME, 1));
    }

    @Test
    void intervaloInvalidoSeTrataComoUno() {
        assertEquals(t(2026, 10, 25, 20, 0),
                EventManager.computeNextStart(t(2026, 10, 24, 21, 0), FIRST, TIME, 0));
    }

    // ---- horario fijo: día por medio con una hora por día de la semana ----

    private static final LocalDate FIRST_MONDAY = LocalDate.of(2026, 10, 19);
    private static final Map<DayOfWeek, LocalTime> TIMES = Map.of(
            DayOfWeek.MONDAY, LocalTime.of(20, 21),
            DayOfWeek.TUESDAY, LocalTime.of(16, 30),
            DayOfWeek.WEDNESDAY, LocalTime.of(20, 21),
            DayOfWeek.THURSDAY, LocalTime.of(16, 30),
            DayOfWeek.FRIDAY, LocalTime.of(20, 21),
            DayOfWeek.SATURDAY, LocalTime.of(18, 40),
            DayOfWeek.SUNDAY, LocalTime.of(20, 21));

    private static ZonedDateTime diaPorMedio(ZonedDateTime now) {
        return EventManager.computeNextStart(now, FIRST_MONDAY, 2, TIMES, LocalTime.of(20, 0));
    }

    @Test
    void diaPorMedioUsaLaHoraDeCadaDia() {
        assertEquals(t(2026, 10, 19, 20, 21), diaPorMedio(t(2026, 10, 19, 10, 0))); // lunes
        assertEquals(t(2026, 10, 21, 20, 21), diaPorMedio(t(2026, 10, 19, 21, 0))); // -> miércoles
        assertEquals(t(2026, 10, 27, 16, 30), diaPorMedio(t(2026, 10, 25, 23, 0))); // domingo -> martes
        assertEquals(t(2026, 10, 31, 18, 40), diaPorMedio(t(2026, 10, 29, 23, 0))); // jueves -> sábado
    }

    @Test
    void todosLosDiasConIntervaloUno() {
        assertEquals(t(2026, 10, 20, 16, 30),
                EventManager.computeNextStart(t(2026, 10, 19, 21, 0), FIRST_MONDAY, 1, TIMES, LocalTime.of(20, 0)));
    }

    @Test
    void diaSinHoraPropiaUsaLaHoraDeRespaldo() {
        assertEquals(t(2026, 10, 19, 20, 0),
                EventManager.computeNextStart(t(2026, 10, 19, 10, 0), FIRST_MONDAY, 2, Map.of(), LocalTime.of(20, 0)));
    }

    @Test
    void parseaDiasEnInglesYEspanol() {
        assertEquals(DayOfWeek.MONDAY, EventManager.parseDay("lunes"));
        assertEquals(DayOfWeek.WEDNESDAY, EventManager.parseDay("miércoles"));
        assertEquals(DayOfWeek.SATURDAY, EventManager.parseDay("SATURDAY"));
        assertEquals(DayOfWeek.SUNDAY, EventManager.parseDay("dom"));
        assertNull(EventManager.parseDay("luness"));
    }
}
