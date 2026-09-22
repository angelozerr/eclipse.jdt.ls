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

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TypeHierarchyItem;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.classpath.ClasspathOrder;

/**
 * Result of analyzing a single open buffer. Feature methods are implemented
 * by the selected compiler backend (ECJ).
 */
public interface AnalysisSession {

    List<PublishedDiagnostic> diagnostics();

    /**
     * Resolve the symbol at an LSP position in the analyzed source.
     */
    Optional<ResolvedSymbol> resolveAt(Position position);

    /**
     * Find references within this unit. For file-local symbols this must be
     * the same session that produced {@code symbol}.
     */
    List<Location> referencesInUnit(ResolvedSymbol symbol);

    /**
     * Find references matching a cross-file {@link SymbolIdentity}
     * (non-file-local only).
     */
    List<Location> findReferencesTo(SymbolIdentity identity);

    Optional<Location> definitionOf(ResolvedSymbol symbol);

    /**
     * Root type hierarchy item for the type at {@code position}, if any.
     */
    Optional<TypeHierarchyItem> prepareTypeHierarchy(Position position);

    /**
     * Direct supertypes of {@code item}.
     */
    List<TypeHierarchyItem> typeHierarchySupertypes(TypeHierarchyItem item);

    /**
     * Direct subtypes of {@code item} visible on this session's classpath.
     */
    List<TypeHierarchyItem> typeHierarchySubtypes(TypeHierarchyItem item);

    /**
     * Non-file-local declarations (types, methods, fields) in the analyzed
     * source, suitable for CodeLens reference counting.
     */
    List<Declaration> declarations();

    /**
     * True when the session has a usable attributed AST.
     */
    boolean isUsable();
}
