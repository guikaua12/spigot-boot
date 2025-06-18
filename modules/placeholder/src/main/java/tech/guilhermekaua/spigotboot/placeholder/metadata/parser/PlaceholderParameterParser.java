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

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.placeholder.exceptions.PatternMismatchException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceholderParameterParser {
    public static @NotNull Map<String, String> parse(@NotNull String placeholderPattern, @NotNull String actualValue) {
        Map<String, String> paramsMap = new HashMap<>();
        List<String> paramNames = new ArrayList<>();
        Set<String> optionalParamNames = new HashSet<>();

        Pattern simpleOptionalPattern = Pattern.compile("\\[([^\\[\\]<>]+)\\]");
        StringBuffer sb = new StringBuffer();
        Matcher simpleOptionalMatcher = simpleOptionalPattern.matcher(placeholderPattern);
        while (simpleOptionalMatcher.find()) {
            simpleOptionalMatcher.appendReplacement(sb, Matcher.quoteReplacement("[<" + simpleOptionalMatcher.group(1) + ">]"));
        }
        simpleOptionalMatcher.appendTail(sb);
        String processedPattern = sb.toString();

        Pattern paramFinderPattern = Pattern.compile("<(.*?)>");
        Matcher paramNameMatcherForExtraction = paramFinderPattern.matcher(processedPattern);

        while (paramNameMatcherForExtraction.find()) {
            paramNames.add(paramNameMatcherForExtraction.group(1));
        }

        StringBuilder regexBuilder = new StringBuilder("^");
        int lastPatternIndex = 0;

        Matcher paramNameMatcherForRegex = paramFinderPattern.matcher(processedPattern);

        while (paramNameMatcherForRegex.find()) {
            String currentParamName = paramNameMatcherForRegex.group(1);

            int placeholderTagStartIndex = paramNameMatcherForRegex.start();
            int placeholderTagEndIndex = paramNameMatcherForRegex.end();

            String textBeforePlaceholderTag = processedPattern.substring(lastPatternIndex, placeholderTagStartIndex);
            int openBracketInTextBefore = textBeforePlaceholderTag.lastIndexOf('[');

            if (openBracketInTextBefore != -1) {
                int closingBracketIndexInPattern = -1;
                int searchPosForClosingBracket = placeholderTagEndIndex;

                while (searchPosForClosingBracket < processedPattern.length()) {
                    if (processedPattern.charAt(searchPosForClosingBracket) == ']') {
                        closingBracketIndexInPattern = searchPosForClosingBracket;
                        break;
                    }
                    if (processedPattern.charAt(searchPosForClosingBracket) == '[') {
                        break; // Original break, reconsider if it breaks valid nested cases.
                    }
                    searchPosForClosingBracket++;
                }


                if (closingBracketIndexInPattern != -1) {
                    String fixedPrefixPart = textBeforePlaceholderTag.substring(0, openBracketInTextBefore);
                    String optionalPrefixLiteral = textBeforePlaceholderTag.substring(openBracketInTextBefore + 1);
                    String optionalSuffixLiteral = processedPattern.substring(placeholderTagEndIndex, closingBracketIndexInPattern);

                    if (!fixedPrefixPart.isEmpty()) {
                        regexBuilder.append(Pattern.quote(fixedPrefixPart));
                    }

                    optionalParamNames.add(currentParamName);

                    regexBuilder.append("(?:");
                    if (!optionalPrefixLiteral.isEmpty()) {
                        regexBuilder.append(Pattern.quote(optionalPrefixLiteral));
                    }
                    regexBuilder.append("(.*?)");
                    if (!optionalSuffixLiteral.isEmpty()) {
                        regexBuilder.append(Pattern.quote(optionalSuffixLiteral));
                    }
                    regexBuilder.append(")?");

                    lastPatternIndex = closingBracketIndexInPattern + 1;
                } else {
                    regexBuilder.append(Pattern.quote(textBeforePlaceholderTag));
                    regexBuilder.append("(.*?)");
                    lastPatternIndex = placeholderTagEndIndex;
                }
            } else {
                if (!textBeforePlaceholderTag.isEmpty()) {
                    regexBuilder.append(Pattern.quote(textBeforePlaceholderTag));
                }
                regexBuilder.append("(.*?)");
                lastPatternIndex = placeholderTagEndIndex;
            }
        }

        if (lastPatternIndex < processedPattern.length()) {
            regexBuilder.append(Pattern.quote(processedPattern.substring(lastPatternIndex)));
        }
        regexBuilder.append("$");

        Pattern compiledPattern;
        try {
            compiledPattern = Pattern.compile(regexBuilder.toString());
        } catch (java.util.regex.PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid placeholder pattern resulting in regex error: " + regexBuilder.toString(), e);
        }
        Matcher valueMatcher = compiledPattern.matcher(actualValue);

        if (!valueMatcher.matches()) {
            throw new PatternMismatchException("Actual value '" + actualValue + "' does not match pattern '" + placeholderPattern + "' (regex: '" + regexBuilder.toString() + "')");
        }

        if (valueMatcher.groupCount() != paramNames.size()) {
            throw new PatternMismatchException(
                    "Regex group count (" + valueMatcher.groupCount() + ") " +
                            "does not match expected parameter count (" + paramNames.size() + ") " +
                            "for pattern '" + placeholderPattern + "' and value '" + actualValue + "'. Regex: '" + regexBuilder.toString() + "'"
            );
        }

        for (int i = 0; i < paramNames.size(); i++) {
            String paramName = paramNames.get(i);
            String value = valueMatcher.group(i + 1);
            if (optionalParamNames.contains(paramName) && "".equals(value)) {
                paramsMap.put(paramName, null);
            } else {
                paramsMap.put(paramName, value);
            }
        }
        return paramsMap;
    }

    public static boolean isValidPlaceholderPattern(@NotNull String placeholderPattern, @NotNull String actualValue) {
        try {
            parse(placeholderPattern, actualValue);
            return true;
        } catch (PatternMismatchException | IllegalArgumentException e) {
            return false;
        }
    }
}
