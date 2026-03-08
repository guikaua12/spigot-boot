package tech.guilhermekaua.spigotboot.core.test.cooldown;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.cooldown.CooldownState;
import tech.guilhermekaua.spigotboot.core.cooldown.DefaultCooldownManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCooldownManagerTest {
    @Test
    void missingKeyIsInactive() {
        DefaultCooldownManager manager = new DefaultCooldownManager(Clock.fixed(Instant.parse("2026-03-08T00:00:00Z"), ZoneId.of("UTC")));

        CooldownState state = manager.getState("missing");

        assertFalse(state.isActive());
        assertEquals(Duration.ZERO, state.getRemaining());
        assertNull(state.getExpiresAt());
    }

    @Test
    void positiveDurationStartsActiveCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DefaultCooldownManager manager = new DefaultCooldownManager(clock);

        CooldownState started = manager.start("kit:daily", Duration.ofSeconds(30));
        CooldownState current = manager.getState("kit:daily");

        assertTrue(started.isActive());
        assertTrue(current.isActive());
        assertEquals(Duration.ofSeconds(30), current.getRemaining());
        assertEquals(Instant.parse("2026-03-08T00:00:30Z"), current.getExpiresAt());
    }

    @Test
    void expiredCooldownIsRemovedWhenRead() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DefaultCooldownManager manager = new DefaultCooldownManager(clock);
        manager.start("kit:daily", Duration.ofSeconds(5));

        clock.advance(Duration.ofSeconds(6));
        CooldownState state = manager.getState("kit:daily");

        assertFalse(state.isActive());
        assertEquals(Duration.ZERO, state.getRemaining());
        assertNull(state.getExpiresAt());
    }

    @Test
    void restartingSameKeyOverwritesExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DefaultCooldownManager manager = new DefaultCooldownManager(clock);
        manager.start("kit:daily", Duration.ofSeconds(30));

        clock.advance(Duration.ofSeconds(10));
        manager.start("kit:daily", Duration.ofSeconds(5));
        CooldownState state = manager.getState("kit:daily");

        assertTrue(state.isActive());
        assertEquals(Duration.ofSeconds(5), state.getRemaining());
        assertEquals(Instant.parse("2026-03-08T00:00:15Z"), state.getExpiresAt());
    }

    @Test
    void clearRemovesSingleKey() {
        DefaultCooldownManager manager = new DefaultCooldownManager(Clock.fixed(Instant.parse("2026-03-08T00:00:00Z"), ZoneId.of("UTC")));
        manager.start("alpha", Duration.ofSeconds(10));
        manager.start("beta", Duration.ofSeconds(10));

        manager.clear("alpha");

        assertFalse(manager.getState("alpha").isActive());
        assertTrue(manager.getState("beta").isActive());
    }

    @Test
    void clearAllRemovesAllKeys() {
        DefaultCooldownManager manager = new DefaultCooldownManager(Clock.fixed(Instant.parse("2026-03-08T00:00:00Z"), ZoneId.of("UTC")));
        manager.start("alpha", Duration.ofSeconds(10));
        manager.start("beta", Duration.ofSeconds(10));

        manager.clearAll();

        assertFalse(manager.getState("alpha").isActive());
        assertFalse(manager.getState("beta").isActive());
    }

    @Test
    void zeroDurationDoesNotCreateActiveCooldown() {
        DefaultCooldownManager manager = new DefaultCooldownManager(Clock.fixed(Instant.parse("2026-03-08T00:00:00Z"), ZoneId.of("UTC")));

        CooldownState state = manager.start("alpha", Duration.ZERO);

        assertFalse(state.isActive());
        assertFalse(manager.getState("alpha").isActive());
    }

    @Test
    void negativeDurationIsRejected() {
        DefaultCooldownManager manager = new DefaultCooldownManager(Clock.fixed(Instant.parse("2026-03-08T00:00:00Z"), ZoneId.of("UTC")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> manager.start("alpha", Duration.ofSeconds(-1))
        );

        assertEquals("duration cannot be negative.", exception.getMessage());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
