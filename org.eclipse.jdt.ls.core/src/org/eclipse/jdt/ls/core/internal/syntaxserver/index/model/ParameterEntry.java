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
 * A single formal parameter of a {@link MethodEntry}.
 *
 * <p>Carries the declared type, the parameter modifiers (e.g.
 * {@code ACC_FINAL}, {@code ACC_SYNTHETIC}, {@code ACC_MANDATED}) and any
 * declaration annotations. {@link #name()} may be {@code null} when the
 * underlying source of truth (bytecode without a {@code MethodParameters}
 * attribute, or {@code SKIP_DEBUG} parsing options) didn't expose a name -
 * callers should fall back to a synthetic {@code "arg" + index} in that
 * case.
 */
public record ParameterEntry(
        String name,
        int modifiers,
        Type type,
        AnnotationRef[] annotations) {

    public ParameterEntry {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        annotations = EmptyArrays.orEmpty(annotations, EmptyArrays.ANNOTATION_REF);
    }

    /** Convenience for the common name-only / no-annotations case. */
    public static ParameterEntry of(Type type) {
        return new ParameterEntry(null, 0, type, EmptyArrays.ANNOTATION_REF);
    }
}
