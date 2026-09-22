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

import java.util.Map;

/**
 * Minimal representation of an annotation attached to a declaration. The
 * annotation type ({@link TypeRef}) plus its supplied element values
 * (matching the JVM annotation grammar of JLS 9.6.1) are kept so that
 * compile-affecting annotations such as {@code @SuppressWarnings},
 * {@code @Deprecated}, {@code @Target}, {@code @Retention} and
 * {@code @Repeatable} can be reconstructed as javac
 * {@code Attribute.Compound} instances in {@code IndexClassReader}.
 *
 * <p>Source-derived annotation types may be {@link TypeRef.Unresolved}
 * simple names; the symbol-side converter resolves them against the
 * enclosing compilation unit's {@link SourceResolutionHints}.
 *
 * <p>Source-derived references may carry {@link AnnotationValue.Unsupported}
 * leaves for expressions the indexer cannot evaluate without a symbol
 * table; the symbol-side converter drops those elements when building
 * attributes, mirroring "unspecified element" semantics.
 */
public record AnnotationRef(TypeRef annotationType, Map<String, AnnotationValue> values) {
    public AnnotationRef {
        if (annotationType == null) {
            throw new IllegalArgumentException("annotationType must not be null");
        }
        values = values == null ? Map.of() : values;
    }

    /** Best-effort name: binary name when resolved, else the simple name. */
    public String jvmName() {
        return switch (annotationType) {
            case TypeRef.Resolved r -> r.jvmBinaryName();
            case TypeRef.Unresolved u -> u.simpleName();
        };
    }
}
