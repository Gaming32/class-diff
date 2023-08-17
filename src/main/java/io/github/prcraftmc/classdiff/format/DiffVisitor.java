package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import org.jetbrains.annotations.Nullable;

public class DiffVisitor {
    @Nullable
    private final DiffVisitor delegate;

    public DiffVisitor() {
        delegate = null;
    }

    protected DiffVisitor(@Nullable DiffVisitor delegate) {
        this.delegate = delegate;
    }

    @Nullable
    public DiffVisitor getDelegate() {
        return delegate;
    }

    public void visit(
        int diffVersion,
        int classVersion,
        int access,
        @Nullable String name,
        @Nullable String signature,
        @Nullable String superName,
        @Nullable Patch<String> interfaces
    ) {
        if (delegate != null) {
            delegate.visit(diffVersion, classVersion, access, name, signature, superName, interfaces);
        }
    }

    public void visitSource(@Nullable String source, @Nullable String debug) {
        if (delegate != null) {
            delegate.visitSource(source, debug);
        }
    }

    public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
        if (delegate != null) {
            delegate.visitCustomAttribute(name, patchOrContents);
        }
    }
}
