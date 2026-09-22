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
 * Declaration kind of an indexed type. Set from source AST for
 * source-derived entries; {@link #UNKNOWN} for bytecode-derived entries
 * where kind is not stored separately.
 */
public enum TypeDeclKind {
    UNKNOWN,
    CLASS,
    INTERFACE,
    ENUM,
    RECORD,
    ANNOTATION
}
