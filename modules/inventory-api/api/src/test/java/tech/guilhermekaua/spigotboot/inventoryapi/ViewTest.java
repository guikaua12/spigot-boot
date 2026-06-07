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
package tech.guilhermekaua.spigotboot.inventoryapi;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.TokenTable;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;
import tech.guilhermekaua.spigotboot.inventoryapi.state.State;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewTest {

    private static final class BlankView extends View {
    }

    @Test
    void stateFactories_registerDistinctTokensWithSequentialIds() {
        BlankView view = new BlankView();
        assertEquals(0, view.tokenTable().size());
        assertFalse(view.tokenTable().isFrozen());

        MutableState<String> first = view.mutableState("a");
        assertEquals(1, view.tokenTable().size());
        State<Integer> second = view.lazyState(ctx -> 1);
        assertEquals(2, view.tokenTable().size());
        MutableState<Integer> third = view.initialState("count", Integer.class);
        assertEquals(3, view.tokenTable().size());
        SharedState<String> fourth = view.sharedState("shared");
        assertEquals(4, view.tokenTable().size());
        MutableState<List<String>> fifth = view.mutableState(ctx -> new ArrayList<>());
        assertEquals(5, view.tokenTable().size());

        List<StateToken> tokens = view.tokenTable().tokens();
        assertSame(first, tokens.get(0));
        assertSame(second, tokens.get(1));
        assertSame(third, tokens.get(2));
        assertSame(fourth, tokens.get(3));
        assertSame(fifth, tokens.get(4));
    }

    @Test
    void stateFactories_afterFreeze_throwIllegalStateException() {
        BlankView view = new BlankView();
        view.tokenTable().freeze();
        assertTrue(view.tokenTable().isFrozen());

        assertThrows(IllegalStateException.class, () -> view.mutableState("late"));
        assertThrows(IllegalStateException.class, () -> view.mutableState(ctx -> "late"));
        assertThrows(IllegalStateException.class, () -> view.lazyState(ctx -> "late"));
        assertThrows(IllegalStateException.class, () -> view.initialState("key", String.class));
        assertThrows(IllegalStateException.class, () -> view.sharedState("late"));
    }

    @Test
    void tokenTable_registerAfterFreeze_throwsIllegalStateException() {
        TokenTable table = new BlankView().tokenTable();
        table.freeze();

        assertThrows(IllegalStateException.class, () -> table.register(new StateToken() {
        }));
    }

    @Test
    void tokenTable_tokensListIsUnmodifiable() {
        BlankView view = new BlankView();
        view.mutableState("a");

        assertThrows(UnsupportedOperationException.class, () -> view.tokenTable().tokens().clear());
    }
}
