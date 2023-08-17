package io.github.prcraftmc.classdiff;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.nothome.delta.Delta;
import io.github.prcraftmc.classdiff.format.DiffConstants;
import io.github.prcraftmc.classdiff.format.DiffVisitor;
import io.github.prcraftmc.classdiff.util.Equalizers;
import io.github.prcraftmc.classdiff.util.ReflectUtils;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

public class ClassDiffer {
    private final Delta delta = new Delta();
    private final DiffVisitor output;

    public ClassDiffer(DiffVisitor output) {
        this.output = output;
    }

    public static void diff(ClassNode original, ClassNode modified, DiffVisitor result) {
        new ClassDiffer(result).accept(original, modified);
    }

    public static void diff(ClassReader original, ClassReader modified, DiffVisitor result) {
        final ClassNode aNode = new ClassNode();
        original.accept(aNode, 0);
        final ClassNode bNode = new ClassNode();
        modified.accept(bNode, 0);
        diff(aNode, bNode, result);
    }

    public void accept(ClassNode original, ClassNode modified) {
        final Patch<String> interfacePatch;
        if (Objects.equals(original.interfaces, modified.interfaces)) {
            interfacePatch = null;
        } else {
            interfacePatch = DiffUtils.diff(
                original.interfaces != null ? original.interfaces : Collections.emptyList(),
                modified.interfaces != null ? modified.interfaces : Collections.emptyList()
            );
        }

        output.visit(
            DiffConstants.V1,
            modified.version == original.version ? -1 : modified.version,
            modified.access == original.access ? -1 : modified.access,
            modified.name.equals(original.name) ? null : modified.name,
            Objects.equals(modified.signature, original.signature) ? null : (modified.signature != null ? modified.signature : ""),
            Objects.equals(modified.superName, original.superName) ? null : (modified.superName != null ? modified.superName : ""),
            interfacePatch
        );

        if (
            !Objects.equals(original.sourceFile, modified.sourceFile) ||
                !Objects.equals(original.sourceDebug, modified.sourceDebug)
        ) {
            output.visitSource(
                Objects.equals(original.sourceFile, modified.sourceFile) ? null : modified.sourceFile,
                Objects.equals(original.sourceDebug, modified.sourceDebug) ? null : modified.sourceDebug
            );
        }

        if (!Equalizers.listEquals(original.innerClasses, modified.innerClasses, Equalizers::innerClass)) {
            output.visitInnerClasses(DiffUtils.diff(
                original.innerClasses != null ? original.innerClasses : Collections.emptyList(),
                modified.innerClasses != null ? modified.innerClasses : Collections.emptyList(),
                Equalizers::innerClass
            ));
        }

        if (
            !Objects.equals(original.outerClass, modified.outerClass) ||
                !Objects.equals(original.outerMethod, modified.outerMethod) ||
                !Objects.equals(original.outerMethodDesc, modified.outerMethodDesc)
        ) {
            output.visitOuterClass(
                Objects.equals(original.outerClass, modified.outerClass)
                    ? null : modified.outerClass,
                Objects.equals(original.outerMethod, modified.outerMethod)
                    ? null : (modified.outerMethod == null ? "" : modified.outerMethod),
                Objects.equals(original.outerMethodDesc, modified.outerMethodDesc)
                    ? null : (modified.outerMethodDesc == null ? "" : modified.outerMethodDesc)
            );
        }

        if (!Objects.equals(original.nestHostClass, modified.nestHostClass)) {
            output.visitNestHost(modified.nestHostClass);
        }

        if (!Objects.equals(original.nestMembers, modified.nestMembers)) {
            output.visitNestMembers(DiffUtils.diff(
                original.nestMembers != null ? original.nestMembers : Collections.emptyList(),
                modified.nestMembers != null ? modified.nestMembers : Collections.emptyList()
            ));
        }

        if (!Objects.equals(original.permittedSubclasses, modified.permittedSubclasses)) {
            output.visitPermittedSubclasses(DiffUtils.diff(
                original.permittedSubclasses != null ? original.permittedSubclasses : Collections.emptyList(),
                modified.permittedSubclasses != null ? modified.permittedSubclasses : Collections.emptyList()
            ));
        }

        if (!Equalizers.listEquals(original.visibleAnnotations, modified.visibleAnnotations, Equalizers::annotation)) {
            output.visitAnnotations(DiffUtils.diff(
                original.visibleAnnotations != null ? original.visibleAnnotations : Collections.emptyList(),
                modified.visibleAnnotations != null ? modified.visibleAnnotations : Collections.emptyList(),
                Equalizers::annotation
            ), true);
        }

        if (!Equalizers.listEquals(original.invisibleAnnotations, modified.invisibleAnnotations, Equalizers::annotation)) {
            output.visitAnnotations(DiffUtils.diff(
                original.invisibleAnnotations != null ? original.invisibleAnnotations : Collections.emptyList(),
                modified.invisibleAnnotations != null ? modified.invisibleAnnotations : Collections.emptyList(),
                Equalizers::annotation
            ), false);
        }

        if (!Equalizers.listEquals(original.visibleTypeAnnotations, modified.visibleTypeAnnotations, Equalizers::typeAnnotation)) {
            output.visitTypeAnnotations(DiffUtils.diff(
                original.visibleTypeAnnotations != null ? original.visibleTypeAnnotations : Collections.emptyList(),
                modified.visibleTypeAnnotations != null ? modified.visibleTypeAnnotations : Collections.emptyList(),
                Equalizers::typeAnnotation
            ), true);
        }

        if (!Equalizers.listEquals(original.invisibleTypeAnnotations, modified.invisibleTypeAnnotations, Equalizers::typeAnnotation)) {
            output.visitTypeAnnotations(DiffUtils.diff(
                original.invisibleTypeAnnotations != null ? original.invisibleTypeAnnotations : Collections.emptyList(),
                modified.invisibleTypeAnnotations != null ? modified.invisibleTypeAnnotations : Collections.emptyList(),
                Equalizers::typeAnnotation
            ), false);
        }

        {
            final Map<String, Attribute> bAttributes = new LinkedHashMap<>();
            if (modified.attrs != null) {
                for (final Attribute attr : modified.attrs) {
                    bAttributes.put(attr.type, attr);
                }
            }
            if (original.attrs != null) {
                for (final Attribute attr : original.attrs) {
                    if (!bAttributes.containsKey(attr.type)) {
                        output.visitCustomAttribute(attr.type, null);
                        continue;
                    }
                    final byte[] aContents = ReflectUtils.getAttributeContent(attr);
                    final byte[] bContents = ReflectUtils.getAttributeContent(bAttributes.remove(attr.type));
                    if (!Arrays.equals(aContents, bContents)) {
                        try {
                            output.visitCustomAttribute(attr.type, delta.compute(aContents, bContents));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                }
            }
            for (final Attribute attr : bAttributes.values()) {
                output.visitCustomAttribute(attr.type, ReflectUtils.getAttributeContent(attr));
            }
        }
    }
}
