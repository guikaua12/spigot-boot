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
package tech.guilhermekaua.spigotboot.data.jdbc.repository;

import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryCategories;
import tech.guilhermekaua.spigotboot.core.context.discovery.SpigotBootDiscoveryCategory;
import tech.guilhermekaua.spigotboot.core.pagination.Page;
import tech.guilhermekaua.spigotboot.core.pagination.Pageable;
import tech.guilhermekaua.spigotboot.data.jdbc.query.SelectQuery;
import tech.guilhermekaua.spigotboot.data.repository.Repository;

@SpigotBootDiscoveryCategory(value = DiscoveryCategories.JDBC_REPOSITORY, kind = SpigotBootDiscoveryCategory.Kind.SUBTYPE)
public interface JdbcRepository<T, ID> extends Repository<T, ID> {
    T insert(T entity);

    T update(T entity);

    Page<T> findAll(Pageable pageable);

    SelectQuery<T> select();
}
