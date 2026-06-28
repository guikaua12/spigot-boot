/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.core.proxy;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProxyGenerator {

    private static final String INTERCEPTOR = "tech/guilhermekaua/spigotboot/core/proxy/MethodInterceptor";
    private static final String INTERCEPTOR_DESC = "L" + INTERCEPTOR + ";";
    private static final String PROXY_IFACE = "tech/guilhermekaua/spigotboot/core/proxy/SpigotBootProxy";
    private static final String INVOKE_DESC =
            "(Ljava/lang/Object;Ljava/lang/reflect/Method;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;";

    // -------- constant pool state --------
    private final ByteArrayOutputStream cpBuf = new ByteArrayOutputStream(512);
    private int cpCount = 1;
    private final Map<String, Integer> cpCache = new HashMap<String, Integer>();

    // -------- collected field/method bytecodes --------
    private final List<byte[]> fields = new ArrayList<byte[]>();
    private final List<byte[]> methods = new ArrayList<byte[]>();
    private final List<Integer> interfaces = new ArrayList<Integer>();

    private String proxyInternal;
    private int thisClassIdx;
    private int superClassIdx;

    // ================================================================
    //  PUBLIC ENTRY POINT
    // ================================================================

    static byte[] generate(String proxyInternalName, Class<?> target) {
        ProxyGenerator gen = new ProxyGenerator();
        return gen.doGenerate(proxyInternalName, target);
    }

    // ================================================================
    //  MAIN GENERATION LOGIC
    // ================================================================

    private byte[] doGenerate(String proxyName, Class<?> target) {
        this.proxyInternal = proxyName;
        String superName = target.isInterface() ? "java/lang/Object" : internal(target);

        thisClassIdx = cpClass(proxyName);
        superClassIdx = cpClass(superName);
        interfaces.add(cpClass(PROXY_IFACE));
        if (target.isInterface()) {
            interfaces.add(cpClass(internal(target)));
        }

        // handler field
        addField(0x0002, "_handler", INTERCEPTOR_DESC); // ACC_PRIVATE

        // collect methods to proxy
        List<Method> proxyable = getProxyableMethods(target);

        // static Method fields for thisMethod and proceed
        for (int i = 0; i < proxyable.size(); i++) {
            addField(0x000A, "_m" + i, "Ljava/lang/reflect/Method;"); // PRIVATE STATIC
            addField(0x000A, "_p" + i, "Ljava/lang/reflect/Method;"); // PRIVATE STATIC
        }

        // constructors
        if (target.isInterface()) {
            addConstructor(superName, new Class<?>[0]);
        } else {
            for (Constructor<?> ctor : target.getDeclaredConstructors()) {
                int mod = ctor.getModifiers();
                if (Modifier.isPrivate(mod)) continue;
                addConstructor(superName, ctor.getParameterTypes());
            }
        }

        // setHandler / getHandler
        addSetHandler();
        addGetHandler();

        // method overrides + proceed methods
        for (int i = 0; i < proxyable.size(); i++) {
            Method m = proxyable.get(i);
            addMethodOverride(i, m, superName, target.isInterface());
            addProceedMethod(i, m, superName, target.isInterface());
        }

        // <clinit> static initializer
        addClinit(proxyable, target);

        return serialize(superName);
    }

    // ================================================================
    //  METHOD COLLECTION
    // ================================================================

    private static List<Method> getProxyableMethods(Class<?> target) {
        Map<String, Method> bySignature = new LinkedHashMap<String, Method>();

        if (target.isInterface()) {
            for (Method m : target.getMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (isSkippedMethod(m)) continue;
                String sig = m.getName() + methodDesc(m);
                bySignature.putIfAbsent(sig, m);
            }
            for (String name : new String[]{"toString", "hashCode", "equals"}) {
                for (Method m : Object.class.getDeclaredMethods()) {
                    if (m.getName().equals(name)) {
                        String sig = m.getName() + methodDesc(m);
                        bySignature.putIfAbsent(sig, m);
                    }
                }
            }
        } else {
            for (Class<?> c = target; c != null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    int mod = m.getModifiers();
                    if (Modifier.isStatic(mod) || Modifier.isFinal(mod) || Modifier.isPrivate(mod)) continue;
                    if (m.isBridge()) continue;
                    if (isSkippedMethod(m)) continue;
                    String sig = m.getName() + methodDesc(m);
                    bySignature.putIfAbsent(sig, m);
                }
            }
        }

        return new ArrayList<Method>(bySignature.values());
    }

    private static boolean isSkippedMethod(Method m) {
        if (m.getDeclaringClass() == Object.class) {
            String name = m.getName();
            return !"toString".equals(name) && !"hashCode".equals(name) && !"equals".equals(name);
        }
        return false;
    }

    // ================================================================
    //  CONSTRUCTOR GENERATION
    // ================================================================

    private void addConstructor(String superName, Class<?>[] paramTypes) {
        String desc = ctorDesc(paramTypes);
        int superInit = cpMethod(superName, "<init>", desc);
        int codeAttr = cpUtf8("Code");

        ByteArrayOutputStream code = new ByteArrayOutputStream();
        code.write(0x2A); // aload_0
        int slot = 1;
        for (Class<?> p : paramTypes) {
            emitLoad(code, p, slot);
            slot += slotSize(p);
        }
        code.write(0xB7); // invokespecial
        w2(code, superInit);
        code.write(0xB1); // return

        byte[] codeBytes = code.toByteArray();
        methods.add(buildMethod(0x0001, "<init>", desc, codeAttr, codeBytes,
                slot + 2, slot, null));
    }

    // ================================================================
    //  SET/GET HANDLER
    // ================================================================

    private void addSetHandler() {
        int handlerField = cpField(proxyInternal, "_handler", INTERCEPTOR_DESC);
        int codeAttr = cpUtf8("Code");

        ByteArrayOutputStream code = new ByteArrayOutputStream();
        code.write(0x2A); // aload_0
        code.write(0x19); // aload
        code.write(1);    // slot 1
        code.write(0xB5); // putfield
        w2(code, handlerField);
        code.write(0xB1); // return

        byte[] codeBytes = code.toByteArray();
        methods.add(buildMethod(0x0001, "setHandler",
                "(" + INTERCEPTOR_DESC + ")V",
                codeAttr, codeBytes, 2, 2, null));
    }

    private void addGetHandler() {
        int handlerField = cpField(proxyInternal, "_handler", INTERCEPTOR_DESC);
        int codeAttr = cpUtf8("Code");

        ByteArrayOutputStream code = new ByteArrayOutputStream();
        code.write(0x2A); // aload_0
        code.write(0xB4); // getfield
        w2(code, handlerField);
        code.write(0xB0); // areturn

        byte[] codeBytes = code.toByteArray();
        methods.add(buildMethod(0x0001, "getHandler",
                "()" + INTERCEPTOR_DESC,
                codeAttr, codeBytes, 1, 1, null));
    }

    // ================================================================
    //  METHOD OVERRIDE GENERATION
    // ================================================================

    private void addMethodOverride(int methodIndex, Method m, String superName, boolean isInterface) {
        int handlerFieldRef = cpField(proxyInternal, "_handler", INTERCEPTOR_DESC);
        int thisMethodFieldRef = cpField(proxyInternal, "_m" + methodIndex, "Ljava/lang/reflect/Method;");
        int proceedFieldRef = cpField(proxyInternal, "_p" + methodIndex, "Ljava/lang/reflect/Method;");
        int invokeRef = cpIMethod(INTERCEPTOR, "invoke", INVOKE_DESC);
        String ownerForSuper = isInterface ? internal(m.getDeclaringClass()) : superName;
        int superMethodRef = isInterface
                ? cpIMethod(ownerForSuper, m.getName(), methodDesc(m))
                : cpMethod(ownerForSuper, m.getName(), methodDesc(m));
        int objectClassRef = cpClass("java/lang/Object");
        int codeAttr = cpUtf8("Code");

        Class<?>[] params = m.getParameterTypes();
        Class<?> retType = m.getReturnType();

        // build handler invocation path
        ByteArrayOutputStream handlerCode = new ByteArrayOutputStream();
        // handler is already on stack from dup
        handlerCode.write(0x2A); // aload_0 (self)
        handlerCode.write(0xB2); // getstatic _m_i
        w2(handlerCode, thisMethodFieldRef);
        handlerCode.write(0xB2); // getstatic _p_i
        w2(handlerCode, proceedFieldRef);
        // build Object[] args
        emitPushInt(handlerCode, params.length);
        handlerCode.write(0xBD); // anewarray
        w2(handlerCode, objectClassRef);
        int slot = 1;
        for (int i = 0; i < params.length; i++) {
            handlerCode.write(0x59); // dup (array)
            emitPushInt(handlerCode, i);
            emitLoad(handlerCode, params[i], slot);
            emitBox(handlerCode, params[i]);
            handlerCode.write(0x53); // aastore
            slot += slotSize(params[i]);
        }
        // invokeinterface
        handlerCode.write(0xB9); // invokeinterface
        w2(handlerCode, invokeRef);
        handlerCode.write(5); // count
        handlerCode.write(0); // must be 0
        // handle return
        emitReturnFromHandler(handlerCode, retType);

        byte[] handlerBytes = handlerCode.toByteArray();

        // build super call path
        ByteArrayOutputStream superCode = new ByteArrayOutputStream();
        superCode.write(0x57); // pop (null handler from dup)
        superCode.write(0x2A); // aload_0
        int slot2 = 1;
        for (Class<?> p : params) {
            emitLoad(superCode, p, slot2);
            slot2 += slotSize(p);
        }
        if (isInterface) {
            // version 49 class files cannot use invokespecial on InterfaceMethodref,
            // so both default and abstract interface methods return the type default
            superCode = new ByteArrayOutputStream();
            superCode.write(0x57); // pop
            emitDefaultReturn(superCode, retType);
            byte[] superBytes = superCode.toByteArray();
            emitFullOverride(methodIndex, m, handlerFieldRef, handlerBytes, superBytes,
                    codeAttr, slot, retType);
            return;
        } else {
            superCode.write(0xB7); // invokespecial
        }
        w2(superCode, superMethodRef);
        emitReturn(superCode, retType);

        byte[] superBytes = superCode.toByteArray();

        emitFullOverride(methodIndex, m, handlerFieldRef, handlerBytes, superBytes,
                codeAttr, slot, retType);
    }

    private void emitFullOverride(int methodIndex, Method m, int handlerFieldRef,
                                  byte[] handlerBytes, byte[] superBytes,
                                  int codeAttr, int maxLocals, Class<?> retType) {
        ByteArrayOutputStream code = new ByteArrayOutputStream();
        code.write(0x2A); // aload_0
        code.write(0xB4); // getfield _handler
        w2(code, handlerFieldRef);
        code.write(0x59); // dup
        // ifnull offset = 3 + handlerBytes.length
        code.write(0xC6); // ifnull
        int offset = 3 + handlerBytes.length;
        w2(code, offset);
        // handler path
        write(code, handlerBytes);
        // super path
        write(code, superBytes);

        byte[] codeBytes = code.toByteArray();
        int access = Modifier.isPublic(m.getModifiers()) ? 0x0001
                : Modifier.isProtected(m.getModifiers()) ? 0x0004
                : 0x0000;
        if (Modifier.isSynchronized(m.getModifiers())) access |= 0x0020;
        methods.add(buildMethod(access, m.getName(), methodDesc(m),
                codeAttr, codeBytes, 10, Math.max(maxLocals, 1), null));
    }

    // ================================================================
    //  PROCEED METHOD GENERATION
    // ================================================================

    private void addProceedMethod(int methodIndex, Method m, String superName, boolean isInterface) {
        int codeAttr = cpUtf8("Code");
        Class<?>[] params = m.getParameterTypes();
        Class<?> retType = m.getReturnType();

        ByteArrayOutputStream code = new ByteArrayOutputStream();

        if (isInterface) {
            // version 49 class files cannot use invokespecial on InterfaceMethodref
            emitDefaultReturn(code, retType);
        } else {
            int superMethodRef = cpMethod(superName, m.getName(), methodDesc(m));
            code.write(0x2A); // aload_0
            int slot = 1;
            for (Class<?> p : params) {
                emitLoad(code, p, slot);
                slot += slotSize(p);
            }
            code.write(0xB7); // invokespecial
            w2(code, superMethodRef);
            emitReturn(code, retType);
        }

        byte[] codeBytes = code.toByteArray();
        int maxLocals = 1;
        for (Class<?> p : params) maxLocals += slotSize(p);

        methods.add(buildMethod(0x0001, "_proceed_" + methodIndex, methodDesc(m),
                codeAttr, codeBytes, Math.max(maxLocals + 1, 4), Math.max(maxLocals, 1), null));
    }

    // ================================================================
    //  STATIC INITIALIZER (<clinit>)
    // ================================================================

    private void addClinit(List<Method> proxyable, Class<?> target) {
        if (proxyable.isEmpty()) return;

        int codeAttr = cpUtf8("Code");
        int getDeclaredMethodRef = cpMethod("java/lang/Class", "getDeclaredMethod",
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
        int setAccessibleRef = cpMethod("java/lang/reflect/Method", "setAccessible", "(Z)V");
        int classClassRef = cpClass("java/lang/Class");
        int runtimeExRef = cpClass("java/lang/RuntimeException");
        int runtimeExInit = cpMethod("java/lang/RuntimeException", "<init>",
                "(Ljava/lang/Throwable;)V");
        int exceptionClassRef = cpClass("java/lang/Exception");

        ByteArrayOutputStream code = new ByteArrayOutputStream();

        for (int i = 0; i < proxyable.size(); i++) {
            Method m = proxyable.get(i);
            Class<?>[] params = m.getParameterTypes();

            // _m_i = declaring class . getDeclaredMethod("name", new Class[] { ... })
            int mFieldRef = cpField(proxyInternal, "_m" + i, "Ljava/lang/reflect/Method;");
            emitLdcClass(code, m.getDeclaringClass());
            emitLdcString(code, m.getName());
            emitClassArray(code, params, classClassRef);
            code.write(0xB6); // invokevirtual
            w2(code, getDeclaredMethodRef);
            code.write(0x59); // dup
            code.write(0x04); // iconst_1
            code.write(0xB6); // invokevirtual setAccessible
            w2(code, setAccessibleRef);
            code.write(0xB3); // putstatic
            w2(code, mFieldRef);

            // _p_i = proxyClass . getDeclaredMethod("_proceed_i", new Class[] { ... })
            int pFieldRef = cpField(proxyInternal, "_p" + i, "Ljava/lang/reflect/Method;");
            emitLdcClass(code, null); // proxy class (use thisClassIdx)
            emitLdcString(code, "_proceed_" + i);
            emitClassArray(code, params, classClassRef);
            code.write(0xB6); // invokevirtual
            w2(code, getDeclaredMethodRef);
            code.write(0x59); // dup
            code.write(0x04); // iconst_1
            code.write(0xB6); // invokevirtual setAccessible
            w2(code, setAccessibleRef);
            code.write(0xB3); // putstatic
            w2(code, pFieldRef);
        }

        int tryEnd = code.size();
        code.write(0xB1); // return
        int handlerPc = code.size();

        // catch handler: wrap in RuntimeException and throw
        int exLocal = 1;
        code.write(0x3A); // astore
        code.write(exLocal);
        code.write(0xBB); // new RuntimeException
        w2(code, runtimeExRef);
        code.write(0x59); // dup
        code.write(0x19); // aload
        code.write(exLocal);
        code.write(0xB7); // invokespecial RuntimeException.<init>(Throwable)
        w2(code, runtimeExInit);
        code.write(0xBF); // athrow

        byte[] codeBytes = code.toByteArray();

        // exception table: one entry covering the whole try block
        byte[] exceptionTable = new byte[8];
        exceptionTable[0] = 0; exceptionTable[1] = 0; // start_pc = 0
        exceptionTable[2] = (byte)(tryEnd >> 8); exceptionTable[3] = (byte)(tryEnd & 0xFF);
        exceptionTable[4] = (byte)(handlerPc >> 8); exceptionTable[5] = (byte)(handlerPc & 0xFF);
        exceptionTable[6] = (byte)(exceptionClassRef >> 8); exceptionTable[7] = (byte)(exceptionClassRef & 0xFF);

        methods.add(buildMethod(0x0008, "<clinit>", "()V", codeAttr, codeBytes,
                8, 2, exceptionTable));
    }

    // ================================================================
    //  CLASS FILE SERIALIZATION
    // ================================================================

    private byte[] serialize(String superName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);

        // magic
        w4(out, 0xCAFEBABE);
        // version 49.0 (Java 5): JVM always uses the type-inference verifier for < 50,
        // so no StackMapTable attribute is needed — works on all JDKs including 21+
        w2(out, 0); // minor
        w2(out, 49); // major

        // constant pool
        w2(out, cpCount);
        write(out, cpBuf.toByteArray());

        // access flags: ACC_PUBLIC | ACC_SUPER
        w2(out, 0x0021);
        // this class
        w2(out, thisClassIdx);
        // super class
        w2(out, superClassIdx);
        // interfaces
        w2(out, interfaces.size());
        for (int idx : interfaces) w2(out, idx);
        // fields
        w2(out, fields.size());
        for (byte[] f : fields) write(out, f);
        // methods
        w2(out, methods.size());
        for (byte[] m : methods) write(out, m);
        // class attributes
        w2(out, 0);

        return out.toByteArray();
    }

    // ================================================================
    //  CONSTANT POOL HELPERS
    // ================================================================

    private int cpUtf8(String s) {
        String key = "u\0" + s;
        Integer cached = cpCache.get(key);
        if (cached != null) return cached;
        int idx = cpCount++;
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        cpBuf.write(1); // CONSTANT_Utf8
        w2(cpBuf, bytes.length);
        cpBuf.write(bytes, 0, bytes.length);
        cpCache.put(key, idx);
        return idx;
    }

    private int cpClass(String internalName) {
        String key = "c\0" + internalName;
        Integer cached = cpCache.get(key);
        if (cached != null) return cached;
        int nameIdx = cpUtf8(internalName);
        int idx = cpCount++;
        cpBuf.write(7); // CONSTANT_Class
        w2(cpBuf, nameIdx);
        cpCache.put(key, idx);
        return idx;
    }

    private int cpNameType(String name, String desc) {
        String key = "nt\0" + name + "\0" + desc;
        Integer cached = cpCache.get(key);
        if (cached != null) return cached;
        int nIdx = cpUtf8(name);
        int dIdx = cpUtf8(desc);
        int idx = cpCount++;
        cpBuf.write(12); // CONSTANT_NameAndType
        w2(cpBuf, nIdx);
        w2(cpBuf, dIdx);
        cpCache.put(key, idx);
        return idx;
    }

    private int cpField(String owner, String name, String desc) {
        String key = "f\0" + owner + "\0" + name + "\0" + desc;
        Integer cached = cpCache.get(key);
        if (cached != null) return cached;
        int cIdx = cpClass(owner);
        int ntIdx = cpNameType(name, desc);
        int idx = cpCount++;
        cpBuf.write(9); // CONSTANT_Fieldref
        w2(cpBuf, cIdx);
        w2(cpBuf, ntIdx);
        cpCache.put(key, idx);
        return idx;
    }

    private int cpMethod(String owner, String name, String desc) {
        String key = "m\0" + owner + "\0" + name + "\0" + desc;
        Integer cached = cpCache.get(key);
        if (cached != null) return cached;
        int cIdx = cpClass(owner);
        int ntIdx = cpNameType(name, desc);
        int idx = cpCount++;
        cpBuf.write(10); // CONSTANT_Methodref
        w2(cpBuf, cIdx);
        w2(cpBuf, ntIdx);
        cpCache.put(key, idx);
        return idx;
    }

    private int cpIMethod(String owner, String name, String desc) {
        String key = "im\0" + owner + "\0" + name + "\0" + desc;
        Integer cached = cpCache.get(key);
        if (cached != null) return cached;
        int cIdx = cpClass(owner);
        int ntIdx = cpNameType(name, desc);
        int idx = cpCount++;
        cpBuf.write(11); // CONSTANT_InterfaceMethodref
        w2(cpBuf, cIdx);
        w2(cpBuf, ntIdx);
        cpCache.put(key, idx);
        return idx;
    }

    private int cpString(String value) {
        String key = "s\0" + value;
        Integer cached = cpCache.get(key);
        if (cached != null) return cached;
        int utf8Idx = cpUtf8(value);
        int idx = cpCount++;
        cpBuf.write(8); // CONSTANT_String
        w2(cpBuf, utf8Idx);
        cpCache.put(key, idx);
        return idx;
    }

    // ================================================================
    //  FIELD / METHOD INFO BUILDERS
    // ================================================================

    private void addField(int accessFlags, String name, String descriptor) {
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        w2(f, accessFlags);
        w2(f, cpUtf8(name));
        w2(f, cpUtf8(descriptor));
        w2(f, 0); // attributes count
        fields.add(f.toByteArray());
    }

    private byte[] buildMethod(int accessFlags, String name, String descriptor,
                               int codeAttrNameIdx, byte[] codeBytes,
                               int maxStack, int maxLocals,
                               byte[] exceptionTable) {
        ByteArrayOutputStream m = new ByteArrayOutputStream();
        w2(m, accessFlags);
        w2(m, cpUtf8(name));
        w2(m, cpUtf8(descriptor));
        w2(m, 1); // attributes count (just Code)

        // Code attribute
        int exTableLen = exceptionTable == null ? 0 : exceptionTable.length;
        int exCount = exTableLen / 8;
        int attrLen = 2 + 2 + 4 + codeBytes.length + 2 + exTableLen + 2;
        w2(m, codeAttrNameIdx);
        w4(m, attrLen);
        w2(m, maxStack);
        w2(m, maxLocals);
        w4(m, codeBytes.length);
        write(m, codeBytes);
        w2(m, exCount);
        if (exceptionTable != null) write(m, exceptionTable);
        w2(m, 0); // code attributes count

        return m.toByteArray();
    }

    // ================================================================
    //  BYTECODE EMISSION HELPERS
    // ================================================================

    private static void emitLoad(ByteArrayOutputStream code, Class<?> type, int slot) {
        if (type == long.class) {
            if (slot <= 3) { code.write(0x1E + slot); } // lload_0..lload_3
            else { code.write(0x16); code.write(slot); } // lload
        } else if (type == double.class) {
            if (slot <= 3) { code.write(0x26 + slot); } // dload_0..dload_3
            else { code.write(0x18); code.write(slot); } // dload
        } else if (type == float.class) {
            if (slot <= 3) { code.write(0x22 + slot); } // fload_0..fload_3
            else { code.write(0x17); code.write(slot); } // fload
        } else if (type.isPrimitive()) {
            // int, boolean, byte, char, short all use iload
            if (slot <= 3) { code.write(0x1A + slot); } // iload_0..iload_3
            else { code.write(0x15); code.write(slot); } // iload
        } else {
            if (slot <= 3) { code.write(0x2A + slot); } // aload_0..aload_3
            else { code.write(0x19); code.write(slot); } // aload
        }
    }

    private static void emitReturn(ByteArrayOutputStream code, Class<?> type) {
        if (type == void.class) code.write(0xB1);
        else if (type == long.class) code.write(0xAD);
        else if (type == double.class) code.write(0xAF);
        else if (type == float.class) code.write(0xAE);
        else if (type.isPrimitive()) code.write(0xAC);
        else code.write(0xB0);
    }

    private static void emitDefaultReturn(ByteArrayOutputStream code, Class<?> type) {
        if (type == void.class) {
            code.write(0xB1); // return
        } else if (type == long.class) {
            code.write(0x09); // lconst_0
            code.write(0xAD); // lreturn
        } else if (type == double.class) {
            code.write(0x0E); // dconst_0
            code.write(0xAF); // dreturn
        } else if (type == float.class) {
            code.write(0x0B); // fconst_0
            code.write(0xAE); // freturn
        } else if (type.isPrimitive()) {
            code.write(0x03); // iconst_0
            code.write(0xAC); // ireturn
        } else {
            code.write(0x01); // aconst_null
            code.write(0xB0); // areturn
        }
    }

    private void emitReturnFromHandler(ByteArrayOutputStream code, Class<?> retType) {
        if (retType == void.class) {
            code.write(0x57); // pop (discard Object result)
            code.write(0xB1); // return
        } else if (retType.isPrimitive()) {
            String wrapper = wrapperInternal(retType);
            code.write(0xC0); // checkcast
            w2(code, cpClass(wrapper));
            emitUnbox(code, retType);
            emitReturn(code, retType);
        } else {
            code.write(0xC0); // checkcast
            w2(code, cpClass(internal(retType)));
            code.write(0xB0); // areturn
        }
    }

    private void emitBox(ByteArrayOutputStream code, Class<?> type) {
        if (!type.isPrimitive()) return;
        String wrapper = wrapperInternal(type);
        String valueOfDesc = "(" + desc(type) + ")L" + wrapper + ";";
        int ref = cpMethod(wrapper, "valueOf", valueOfDesc);
        code.write(0xB8); // invokestatic
        w2(code, ref);
    }

    private void emitUnbox(ByteArrayOutputStream code, Class<?> type) {
        if (!type.isPrimitive()) return;
        String wrapper = wrapperInternal(type);
        String methodName;
        String methodDesc;
        if (type == int.class)     { methodName = "intValue";     methodDesc = "()I"; }
        else if (type == long.class)    { methodName = "longValue";    methodDesc = "()J"; }
        else if (type == float.class)   { methodName = "floatValue";   methodDesc = "()F"; }
        else if (type == double.class)  { methodName = "doubleValue";  methodDesc = "()D"; }
        else if (type == boolean.class) { methodName = "booleanValue"; methodDesc = "()Z"; }
        else if (type == byte.class)    { methodName = "byteValue";    methodDesc = "()B"; }
        else if (type == char.class)    { methodName = "charValue";    methodDesc = "()C"; }
        else if (type == short.class)   { methodName = "shortValue";   methodDesc = "()S"; }
        else return;
        int ref = cpMethod(wrapper, methodName, methodDesc);
        code.write(0xB6); // invokevirtual
        w2(code, ref);
    }

    private static void emitPushInt(ByteArrayOutputStream code, int value) {
        if (value >= -1 && value <= 5) {
            code.write(0x03 + value); // iconst_m1 .. iconst_5
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            code.write(0x10); // bipush
            code.write(value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            code.write(0x11); // sipush
            code.write(value >> 8);
            code.write(value & 0xFF);
        } else {
            // for proxy generation we should never need > Short.MAX_VALUE params
            throw new IllegalStateException("too many parameters");
        }
    }

    private void emitLdcClass(ByteArrayOutputStream code, Class<?> clazz) {
        if (clazz == null) {
            // proxy class itself
            int idx = thisClassIdx;
            code.write(0x13); // ldc_w
            w2(code, idx);
            return;
        }
        if (clazz.isPrimitive()) {
            // use Wrapper.TYPE static field
            String wrapper = wrapperInternal(clazz);
            int fieldRef = cpField(wrapper, "TYPE", "Ljava/lang/Class;");
            code.write(0xB2); // getstatic
            w2(code, fieldRef);
        } else {
            int classRef = cpClass(internal(clazz));
            code.write(0x13); // ldc_w
            w2(code, classRef);
        }
    }

    private void emitLdcString(ByteArrayOutputStream code, String value) {
        int stringRef = cpString(value);
        code.write(0x13); // ldc_w
        w2(code, stringRef);
    }

    private void emitClassArray(ByteArrayOutputStream code, Class<?>[] types, int classClassRef) {
        emitPushInt(code, types.length);
        code.write(0xBD); // anewarray
        w2(code, classClassRef);
        for (int i = 0; i < types.length; i++) {
            code.write(0x59); // dup
            emitPushInt(code, i);
            emitLdcClass(code, types[i]);
            code.write(0x53); // aastore
        }
    }

    // ================================================================
    //  TYPE DESCRIPTOR UTILITIES
    // ================================================================

    static String desc(Class<?> type) {
        if (type == void.class) return "V";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type.isArray()) return type.getName().replace('.', '/');
        return "L" + type.getName().replace('.', '/') + ";";
    }

    static String internal(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        return type.getName().replace('.', '/');
    }

    static String methodDesc(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) sb.append(desc(p));
        sb.append(")");
        sb.append(desc(m.getReturnType()));
        return sb.toString();
    }

    private static String ctorDesc(Class<?>[] paramTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : paramTypes) sb.append(desc(p));
        sb.append(")V");
        return sb.toString();
    }

    private static String wrapperInternal(Class<?> primitiveType) {
        if (primitiveType == int.class) return "java/lang/Integer";
        if (primitiveType == long.class) return "java/lang/Long";
        if (primitiveType == float.class) return "java/lang/Float";
        if (primitiveType == double.class) return "java/lang/Double";
        if (primitiveType == boolean.class) return "java/lang/Boolean";
        if (primitiveType == byte.class) return "java/lang/Byte";
        if (primitiveType == char.class) return "java/lang/Character";
        if (primitiveType == short.class) return "java/lang/Short";
        throw new IllegalArgumentException("not a primitive: " + primitiveType);
    }

    private static int slotSize(Class<?> type) {
        return (type == long.class || type == double.class) ? 2 : 1;
    }

    // ================================================================
    //  BINARY WRITE HELPERS
    // ================================================================

    private static void w2(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void w4(ByteArrayOutputStream out, int value) {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void write(ByteArrayOutputStream out, byte[] data) {
        out.write(data, 0, data.length);
    }
}
