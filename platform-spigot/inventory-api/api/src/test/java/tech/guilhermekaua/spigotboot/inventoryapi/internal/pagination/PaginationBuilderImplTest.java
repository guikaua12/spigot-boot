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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.IdentifiableToken;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationItemRenderer;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationBuilderImplTest {

    private static final class TestView extends View {
    }

    private static <T> PaginationItemRenderer<T> renderer() {
        return (context, item, index, value) -> {
        };
    }

    private static PaginationBuilderImpl<String> eagerBuilder(View owner) {
        return new PaginationBuilderImpl<>(owner, owner.tokenTable(),
                PaginationSourceSpec.eager(Arrays.asList("a", "b", "c")));
    }

    private static PaginationBuilderImpl<String> asyncBuilder(View owner) {
        PaginationSourceSpec<String> source = PaginationSourceSpec.async(request ->
                CompletableFuture.completedFuture(PageResult.of(Collections.<String>emptyList(), 0)));
        return new PaginationBuilderImpl<>(owner, owner.tokenTable(), source);
    }

    private static Layout nonEmptyLayout() {
        return Layout.ofSlots(0, 1, 2);
    }

    private static Layout emptyLayout() {
        return Layout.ofGrid("         ");
    }

    // --- rule 12 clause 1: renderer required, validated first ---

    @Test
    void build_withoutRenderer_throwsViewConfigurationException() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("itemRenderer"),
                "message must name the missing itemRenderer, got: " + thrown.getMessage());
    }

    @Test
    void build_missingRenderer_isReportedBeforePatternConflicts() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.scroll().patterns(nonEmptyLayout());

        // validation order is pinned: the missing renderer wins over the patterns conflict
        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("itemRenderer"));
    }

    // --- rule 12 clause 2: patterns conflicts ---

    @Test
    void build_patternsCombinedWithExplicitLayoutChar_throws() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).layoutChar('P').patterns(nonEmptyLayout());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("patterns"));
    }

    @Test
    void patternsConflict_evenWhenLayoutCharExplicitlySetToTheDefaultValue() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).layoutChar('O').patterns(nonEmptyLayout());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("patterns"));
        // the conflict keys on the explicit CALL, not the value: 'O' is the default yet still conflicts
    }

    @Test
    void build_patternsCombinedWithLayout_throws() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).layout(nonEmptyLayout()).patterns(nonEmptyLayout());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("patterns"));
    }

    @Test
    void build_patternsCombinedWithScroll_throws() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).scroll().patterns(nonEmptyLayout());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("patterns"));
    }

    // --- rule 12 clause 3: empty geometry inputs ---

    @Test
    void build_emptyPatternsArray_throws() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).patterns();

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("at least one pattern"));
    }

    @Test
    void build_patternWithEmptyLayout_throws() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).patterns(nonEmptyLayout(), emptyLayout());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("at least one slot"));
    }

    @Test
    void build_emptyExplicitLayout_throws() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).layout(emptyLayout());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("at least one slot"));
    }

    // --- rule 12 clause 4: async-only options on a non-async source ---

    @Test
    void build_asyncOnlyOptions_onEagerSource_throwNamingTheOption() {
        // the option functions are never evaluated at build time, so returning null is safe
        assertAsyncOnlyRejected(builder -> builder.loadingItem(context -> null), "loadingItem");
        assertAsyncOnlyRejected(builder -> builder.onError((request, error) -> {
        }), "onError");
        assertAsyncOnlyRejected(builder -> builder.requestTimeout(Duration.ofSeconds(1)), "requestTimeout");
        assertAsyncOnlyRejected(builder -> builder.cacheTtl(Duration.ofSeconds(1)), "cacheTtl");
        assertAsyncOnlyRejected(builder -> builder.cacheMaxPages(4), "cacheMaxPages");
    }

    private static void assertAsyncOnlyRejected(Consumer<PaginationBuilder<String>> option, String optionName) {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer());
        option.accept(builder);

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains(optionName),
                "expected a message naming " + optionName + ", got: " + thrown.getMessage());
    }

    // --- rule 12 clause 5: cacheMaxPages requires cacheTtl ---

    @Test
    void build_cacheMaxPagesWithoutCacheTtl_onAsyncSource_throws() {
        PaginationBuilderImpl<String> builder = asyncBuilder(new TestView());
        builder.itemRenderer(renderer()).cacheMaxPages(16);

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("cacheTtl"));
    }

    @Test
    void build_cacheMaxPagesWithCacheTtl_onAsyncSource_succeeds() {
        PaginationBuilderImpl<String> builder = asyncBuilder(new TestView());
        Pagination<String> token = builder.itemRenderer(renderer())
                .cacheTtl(Duration.ofSeconds(30)).cacheMaxPages(16).build();

        assertEquals(16, ((PaginationImpl<String>) token).spec().cacheMaxPages());
    }

    // --- rule 12 setter-time value errors ---

    @Test
    void requestTimeout_nonPositive_throwsAtSetterTime() {
        PaginationBuilderImpl<String> builder = asyncBuilder(new TestView());

        assertThrows(IllegalArgumentException.class, () -> builder.requestTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> builder.requestTimeout(Duration.ofSeconds(-1)));
    }

    @Test
    void cacheTtl_nonPositive_throwsAtSetterTime() {
        PaginationBuilderImpl<String> builder = asyncBuilder(new TestView());

        assertThrows(IllegalArgumentException.class, () -> builder.cacheTtl(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> builder.cacheTtl(Duration.ofMillis(-5)));
    }

    @Test
    void cacheMaxPages_belowOne_throwsAtSetterTime() {
        PaginationBuilderImpl<String> builder = asyncBuilder(new TestView());

        assertThrows(IllegalArgumentException.class, () -> builder.cacheMaxPages(0));
        assertThrows(IllegalArgumentException.class, () -> builder.cacheMaxPages(-7));
    }

    @Test
    void nullArguments_throwNullPointerExceptionAtSetterTime() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());

        assertThrows(NullPointerException.class, () -> builder.layout(null));
        assertThrows(NullPointerException.class, () -> builder.patterns((Layout[]) null));
        assertThrows(NullPointerException.class, () -> builder.patterns(nonEmptyLayout(), null));
        assertThrows(NullPointerException.class, () -> builder.itemRenderer(null));
        assertThrows(NullPointerException.class, () -> builder.fallbackItem(null));
        assertThrows(NullPointerException.class, () -> builder.loadingItem(null));
        assertThrows(NullPointerException.class, () -> builder.onError(null));
        assertThrows(NullPointerException.class, () -> builder.requestTimeout(null));
        assertThrows(NullPointerException.class, () -> builder.cacheTtl(null));
    }

    // --- defaults and geometry/target resolution ---

    @Test
    void defaults_normalGeometryLayoutCharOAndCacheMaxPages128_reachTheSpec() {
        PaginationImpl<String> token = (PaginationImpl<String>) eagerBuilder(new TestView())
                .itemRenderer(renderer()).build();
        PaginationSpec<String> spec = token.spec();

        assertEquals(PaginationSpec.Geometry.NORMAL, spec.geometry());
        assertEquals(PaginationSpec.Target.LAYOUT_CHAR, spec.target());
        assertEquals('O', spec.layoutChar());
        assertNull(spec.explicitLayout());
        assertTrue(spec.patterns().isEmpty());
        assertEquals(128, spec.cacheMaxPages());
    }

    @Test
    void layout_silentlyOverridesLayoutChar() {
        Layout explicit = nonEmptyLayout();
        PaginationImpl<String> token = (PaginationImpl<String>) eagerBuilder(new TestView())
                .layoutChar('X').layout(explicit).itemRenderer(renderer()).build();

        assertEquals(PaginationSpec.Target.EXPLICIT_LAYOUT, token.spec().target());
        assertSame(explicit, token.spec().explicitLayout());
    }

    @Test
    void scroll_resolvesScrollGeometryOnLayoutCharTarget() {
        PaginationImpl<String> token = (PaginationImpl<String>) eagerBuilder(new TestView())
                .scroll().itemRenderer(renderer()).build();

        assertEquals(PaginationSpec.Geometry.SCROLL, token.spec().geometry());
        assertEquals(PaginationSpec.Target.LAYOUT_CHAR, token.spec().target());
    }

    @Test
    void patterns_resolvePatternGeometryAndPatternsTarget() {
        Layout first = nonEmptyLayout();
        Layout second = Layout.ofSlots(9, 10);
        PaginationImpl<String> token = (PaginationImpl<String>) eagerBuilder(new TestView())
                .patterns(first, second).itemRenderer(renderer()).build();

        assertEquals(PaginationSpec.Geometry.PATTERN, token.spec().geometry());
        assertEquals(PaginationSpec.Target.PATTERNS, token.spec().target());
        assertEquals(Arrays.asList(first, second), token.spec().patterns());
    }

    // --- registration timing ---

    @Test
    void build_registersTheTokenInTheTable_constructionDoesNot() {
        TestView view = new TestView();
        PaginationBuilderImpl<String> builder = eagerBuilder(view);
        builder.itemRenderer(renderer());
        assertEquals(0, view.tokenTable().size());

        Pagination<String> token = builder.build();

        assertEquals(1, view.tokenTable().size());
        assertSame(token, view.tokenTable().tokens().get(0));
        assertEquals(0, ((IdentifiableToken) token).tokenId());
    }

    @Test
    void secondBuild_throwsIllegalStateException() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, builder::build);
        assertEquals("build() may only be called once per paginate* call", thrown.getMessage());
    }

    @Test
    void buildAfterFreeze_throwsIllegalStateExceptionFromTokenTable() {
        TestView view = new TestView();
        PaginationBuilderImpl<String> builder = eagerBuilder(view);
        builder.itemRenderer(renderer());

        view.tokenTable().freeze();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(thrown.getMessage().contains("frozen"));
        assertEquals(0, view.tokenTable().size());
    }

    // --- emptyStateItem and slotted loadingItem ---

    @Test
    void emptyStateItem_storesFunctionAndDeduplicatedSlots() {
        Function<ViewContext, ItemStack> fn = ctx -> new ItemStack(Material.BARRIER);
        PaginationImpl<String> token = (PaginationImpl<String>) eagerBuilder(new TestView())
                .itemRenderer(renderer()).emptyStateItem(fn, 22, 22, 4).build();

        assertSame(fn, token.spec().emptyStateItem());
        assertArrayEquals(new int[]{22, 4}, token.spec().emptyStateSlots());
    }

    @Test
    void emptyStateItem_nullFunction_throwsNpe() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        assertThrows(NullPointerException.class, () -> builder.emptyStateItem(null, 1));
    }

    @Test
    void emptyStateItem_noSlots_throwsIllegalArgument() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        assertThrows(IllegalArgumentException.class,
                () -> builder.emptyStateItem(ctx -> new ItemStack(Material.BARRIER)));
    }

    @Test
    void emptyStateItem_negativeSlot_throwsIllegalArgument() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        assertThrows(IllegalArgumentException.class,
                () -> builder.emptyStateItem(ctx -> new ItemStack(Material.BARRIER), 0, -1));
    }

    @Test
    void loadingItem_withSlots_storesDeduplicatedSlots_onAsyncBuilder() {
        PaginationImpl<String> token = (PaginationImpl<String>) asyncBuilder(new TestView())
                .itemRenderer(renderer())
                .loadingItem(ctx -> new ItemStack(Material.EMERALD), 4, 4)
                .build();

        assertArrayEquals(new int[]{4}, token.spec().loadingSlots());
    }

    @Test
    void loadingItem_withSlots_onEagerSource_throwsAsyncOnly() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).loadingItem(ctx -> new ItemStack(Material.EMERALD), 4);

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("loadingItem"));
    }

    @Test
    void loadingItem_withoutSlots_resetsToFillAll() {
        PaginationImpl<String> token = (PaginationImpl<String>) asyncBuilder(new TestView())
                .itemRenderer(renderer())
                .loadingItem(ctx -> new ItemStack(Material.EMERALD), 4)
                .loadingItem(ctx -> new ItemStack(Material.EMERALD))
                .build();

        assertEquals(0, token.spec().loadingSlots().length);
    }

    @Test
    void defaults_noEmptyStateItemNorFrameSlots() {
        PaginationSpec<String> spec = ((PaginationImpl<String>) eagerBuilder(new TestView())
                .itemRenderer(renderer()).build()).spec();

        assertNull(spec.emptyStateItem());
        assertEquals(0, spec.emptyStateSlots().length);
        assertEquals(0, spec.loadingSlots().length);
    }
}
