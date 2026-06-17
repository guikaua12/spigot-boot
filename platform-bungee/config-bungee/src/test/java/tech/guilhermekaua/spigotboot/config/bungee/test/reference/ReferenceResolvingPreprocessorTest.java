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
package tech.guilhermekaua.spigotboot.config.bungee.test.reference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceErrorHandler;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceLookup;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceParser;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceResolver;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigTypeMismatchContext;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.config.bungee.node.YamlConfigNode;
import tech.guilhermekaua.spigotboot.config.bungee.reference.ReferenceResolvingPreprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReferenceResolvingPreprocessor")
class ReferenceResolvingPreprocessorTest {

    @Mock
    private ConfigReferenceLookup lookup;

    @Mock
    private ConfigReferenceErrorHandler errorHandler;

    @Test
    @DisplayName("passes the expected type to the resolver so onTypeMismatch fires")
    void firesTypeMismatchWhenResolvedValueIncompatibleWithExpectedType() {
        when(lookup.resolve(any())).thenReturn(new YamlConfigNode("not a number"));

        ConfigReferenceResolver resolver = new ConfigReferenceResolver(lookup, new ConfigReferenceParser(), errorHandler);
        ReferenceResolvingPreprocessor preprocessor = new ReferenceResolvingPreprocessor(resolver);
        preprocessor.setCurrentSourceKey(ReferenceKey.singleConfig("source"));

        try {
            ConfigNode input = new YamlConfigNode("${label}");
            preprocessor.preprocess(input, null, Integer.class);
        } finally {
            preprocessor.clearCurrentSourceKey();
        }

        ArgumentCaptor<ConfigTypeMismatchContext> captor = ArgumentCaptor.forClass(ConfigTypeMismatchContext.class);
        verify(errorHandler).onTypeMismatch(captor.capture());
        assertEquals("${label}", captor.getValue().getFullReference());
        assertEquals(Integer.class, captor.getValue().getExpectedType());
    }
}
