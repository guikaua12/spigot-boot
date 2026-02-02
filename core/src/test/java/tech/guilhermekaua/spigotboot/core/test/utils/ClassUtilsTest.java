package tech.guilhermekaua.spigotboot.core.test.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.utils.ClassUtils;

import static org.junit.jupiter.api.Assertions.*;

public class ClassUtilsTest {

    @BeforeEach
    void setUp() {
        ClassUtils.clearCache();
    }

    @Test
    void isPresent_returnsTrue_whenClassExists() {
        ClassLoader classLoader = getClass().getClassLoader();
        boolean result = ClassUtils.isPresent("java.lang.String", classLoader);
        assertTrue(result);
    }

    @Test
    void isPresent_returnsFalse_whenClassDoesNotExist() {
        ClassLoader classLoader = getClass().getClassLoader();
        boolean result = ClassUtils.isPresent("com.nonexistent.FakeClass", classLoader);
        assertFalse(result);
    }

    @Test
    void isPresent_cachesResult_sameClassLoaderSameClass() {
        CountingClassLoader countingClassLoader = new CountingClassLoader(getClass().getClassLoader());

        boolean result1 = ClassUtils.isPresent("java.lang.String", countingClassLoader);
        assertTrue(result1);
        assertEquals(1, countingClassLoader.getLoadCount());

        boolean result2 = ClassUtils.isPresent("java.lang.String", countingClassLoader);
        assertTrue(result2);
        assertEquals(1, countingClassLoader.getLoadCount());
    }

    @Test
    void isPresent_separatesCacheByClassLoader() {
        CountingClassLoader classLoader1 = new CountingClassLoader(getClass().getClassLoader());
        CountingClassLoader classLoader2 = new CountingClassLoader(getClass().getClassLoader());

        boolean result1 = ClassUtils.isPresent("java.lang.String", classLoader1);
        assertTrue(result1);
        assertEquals(1, classLoader1.getLoadCount());

        boolean result2 = ClassUtils.isPresent("java.lang.String", classLoader2);
        assertTrue(result2);
        assertEquals(1, classLoader2.getLoadCount());
    }

    @Test
    void clearCache_emptiesAllEntries() {
        CountingClassLoader countingClassLoader = new CountingClassLoader(getClass().getClassLoader());

        boolean result1 = ClassUtils.isPresent("java.lang.String", countingClassLoader);
        assertTrue(result1);
        assertEquals(1, countingClassLoader.getLoadCount());

        ClassUtils.clearCache();

        boolean result2 = ClassUtils.isPresent("java.lang.String", countingClassLoader);
        assertTrue(result2);
        assertEquals(2, countingClassLoader.getLoadCount());
    }

    @Test
    void isPresent_throwsNullPointerException_whenClassNameIsNull() {
        ClassLoader classLoader = getClass().getClassLoader();
        assertThrows(NullPointerException.class, () -> ClassUtils.isPresent(null, classLoader));
    }

    @Test
    void isPresent_throwsNullPointerException_whenClassLoaderIsNull() {
        assertThrows(NullPointerException.class, () -> ClassUtils.isPresent("java.lang.String", null));
    }

    private static class CountingClassLoader extends ClassLoader {
        private int loadCount = 0;

        public CountingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadCount++;
            return super.loadClass(name);
        }

        public int getLoadCount() {
            return loadCount;
        }
    }
}
