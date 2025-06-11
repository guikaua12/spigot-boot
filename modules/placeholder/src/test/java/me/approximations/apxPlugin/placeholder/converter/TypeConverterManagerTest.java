package me.approximations.apxPlugin.placeholder.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TypeConverterManagerTest {
    private TypeConverterManager manager;

    @BeforeEach
    void setUp() {
        manager = new TypeConverterManager();
    }

    @Test
    void testConvert_string() {
        assertEquals("abc", manager.convert(String.class, "abc"));
    }

    @Test
    void testConvert_integer() {
        assertEquals(123, manager.convert(Integer.class, "123"));
    }

    @Test
    void testConvert_boolean() {
        assertTrue(manager.convert(Boolean.class, "true"));
        assertFalse(manager.convert(Boolean.class, "false"));
    }

    @Test
    void testConvert_double() {
        assertEquals(1.23, manager.convert(Double.class, "1.23"));
    }

    @Test
    void testConvert_long() {
        assertEquals(123L, manager.convert(Long.class, "123"));
    }

    @Test
    void testConvert_float() {
        assertEquals(1.23f, manager.convert(Float.class, "1.23"));
    }

    @Test
    void testConvert_short() {
        assertEquals((short) 12, manager.convert(Short.class, "12"));
    }

    @Test
    void testConvert_byte() {
        assertEquals((byte) 7, manager.convert(Byte.class, "7"));
    }

    @Test
    void testConvert_character() {
        assertEquals('a', manager.convert(Character.class, "a"));
    }

    @Test
    void testConvert_stringArray() {
        assertArrayEquals(new String[]{"a", "b", "c"}, manager.convert(String[].class, "a,b,c"));
    }

    @Test
    void testConvert_integerArray() {
        assertArrayEquals(new Integer[]{1, 2, 3}, manager.convert(Integer[].class, "1,2,3"));
    }

    @Test
    void testConvert_booleanArray() {
        assertArrayEquals(new Boolean[]{true, false}, manager.convert(Boolean[].class, "true,false"));
    }

    @Test
    void testConvert_doubleArray() {
        assertArrayEquals(new Double[]{1.1, 2.2}, manager.convert(Double[].class, "1.1,2.2"));
    }

    @Test
    void testConvert_longArray() {
        assertArrayEquals(new Long[]{1L, 2L}, manager.convert(Long[].class, "1,2"));
    }

    @Test
    void testConvert_floatArray() {
        assertArrayEquals(new Float[]{1.1f, 2.2f}, manager.convert(Float[].class, "1.1,2.2"));
    }

    @Test
    void testConvert_shortArray() {
        assertArrayEquals(new Short[]{(short) 1, (short) 2}, manager.convert(Short[].class, "1,2"));
    }

    @Test
    void testConvert_byteArray() {
        assertArrayEquals(new Byte[]{(byte) 1, (byte) 2}, manager.convert(Byte[].class, "1,2"));
    }

    @Test
    void testConvert_characterArray() {
        assertArrayEquals(new Character[]{'a', 'b'}, manager.convert(Character[].class, "a,b"));
    }

    @Test
    void testConvert_invalidType_throws() {
        class Dummy {
        }
        Exception ex = assertThrows(IllegalArgumentException.class, () -> manager.convert(Dummy.class, "foo"));
        assertTrue(ex.getMessage().contains("No converter registered"));
    }

    @Test
    void testRegisterConverter_duplicate_throws() {
        Exception ex = assertThrows(IllegalArgumentException.class, () -> manager.registerConverter(String.class, DefaultTypeConverters.STRING));
        assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    void testRegisterConverter_andConvert_customType() {
        class Dummy {
        }
        manager.registerConverter(Dummy.class, value -> new Dummy());
        assertNotNull(manager.convert(Dummy.class, "any"));
    }
}
