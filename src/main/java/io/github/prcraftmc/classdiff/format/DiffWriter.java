package io.github.prcraftmc.classdiff.format;

import com.github.difflib.patch.Patch;
import io.github.prcraftmc.classdiff.util.MemberName;
import io.github.prcraftmc.classdiff.util.PatchWriter;
import io.github.prcraftmc.classdiff.util.ReflectUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.tree.*;

import java.util.*;

public class DiffWriter extends DiffVisitor {
    private final SymbolTable symbolTable = new SymbolTable();
    private final PatchWriter<String> classPatchWriter = new PatchWriter<>((vec, value) ->
        vec.putShort(symbolTable.addConstantClass(value).index)
    );
    private final PatchWriter<AnnotationNode> annotationPatchWriter = new PatchWriter<>((vec, value) -> {
        vec.putShort(symbolTable.addConstantUtf8(value.desc)).putShort(0);
        value.accept(new AnnotationWriter(symbolTable, true, vec));
    });
    private final PatchWriter<TypeAnnotationNode> typeAnnotationPatchWriter = new PatchWriter<>((vec, value) -> {
        ReflectUtils.invokeTypeReferencePutTarget(value.typeRef, vec);
        ReflectUtils.invokeTypePathPut(value.typePath, vec);
        vec.putShort(symbolTable.addConstantUtf8(value.desc)).putShort(0);
        value.accept(new AnnotationWriter(symbolTable, true, vec));
    });
    private final PatchWriter<MemberName> memberNamePatchWriter = new PatchWriter<>((vec, value) ->
        vec.putShort(symbolTable.addConstantUtf8(value.name)).putShort(symbolTable.addConstantUtf8(value.descriptor))
    );
    private final PatchWriter<String> packagePatchWriter = new PatchWriter<>((vec, value) ->
        vec.putShort(symbolTable.addConstantPackage(value).index)
    );

    private int diffVersion;
    private int classVersion;
    private int access;
    private int name;
    private int signature;
    private int superName;
    private ByteVector interfaces;

    private int source;
    private int debug;

    private ByteVector innerClasses;

    private int outerClass;
    private int outerMethod;
    private int outerMethodDesc;

    private int nestHost;

    private ByteVector nestMembers;

    private ByteVector permittedSubclasses;

    private ByteVector visibleAnnotations;
    private ByteVector invisibleAnnotations;

    private ByteVector visibleTypeAnnotations;
    private ByteVector invisibleTypeAnnotations;

    private ByteVector recordComponentsPatch;
    private final List<ByteVector> recordComponents = new ArrayList<>();

    private ByteVector module;

    private ByteVector fieldsPatch;
    private final List<ByteVector> fields = new ArrayList<>();

    private final Map<Integer, byte @Nullable []> customAttributes = new LinkedHashMap<>();

    public DiffWriter() {
    }

    public DiffWriter(@Nullable DiffVisitor delegate) {
        super(delegate);
    }

    @Override
    public void visit(
        int diffVersion,
        int classVersion,
        int access,
        @Nullable String name,
        @Nullable String signature,
        @Nullable String superName,
        @Nullable Patch<String> interfaces
    ) {
        super.visit(diffVersion, classVersion, access, name, signature, superName, interfaces);

        this.diffVersion = diffVersion;
        this.classVersion = classVersion;
        this.access = access;
        this.name = name != null ? symbolTable.addConstantClass(name).index : 0;
        this.signature = signature != null ? symbolTable.addConstantUtf8(signature) : 0;
        this.superName = superName != null ? symbolTable.addConstantClass(superName).index : 0;

        if (interfaces != null) {
            classPatchWriter.write(this.interfaces = new ByteVector(), interfaces);
        } else {
            this.interfaces = null;
        }
    }

    @Override
    public void visitSource(@Nullable String source, @Nullable String debug) {
        super.visitSource(source, debug);

        this.source = source != null ? symbolTable.addConstantUtf8(source) : 0;
        this.debug = debug != null ? symbolTable.addConstantUtf8(debug) : 0;
    }

    @Override
    public void visitInnerClasses(Patch<InnerClassNode> patch) {
        super.visitInnerClasses(patch);

        new PatchWriter<InnerClassNode>((vec, value) ->
            vec.putShort(symbolTable.addConstantClass(value.name).index)
                .putShort(symbolTable.addConstantClass(value.outerName).index)
                .putShort(symbolTable.addConstantUtf8(value.innerName))
                .putShort(value.access)
        ).write(innerClasses = new ByteVector(), patch);
    }

    @Override
    public void visitOuterClass(
        @Nullable String className,
        @Nullable String methodName,
        @Nullable String methodDescriptor
    ) {
        super.visitOuterClass(className, methodName, methodDescriptor);

        outerClass = className != null ? symbolTable.addConstantClass(className).index : 0;
        outerMethod = methodName != null ? symbolTable.addConstantUtf8(methodName) : 0;
        outerMethodDesc = methodDescriptor != null ? symbolTable.addConstantUtf8(methodDescriptor) : 0;
    }

    @Override
    public void visitNestHost(@Nullable String nestHost) {
        super.visitNestHost(nestHost);

        this.nestHost = nestHost != null ? symbolTable.addConstantClass(nestHost).index : 0;
    }

    @Override
    public void visitNestMembers(Patch<String> patch) {
        super.visitNestMembers(patch);

        classPatchWriter.write(nestMembers = new ByteVector(), patch);
    }

    @Override
    public void visitPermittedSubclasses(Patch<String> patch) {
        super.visitPermittedSubclasses(patch);

        classPatchWriter.write(permittedSubclasses = new ByteVector(), patch);
    }

    @Override
    public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
        super.visitAnnotations(patch, visible);

        final ByteVector vector = new ByteVector();
        if (visible) {
            visibleAnnotations = vector;
        } else {
            invisibleAnnotations = vector;
        }
        annotationPatchWriter.write(vector, patch);
    }

    @Override
    public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
        super.visitTypeAnnotations(patch, visible);

        final ByteVector vector = new ByteVector();
        if (visible) {
            visibleTypeAnnotations = vector;
        } else {
            invisibleTypeAnnotations = vector;
        }
        typeAnnotationPatchWriter.write(vector, patch);
    }

    @Override
    public void visitRecordComponents(Patch<MemberName> patch) {
        super.visitRecordComponents(patch);

        memberNamePatchWriter.write(recordComponentsPatch = new ByteVector(), patch);
    }

    @Override
    public RecordComponentDiffVisitor visitRecordComponent(String name, String descriptor, @Nullable String signature) {
        final RecordComponentDiffVisitor delegate = super.visitRecordComponent(name, descriptor, signature);

        final ByteVector vector = new ByteVector();
        recordComponents.add(vector);

        vector.putShort(symbolTable.addConstantUtf8(name));
        vector.putShort(symbolTable.addConstantUtf8(descriptor));
        vector.putShort(signature != null ? symbolTable.addConstantUtf8(signature) : 0);

        vector.putShort(0);
        return new RecordComponentDiffVisitor(delegate) {
            final int countIndex = vector.size() - 2;
            int sizeIndex;
            int attributeCount;

            @Override
            public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
                super.visitAnnotations(patch, visible);

                preAttr((visible ? "Visible" : "Invisible") + "Annotations");
                annotationPatchWriter.write(vector, patch);
                postAttr();
            }

            @Override
            public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
                super.visitTypeAnnotations(patch, visible);

                preAttr((visible ? "Visible" : "Invisible") + "TypeAnnotations");
                typeAnnotationPatchWriter.write(vector, patch);
                postAttr();
            }

            @Override
            public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
                super.visitCustomAttribute(name, patchOrContents);

                vector.putShort(symbolTable.addConstantUtf8("Custom" + name));
                if (patchOrContents == null) {
                    vector.putInt(1).putByte(0);
                } else {
                    vector.putInt(patchOrContents.length + 1)
                        .putByte(1)
                        .putByteArray(patchOrContents, 0, patchOrContents.length);
                }
                attributeCount++;
            }

            @Override
            public void visitEnd() {
                super.visitEnd();

                final byte[] data = ReflectUtils.getByteVectorData(vector);
                data[countIndex] = (byte)(attributeCount >> 8);
                data[countIndex + 1] = (byte)attributeCount;
            }

            private void preAttr(String name) {
                vector.putShort(symbolTable.addConstantUtf8(name));
                sizeIndex = vector.size();
                vector.putInt(0);
            }

            private void postAttr() {
                final byte[] data = ReflectUtils.getByteVectorData(vector);
                final int index = sizeIndex;
                final int size = vector.size() - sizeIndex - 4;
                data[index] = (byte)(size >>> 24);
                data[index + 1] = (byte)(size >> 16);
                data[index + 2] = (byte)(size >> 8);
                data[index + 3] = (byte)size;
                attributeCount++;
            }
        };
    }

    @Override
    public ModuleDiffVisitor visitModule(@Nullable String name, int access, @Nullable String version) {
        final ModuleDiffVisitor delegate = super.visitModule(name, access, version);

        final ByteVector vector = new ByteVector();
        module = vector;

        vector.putShort(name != null ? symbolTable.addConstantModule(name).index : 0);
        vector.putShort(access);
        vector.putShort(version != null ? symbolTable.addConstantUtf8(version) : 0);
        vector.putShort(0);
        return new ModuleDiffVisitor(delegate) {
            final int countIndex = vector.size() - 2;
            int sizeIndex;
            int attributeCount;

            @Override
            public void visitMainClass(@Nullable String mainClass) {
                super.visitMainClass(mainClass);

                preAttr("MainClass");
                vector.putShort(mainClass != null ? symbolTable.addConstantClass(mainClass).index : 0);
                postAttr();
            }

            @Override
            public void visitPackages(Patch<String> patch) {
                super.visitPackages(patch);

                preAttr("Packages");
                packagePatchWriter.write(vector, patch);
                postAttr();
            }

            @Override
            public void visitRequires(Patch<ModuleRequireNode> patch) {
                super.visitRequires(patch);

                preAttr("Requires");
                new PatchWriter<ModuleRequireNode>((vec, value) -> {
                    vec.putShort(symbolTable.addConstantModule(value.module).index);
                    vec.putShort(value.access);
                    vec.putShort(value.version != null ? symbolTable.addConstantUtf8(value.version) : 0);
                }).write(vector, patch);
                postAttr();
            }

            @Override
            public void visitExports(Patch<ModuleExportNode> patch) {
                super.visitExports(patch);

                preAttr("Exports");
                new PatchWriter<ModuleExportNode>((vec, value) -> {
                    vec.putShort(symbolTable.addConstantPackage(value.packaze).index);
                    vec.putShort(value.access);
                    if (value.modules != null) {
                        vec.putShort(value.modules.size());
                        for (final String module : value.modules) {
                            vec.putShort(symbolTable.addConstantModule(module).index);
                        }
                    } else {
                        vec.putShort(0);
                    }
                }).write(vector, patch);
                postAttr();
            }

            @Override
            public void visitOpens(Patch<ModuleOpenNode> patch) {
                super.visitOpens(patch);

                preAttr("Opens");
                new PatchWriter<ModuleOpenNode>((vec, value) -> {
                    vec.putShort(symbolTable.addConstantPackage(value.packaze).index);
                    vec.putShort(value.access);
                    if (value.modules != null) {
                        vec.putShort(value.modules.size());
                        for (final String module : value.modules) {
                            vec.putShort(symbolTable.addConstantModule(module).index);
                        }
                    } else {
                        vec.putShort(0);
                    }
                }).write(vector, patch);
                postAttr();
            }

            @Override
            public void visitUses(Patch<String> patch) {
                super.visitUses(patch);

                preAttr("Uses");
                classPatchWriter.write(vector, patch);
                postAttr();
            }

            @Override
            public void visitProvides(Patch<ModuleProvideNode> patch) {
                super.visitProvides(patch);

                preAttr("Provides");
                new PatchWriter<ModuleProvideNode>((vec, value) -> {
                    vec.putShort(symbolTable.addConstantClass(value.service).index);
                    if (value.providers != null) {
                        vec.putShort(value.providers.size());
                        for (final String provider : value.providers) {
                            vec.putShort(symbolTable.addConstantClass(provider).index);
                        }
                    } else {
                        vec.putShort(0);
                    }
                }).write(vector, patch);
                postAttr();
            }

            @Override
            public void visitEnd() {
                super.visitEnd();

                final byte[] data = ReflectUtils.getByteVectorData(vector);
                data[countIndex] = (byte)(attributeCount >> 8);
                data[countIndex + 1] = (byte)attributeCount;
            }

            private void preAttr(String name) {
                vector.putShort(symbolTable.addConstantUtf8(name));
                sizeIndex = vector.size();
                vector.putInt(0);
            }

            private void postAttr() {
                final byte[] data = ReflectUtils.getByteVectorData(vector);
                final int index = sizeIndex;
                final int size = vector.size() - sizeIndex - 4;
                data[index] = (byte)(size >>> 24);
                data[index + 1] = (byte)(size >> 16);
                data[index + 2] = (byte)(size >> 8);
                data[index + 3] = (byte)size;
                attributeCount++;
            }
        };
    }

    @Override
    public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
        super.visitCustomAttribute(name, patchOrContents);

        customAttributes.put(symbolTable.addConstantUtf8("Custom" + name), patchOrContents);
    }

    @Override
    public void visitFields(Patch<MemberName> patch) {
        super.visitFields(patch);

        memberNamePatchWriter.write(fieldsPatch = new ByteVector(), patch);
    }

    @Override
    public FieldDiffVisitor visitField(int access, String name, String descriptor, @Nullable String signature, @Nullable Object value) {
        final FieldDiffVisitor delegate = super.visitField(access, name, descriptor, signature, value);

        final ByteVector vector = new ByteVector();
        fields.add(vector);

        vector.putInt(access);
        vector.putShort(symbolTable.addConstantUtf8(name));
        vector.putShort(symbolTable.addConstantUtf8(descriptor));
        vector.putShort(signature != null ? symbolTable.addConstantUtf8(signature) : 0);
        vector.putShort(value != null ? symbolTable.addConstant(value).index : 0);

        vector.putShort(0);
        return new FieldDiffVisitor(delegate) {
            final int countIndex = vector.size() - 2;
            int sizeIndex;
            int attributeCount;

            @Override
            public void visitAnnotations(Patch<AnnotationNode> patch, boolean visible) {
                super.visitAnnotations(patch, visible);

                preAttr((visible ? "Visible" : "Invisible") + "Annotations");
                annotationPatchWriter.write(vector, patch);
                postAttr();
            }

            @Override
            public void visitTypeAnnotations(Patch<TypeAnnotationNode> patch, boolean visible) {
                super.visitTypeAnnotations(patch, visible);

                preAttr((visible ? "Visible" : "Invisible") + "TypeAnnotations");
                typeAnnotationPatchWriter.write(vector, patch);
                postAttr();
            }

            @Override
            public void visitCustomAttribute(String name, byte @Nullable [] patchOrContents) {
                super.visitCustomAttribute(name, patchOrContents);

                vector.putShort(symbolTable.addConstantUtf8("Custom" + name));
                if (patchOrContents == null) {
                    vector.putInt(1).putByte(0);
                } else {
                    vector.putInt(patchOrContents.length + 1)
                        .putByte(1)
                        .putByteArray(patchOrContents, 0, patchOrContents.length);
                }
                attributeCount++;
            }

            @Override
            public void visitEnd() {
                super.visitEnd();

                final byte[] data = ReflectUtils.getByteVectorData(vector);
                data[countIndex] = (byte)(attributeCount >> 8);
                data[countIndex + 1] = (byte)attributeCount;
            }

            private void preAttr(String name) {
                vector.putShort(symbolTable.addConstantUtf8(name));
                sizeIndex = vector.size();
                vector.putInt(0);
            }

            private void postAttr() {
                final byte[] data = ReflectUtils.getByteVectorData(vector);
                final int index = sizeIndex;
                final int size = vector.size() - sizeIndex - 4;
                data[index] = (byte)(size >>> 24);
                data[index + 1] = (byte)(size >> 16);
                data[index + 2] = (byte)(size >> 8);
                data[index + 3] = (byte)size;
                attributeCount++;
            }
        };
    }

    public byte[] toByteArray() {
        final ByteVector result = new ByteVector();

        result.putInt(DiffConstants.MAGIC);
        result.putShort(diffVersion);

        int attributeCount = customAttributes.size();
        if (source != 0 || debug != 0) {
            symbolTable.addConstantUtf8("Source");
            attributeCount++;
        }
        if (innerClasses != null) {
            symbolTable.addConstantUtf8("InnerClasses");
            attributeCount++;
        }
        if (outerClass != 0 || outerMethod != 0 || outerMethodDesc != 0) {
            symbolTable.addConstantUtf8("OuterClass");
            attributeCount++;
        }
        if (nestHost != 0) {
            symbolTable.addConstantUtf8("NestHost");
            attributeCount++;
        }
        if (nestMembers != null) {
            symbolTable.addConstantUtf8("NestMembers");
            attributeCount++;
        }
        if (permittedSubclasses != null) {
            symbolTable.addConstantUtf8("PermittedSubclasses");
            attributeCount++;
        }
        if (visibleAnnotations != null) {
            symbolTable.addConstantUtf8("VisibleAnnotations");
            attributeCount++;
        }
        if (invisibleAnnotations != null) {
            symbolTable.addConstantUtf8("InvisibleAnnotations");
            attributeCount++;
        }
        if (visibleTypeAnnotations != null) {
            symbolTable.addConstantUtf8("VisibleTypeAnnotations");
            attributeCount++;
        }
        if (invisibleTypeAnnotations != null) {
            symbolTable.addConstantUtf8("InvisibleTypeAnnotations");
            attributeCount++;
        }
        if (recordComponentsPatch != null || !recordComponents.isEmpty()) {
            symbolTable.addConstantUtf8("RecordComponents");
            attributeCount++;
        }
        if (module != null) {
            symbolTable.addConstantUtf8("Module");
            attributeCount++;
        }

        symbolTable.putConstantPool(result);

        result.putInt(classVersion);
        result.putInt(access);
        result.putShort(name);
        result.putShort(signature);
        result.putShort(superName);

        if (interfaces == null) {
            result.putShort(0);
        } else {
            result.putByteArray(ReflectUtils.getByteVectorData(interfaces), 0, interfaces.size());
        }

        result.putShort(attributeCount);
        if (source != 0 || debug != 0) {
            result.putShort(symbolTable.addConstantUtf8("Source")).putInt(4);
            result.putShort(source).putShort(debug);
        }
        if (innerClasses != null) {
            result.putShort(symbolTable.addConstantUtf8("InnerClasses")).putInt(innerClasses.size());
            result.putByteArray(ReflectUtils.getByteVectorData(innerClasses), 0, innerClasses.size());
        }
        if (outerClass != 0 || outerMethod != 0 || outerMethodDesc != 0) {
            result.putShort(symbolTable.addConstantUtf8("OuterClass")).putInt(6);
            result.putShort(outerClass).putShort(outerMethod).putShort(outerMethodDesc);
        }
        if (nestHost != 0) {
            result.putShort(symbolTable.addConstantUtf8("NestHost")).putInt(2);
            result.putShort(nestHost);
        }
        if (nestMembers != null) {
            result.putShort(symbolTable.addConstantUtf8("NestMembers")).putInt(nestMembers.size());
            result.putByteArray(ReflectUtils.getByteVectorData(nestMembers), 0, nestMembers.size());
        }
        if (permittedSubclasses != null) {
            result.putShort(symbolTable.addConstantUtf8("PermittedSubclasses")).putInt(permittedSubclasses.size());
            result.putByteArray(ReflectUtils.getByteVectorData(permittedSubclasses), 0, permittedSubclasses.size());
        }
        if (visibleAnnotations != null) {
            result.putShort(symbolTable.addConstantUtf8("VisibleAnnotations")).putInt(visibleAnnotations.size());
            result.putByteArray(ReflectUtils.getByteVectorData(visibleAnnotations), 0, visibleAnnotations.size());
        }
        if (invisibleAnnotations != null) {
            result.putShort(symbolTable.addConstantUtf8("InvisibleAnnotations")).putInt(invisibleAnnotations.size());
            result.putByteArray(ReflectUtils.getByteVectorData(invisibleAnnotations), 0, invisibleAnnotations.size());
        }
        if (visibleTypeAnnotations != null) {
            result.putShort(symbolTable.addConstantUtf8("VisibleTypeAnnotations")).putInt(visibleTypeAnnotations.size());
            result.putByteArray(ReflectUtils.getByteVectorData(visibleTypeAnnotations), 0, visibleTypeAnnotations.size());
        }
        if (invisibleTypeAnnotations != null) {
            result.putShort(symbolTable.addConstantUtf8("InvisibleTypeAnnotations")).putInt(invisibleTypeAnnotations.size());
            result.putByteArray(ReflectUtils.getByteVectorData(invisibleTypeAnnotations), 0, invisibleTypeAnnotations.size());
        }
        if (recordComponentsPatch != null || !recordComponents.isEmpty()) {
            int size = 0;
            if (recordComponentsPatch != null) {
                size += recordComponentsPatch.size();
            } else {
                size += 2;
            }
            size += 2;
            for (final ByteVector component : recordComponents) {
                size += component.size();
            }
            result.putShort(symbolTable.addConstantUtf8("RecordComponents")).putInt(size);
            if (recordComponentsPatch != null) {
                result.putByteArray(ReflectUtils.getByteVectorData(recordComponentsPatch), 0, recordComponentsPatch.size());
            } else {
                result.putShort(0);
            }
            result.putShort(recordComponents.size());
            for (final ByteVector component : recordComponents) {
                result.putByteArray(ReflectUtils.getByteVectorData(component), 0, component.size());
            }
        }
        if (module != null) {
            result.putShort(symbolTable.addConstantUtf8("Module")).putInt(module.size());
            result.putByteArray(ReflectUtils.getByteVectorData(module), 0, module.size());
        }
        for (final Map.Entry<Integer, byte @Nullable []> entry : customAttributes.entrySet()) {
            result.putShort(entry.getKey());
            final byte @Nullable [] value = entry.getValue();
            if (value == null) {
                result.putInt(1).putByte(0);
            } else {
                result.putInt(value.length + 1)
                    .putByte(1)
                    .putByteArray(value, 0, value.length);
            }
        }

        if (fieldsPatch != null) {
            result.putByteArray(ReflectUtils.getByteVectorData(fieldsPatch), 0, fieldsPatch.size());
        } else {
            result.putShort(0);
        }
        result.putShort(fields.size());
        for (final ByteVector field : fields) {
            result.putByteArray(ReflectUtils.getByteVectorData(field), 0, field.size());
        }

        return Arrays.copyOf(ReflectUtils.getByteVectorData(result), result.size());
    }
}
