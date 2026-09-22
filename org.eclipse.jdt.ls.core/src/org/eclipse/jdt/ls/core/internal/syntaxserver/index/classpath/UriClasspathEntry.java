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
package org.eclipse.jdt.ls.core.internal.syntaxserver.index.classpath;

import java.util.Objects;

/**
 * A {@link ClasspathEntry} that matches by URI prefix: {@link #contains}
 * returns {@code true} when this entry's {@link #sourceUri()} is a
 * prefix of the queried URI string.
 *
 * <p>This is the natural shape for entries that come from a directory
 * root or a jar file: the indexer stamps every
 * {@link org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeEntry} it emits with
 * the {@link org.eclipse.jdt.ls.core.internal.syntaxserver.index.scan.InputSource#sourceUri()}
 * of the source it came from, so an entry whose stored URI is that
 * source URI will recognise (via prefix match) every type the indexer
 * emitted for that source.
 */
public record UriClasspathEntry(String sourceUri) implements ClasspathEntry {

    public UriClasspathEntry {
        Objects.requireNonNull(sourceUri, "sourceUri");
    }

    @Override
    public boolean contains(String uri) {
        return uri != null && uri.startsWith(sourceUri);
    }

    public static UriClasspathEntry of(String uri) {
        Objects.requireNonNull(uri, "uri");
        return new UriClasspathEntry(uri);
    }
}
