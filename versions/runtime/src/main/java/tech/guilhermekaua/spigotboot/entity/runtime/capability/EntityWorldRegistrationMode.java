/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.entity.runtime.capability;

/**
 * Describes how the active version wires a native entity into world and chunk state.
 *
 * @since 2.0.2
 */
public enum EntityWorldRegistrationMode {
    /**
     * Registration currently happens through the Bukkit or vanilla spawn pipeline.
     */
    BUKKIT_SPAWN_PIPELINE,

    /**
     * Registration requires loading the destination chunk before adding the native entity to the world.
     */
    CHUNK_PRELOAD_AND_ADD,

    /**
     * Registration updates existing world, chunk, and tracker references from one native handle to another.
     */
    REFERENCE_REWRITE,

    /**
     * The adapter does not expose a world-registration descriptor.
     */
    UNSPECIFIED
}
