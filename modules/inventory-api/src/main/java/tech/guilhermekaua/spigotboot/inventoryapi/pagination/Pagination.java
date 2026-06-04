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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination;

import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.List;

public interface Pagination<T> {
    /**
     * Initializes the paginator against a freshly opened viewer. Must be called once before any
     * navigation method is invoked.
     */
    void init(Viewer viewer);

    /**
     * Renders the items of the current page into the viewer's inventory.
     */
    void apply();

    /**
     * Advances to the next page if one exists.
     */
    void nextPage();

    /**
     * @return {@code true} if there is at least one page after the current one
     */
    boolean hasNextPage();

    /**
     * Returns to the previous page if one exists.
     */
    void previousPage();

    /**
     * @return {@code true} if there is at least one page before the current one
     */
    boolean hasPreviousPage();

    /**
     * Inserts the items of the current page into the viewer's inventory.
     */
    void insertPageItems();

    /**
     * Navigates directly to the given 1-indexed page, clamped to the valid range.
     */
    void changePage(int page);

    /**
     * @return the total number of pages backing the current source
     */
    int getTotalPages();

    /**
     * @return the 1-indexed current page number
     */
    int getCurrentPage();

    /**
     * @return the 1-indexed page containing the given source index
     */
    int getPageOfIndex(int index);

    /**
     * Replaces the entire backing source list and resets internal state accordingly.
     */
    void setSource(List<T> source);

    /**
     * @return the current backing source list
     */
    List<T> getSource();

    /**
     * @return the maximum number of items rendered per page
     */
    int getItemPageLimit();

    /**
     * @return the fallback item for empty slots, or {@code null} if none was configured
     */
    InventoryItem getFallbackItem();
}
