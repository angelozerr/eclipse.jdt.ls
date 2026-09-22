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
import java.util.Set;

/**
 * Per compilation-unit hints captured by the source indexer and consumed by
 * the class reader when it materialises a {@link TypeEntry} into a javac
 * {@code ClassSymbol}. Every {@link TypeRef.Unresolved} emitted by the
 * source indexer is resolved against the hints of its enclosing type using
 * the JLS ordering:
 *
 * <ol>
 *   <li>Declared in the same compilation unit ({@link #siblingSimpleNames()}).</li>
 *   <li>Single-type imports ({@link #singleTypeImports()}).</li>
 *   <li>Same-package types (derived from {@link #sourcePackage()}).</li>
 *   <li>On-demand ({@code .*}) imports ({@link #onDemandImports()}).</li>
 *   <li>Implicit {@code java.lang} import.</li>
 * </ol>
 *
 * <p>All fields use JVM binary form (slash-delimited, never dot-delimited),
 * except {@link #siblingSimpleNames()} which is made of Java simple names.
 */
public record SourceResolutionHints(
        String sourcePackage,
        Map<String, String> singleTypeImports,
        String[] onDemandImports,
        Set<String> siblingSimpleNames) {

    public SourceResolutionHints {
        sourcePackage = sourcePackage == null ? "" : sourcePackage;
        singleTypeImports = singleTypeImports == null ? Map.of() : singleTypeImports;
        onDemandImports = EmptyArrays.orEmpty(onDemandImports, EmptyArrays.STRING);
        siblingSimpleNames = siblingSimpleNames == null ? Set.of() : siblingSimpleNames;
    }
}
