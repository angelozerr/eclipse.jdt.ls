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

import java.util.List;

/**
 * Outcome of a {@link Scanner#scan} pass, including wall-clock timings
 * split by input kind.
 *
 * <p>{@link #classFilesMs()} covers {@link JarInput} / {@link JrtInput}
 * (and any other non-{@link DirInput} source). {@link #sourceFilesMs()}
 * covers {@link DirInput} trees. Phases run sequentially so the numbers
 * are non-overlapping wall-clock times; {@link #elapsedMs()} is the
 * end-to-end scan duration.
 *
 * @param failures      per-file / per-source errors collected during the scan
 * @param classFilesMs  wall-clock ms spent indexing class-file inputs
 * @param sourceFilesMs wall-clock ms spent indexing source-directory inputs
 * @param elapsedMs     wall-clock ms for the whole scan
 */
public record ScanResult(
        List<Throwable> failures,
        long classFilesMs,
        long sourceFilesMs,
        long elapsedMs) {
}
