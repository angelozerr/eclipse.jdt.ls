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
 * A single field declaration attached to a {@link TypeEntry}.
 *
 * <p>The declared type is captured as a {@link Type}; for source-derived
 * fields this may be {@link TypeRef.Unresolved}, to be resolved later by
 * the class reader using the {@link SourceResolutionHints} on the
 * enclosing type.
 *
 * <p>{@link #constantValue()} is non-null when this field carries a
 * JLS-style compile-time constant (a {@code static final} primitive or
 * {@code String}). The actual value is captured as the matching boxed
 * type ({@link Integer}/{@link Long}/{@link Float}/{@link Double}/
 * {@link String}), matching the convention used by javac's
 * {@code ClassReader} so that callers can pipe it straight into
 * {@code VarSymbol.setData(...)} and javac can constant-fold use sites
 * against it.
 *
 * <p>Bytecode-derived entries populate this from the {@code ConstantValue}
 * classfile attribute; source-derived entries do a best-effort literal
 * extraction (typed literals and unary-minus over a numeric literal). When
 * the initializer is a field reference ({@code TypeName.FIELD} or a
 * same-file identifier) rather than a literal, {@link #constantOwner()} and
 * {@link #constantName()} record that alias so the class reader can copy
 * the referenced field's constant. A {@code null} {@link #constantValue()}
 * just means "no compile-time constant known yet".
 */
public record FieldEntry(
        int modifiers,
        String name,
        Type type,
        Object constantValue,
        TypeRef constantOwner,
        String constantName,
        AnnotationRef[] annotations) implements IndexEntry {

    public FieldEntry {
        annotations = EmptyArrays.orEmpty(annotations, EmptyArrays.ANNOTATION_REF);
    }

    /** Constructor for a field with a known constant value and no alias. */
    public FieldEntry(
            int modifiers,
            String name,
            Type type,
            Object constantValue,
            AnnotationRef[] annotations) {
        this(modifiers, name, type, constantValue, null, null, annotations);
    }

    /** Constructor without a constant value. */
    public FieldEntry(
            int modifiers,
            String name,
            Type type,
            AnnotationRef[] annotations) {
        this(modifiers, name, type, null, null, null, annotations);
    }

    @Override
    public EntryKind kind() {
        return EntryKind.FIELD;
    }
}
