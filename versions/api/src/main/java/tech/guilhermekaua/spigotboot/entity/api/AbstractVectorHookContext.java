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
package tech.guilhermekaua.spigotboot.entity.api;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

abstract class AbstractVectorHookContext<T extends LivingEntity> extends AbstractEntityHookContext<T, Void> {
    private double x;
    private double y;
    private double z;

    protected AbstractVectorHookContext(
            @NotNull ControlledEntity<T> entity,
            @NotNull EntityBaseInvoker<Void> base,
            double x,
            double y,
            double z
    ) {
        super(entity, base);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public final double x() {
        return x;
    }

    public final void setX(double x) {
        this.x = x;
    }

    public final double y() {
        return y;
    }

    public final void setY(double y) {
        this.y = y;
    }

    public final double z() {
        return z;
    }

    public final void setZ(double z) {
        this.z = z;
    }
}
