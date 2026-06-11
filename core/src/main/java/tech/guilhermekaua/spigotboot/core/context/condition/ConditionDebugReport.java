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
package tech.guilhermekaua.spigotboot.core.context.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Collects and reports condition evaluation results for debugging purposes.
 * Records both matched and skipped conditions with their source and reason.
 */
public class ConditionDebugReport {
    private final List<ConditionEvaluation> evaluations = new ArrayList<>();

    public void record(String source, String elementName, boolean matched, String reason) {
        evaluations.add(new ConditionEvaluation(source, elementName, matched, reason));
    }

    public void print(Logger logger) {
        if (evaluations.isEmpty()) {
            logger.info("=== Condition Evaluation Report ===");
            logger.info("No conditions evaluated");
            return;
        }

        logger.info("=== Condition Evaluation Report ===");

        List<ConditionEvaluation> matched = evaluations.stream()
                .filter(e -> e.matched)
                .collect(Collectors.toList());

        logger.info("Matched Conditions:");
        if (matched.isEmpty()) {
            logger.info("  (none)");
        } else {
            for (ConditionEvaluation eval : matched) {
                logger.info("  [" + eval.source + "] " + eval.elementName);
            }
        }

        List<ConditionEvaluation> skipped = evaluations.stream()
                .filter(e -> !e.matched)
                .collect(Collectors.toList());

        logger.info("Skipped Conditions:");
        if (skipped.isEmpty()) {
            logger.info("  (none)");
        } else {
            for (ConditionEvaluation eval : skipped) {
                logger.info("  [" + eval.source + "] " + eval.elementName + " - " + eval.reason);
            }
        }
    }

    public List<String> getMatchedSummary() {
        return evaluations.stream()
                .filter(e -> e.matched)
                .map(e -> "[" + e.source + "] " + e.elementName)
                .collect(Collectors.toList());
    }

    public List<String> getSkippedSummary() {
        return evaluations.stream()
                .filter(e -> !e.matched)
                .map(e -> "[" + e.source + "] " + e.elementName + " - " + e.reason)
                .collect(Collectors.toList());
    }

    public boolean isEmpty() {
        return evaluations.isEmpty();
    }

    private static class ConditionEvaluation {
        final String source;
        final String elementName;
        final boolean matched;
        final String reason;

        ConditionEvaluation(String source, String elementName, boolean matched, String reason) {
            this.source = source;
            this.elementName = elementName;
            this.matched = matched;
            this.reason = reason;
        }
    }
}
