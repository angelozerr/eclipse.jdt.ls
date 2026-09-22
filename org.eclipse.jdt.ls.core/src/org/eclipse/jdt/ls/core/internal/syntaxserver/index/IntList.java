/*******************************************************************************
 * Copyright (c) 2026 Angelo Zerr and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from java-ls (https://github.com/tsmaeder/java-ls) and adapted.
 * Original code by Thomas Mäder, Castle Ridge Software, licensed under MIT.
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.syntaxserver.index;

import java.util.Arrays;

/**
 * Compact growable list of {@code int}s with doubling growth.
 *
 * <p>Used by {@link InMemoryIndex} secondary indexes so type IDs stay
 * unboxed. Most buckets are tiny (often a single entry), so the default
 * capacity is small; {@link #ensureCapacity(int)} is for bulk merges.
 */
final class IntList {

    private int[] data;
    private int size;

    IntList() {
        this(2);
    }

    IntList(int capacity) {
        this.data = new int[Math.max(1, capacity)];
    }

    int size() {
        return size;
    }

    boolean isEmpty() {
        return size == 0;
    }

    int get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
        return data[index];
    }

    void add(int value) {
        ensureCapacity(size + 1);
        data[size++] = value;
    }

    /**
     * Remove the first occurrence of {@code value} via swap-with-last.
     * Returns {@code true} when a value was removed.
     */
    boolean removeValue(int value) {
        for (int i = 0; i < size; i++) {
            if (data[i] == value) {
                data[i] = data[size - 1];
                size--;
                return true;
            }
        }
        return false;
    }

    /**
     * Append every value from {@code other} after adding {@code offset}
     * (used when remapping source-index IDs into a target index).
     */
    void addAllRemapped(IntList other, int offset) {
        if (other == null || other.size == 0) return;
        ensureCapacity(size + other.size);
        for (int i = 0; i < other.size; i++) {
            data[size++] = other.data[i] + offset;
        }
    }

    void ensureCapacity(int minCapacity) {
        if (minCapacity <= data.length) return;
        int newCap = data.length;
        while (newCap < minCapacity) {
            int doubled = newCap << 1;
            if (doubled < 0) {
                newCap = minCapacity;
                break;
            }
            newCap = Math.max(doubled, minCapacity);
        }
        data = Arrays.copyOf(data, newCap);
    }

    /** Independent copy; mutations to either list do not affect the other. */
    IntList copy() {
        IntList copy = new IntList(Math.max(1, size));
        System.arraycopy(data, 0, copy.data, 0, size);
        copy.size = size;
        return copy;
    }
}
