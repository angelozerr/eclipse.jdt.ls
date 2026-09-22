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
package org.eclipse.jdt.ls.core.internal.syntaxserver.index.scan;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable, thread-safe accumulator for scan statistics. Walkers record
 * per-resource counts/sizes when they already know them; {@link Scanner}
 * snapshots the result after {@link Scanner#scanAll}.
 */
public final class ScanCollector {

    private final AtomicInteger sourceFileCount = new AtomicInteger();
    private final AtomicLong classFileBytes = new AtomicLong();

    public void addSourceFile() {
        sourceFileCount.incrementAndGet();
    }

    public void addClassFileBytes(long n) {
        if (n >= 0) {
            classFileBytes.addAndGet(n);
        }
    }

    public ScanStats snapshot() {
        return new ScanStats(sourceFileCount.get(), classFileBytes.get());
    }
}
