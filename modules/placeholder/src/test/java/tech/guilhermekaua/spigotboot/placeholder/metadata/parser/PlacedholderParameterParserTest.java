/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.placeholder.metadata.parser;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PlacedholderParameterParserTest {
    @Test
    public void testSimpleMultiplePlaceholders() {
        String placeholderPattern = "user_<id>_name_<name>";
        String actualValue = "user_123_name_JohnDoe";
        Map<String, String> params = PlaceholderParameterParser.parse(placeholderPattern, actualValue);
        assertEquals(2, params.size());
        assertEquals("123", params.get("id"));
        assertEquals("JohnDoe", params.get("name"));
    }

    @Test
    public void testOptionalPlaceholderPresent() {
        String placeholderPattern = "prefix_[optional]_suffix";
        String actualValue = "prefix_opt_suffix";
        Map<String, String> params = PlaceholderParameterParser.parse(placeholderPattern, actualValue);
        assertEquals(1, params.size());
        assertEquals("opt", params.get("optional"));
    }

    @Test
    public void testOptionalPlaceholderAbsent() {
        String placeholderPattern = "prefix_[optional]_suffix";
        String actualValue = "prefix__suffix";
        Map<String, String> params = PlaceholderParameterParser.parse(placeholderPattern, actualValue);
        assertEquals(1, params.size());
        assertNull(params.get("optional"));
    }

    @Test
    public void testNoMatch() {
        String placeholderPattern = "pattern_<value>";
        String actualValue = "another_string";
        assertFalse(PlaceholderParameterParser.isValidPlaceholderPattern(placeholderPattern, actualValue));
    }

    @Test
    public void testPlaceholderAtStartAndEnd() {
        String placeholderPattern = "<start>_middle_<end>";
        String actualValue = "begin_middle_finish";
        Map<String, String> params = PlaceholderParameterParser.parse(placeholderPattern, actualValue);
        assertEquals(2, params.size());
        assertEquals("begin", params.get("start"));
        assertEquals("finish", params.get("end"));
    }

    @Test
    public void testOptionalPlaceholderAtStartPresent() {
        String placeholderPattern = "[opt_start]_text";
        String actualValue = "start_text";
        Map<String, String> params = PlaceholderParameterParser.parse(placeholderPattern, actualValue);
        assertEquals(1, params.size());
        assertEquals("start", params.get("opt_start"));
    }

    @Test
    public void testOptionalPlaceholderAtStartAbsent() {
        String placeholderPattern = "[opt_start]_text";
        String actualValue = "_text";
        Map<String, String> params = PlaceholderParameterParser.parse(placeholderPattern, actualValue);
        assertEquals(1, params.size());
        assertNull(params.get("opt_start"));
    }

    @Test
    public void testOptionalPlaceholderAtEndPresent() {
        String placeholderPattern = "text_[opt_end]";
        String actualValue = "text_end";
        Map<String, String> params = PlaceholderParameterParser.parse(placeholderPattern, actualValue);
        assertEquals(1, params.size());
        assertEquals("end", params.get("opt_end"));
    }

    @Test
    public void testOptionalPlaceholderAtEndAbsent() {
        String placeholderPattern = "text_[opt_end]";
        String actualValue = "text_";
        Map<String, String> params = PlaceholderParameterParser.parse(placeholderPattern, actualValue);
        assertEquals(1, params.size());
        assertNull(params.get("opt_end"));
    }

    @Test
    public void testPatternWithNoPlaceholders() {
        String placeholderPattern = "just_literal_text";
        String actualValue = "just_literal_text";
        Map<String, String> params = PlaceholderParameterParser.parse(placeholderPattern, actualValue);
        assertTrue(params.isEmpty());
    }

    @Test
    public void testPatternWithNoPlaceholdersNoMatch() {
        String placeholderPattern = "just_literal_text";
        String actualValue = "different_text";
        assertFalse(PlaceholderParameterParser.isValidPlaceholderPattern(placeholderPattern, actualValue));
    }

    @Test
    public void testEmptyActualValue() {
        String placeholderPattern = "data_<value>";
        String actualValue = "";
        assertFalse(PlaceholderParameterParser.isValidPlaceholderPattern(placeholderPattern, actualValue));
    }

    @Test
    public void testEmptyPattern() {
        String placeholderPattern = "";
        String actualValue = "some_value";
        assertFalse(PlaceholderParameterParser.isValidPlaceholderPattern(placeholderPattern, actualValue));
    }

    @Test
    public void testEmptyPatternAndEmptyActualValue() {
        String placeholderPattern = "";
        String actualValue = "";
        Map<String, String> params = PlaceholderParameterParser.parse(placeholderPattern, actualValue);
        assertTrue(params.isEmpty());
    }

    @Test
    public void testMatches_SimpleNoMatch_ThrowsException() {
        assertFalse(PlaceholderParameterParser.isValidPlaceholderPattern("user_<id>", "admin_123"));
    }

    @Test
    public void testMatches_OptionalAbsentNoMatchStructure_ThrowsException() {
        assertFalse(PlaceholderParameterParser.isValidPlaceholderPattern("prefix_[opt]_suffix", "prefix_suffix"));
    }

    @Test
    public void testMatches_LiteralPatternNoMatch_ThrowsException() {
        assertFalse(PlaceholderParameterParser.isValidPlaceholderPattern("just_literal_text", "different_text"));
    }

    @Test
    public void testMatches_EmptyPatternNonEmptyValue_ThrowsException() {
        assertFalse(PlaceholderParameterParser.isValidPlaceholderPattern("", "some_value"));
    }

    @Test
    public void testMatches_PlaceholderAtStartAndEnd_NoMatch_ThrowsException() {
        assertFalse(PlaceholderParameterParser.isValidPlaceholderPattern("<start>_middle_<end>", "begin_finish"));
    }

    @Test
    public void testMatches_NonEmptyPatternEmptyValue_OptionalPlaceholderOnly_Parses() {
        Map<String, String> params = PlaceholderParameterParser.parse("[optional_val]", "");
        assertEquals(1, params.size());
        assertNull(params.get("optional_val"));
    }
}
