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

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.ResourcePaths;

/**
 * Maps the resource an index entry was read from to the source a client can
 * navigate to.
 *
 * <p>Source entries - workspace files, {@code .java} entries of an archive -
 * are already navigable. A {@code .class} entry only becomes navigable when
 * its container has an attached sources archive (a dependency's
 * {@code -sources.jar}, the JDK's {@code src.zip}) registered under the
 * container's URI.
 */
public final class AttachedSource {

    private AttachedSource() {}

    /**
     * @param resourceUri full resource URI of an indexed type
     * @param containerUri URI of the classpath container the type came from
     * @param sourceJarByBinaryJar sources archive per binary classpath container
     */
    public static Optional<String> javaUri(String resourceUri,
                                           String containerUri,
                                           Map<String, String> sourceJarByBinaryJar) {
        if (resourceUri == null || resourceUri.isBlank()) return Optional.empty();
        if (!resourceUri.endsWith(".class")) return Optional.of(resourceUri);

        String sourcesArchive = sourceJarByBinaryJar == null ? null : sourceJarByBinaryJar.get(containerUri);
        if (sourcesArchive == null || sourcesArchive.isBlank()) return Optional.empty();

        int separator = resourceUri.indexOf("!/");
        if (separator < 0 || separator + 2 >= resourceUri.length()) return Optional.empty();
        String classEntry = resourceUri.substring(separator + 2);
        try {
            return Optional.of("jar:" + URI.create(sourcesArchive) + "!/" + outerClassJavaEntry(classEntry));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Companion {@code .java} entry of a {@code .class} archive entry. */
    public static String outerClassJavaEntry(String classEntryPath) {
        String withoutExtension = classEntryPath.substring(0, classEntryPath.length() - ".class".length());
        return ResourcePaths.defaultPath(withoutExtension, ResourcePaths.Kind.SOURCE);
    }
}
