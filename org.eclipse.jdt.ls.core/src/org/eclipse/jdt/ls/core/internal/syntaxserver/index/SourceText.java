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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Reads the text of a Java source file addressed by an index resource URI.
 *
 * <p>Bytes go through {@link URLConnection}, so {@code file:}, {@code jar:}
 * and {@code jrt:} - the URI shapes the indexer stamps on its entries - all
 * work without special casing.
 */
public final class SourceText {

    private SourceText() {}

    /** UTF-8 text at {@code uri}, or {@code null} when it cannot be read. */
    public static String read(String uri) {
        if (uri == null || uri.isBlank()) return null;
        try {
            URLConnection connection = URI.create(uri).toURL().openConnection();
            connection.setUseCaches(false);
            try (InputStream in = connection.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException e) {
            return null;
        }
    }
}
