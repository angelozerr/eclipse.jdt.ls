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
 * A formal type parameter declared on a {@link TypeEntry} (class) or, in
 * the future, a {@link MethodEntry}.
 *
 * <p>Phase 1 of generics support only stores the parameter <em>name</em>
 * (e.g. {@code "T"}, {@code "R"}); bounds are normalised to a single
 * {@code java/lang/Object} bound. That is enough for javac to see the
 * correct arity on the enclosing {@code ClassType} so that source
 * references like {@code Function<? super T, U>} parse and type-check
 * structurally.
 *
 * <p>The {@link #bounds()} array is never empty: a parameter with no
 * declared bound is normalised to {@code [java/lang/Object]} so callers
 * can synthesize a {@code TypeVar} unconditionally.
 */
public record TypeParamRef(String name, Type[] bounds) {

    private static final Type[] OBJECT_BOUND = new Type[]{TypeRef.resolved("java/lang/Object")};

    public TypeParamRef {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must be non-empty");
        }
        bounds = (bounds == null || bounds.length == 0)
                ? OBJECT_BOUND
                : EmptyArrays.orEmpty(bounds, EmptyArrays.TYPE);
    }

    /** Convenience for the common case of "no declared bound". */
    public static TypeParamRef of(String name) {
        return new TypeParamRef(name, EmptyArrays.TYPE);
    }
}
