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

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeEntry;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeEntryCodec;

/**
 * Fixed-size LRU of decoded {@link TypeEntry}s keyed by artificial type ID.
 *
 * <p>IDs are index-local, append-only, and never reused, so they are stable
 * cache identities for the life of an {@link InMemoryIndex}. Eviction drops
 * the decoded object graph only; the encoded blob remains in the index.
 */
final class DecodedTypeCache {

    static final int DEFAULT_CAPACITY = 4096;

    private final int capacity;
    private final Map<Integer, TypeEntry> cache;

    DecodedTypeCache() {
        this(DEFAULT_CAPACITY);
    }

    DecodedTypeCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, TypeEntry> eldest) {
                return size() > DecodedTypeCache.this.capacity;
            }
        };
    }

    /** Drop a cached decode for {@code id} (e.g. after the blob is tombstoned). */
    void invalidate(int id) {
        synchronized (this) {
            cache.remove(id);
        }
    }

    /**
     * Return the decoded entry for {@code id}, decoding {@code blob} on a
     * miss. {@code blob} must be the canonical encoding stored under
     * {@code id} in the owning index.
     */
    TypeEntry get(int id, byte[] blob) {
        if (blob == null) return null;
        // Fast path: a hit only needs the (cheap) map lookup under the lock.
        // The access-ordered LRU mutates on read, so the get itself must be
        // guarded, but we keep the critical section to the map operation.
        synchronized (this) {
            TypeEntry cached = cache.get(id);
            if (cached != null) return cached;
        }
        // Decode outside the lock so concurrent decodes of different IDs
        // run in parallel instead of serializing on this monitor. A racing
        // duplicate decode of the same ID is harmless: we keep whichever
        // decoded graph wins the insert and drop the other for GC.
        TypeEntry decoded = TypeEntryCodec.decode(blob);
        synchronized (this) {
            TypeEntry existing = cache.get(id);
            if (existing != null) return existing;
            cache.put(id, decoded);
            return decoded;
        }
    }
}
