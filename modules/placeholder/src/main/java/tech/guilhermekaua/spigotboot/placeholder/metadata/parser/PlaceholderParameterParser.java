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

    private static final Pattern SIMPLE_OPTIONAL_PATTERN = Pattern.compile("\\[([^\\[\\]<>]+)\\]");
    private static final Pattern PARAM_FINDER_PATTERN = Pattern.compile("<(.*?)>");

    private PlaceholderParameterParser() {
    }

    public static @NotNull Map<String, String> parse(@NotNull String placeholderPattern, @NotNull String actualValue) {
        Objects.requireNonNull(placeholderPattern, "placeholderPattern cannot be null");
        Objects.requireNonNull(actualValue, "actualValue cannot be null");

        String processedPattern = preprocessOptionalParams(placeholderPattern);
        List<String> paramNames = extractParamNames(processedPattern);
        Set<String> optionalParamNames = new HashSet<>();

        String regex = buildRegex(processedPattern, optionalParamNames);
        Pattern compiledPattern = compilePattern(regex);

        return matchAndExtract(compiledPattern, actualValue, paramNames, optionalParamNames, placeholderPattern, regex);
    }

    private static @NotNull String preprocessOptionalParams(@NotNull String placeholderPattern) {
        StringBuffer sb = new StringBuffer();
        Matcher simpleOptionalMatcher = SIMPLE_OPTIONAL_PATTERN.matcher(placeholderPattern);
        while (simpleOptionalMatcher.find()) {
            simpleOptionalMatcher.appendReplacement(sb, Matcher.quoteReplacement("[<" + simpleOptionalMatcher.group(1) + ">]"));
        }
        simpleOptionalMatcher.appendTail(sb);
        return sb.toString();
    }

    private static @NotNull List<String> extractParamNames(@NotNull String processedPattern) {
        List<String> paramNames = new ArrayList<>();
        Matcher paramNameMatcher = PARAM_FINDER_PATTERN.matcher(processedPattern);
        while (paramNameMatcher.find()) {
            paramNames.add(paramNameMatcher.group(1));
        }
        return paramNames;
    }

    private static @NotNull String buildRegex(@NotNull String processedPattern, @NotNull Set<String> optionalParamNames) {
        StringBuilder regexBuilder = new StringBuilder("^");
        int lastPatternIndex = 0;

        Matcher paramNameMatcher = PARAM_FINDER_PATTERN.matcher(processedPattern);

        while (paramNameMatcher.find()) {
            String currentParamName = paramNameMatcher.group(1);
            int placeholderTagStartIndex = paramNameMatcher.start();
            int placeholderTagEndIndex = paramNameMatcher.end();

            String textBeforePlaceholderTag = processedPattern.substring(lastPatternIndex, placeholderTagStartIndex);
            int openBracketInTextBefore = textBeforePlaceholderTag.lastIndexOf('[');

            if (openBracketInTextBefore != -1) {
                lastPatternIndex = processOptionalParam(
                        regexBuilder, processedPattern, textBeforePlaceholderTag,
                        openBracketInTextBefore, placeholderTagEndIndex, currentParamName, optionalParamNames
                );
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

        return regexBuilder.toString();
    }

    private static int processOptionalParam(
            @NotNull StringBuilder regexBuilder,
            @NotNull String processedPattern,
            @NotNull String textBeforePlaceholderTag,
            int openBracketInTextBefore,
            int placeholderTagEndIndex,
            @NotNull String currentParamName,
            @NotNull Set<String> optionalParamNames
    ) {
        int closingBracketIndexInPattern = findClosingBracket(processedPattern, placeholderTagEndIndex);

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

            return closingBracketIndexInPattern + 1;
        } else {
            regexBuilder.append(Pattern.quote(textBeforePlaceholderTag));
            regexBuilder.append("(.*?)");
            return placeholderTagEndIndex;
        }
    }

    private static int findClosingBracket(@NotNull String processedPattern, int startIndex) {
        int searchPos = startIndex;
        while (searchPos < processedPattern.length()) {
            char c = processedPattern.charAt(searchPos);
            if (c == ']') {
                return searchPos;
            }
            if (c == '[') {
                break;
            }
            searchPos++;
        }
        return -1;
    }

    private static @NotNull Pattern compilePattern(@NotNull String regex) {
        try {
            return Pattern.compile(regex);
        } catch (java.util.regex.PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid placeholder pattern resulting in regex error: " + regex, e);
        }
    }

    private static @NotNull Map<String, String> matchAndExtract(
            @NotNull Pattern compiledPattern,
            @NotNull String actualValue,
            @NotNull List<String> paramNames,
            @NotNull Set<String> optionalParamNames,
            @NotNull String placeholderPattern,
            @NotNull String regex
    ) {
        Matcher valueMatcher = compiledPattern.matcher(actualValue);

        if (!valueMatcher.matches()) {
            throw new PatternMismatchException(
                    "Actual value '" + actualValue + "' does not match pattern '" + placeholderPattern + "' (regex: '" + regex + "')"
            );
        }

        if (valueMatcher.groupCount() != paramNames.size()) {
            throw new PatternMismatchException(
                    "Regex group count (" + valueMatcher.groupCount() + ") " +
                            "does not match expected parameter count (" + paramNames.size() + ") " +
                            "for pattern '" + placeholderPattern + "' and value '" + actualValue + "'. Regex: '" + regex + "'"
            );
        }

        Map<String, String> paramsMap = new HashMap<>();
        for (int i = 0; i < paramNames.size(); i++) {
            String paramName = paramNames.get(i);
            String value = valueMatcher.group(i + 1);
            if (optionalParamNames.contains(paramName) && (value == null || value.isEmpty())) {
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
