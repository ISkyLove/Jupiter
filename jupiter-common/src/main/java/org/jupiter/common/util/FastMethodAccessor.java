/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jupiter.common.util;

import org.objectweb.asm.*;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.objectweb.asm.Opcodes.*;

/**
 * jupiter
 * org.jupiter.common.util
 *
 * @author jiachun.fjc
 */
public abstract class FastMethodAccessor {

    private static final ConcurrentMap<Class<?>, FastMethodAccessor> fastAccessorCache = Maps.newConcurrentMap();

    private String[] methodNames;
    private Class<?>[][] parameterTypes_s;

    public abstract Object invoke(Object object, int methodIndex, Object... args);

    public Object invoke(Object object, String methodName, Class<?>[] parameterTypes, Object... args) {
        return invoke(object, getIndex(methodName, parameterTypes), args);
    }

    public int getIndex(String methodName, Class<?>... parameterTypes) {
        for (int i = 0; i < methodNames.length; i++) {
            if (methodNames[i].equals(methodName) && Arrays.equals(parameterTypes, parameterTypes_s[i])) {
                return i;
            }
        }
        throw new IllegalArgumentException(
                "unable to find non-private method: " + methodName + " " + Arrays.toString(parameterTypes));
    }

    public static FastMethodAccessor get(Class<?> type) {
        FastMethodAccessor accessor = fastAccessorCache.get(type);
        if (accessor == null) {
            FastMethodAccessor newAccessor = create(type);
            accessor = fastAccessorCache.putIfAbsent(type, newAccessor);
            if (accessor == null) {
                accessor = newAccessor;
            }
        }
        return accessor;
    }

    private static FastMethodAccessor create(Class<?> type) {
        ArrayList<Method> methods = Lists.newArrayList();
        boolean isInterface = type.isInterface();
        if (!isInterface) {
            Class nextClass = type;
            while (nextClass != Object.class) {
                addDeclaredMethodsToList(nextClass, methods);
                nextClass = nextClass.getSuperclass();
            }
        } else {
            recursiveAddInterfaceMethodsToList(type, methods);
        }

        int methodLength = methods.size();
        String[] methodNames = new String[methodLength];
        Class<?>[][] parameterTypes_s = new Class[methodLength][];
        Class<?>[] returnTypes = new Class[methodLength];
        for (int i = 0; i < methodLength; i++) {
            Method method = methods.get(i);
            methodNames[i] = method.getName();
            parameterTypes_s[i] = method.getParameterTypes();
            returnTypes[i] = method.getReturnType();
        }

        String className = type.getName();
        String accessorClassName = className + "FastMethodAccessor";

        String accessorClassNameInternal = accessorClassName.replace('.', '/');
        String classNameInternal = className.replace('.', '/');

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        MethodVisitor mv;
        cw.visit(V1_1, ACC_PUBLIC + ACC_SUPER, accessorClassNameInternal, null, "org/jupiter/common/util/FastMethodAccessor", null);
        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "org/jupiter/common/util/FastMethodAccessor", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "invoke", "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
            mv.visitCode();

            if (!methods.isEmpty()) {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, classNameInternal);
                mv.visitVarInsn(ASTORE, 4);

                mv.visitVarInsn(ILOAD, 2);
                Label[] labels = new Label[methodLength];
                for (int i = 0; i < methodLength; i++) {
                    labels[i] = new Label();
                }
                Label defaultLabel = new Label();
                mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

                StringBuilder buf = new StringBuilder(128);
                for (int i = 0; i < methodLength; i++) {
                    mv.visitLabel(labels[i]);
                    if (i == 0) {
                        mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] { classNameInternal }, 0, null);
                    } else {
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                    mv.visitVarInsn(ALOAD, 4);

                    buf.setLength(0);
                    buf.append('(');

                    Class<?>[] parameterTypes = parameterTypes_s[i];
                    Class<?> returnType = returnTypes[i];
                    for (int parameterIndex = 0; parameterIndex < parameterTypes.length; parameterIndex++) {
                        mv.visitVarInsn(ALOAD, 3);
                        mv.visitIntInsn(BIPUSH, parameterIndex);
                        mv.visitInsn(AALOAD);

                        Type p_type = Type.getType(parameterTypes[parameterIndex]);
                        switch (p_type.getSort()) {
                            case Type.BOOLEAN:
                                mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                                break;
                            case Type.BYTE:
                                mv.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
                                break;
                            case Type.CHAR:
                                mv.visitTypeInsn(CHECKCAST, "java/lang/Character");
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                                break;
                            case Type.SHORT:
                                mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                                break;
                            case Type.INT:
                                mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                                break;
                            case Type.FLOAT:
                                mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                                break;
                            case Type.LONG:
                                mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                                break;
                            case Type.DOUBLE:
                                mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                                break;
                            case Type.ARRAY:
                                mv.visitTypeInsn(CHECKCAST, p_type.getDescriptor());
                                break;
                            case Type.OBJECT:
                                mv.visitTypeInsn(CHECKCAST, p_type.getInternalName());
                                break;
                        }
                        buf.append(p_type.getDescriptor());
                    }

                    buf.append(')');
                    buf.append(Type.getDescriptor(returnType));
                    int invoke;
                    if (isInterface) {
                        invoke = INVOKEINTERFACE;
                    } else if (Modifier.isStatic(methods.get(i).getModifiers())) {
                        invoke = INVOKESTATIC;
                    } else {
                        invoke = INVOKEVIRTUAL;
                    }
                    mv.visitMethodInsn(invoke, classNameInternal, methodNames[i], buf.toString(), invoke == INVOKEINTERFACE);

                    Type r_type = Type.getType(returnType);
                    switch (r_type.getSort()) {
                        case Type.VOID:
                            mv.visitInsn(ACONST_NULL);
                            break;
                        case Type.BOOLEAN:
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                            break;
                        case Type.BYTE:
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                            break;
                        case Type.CHAR:
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                            break;
                        case Type.SHORT:
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                            break;
                        case Type.INT:
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                            break;
                        case Type.FLOAT:
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                            break;
                        case Type.LONG:
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                            break;
                        case Type.DOUBLE:
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                            break;
                    }
                    mv.visitInsn(ARETURN);
                }
                mv.visitLabel(defaultLabel);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
            mv.visitInsn(DUP);
            mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
            mv.visitInsn(DUP);
            mv.visitLdcInsn("method not found: ");
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
            mv.visitVarInsn(ILOAD, 2);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false);
            mv.visitInsn(ATHROW);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();
        byte[] bytes = cw.toByteArray();

        AccessorClassLoader loader = AccessorClassLoader.get(type);
        Class<?> accessorClass = loader.defineClass(accessorClassName, bytes);

        try {
            FastMethodAccessor accessor = (FastMethodAccessor) accessorClass.newInstance();
            accessor.methodNames = methodNames;
            accessor.parameterTypes_s = parameterTypes_s;
            return accessor;
        } catch (Throwable t) {
            throw new RuntimeException("Error constructing method access class: " + accessorClass, t);
        }
    }

    private static void addDeclaredMethodsToList(Class<?> type, ArrayList<Method> methods) {
        Method[] declaredMethods = type.getDeclaredMethods();
        for (Method method : declaredMethods) {
            if (!Modifier.isPrivate(method.getModifiers())) {
                methods.add(method);
            }
        }
    }

    private static void recursiveAddInterfaceMethodsToList(Class<?> interfaceType, ArrayList<Method> methods) {
        addDeclaredMethodsToList(interfaceType, methods);
        for (Class nextInterface : interfaceType.getInterfaces()) {
            recursiveAddInterfaceMethodsToList(nextInterface, methods);
        }
    }

    static class AccessorClassLoader extends ClassLoader {

        private static final WeakHashMap<ClassLoader, WeakReference<AccessorClassLoader>> accessorClassLoaders
                = new WeakHashMap<>();

        private static final ClassLoader selfContextParentClassLoader =
                getParentClassLoader(AccessorClassLoader.class);
        private static volatile AccessorClassLoader selfContextAccessorClassLoader =
                new AccessorClassLoader(selfContextParentClassLoader);

        public AccessorClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> defineClass(String name, byte[] bytes) throws ClassFormatError {
            return defineClass(name, bytes, 0, bytes.length, getClass().getProtectionDomain());
        }

        static AccessorClassLoader get(Class<?> type) {
            ClassLoader parent = getParentClassLoader(type);
            if (selfContextParentClassLoader.equals(parent)) {
                if (selfContextAccessorClassLoader == null) {
                    synchronized (accessorClassLoaders) { // DCL with volatile semantics
                        if (selfContextAccessorClassLoader == null)
                            selfContextAccessorClassLoader = new AccessorClassLoader(selfContextParentClassLoader);
                    }
                }
                return selfContextAccessorClassLoader;
            }
            synchronized (accessorClassLoaders) {
                WeakReference<AccessorClassLoader> ref = accessorClassLoaders.get(parent);
                if (ref != null) {
                    AccessorClassLoader accessorClassLoader = ref.get();
                    if (accessorClassLoader != null) {
                        return accessorClassLoader;
                    } else {
                        accessorClassLoaders.remove(parent); // the value has been GC-reclaimed, but still not the key (defensive sanity)
                    }
                }
                AccessorClassLoader accessorClassLoader = new AccessorClassLoader(parent);
                accessorClassLoaders.put(parent, new WeakReference<>(accessorClassLoader));
                return accessorClassLoader;
            }
        }

        private static ClassLoader getParentClassLoader(Class<?> type) {
            ClassLoader parent = type.getClassLoader();
            if (parent == null) {
                parent = ClassLoader.getSystemClassLoader();
            }
            return parent;
        }
    }
}