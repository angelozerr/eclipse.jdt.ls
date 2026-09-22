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
 * Common superinterface for every declaration the indexer produces. Every
 * entry knows its stored declaration modifiers and any annotations attached
 * to the declaration.
 *
 * <p>Resource URI and JVM owner name live on {@link TypeEntry} (and related
 * locator types). {@link FieldEntry} / {@link MethodEntry} are nested under
 * their enclosing type and do not duplicate those locator fields.
 *
 * <p>For source-derived type entries, {@link #modifiers()} holds only explicit
 * source modifiers; JVM classfile access flags are synthesized later by
 * {@code IndexClassReader}. For bytecode-derived entries, {@link #modifiers()}
 * is the ASM access mask and is used as-is at read time.
 */
public sealed interface IndexEntry permits TypeEntry, FieldEntry, MethodEntry {

    int modifiers();

    AnnotationRef[] annotations();

    EntryKind kind();
}
