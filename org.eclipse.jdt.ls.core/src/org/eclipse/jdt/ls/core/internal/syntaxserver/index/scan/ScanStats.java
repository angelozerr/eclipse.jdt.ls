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
package org.eclipse.jdt.ls.core.internal.syntaxserver.index.scan;

/**
 * Aggregate file counts and sizes collected while scanning.
 *
 * @param sourceFileCount number of {@code .java} resources walked
 * @param classFileBytes  sum of known sizes of {@code .class} resources
 */
public record ScanStats(int sourceFileCount, long classFileBytes) {
    public static final ScanStats EMPTY = new ScanStats(0, 0L);
}
