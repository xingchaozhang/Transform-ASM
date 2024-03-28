package com.enjoy.asminject;


import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class ASMUnitTest {


    @Test
    public void test() throws IOException {
        /*
         * 1、获得待插桩的字节码数据
         */
        FileInputStream fis = new FileInputStream("D:\\Android\\TransformApi\\app\\src\\test\\java\\com\\enjoy\\asminject\\InjectTest.class");
        /*
         * 2、通过一个分析器，去读class文件，执行插桩
         */
        ClassReader classReader = new ClassReader(fis);  // class解析器
        // 执行解析，COMPUTE_FRAMES可以自动计算栈帧，安卓中一般都用这个。
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        // 开始插桩
        classReader.accept(new MyClassVisitor(Opcodes.ASM9, classWriter), 0);
        /*
         * 3、插桩结束后是一个字节码数据，写入一个文件，一般的，我们会直接覆盖源文件输出结果
         */
        byte[] bytes = classWriter.toByteArray();
        FileOutputStream fos = new FileOutputStream("D:\\Android\\TransformApi\\app\\src\\test\\java\\com\\enjoy\\asminject\\InjectTest.class");
        fos.write(bytes);
        fos.close();
    }

    /**
     * 一个访问类，在这里边我们会实现所有的插桩实现。
     */
    static class MyClassVisitor extends ClassVisitor {
        public MyClassVisitor(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        /**
         * 读取class文件的信息的时候，每次读一个方法，就会执行这个api。这个方法无法知道方法内部具体有哪些代码。
         * @param access the method's access flags (see {@link Opcodes}). This parameter also indicates if
         *     the method is synthetic and/or deprecated.
         * @param name the method's name.
         * @param descriptor the method's descriptor (see {@link Type}).
         * @param signature the method's signature. May be {@literal null} if the method parameters,
         *     return type and exceptions do not use generic types.
         * @param exceptions the internal names of the method's exception classes (see {@link
         *     Type#getInternalName()}). May be {@literal null}.
         * @return
         */
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            System.out.println(name);
            return new MyMethodVisitor(api, methodVisitor, access, name, descriptor);
        }
    }

    /**
     * visitMethod无法访问方法内部的内容，因此我们需要通过自定义MyMethodVisitor来完成插桩，用他来处理方法。
     */
    static class MyMethodVisitor extends AdviceAdapter {
        int startTime;
        boolean isInject = false;

        protected MyMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
            super(api, methodVisitor, access, name, descriptor);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals("Lcom/enjoy/asminject/ASMTest;")) {
                isInject = true;
            }
            return super.visitAnnotation(descriptor, visible);
        }

        /**
         * 方法进入时执行。
         */
        @Override
        protected void onMethodEnter() {
            //    INVOKESTATIC java/lang/System.currentTimeMillis ()J
            //    LSTORE 1
            super.onMethodEnter();
            if (!isInject){
                return;
            }
            // 下边这一行 INVOKESTATIC java/lang/System.currentTimeMillis ()J
            // System是一个对象，因此需要在最前边加上L， ()J表示一个方法的签名标识。
            invokeStatic(Type.getType("Ljava/lang/System;"), new Method("currentTimeMillis", "()J"));
            // 下边两行便是 LSTORE 1
            startTime = newLocal(Type.LONG_TYPE);
            storeLocal(startTime);
        }

        /**
         * 方法退出时执行。
         * @param opcode one of {@link Opcodes#RETURN}, {@link Opcodes#IRETURN}, {@link Opcodes#FRETURN},
         *     {@link Opcodes#ARETURN}, {@link Opcodes#LRETURN}, {@link Opcodes#DRETURN} or {@link
         *     Opcodes#ATHROW}.
         */
        @Override
        protected void onMethodExit(int opcode) {
            super.onMethodExit(opcode);
            if (!isInject){
                return;
            }
            //    下边的逻辑就开始处理后边的字节码。
            //    INVOKESTATIC java/lang/System.currentTimeMillis ()J
            //    LSTORE 3
            //    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
            //    NEW java/lang/StringBuilder
            //    DUP
            //    INVOKESPECIAL java/lang/StringBuilder.<init> ()V
            //    LDC "execute:"
            //    INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;
            //    LLOAD 3
            //    LLOAD 1
            //    LSUB
            //    INVOKEVIRTUAL java/lang/StringBuilder.append (J)Ljava/lang/StringBuilder;
            //    INVOKEVIRTUAL java/lang/StringBuilder.toString ()Ljava/lang/String;
            //    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V

            invokeStatic(Type.getType("Ljava/lang/System;"), new Method("currentTimeMillis", "()J"));
            int endTime = newLocal(Type.LONG_TYPE);
            storeLocal(endTime);

            getStatic(Type.getType("Ljava/lang/System;"), "out", Type.getType("Ljava/io/PrintStream;"));
            newInstance(Type.getType("Ljava/lang/StringBuilder;"));
            dup();

            invokeConstructor(Type.getType("Ljava/lang/StringBuilder;"), new Method("<init>", "()V"));

            visitLdcInsn("execute:");

            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));

            loadLocal(endTime);
            loadLocal(startTime);
            math(SUB, Type.LONG_TYPE);

            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("append", "(J)Ljava/lang/StringBuilder;"));
            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("toString", "()Ljava/lang/String;"));
            invokeVirtual(Type.getType("Ljava/io/PrintStream;"), new Method("println", "(Ljava/lang/String;)V"));

        }
    }
}
