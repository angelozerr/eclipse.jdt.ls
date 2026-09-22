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

import java.io.IOException;

/**
 * Callback shape used by {@link InputSource#walk(ResourceSink)}. The
 * walker supplies a path relative to the input source plus a
 * {@link BytesProvider} that lazily produces the content. Walkers must call
 * {@link #accept} exactly once per resource; if they throw, the scan bails out.
 *
 * <p>The full resource URI is not built during the walk; the scanner pairs
 * {@code relativePath} with {@link InputSource#sourceUri()} and resolves a
 * URI only when a consumer needs one (via
 * {@link org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.ResourceUris#resolve}).
 */
@FunctionalInterface
public interface ResourceSink {
    void accept(String relativePath, String fileName, BytesProvider bytes) throws IOException;

    @FunctionalInterface
    interface BytesProvider {
        byte[] get() throws IOException;
    }
}
