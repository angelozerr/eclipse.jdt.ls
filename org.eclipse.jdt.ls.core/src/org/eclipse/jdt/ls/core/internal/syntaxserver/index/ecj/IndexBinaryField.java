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
import org.eclipse.jdt.internal.compiler.env.IBinaryField;
import org.eclipse.jdt.internal.compiler.env.IBinaryTypeAnnotation;
import org.eclipse.jdt.internal.compiler.impl.BooleanConstant;
import org.eclipse.jdt.internal.compiler.impl.ByteConstant;
import org.eclipse.jdt.internal.compiler.impl.CharConstant;
import org.eclipse.jdt.internal.compiler.impl.Constant;
import org.eclipse.jdt.internal.compiler.impl.DoubleConstant;
import org.eclipse.jdt.internal.compiler.impl.FloatConstant;
import org.eclipse.jdt.internal.compiler.impl.IntConstant;
import org.eclipse.jdt.internal.compiler.impl.LongConstant;
import org.eclipse.jdt.internal.compiler.impl.ShortConstant;
import org.eclipse.jdt.internal.compiler.impl.StringConstant;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.FieldEntry;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeEntry;

final class IndexBinaryField implements IBinaryField {
    private final TypeEntry owner;
    private final char[] name;
    private final char[] typeName;
    private final char[] signature;
    private final int modifiers;
    private final long tagBits;
    private final Constant constant;
    private final IBinaryAnnotation[] annotations;

    IndexBinaryField(FieldEntry field, TypeEntry owner, IndexTypeEncoding encoding) {
        this.owner = owner;
        this.name = field.name().toCharArray();
        this.typeName = encoding.descriptor(field.type()).toCharArray();
        String sig = encoding.fieldSignature(field.type());
        this.signature = sig == null ? null : sig.toCharArray();
        this.modifiers = IndexBinaryAccessFlags.fieldModifiers(owner, field);
        this.tagBits = IndexBinaryAccessFlags.annotationTagBits(field.annotations());
        this.constant = constantOf(field, encoding);
        this.annotations = IndexBinaryAnnotations.of(field.annotations(), encoding);
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
        return constant;
    }

    @Override
    public char[] getGenericSignature() {
        return signature;
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    public char[] getName() {
        return name;
    }

    @Override
    public long getTagBits() {
        return tagBits;
    }

    @Override
    public char[] getTypeName() {
        return typeName;
    }

    private Constant constantOf(FieldEntry entry, IndexTypeEncoding encoding) {
        int flags = IndexBinaryAccessFlags.fieldModifiers(owner, entry);
        if ((flags & org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants.AccStatic) == 0
                || (flags & org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants.AccFinal) == 0) {
            return Constant.NotAConstant;
        }
        Object value = encoding.foldConstant(entry);
        if (value == null) return Constant.NotAConstant;
        return toConstant(value);
    }

    /**
     * The javac source indexer stores boolean/char/byte/short literals as
     * {@link Integer} (javac's {@code VarSymbol} convention). ECJ wants a
     * typed {@link Constant}, so reinterpret the boxed integer against the
     * field descriptor.
     */
    private Constant toConstant(Object value) {
        String desc = new String(typeName);
        if (value instanceof Integer i) {
            return switch (desc) {
                case "Z" -> BooleanConstant.fromValue(i != 0);
                case "B" -> ByteConstant.fromValue(i.byteValue());
                case "C" -> CharConstant.fromValue((char) i.intValue());
                case "S" -> ShortConstant.fromValue(i.shortValue());
                default -> IntConstant.fromValue(i);
            };
        }
        return switch (value) {
            case Boolean b -> BooleanConstant.fromValue(b);
            case Byte b -> ByteConstant.fromValue(b);
            case Short s -> ShortConstant.fromValue(s);
            case Character c -> CharConstant.fromValue(c);
            case Long l -> LongConstant.fromValue(l);
            case Float f -> FloatConstant.fromValue(f);
            case Double d -> DoubleConstant.fromValue(d);
            case String s -> StringConstant.fromValue(s);
            default -> Constant.NotAConstant;
        };
    }
}
