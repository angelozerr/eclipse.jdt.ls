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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.objectweb.asm.Opcodes;

import com.google.gson.JsonElement;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.bloom.BloomEntry;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.classpath.ClasspathOrder;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.SourceResolutionHints;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.SourceTypeEntry;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.Type;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeDeclKind;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeEntry;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeRef;

public final class TypeHierarchySupport {

    private TypeHierarchySupport() {}

    @FunctionalInterface
    public interface TypeLocator {
        Optional<Location> locate(TypeEntry entry);
    }

    public static Optional<TypeHierarchyItem> itemFor(TypeEntry entry, TypeLocator locator) {
        if (entry == null || locator == null) return Optional.empty();
        Optional<Location> location = locator.locate(entry);
        if (location.isEmpty()) return Optional.empty();
        Location loc = location.get();
        Range range = loc.getRange();
        if (range == null) {
            range = new Range(new Position(0, 0), new Position(0, 0));
        }
        String simple = simpleName(entry.jvmOwnerName());
        String detail = entry.jvmOwnerName().replace('/', '.').replace('$', '.');
        TypeHierarchyItem item = new TypeHierarchyItem(
                simple, symbolKind(entry), loc.getUri(), range, range, detail);
        item.setData(dataKey(entry));
        return Optional.of(item);
    }

    public static Optional<TypeHierarchyItem> itemForResolved(
            ResolvedSymbol symbol, Location location, SymbolKind kind) {
        if (symbol == null || location == null) return Optional.empty();
        String matchKey = symbol.identity().matchKey();
        if (matchKey == null || !matchKey.startsWith("T:")) return Optional.empty();
        Range range = location.getRange();
        if (range == null) {
            range = new Range(new Position(0, 0), new Position(0, 0));
        }
        String detail = binaryNameFromData(matchKey).replace('$', '.');
        TypeHierarchyItem item = new TypeHierarchyItem(
                symbol.simpleName(),
                kind == null ? SymbolKind.Class : kind,
                location.getUri(),
                range,
                range,
                detail);
        item.setData(matchKey);
        return Optional.of(item);
    }

    public static List<TypeHierarchyItem> directSupertypes(
            TypeHierarchyItem item,
            Index index,
            ClasspathOrder classpath,
            TypeLocator locator) {
        TypeEntry entry = entryFor(item, index, classpath);
        if (entry == null) return List.of();
        List<TypeHierarchyItem> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String jvm : directSuperJvmNames(entry, index, classpath)) {
            if (!seen.add(jvm)) continue;
            TypeEntry superEntry = classpath.pick(index.getAll(jvm), TypeEntry::sourceUri);
            if (superEntry == null) continue;
            itemFor(superEntry, locator).ifPresent(out::add);
        }
        return out;
    }

    public static List<TypeHierarchyItem> directSubtypes(
            TypeHierarchyItem item,
            Index index,
            ClasspathOrder classpath,
            TypeLocator locator) {
        TypeEntry target = entryFor(item, index, classpath);
        if (target == null) return List.of();
        String targetJvm = target.jvmOwnerName();
        List<TypeHierarchyItem> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (TypeRef permitted : target.permittedSubclasses()) {
            String jvm = resolveRef(permitted, target, index, classpath);
            if (jvm == null || !seen.add(jvm)) continue;
            TypeEntry subtype = classpath.pick(index.getAll(jvm), TypeEntry::sourceUri);
            if (subtype == null) continue;
            itemFor(subtype, locator).ifPresent(out::add);
        }

        String simple = simpleName(targetJvm);
        Set<ResourceIdentity> candidates = new LinkedHashSet<>();
        for (BloomEntry bloom : index.bloomFilters()) {
            if (bloom.filter().mightContain(simple)) {
                candidates.add(new ResourceIdentity(bloom.sourceUri(), bloom.resourcePath()));
            }
        }
        if (candidates.isEmpty()) return out;

        for (TypeEntry candidate : winners(index.all((sourceUri, resourcePath) ->
                candidates.contains(new ResourceIdentity(sourceUri, resourcePath))),
                index, classpath)) {
            if (candidate.jvmOwnerName().equals(targetJvm)) continue;
            if (seen.contains(candidate.jvmOwnerName())) continue;
            if (!directSuperJvmNames(candidate, index, classpath).contains(targetJvm)) continue;
            if (!seen.add(candidate.jvmOwnerName())) continue;
            itemFor(candidate, locator).ifPresent(out::add);
        }
        return out;
    }

    public static Optional<String> jvmOwnerFromData(Object data) {
        String binary = binaryNameFromData(data);
        if (binary == null || binary.isBlank()) return Optional.empty();
        return Optional.of(binary.replace('.', '/'));
    }

    public static String dataKey(TypeEntry entry) {
        String origin = entry.resourceUri() == null ? "" : entry.resourceUri();
        String binary = entry.jvmOwnerName().replace('/', '.');
        return "T:" + origin + "|" + binary;
    }

    public static SymbolKind symbolKind(TypeEntry entry) {
        if (entry instanceof SourceTypeEntry source) {
            return switch (source.declKind()) {
                case INTERFACE -> SymbolKind.Interface;
                case ENUM -> SymbolKind.Enum;
                case ANNOTATION -> SymbolKind.Interface;
                case RECORD, CLASS, UNKNOWN -> SymbolKind.Class;
            };
        }
        int modifiers = entry.modifiers();
        if ((modifiers & Opcodes.ACC_ANNOTATION) != 0) return SymbolKind.Interface;
        if ((modifiers & Opcodes.ACC_INTERFACE) != 0) return SymbolKind.Interface;
        if ((modifiers & Opcodes.ACC_ENUM) != 0) return SymbolKind.Enum;
        return SymbolKind.Class;
    }

    private static TypeEntry entryFor(TypeHierarchyItem item, Index index, ClasspathOrder classpath) {
        if (item == null || index == null) return null;
        ClasspathOrder order = classpath == null ? ClasspathOrder.UNRESTRICTED : classpath;
        Optional<String> jvmOpt = jvmOwnerFromData(item.getData());
        if (jvmOpt.isEmpty()) return null;
        String jvm = jvmOpt.get();
        List<TypeEntry> all = index.getAll(jvm);
        if (all.isEmpty()) return null;
        String origin = originFromData(item.getData());
        if (origin != null) {
            for (TypeEntry entry : all) {
                if (origin.equals(entry.resourceUri()) && order.contains(entry.sourceUri())) {
                    return entry;
                }
            }
        }
        return order.pick(all, TypeEntry::sourceUri);
    }

    static List<String> directSuperJvmNames(TypeEntry entry, Index index, ClasspathOrder classpath) {
        List<String> out = new ArrayList<>();
        if ("java/lang/Object".equals(entry.jvmOwnerName())) {
            return out;
        }
        if (entry.superRef() != null) {
            String jvm = erasedJvm(entry.superRef(), entry, index, classpath);
            if (jvm != null) out.add(jvm);
        } else {
            String implicit = implicitSuperclass(entry);
            if (implicit != null) out.add(implicit);
        }
        for (Type iface : entry.interfaceRefs()) {
            String jvm = erasedJvm(iface, entry, index, classpath);
            if (jvm != null) out.add(jvm);
        }
        if (isAnnotation(entry) && !out.contains("java/lang/annotation/Annotation")) {
            out.add("java/lang/annotation/Annotation");
        }
        return out;
    }

    private static String implicitSuperclass(TypeEntry entry) {
        if (entry instanceof SourceTypeEntry source) {
            return switch (source.declKind()) {
                case ENUM -> "java/lang/Enum";
                case RECORD -> "java/lang/Record";
                case INTERFACE, ANNOTATION, CLASS, UNKNOWN -> "java/lang/Object";
            };
        }
        return "java/lang/Object";
    }

    private static boolean isAnnotation(TypeEntry entry) {
        if (entry instanceof SourceTypeEntry source) {
            return source.declKind() == TypeDeclKind.ANNOTATION;
        }
        return (entry.modifiers() & Opcodes.ACC_ANNOTATION) != 0;
    }

    private static String erasedJvm(Type type, TypeEntry context, Index index, ClasspathOrder classpath) {
        Type plain = unwrap(type);
        if (plain instanceof Type.Parameterized parameterized) {
            return resolveRef(parameterized.raw(), context, index, classpath);
        }
        if (plain instanceof TypeRef ref) {
            return resolveRef(ref, context, index, classpath);
        }
        return null;
    }

    private static String resolveRef(TypeRef ref, TypeEntry context, Index index, ClasspathOrder classpath) {
        if (ref instanceof TypeRef.Resolved resolved) {
            return qualifyResolved(resolved.jvmBinaryName(), context, index, classpath);
        }
        if (ref instanceof TypeRef.Unresolved unresolved) {
            return resolveSimple(unresolved.simpleName(), context, index, classpath);
        }
        return null;
    }

    private static String qualifyResolved(String jvm, TypeEntry context, Index index, ClasspathOrder classpath) {
        if (jvm.indexOf('/') >= 0 || !(context instanceof SourceTypeEntry)) {
            return jvm;
        }
        int dollar = jvm.indexOf('$');
        String outerSimple = dollar < 0 ? jvm : jvm.substring(0, dollar);
        String nested = dollar < 0 ? "" : jvm.substring(dollar);
        String outer = resolveSimple(outerSimple, context, index, classpath);
        return outer == null ? jvm : outer + nested;
    }

    private static String resolveSimple(String simple, TypeEntry context, Index index, ClasspathOrder classpath) {
        if (!(context instanceof SourceTypeEntry source)) {
            return simple;
        }
        SourceResolutionHints hints = source.hints();
        String samePackage = join(hints.sourcePackage(), simple);
        if (hints.siblingSimpleNames().contains(simple) && available(samePackage, index, classpath)) {
            return samePackage;
        }
        String imported = hints.singleTypeImports().get(simple);
        if (available(imported, index, classpath)) return imported;
        if (available(samePackage, index, classpath)) return samePackage;
        for (String packageName : hints.onDemandImports()) {
            String candidate = join(packageName, simple);
            if (available(candidate, index, classpath)) return candidate;
        }
        String javaLang = "java/lang/" + simple;
        return available(javaLang, index, classpath) ? javaLang : samePackage;
    }

    private static boolean available(String jvm, Index index, ClasspathOrder classpath) {
        if (jvm == null || jvm.isEmpty()) return false;
        return classpath.pick(index.getAll(jvm), TypeEntry::sourceUri) != null;
    }

    private static String join(String packageJvm, String simple) {
        if (packageJvm == null || packageJvm.isEmpty()) return simple;
        return packageJvm + "/" + simple;
    }

    private static Type unwrap(Type type) {
        Type current = type;
        while (current instanceof Type.Annotated annotated) {
            current = annotated.inner();
        }
        return current;
    }

    private static String simpleName(String jvmOwnerName) {
        int cut = Math.max(jvmOwnerName.lastIndexOf('/'), jvmOwnerName.lastIndexOf('$'));
        return cut < 0 ? jvmOwnerName : jvmOwnerName.substring(cut + 1);
    }

    private static String key(Object data) {
        String key = null;
        if (data instanceof String string) {
            key = string;
        } else if (data instanceof JsonElement element && element.isJsonPrimitive()) {
            key = element.getAsString();
        }
        return key != null && key.startsWith("T:") ? key : null;
    }

    private static String binaryNameFromData(Object data) {
        String key = key(data);
        if (key == null) return null;
        int bar = key.indexOf('|');
        if (bar < 0 || bar + 1 >= key.length()) return null;
        return key.substring(bar + 1);
    }

    private static String originFromData(Object data) {
        String key = key(data);
        if (key == null) return null;
        int bar = key.indexOf('|');
        if (bar < 0) return null;
        return key.substring(2, bar);
    }

    private record ResourceIdentity(String sourceUri, String resourcePath) {}

    private static List<TypeEntry> winners(
            Collection<TypeEntry> entries, Index index, ClasspathOrder classpath) {
        ClasspathOrder order = classpath == null ? ClasspathOrder.UNRESTRICTED : classpath;
        Map<String, TypeEntry> byJvm = new LinkedHashMap<>();
        for (TypeEntry entry : entries) {
            byJvm.computeIfAbsent(entry.jvmOwnerName(),
                    jvm -> order.pick(index.getAll(jvm), TypeEntry::sourceUri));
        }
        List<TypeEntry> out = new ArrayList<>(byJvm.size());
        for (TypeEntry entry : byJvm.values()) {
            if (entry != null) out.add(entry);
        }
        return out;
    }
}
