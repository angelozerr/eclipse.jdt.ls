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

import java.util.Objects;

import org.eclipse.lsp4j.Range;

/**
 * A named declaration (type, method, or field) in a compilation unit,
 * carrying enough information to count cross-file references via
 * {@link AnalysisSession#findReferencesTo(SymbolIdentity)}.
 */
public record Declaration(String name, Range nameRange, SymbolIdentity identity, Kind kind) {

    /**
     * The kind of declaration, used to filter which declarations
     * show a "references" CodeLens.
     */
    public enum Kind {
        TYPE, METHOD, FIELD
    }

    public Declaration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(nameRange, "nameRange");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(kind, "kind");
    }
}
