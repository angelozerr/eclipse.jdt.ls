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

/**
 * Something the scanner can pull declarations from. Three shapes:
 * a filesystem tree, a jar file, or a subset of the JRT image.
 *
 * <p>Every input source exposes a {@link #sourceUri()} that uniquely
 * identifies it. That URI is stamped on every {@link
 * org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeEntry} the scanner emits from
 * this source so downstream consumers (e.g. the file manager) can later
 * decide, given a list of input sources ordered by classpath priority,
 * which duplicate entry to prefer.
 *
 * <p>Walkers emit paths relative to this source (jar entry names, paths under
 * a directory root, paths inside the jrt filesystem). Full resource URIs are
 * resolved later via {@link org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.ResourceUris}.
 */
public sealed interface InputSource permits DirInput, JarInput, JrtInput {

    void walk(ResourceSink sink);

    /**
     * URI identifying this source as a whole (the jar file, the directory
     * root, the JRT module subset). Stable across runs so callers can build
     * classpath-priority maps keyed by it.
     */
    String sourceUri();
}
