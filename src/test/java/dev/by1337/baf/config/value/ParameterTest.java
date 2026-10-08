package dev.by1337.baf.config.value;

import dev.by1337.baf.util.Vec3d;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.yaml.YamlValue;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ParameterTest {
    private static final PlaceholderApplier FORBIDDEN = value -> {
        throw new AssertionError("Literal consulted resolver");
    };

    @Test
    void literalsAreParsedOnceAndNeverConsultTheResolver() {
        var parses = new AtomicInteger();
        var value = new Parameter<>("12", source -> {
            parses.incrementAndGet();
            return Integer.parseInt(source);
        });
        assertEquals(1, parses.get());
        for (int i = 0; i < 20; i++) assertEquals(12, value.get(FORBIDDEN));
        assertEquals(1, parses.get());
        assertEquals(3, new IntValue("3").getInt(null));
        assertEquals(.25, new DoubleValue("0.25").getDouble(FORBIDDEN));
        assertTrue(new BooleanValue("TRUE").getBoolean(FORBIDDEN));
        assertEquals("50% complete {", new StringValue("50% complete {").getString(FORBIDDEN));
        assertEquals("{args:count}", new IntValue("{args:count}").source());
    }

    @Test
    void dynamicValuesResolveEachTimeAndNeverCacheAnotherAirdropsResult() {
        var value = new IntValue("{args:count}");
        assertEquals(2, value.getInt(ignored -> "2"));
        assertEquals(90, value.getInt(ignored -> "90"));
        assertEquals(3, value.getInt(ignored -> "3"));
        var papi = new IntValue("%some_amount%");
        assertEquals(7, papi.getInt(ignored -> "7"));
        assertFalse(papi.isConstant());
        assertFalse(value.isConstant());
    }

    @Test
    void vectorsCacheConstantsAndParseResolvedCoordinatesDirectly() {
        var constant = new Vec3iValue(" 1 ; -2 ; 3 ");
        assertSame(constant.getVector(FORBIDDEN), constant.getVector(FORBIDDEN));
        assertEquals(new Vec3i(1, -2, 3), constant.getVector(null));
        var vector = new Vec3dValue("{args:offset}");
        assertEquals(new Vec3d(.5, 1.8, -2), vector.getVector(ignored -> "0.5;1.8;-2"));
        assertEquals(new Vec3d(0, 2, 4), vector.getVector(ignored -> "0;2;4"));
        assertEquals(new Vec3i(2, 3, 4), new Vec3iValue("{x};{y};{z}").getVector(ignored -> "2;3;4"));
        for (String invalid : new String[]{"1;2", "1;2;3;4", "1;;3", "NaN;2;3", "Infinity;2;3"}) {
            assertThrows(IllegalArgumentException.class, () -> new Vec3dValue(invalid), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> new Vec3iValue("1.5;2;3"));
        assertThrows(IllegalArgumentException.class, () -> vector.getVector(ignored -> "1;2"));
    }

    @Test
    void limitsApplyBothToConstantsAndAfterSubstitution() {
        assertThrows(IllegalArgumentException.class, () -> new IntValue("-1").checked(v -> v > 0, "positive"));
        var value = new IntValue("{count}").checked(v -> v > 0, "positive").checked(v -> v <= 90, "maximum");
        assertEquals(90, value.getInt(ignored -> "90"));
        assertThrows(IllegalArgumentException.class, () -> value.getInt(ignored -> "-1"));
        var error = assertThrows(IllegalArgumentException.class, () -> value.getInt(ignored -> "100"));
        assertTrue(error.getMessage().contains("{count}"));
        assertTrue(error.getMessage().contains("100"));
        assertThrows(IllegalArgumentException.class, () -> new DoubleValue("NaN"));
        assertThrows(IllegalArgumentException.class, () -> new BooleanValue("yes"));
        assertThrows(IllegalArgumentException.class, () -> new BooleanValue("{flag}").getBoolean(ignored -> "maybe"));
    }

    @Test
    void decodersAcceptNativeYamlScalarsAndDeferredStrings() {
        assertEquals(20, IntValue.DECODER.decode(YamlValue.wrap(20)).getOrThrow().getInt(FORBIDDEN));
        assertFalse(BooleanValue.DECODER.decode(YamlValue.wrap(false)).getOrThrow().getBoolean(FORBIDDEN));
        var value = IntValue.DECODER.decode(YamlValue.wrap("{args:count}")).getOrThrow();
        assertEquals(9, value.getInt(ignored -> "9"));
        assertTrue(IntValue.DECODER.decode(YamlValue.wrap("invalid")).hasError());
    }
}
