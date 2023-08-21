package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

public abstract class FieldDiffVisitor implements AnnotatedElementVisitor, CustomAttributableVisitor {
    @Nullable
    private final FieldDiffVisitor delegate;

    public FieldDiffVisitor() {
        delegate = null;
    }

    protected FieldDiffVisitor(@Nullable FieldDiffVisitor delegate) {
        this.delegate = delegate;
    }

    @Nullable
    public FieldDiffVisitor getDelegate() {
        return delegate;
    }

    @Override
    public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
        if (delegate != null) {
            delegate.visitAnnotations(patch, visible);
        }
    }

    @Override
    public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
        if (delegate != null) {
            delegate.visitTypeAnnotations(patch, visible);
        }
    }

    @Override
    public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
        if (delegate != null) {
            delegate.visitCustomAttribute(name, patchOrContents);
        }
    }

    public void visitEnd() {
        if (delegate != null) {
            delegate.visitEnd();
        }
    }
}
