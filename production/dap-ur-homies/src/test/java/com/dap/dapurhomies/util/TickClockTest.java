package com.dap.dapurhomies.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TickClockTest {
    @Test
    void millisecondsRoundUpToWholeTicks() {
        assertAll(
                () -> assertEquals(0, TickClock.msToTicks(0)),
                () -> assertEquals(1, TickClock.msToTicks(1)),
                () -> assertEquals(1, TickClock.msToTicks(49)),
                () -> assertEquals(1, TickClock.msToTicks(50)),
                () -> assertEquals(2, TickClock.msToTicks(51)),
                () -> assertEquals(10, TickClock.msToTicks(500)),
                () -> assertEquals(60, TickClock.msToTicks(3000))
        );
    }
}
