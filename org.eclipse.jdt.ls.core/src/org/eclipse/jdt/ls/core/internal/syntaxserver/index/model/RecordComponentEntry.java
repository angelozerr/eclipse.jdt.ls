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
 * A single record component declared in the header of a record type.
 *
 * <p>The JVM exposes record components as a structured attribute, distinct
 * from the synthetic field and accessor method that the compiler generates
 * for each one. Tools that walk
 * {@code java.lang.Class.getRecordComponents()} (and javac itself, via
 * {@code ClassSymbol.getRecordComponents}) need to see this attribute,
 * not just the synthesised field/accessor pair.
 *
 * <p>Bytecode-derived entries populate this from the {@code Record}
 * classfile attribute; source-derived entries populate it from the
 * record header so the class reader can synthesize accessors and the
 * canonical constructor.
 */
public record RecordComponentEntry(
        String name,
        Type type,
        AnnotationRef[] annotations) {

    public RecordComponentEntry {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must be non-empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        annotations = EmptyArrays.orEmpty(annotations, EmptyArrays.ANNOTATION_REF);
    }
}
