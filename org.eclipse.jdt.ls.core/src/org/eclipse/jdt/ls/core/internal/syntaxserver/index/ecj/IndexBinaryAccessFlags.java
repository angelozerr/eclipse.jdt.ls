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

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.ExtraCompilerModifiers;
import org.eclipse.jdt.internal.compiler.lookup.TagBits;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.AnnotationRef;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.AnnotationValue;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.ClassFileTypeEntry;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.FieldEntry;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.MethodEntry;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.SourceTypeEntry;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeDeclKind;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeEntry;

/**
 * Maps indexed modifiers onto ECJ {@link ClassFileConstants} /
 * {@link ExtraCompilerModifiers} bits. ASM's {@code ACC_RECORD} (0x10000) is
 * remapped to {@link ExtraCompilerModifiers#AccRecord}.
 */
final class IndexBinaryAccessFlags {
    /** ASM {@code ACC_RECORD}; not an ECJ ClassFileConstants bit. */
    static final int ACC_RECORD = 0x10000;
    static final int ACC_ANNOTATION = ClassFileConstants.AccAnnotation;

    private IndexBinaryAccessFlags() {}

    static int rawModifiers(TypeEntry entry) {
        return switch (entry) {
            case SourceTypeEntry source -> source.modifiers();
            case ClassFileTypeEntry classFile -> classFile.modifiers();
        };
    }

    static int classModifiers(TypeEntry entry) {
        int access = rawModifiers(entry);
        if (entry instanceof SourceTypeEntry source) {
            access |= switch (source.declKind()) {
                case INTERFACE -> ClassFileConstants.AccInterface | ClassFileConstants.AccAbstract;
                case ANNOTATION -> ClassFileConstants.AccAnnotation
                        | ClassFileConstants.AccInterface
                        | ClassFileConstants.AccAbstract;
                case ENUM -> ClassFileConstants.AccEnum | ClassFileConstants.AccFinal;
                case RECORD -> ACC_RECORD | ClassFileConstants.AccFinal;
                default -> 0;
            };
        }
        boolean record = (access & ACC_RECORD) != 0
                || (entry instanceof SourceTypeEntry source && source.declKind() == TypeDeclKind.RECORD);
        access &= ~ACC_RECORD;
        if (record) access |= ExtraCompilerModifiers.AccRecord;
        if ((access & ClassFileConstants.AccInterface) == 0) {
            access |= ClassFileConstants.AccSuper;
        }
        if (entry.permittedSubclasses() != null && entry.permittedSubclasses().length > 0) {
            access |= ExtraCompilerModifiers.AccSealed;
        }
        if (hasDeprecated(annotationsOf(entry))) {
            access |= ClassFileConstants.AccDeprecated;
        }
        return access;
    }

    static int innerClassModifiers(TypeEntry outer, TypeEntry inner) {
        int flags = classModifiers(inner);
        if (!(inner instanceof SourceTypeEntry sourceInner)) return flags;
        if (isInterfaceOwner(outer)) {
            flags |= ClassFileConstants.AccPublic | ClassFileConstants.AccStatic;
        }
        if (isImplicitlyStaticMember(sourceInner.declKind())) {
            flags |= ClassFileConstants.AccStatic;
        }
        return flags;
    }

    static int fieldModifiers(TypeEntry owner, FieldEntry field) {
        int access = field.modifiers();
        if (owner instanceof SourceTypeEntry source && isInterfaceLike(source.declKind())) {
            access |= ClassFileConstants.AccPublic
                    | ClassFileConstants.AccStatic
                    | ClassFileConstants.AccFinal;
        }
        if (hasDeprecated(field.annotations())) {
            access |= ClassFileConstants.AccDeprecated;
        }
        return access;
    }

    static int methodModifiers(TypeEntry owner, MethodEntry method) {
        int access = method.modifiers();
        if (method.varargs()) access |= ClassFileConstants.AccVarargs;
        if (owner instanceof SourceTypeEntry source && isInterfaceLike(source.declKind())) {
            if ((access & (ClassFileConstants.AccPublic
                    | ClassFileConstants.AccPrivate
                    | ClassFileConstants.AccProtected)) == 0) {
                access |= ClassFileConstants.AccPublic;
            }
            if (!method.hasBody()
                    && (access & (ClassFileConstants.AccPrivate | ClassFileConstants.AccStatic)) == 0) {
                access |= ClassFileConstants.AccAbstract;
            }
        }
        if (method.annotationDefault() != null) {
            access |= ClassFileConstants.AccAnnotationDefault;
        }
        if (hasDeprecated(method.annotations())) {
            access |= ClassFileConstants.AccDeprecated;
        }
        return access;
    }

    static long annotationTagBits(AnnotationRef[] annotations) {
        if (annotations == null || annotations.length == 0) return 0L;
        long bits = 0L;
        for (AnnotationRef ref : annotations) {
            String jvm = ref.jvmName();
            if ("java/lang/Deprecated".equals(jvm) || "Deprecated".equals(jvm)) {
                bits |= TagBits.AnnotationDeprecated;
            } else if ("java/lang/annotation/Target".equals(jvm) || "Target".equals(jvm)) {
                bits |= targetTagBits(ref);
            } else if ("java/lang/annotation/Retention".equals(jvm) || "Retention".equals(jvm)) {
                bits |= retentionTagBits(ref);
            } else if ("java/lang/annotation/Documented".equals(jvm) || "Documented".equals(jvm)) {
                bits |= TagBits.AnnotationDocumented;
            } else if ("java/lang/annotation/Inherited".equals(jvm) || "Inherited".equals(jvm)) {
                bits |= TagBits.AnnotationInherited;
            } else if ("java/lang/annotation/Repeatable".equals(jvm) || "Repeatable".equals(jvm)) {
                bits |= TagBits.AnnotationRepeatable;
            }
        }
        return bits;
    }

    /**
     * Mirror ECJ {@code AnnotationInfo.readTargetValue}: {@code @Target({})}
     * sets only {@link TagBits#AnnotationTarget}; each {@code ElementType}
     * constant sets the corresponding {@code AnnotationFor*} bit. Without
     * these, a TYPE_USE annotation from the index is treated as having no
     * explicit targets and cannot be applied to type arguments.
     */
    private static long targetTagBits(AnnotationRef ref) {
        AnnotationValue value = ref.values() == null ? null : ref.values().get("value");
        if (value == null) return 0L;
        if (value instanceof AnnotationValue.Arr arr) {
            if (arr.elements().length == 0) return TagBits.AnnotationTarget;
            long bits = 0L;
            for (AnnotationValue element : arr.elements()) {
                bits |= targetElementBit(element);
            }
            return bits;
        }
        return targetElementBit(value);
    }

    private static long targetElementBit(AnnotationValue value) {
        if (!(value instanceof AnnotationValue.EnumConst enumConst)) return 0L;
        return switch (enumConst.constant()) {
            case "TYPE" -> TagBits.AnnotationForType;
            case "FIELD" -> TagBits.AnnotationForField;
            case "METHOD" -> TagBits.AnnotationForMethod;
            case "PARAMETER" -> TagBits.AnnotationForParameter;
            case "CONSTRUCTOR" -> TagBits.AnnotationForConstructor;
            case "LOCAL_VARIABLE" -> TagBits.AnnotationForLocalVariable;
            case "ANNOTATION_TYPE" -> TagBits.AnnotationForAnnotationType;
            case "PACKAGE" -> TagBits.AnnotationForPackage;
            case "TYPE_USE" -> TagBits.AnnotationForTypeUse;
            case "TYPE_PARAMETER" -> TagBits.AnnotationForTypeParameter;
            case "MODULE" -> TagBits.AnnotationForModule;
            case "RECORD_COMPONENT" -> TagBits.AnnotationForRecordComponent;
            default -> 0L;
        };
    }

    private static long retentionTagBits(AnnotationRef ref) {
        AnnotationValue value = ref.values() == null ? null : ref.values().get("value");
        if (!(value instanceof AnnotationValue.EnumConst enumConst)) return 0L;
        return switch (enumConst.constant()) {
            case "SOURCE" -> TagBits.AnnotationSourceRetention;
            case "CLASS" -> TagBits.AnnotationClassRetention;
            case "RUNTIME" -> TagBits.AnnotationRuntimeRetention;
            default -> 0L;
        };
    }

    static boolean isRecord(TypeEntry entry) {
        if (entry instanceof SourceTypeEntry source && source.declKind() == TypeDeclKind.RECORD) {
            return true;
        }
        return (rawModifiers(entry) & ACC_RECORD) != 0;
    }

    static boolean isInterfaceOwner(TypeEntry owner) {
        return switch (owner) {
            case SourceTypeEntry source -> isInterfaceLike(source.declKind());
            case ClassFileTypeEntry classFile ->
                    (classFile.modifiers() & ClassFileConstants.AccInterface) != 0;
        };
    }

    private static boolean isImplicitlyStaticMember(TypeDeclKind kind) {
        return kind == TypeDeclKind.INTERFACE
                || kind == TypeDeclKind.ANNOTATION
                || kind == TypeDeclKind.ENUM
                || kind == TypeDeclKind.RECORD;
    }

    private static boolean isInterfaceLike(TypeDeclKind kind) {
        return kind == TypeDeclKind.INTERFACE || kind == TypeDeclKind.ANNOTATION;
    }

    private static AnnotationRef[] annotationsOf(TypeEntry entry) {
        return switch (entry) {
            case SourceTypeEntry source -> source.annotations();
            case ClassFileTypeEntry classFile -> classFile.annotations();
        };
    }

    private static boolean hasDeprecated(AnnotationRef[] annotations) {
        if (annotations == null) return false;
        for (AnnotationRef ref : annotations) {
            if ("java/lang/Deprecated".equals(ref.jvmName())) return true;
        }
        return false;
    }
}
