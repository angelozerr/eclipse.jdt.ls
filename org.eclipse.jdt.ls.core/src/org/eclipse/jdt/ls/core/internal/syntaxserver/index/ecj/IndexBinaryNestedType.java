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
package org.eclipse.jdt.ls.core.internal.syntaxserver.index.ecj;

import org.eclipse.jdt.internal.compiler.env.IBinaryNestedType;

final class IndexBinaryNestedType implements IBinaryNestedType {
    private final char[] enclosingTypeName;
    private final char[] name;
    private final int modifiers;

    IndexBinaryNestedType(String enclosingJvm, String memberJvm, int modifiers) {
        this.enclosingTypeName = enclosingJvm.toCharArray();
        this.name = memberJvm.toCharArray();
        this.modifiers = modifiers;
    }

    @Override
    public char[] getEnclosingTypeName() {
        return enclosingTypeName;
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    public char[] getName() {
        return name;
    }
}
