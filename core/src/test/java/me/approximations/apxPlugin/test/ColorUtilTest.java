package me.approximations.apxPlugin.test;

import me.approximations.apxPlugin.utils.ColorUtil;
import org.junit.jupiter.api.Test;


public class ColorUtilTest {

    @Test
    public void testColored() {
        final String message = "#AA0000";

        final String colored = ColorUtil.colored(message);

        System.out.println(colored);
    }
}
