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
package org.eclipse.jdt.ls.core.internal.syntaxserver.index.model;

/**
 * Locator metadata for an indexed {@link TypeEntry}: enough to map a compiled
 * class back to its originating resource and sources-jar companion.
 *
 * <p>{@link #resourcePath()} is compact relative to {@link #sourceUri()} when
 * possible; prefer {@link #resourceUri()} for the full location.
 */
public record IndexedClassRef(
        String resourcePath,
        String sourceUri,
        String jvmOwnerName) {

    public IndexedClassRef {
        resourcePath = ResourceUris.compact(resourcePath, sourceUri);
    }

    public String resourceUri() {
        return ResourceUris.resolve(sourceUri, resourcePath);
    }

    public String jvmName() {
        return jvmOwnerName;
    }

    public static IndexedClassRef from(TypeEntry entry) {
        String path = switch (entry) {
            case ClassFileTypeEntry c -> c.resourcePath();
            case SourceTypeEntry s -> s.resourcePath();
        };
        return new IndexedClassRef(path, entry.sourceUri(), entry.jvmOwnerName());
    }
}
