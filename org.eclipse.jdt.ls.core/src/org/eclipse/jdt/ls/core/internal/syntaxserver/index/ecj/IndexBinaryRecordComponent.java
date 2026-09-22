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

import org.eclipse.jdt.internal.compiler.env.IBinaryAnnotation;
import org.eclipse.jdt.internal.compiler.env.IBinaryTypeAnnotation;
import org.eclipse.jdt.internal.compiler.env.IRecordComponent;
import org.eclipse.jdt.internal.compiler.impl.Constant;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.RecordComponentEntry;

final class IndexBinaryRecordComponent implements IRecordComponent {
    private final char[] name;
    private final char[] typeName;
    private final char[] signature;
    private final IBinaryAnnotation[] annotations;

    IndexBinaryRecordComponent(RecordComponentEntry component, IndexTypeEncoding encoding) {
        this.name = component.name().toCharArray();
        this.typeName = encoding.descriptor(component.type()).toCharArray();
        String sig = encoding.fieldSignature(component.type());
        this.signature = sig == null ? null : sig.toCharArray();
        this.annotations = IndexBinaryAnnotations.of(component.annotations(), encoding);
    }

    @Override
    public IBinaryAnnotation[] getAnnotations() {
        return annotations;
    }

    @Override
    public IBinaryTypeAnnotation[] getTypeAnnotations() {
        return null;
    }

    @Override
    public Constant getConstant() {
        return Constant.NotAConstant;
    }

    @Override
    public char[] getGenericSignature() {
        return signature;
    }

    @Override
    public int getModifiers() {
        return 0;
    }

    @Override
    public char[] getName() {
        return name;
    }

    @Override
    public long getTagBits() {
        return 0L;
    }

    @Override
    public char[] getTypeName() {
        return typeName;
    }
}
