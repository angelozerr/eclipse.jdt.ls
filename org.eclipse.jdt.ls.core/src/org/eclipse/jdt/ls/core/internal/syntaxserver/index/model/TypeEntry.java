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
 * Common surface for indexed type declarations regardless of origin
 * (source parser or classfile parser). Origin-specific payload is carried
 * by {@link SourceTypeEntry} and {@link ClassFileTypeEntry}.
 *
 * <p>{@link #resourceUri()} is the full resource location, reconstructed
 * from the compact {@code resourcePath} stored on the concrete entry and
 * {@link #sourceUri()} when compaction is loss-free (see {@link ResourceUris}).
 */
public sealed interface TypeEntry extends IndexEntry permits SourceTypeEntry, ClassFileTypeEntry {
    String resourceUri();

    String sourceUri();

    String jvmOwnerName();

    Type superRef();

    Type[] interfaceRefs();

    TypeParamRef[] typeParams();

    FieldEntry[] fields();

    MethodEntry[] methods();

    String[] innerTypeJvmNames();

    TypeRef[] permittedSubclasses();

    RecordComponentEntry[] recordComponents();

    default String jvmName() {
        return jvmOwnerName();
    }

    default String packageJvm() {
        String owner = jvmOwnerName();
        int slash = owner.lastIndexOf('/');
        return slash < 0 ? "" : owner.substring(0, slash);
    }

    @Override
    default EntryKind kind() {
        return EntryKind.TYPE;
    }
}
