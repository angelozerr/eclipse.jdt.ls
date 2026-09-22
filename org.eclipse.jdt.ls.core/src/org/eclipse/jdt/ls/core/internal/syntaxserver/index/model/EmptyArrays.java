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

import java.util.List;

/**
 * Shared empty arrays for the in-memory index model. Empty member lists are
 * common; sharing one zero-length array per element type avoids allocating a
 * distinct empty array (and the List wrapper) per entry.
 */
public final class EmptyArrays {

    public static final AnnotationRef[] ANNOTATION_REF = new AnnotationRef[0];
    public static final AnnotationValue[] ANNOTATION_VALUE = new AnnotationValue[0];
    public static final FieldEntry[] FIELD = new FieldEntry[0];
    public static final MethodEntry[] METHOD = new MethodEntry[0];
    public static final ParameterEntry[] PARAMETER = new ParameterEntry[0];
    public static final RecordComponentEntry[] RECORD_COMPONENT = new RecordComponentEntry[0];
    public static final Type[] TYPE = new Type[0];
    public static final TypeParamRef[] TYPE_PARAM = new TypeParamRef[0];
    public static final TypeRef[] TYPE_REF = new TypeRef[0];
    public static final String[] STRING = new String[0];
    public static final ModuleEntry.Requires[] REQUIRES = new ModuleEntry.Requires[0];
    public static final ModuleEntry.Exports[] EXPORTS = new ModuleEntry.Exports[0];
    public static final ModuleEntry.Opens[] OPENS = new ModuleEntry.Opens[0];
    public static final ModuleEntry.Provides[] PROVIDES = new ModuleEntry.Provides[0];

    private EmptyArrays() {}

    /**
     * Returns {@code empty} when {@code src} is null or zero-length; otherwise
     * {@code src} itself (callers transfer ownership).
     */
    public static <T> T[] orEmpty(T[] src, T[] empty) {
        if (src == null || src.length == 0) {
            return empty;
        }
        return src;
    }

    /**
     * Returns {@code empty} when {@code list} is null or empty; otherwise
     * {@code list.toArray(empty)} (which allocates a correctly typed array).
     */
    public static <T> T[] toArray(List<T> list, T[] empty) {
        if (list == null || list.isEmpty()) {
            return empty;
        }
        return list.toArray(empty);
    }
}
