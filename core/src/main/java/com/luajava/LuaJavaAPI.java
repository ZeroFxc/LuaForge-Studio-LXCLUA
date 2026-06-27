/*
 * $Id: LuaJavaAPI.java,v 1.4 2006/12/22 14:06:40 thiago Exp $
 * Copyright (C) 2003-2007 Kepler Project.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Softwarea.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.luajava;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.Log;

import com.android.cglib.proxy.EnhancerInterface;
import com.android.cglib.proxy.MethodFilter;
import com.androlua.LuaBitmap;
import com.androlua.LuaEnhancer;
import com.androlua.LuaGcable;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class that contains functions accessed by lua.
 *
 * @author Thiago Ponte
 */
@SuppressLint("UseSparseArrays")
public final class LuaJavaAPI {
    private final static Map<Class<?>, Method[]> methodsMap = new ConcurrentHashMap<Class<?>, Method[]>();
    private final static Map<String, Method[]> methodCache = new ConcurrentHashMap<String, Method[]>();
    private final static Map<Class<?>, Map<String, ArrayList<Method>>> methodCache3 = new ConcurrentHashMap<Class<?>, Map<String, ArrayList<Method>>>();
    private final static Map<String, Method> stringMethodCache = new ConcurrentHashMap<>();
    private final static Map<String, Method> integerMethodCache = new ConcurrentHashMap<>();
    private final static Map<String, Method> doubleMethodCache = new ConcurrentHashMap<>();
    private final static Map<String, Method> boolMethodCache = new ConcurrentHashMap<>();
    private final static Map<String, Method> voidMethodCache = new ConcurrentHashMap<>();
    private final static Map<String, Method> getterMethodCache = new ConcurrentHashMap<>();
    private final static Map<String, Method> setterMethodCache = new ConcurrentHashMap<>();
    private final static Map<Integer, Object> javaObjectMap = new ConcurrentHashMap<>();

    private LuaJavaAPI() {
    }

    /**
     * 将 Java 异常及其完整堆栈（包括 cause 链）转换为字符串
     * 供 C 层 checkError 调用，确保 Lua error 包含完整的 Java 异常信息
     * @param t Java 异常对象
     * @return 完整堆栈字符串
     */
    public static String getStackTrace(Throwable t) {
        if (t == null) return "null";
        StringBuilder sb = new StringBuilder();
        Throwable current = t;
        int depth = 0;
        while (current != null) {
            if (depth > 0) {
                sb.append("\nCaused by: ");
            }
            // 异常类名 + 消息
            sb.append(current.getClass().getName());
            if (current.getMessage() != null) {
                sb.append(": ").append(current.getMessage());
            }
            sb.append("\n");
            // 堆栈帧
            StackTraceElement[] stackTrace = current.getStackTrace();
            int framesToShow = Math.min(stackTrace.length, 30); // 最多显示 30 帧
            for (int i = 0; i < framesToShow; i++) {
                sb.append("\tat ").append(stackTrace[i]).append("\n");
            }
            if (stackTrace.length > framesToShow) {
                sb.append("\t... ").append(stackTrace.length - framesToShow).append(" more\n");
            }
            current = current.getCause();
            depth++;
            if (depth > 10) break; // 防止无限循环
        }
        return sb.toString();
    }

    public static void clearCaches() {
        methodCache.clear();
        methodsMap.clear();
        methodCache3.clear();

        stringMethodCache.clear();
        integerMethodCache.clear();
        doubleMethodCache.clear();
        boolMethodCache.clear();
        voidMethodCache.clear();
        getterMethodCache.clear();
        setterMethodCache.clear();

        // 静态方法缓存
        staticVoidMethodCache.clear();
        staticStringMethodCache.clear();
        staticIntegerMethodCache.clear();
        staticDoubleMethodCache.clear();
        staticBoolMethodCache.clear();
        staticMethodCache.clear();
    }

    /**
     * Java implementation of the metamethod __index
     *
     * @param luaState   int that indicates the state used
     * @param idx        Object idx to be indexed
     * @param searchName the name of the method
     * @return number of returned objects
     */

    public static int objectIndex(long luaState, int idx, String searchName, int type)
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            // 对象已被 clear/gc 释放，返回 nil
            if (obj == null) {
                return 0;
            }
            int ret = 0;
            if (type == 0)
                if (checkMethod(L, obj, searchName) != 0)
                    return 2;

            if (type == 0 || type == 1 || type == 5)
                if ((ret = checkField(L, obj, searchName)) != 0)
                    return ret;

            if (type == 0 || type == 4)
                if (javaGetter(L, obj, searchName) != 0)
                    return 4;

            if (type == 0 || type == 3)
                if (checkClass(L, obj, searchName) != 0)
                    return 3;

            if ((type == 0 || type == 6) && obj instanceof LuaMetaTable) {
                Object res = ((LuaMetaTable) obj).__index(searchName);
                L.pushObjectValue(res);
                return 6;
            }

            return 0;
        }
    }

    /**
     * 显式签名方法调用：obj["methodName(typ1,typ2,...)"](args)
     * 直接按签名匹配方法，跳过重载解析
     */
    private static int callMethodExplicitSignature(LuaState L, Object obj, int objIdx,
                                                    String cacheName, Class<?>[] explicitSig, int top)
            throws LuaException {
        synchronized (L) {
            boolean isClass = obj instanceof Class;
            Class<?> clazz = isClass ? (Class<?>) obj : obj.getClass();
            // 从 cacheName 提取纯方法名（去掉类名前缀）
            String pureMethodName = cacheName;
            int dotIdx = Math.max(cacheName.lastIndexOf('.'), cacheName.lastIndexOf('@'));
            if (dotIdx >= 0) {
                pureMethodName = cacheName.substring(dotIdx + 1);
            }
            int argCount = top;
            Object[] objs = new Object[argCount];
            int[] types = new int[argCount];
            for (int i = 0; i < argCount; i++) {
                types[i] = L.type(i + 1);
            }
            // 查找匹配签名的方法
            Method targetMethod = null;
            Class<?> searchClass = clazz;
            while (searchClass != null && targetMethod == null) {
                for (Method m : searchClass.getDeclaredMethods()) {
                    if (!m.getName().equals(pureMethodName)) continue;
                    if (isClass && !Modifier.isStatic(m.getModifiers())) continue;
                    if (!isClass && Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length != explicitSig.length) continue;
                    boolean match = true;
                    boolean exactMatch = true;
                    for (int i = 0; i < params.length; i++) {
                        if (!isTypeCompatible(params[i], explicitSig[i])) {
                            match = false;
                            break;
                        }
                        if (params[i] != explicitSig[i]) {
                            exactMatch = false;
                        }
                    }
                    if (match) {
                        // 优先精确匹配，若已有精确匹配则跳过兼容匹配
                        if (exactMatch) {
                            targetMethod = m;
                            break;
                        }
                        if (targetMethod == null) {
                            targetMethod = m;
                        }
                    }
                }
                searchClass = searchClass.getSuperclass();
            }
            // 也检查接口 default 方法
            if (targetMethod == null) {
                for (Class<?> iface : clazz.getInterfaces()) {
                    targetMethod = findDefaultMethod(iface, pureMethodName, explicitSig);
                    if (targetMethod != null) break;
                }
            }
            if (targetMethod == null) {
                throw new LuaException("找不到方法 " + pureMethodName + " 签名: " + arrayToString(explicitSig));
            }
            // 转换参数
            try {
                if (!Modifier.isPublic(targetMethod.getModifiers()))
                    targetMethod.setAccessible(true);
                for (int i = 0; i < argCount; i++) {
                    objs[i] = compareTypes(L, targetMethod.getParameterTypes()[i], types[i], i + 1);
                }
                Object ret = targetMethod.invoke(isClass ? null : obj, objs);
                if (ret == null && targetMethod.getReturnType().equals(Void.TYPE))
                    return 0;
                L.pushObjectValue(ret);
                return 1;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new LuaException("方法调用异常: " + targetMethod + "\n  -> " + cause, cause);
            } catch (Exception e) {
                throw new LuaException("调用方法失败: " + pureMethodName + "\n  -> " + e, e);
            }
        }
    }

    /**
     * 在接口中查找匹配签名的 default 方法（递归父接口）
     */
    private static Method findDefaultMethod(Class<?> iface, String name, Class<?>[] sig) {
        try {
            for (Method m : iface.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.isDefault()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length != sig.length) continue;
                    boolean match = true;
                    for (int i = 0; i < params.length; i++) {
                        if (!isTypeCompatible(params[i], sig[i])) {
                            match = false;
                            break;
                        }
                    }
                    if (match) return m;
                }
            }
        } catch (Exception ignored) {}
        for (Class<?> parent : iface.getInterfaces()) {
            Method m = findDefaultMethod(parent, name, sig);
            if (m != null) return m;
        }
        return null;
    }

    private static String arrayToString(Class<?>[] arr) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i].getSimpleName());
        }
        sb.append(")");
        return sb.toString();
    }

    public static int callMethod(long luaState, int idx, String cacheName)
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            // 对象已被释放，抛明确错误
            if (obj == null) {
                throw new LuaException("Java 对象已被释放，无法调用方法: " + cacheName);
            }
            // 解析显式签名：如果 cacheName 包含 ( 和 )，则是显式签名调用
            String methodName = cacheName;
            Class<?>[] explicitSig = null;
            int sigDot = cacheName.lastIndexOf('@');
            if (sigDot < 0) sigDot = cacheName.lastIndexOf('.');
            String namePart = sigDot >= 0 ? cacheName.substring(sigDot + 1) : cacheName;
            int parenIdx = namePart.indexOf('(');
            if (parenIdx > 0 && namePart.endsWith(")")) {
                String baseName = namePart.substring(0, parenIdx);
                String sigStr = namePart.substring(parenIdx + 1, namePart.length() - 1);
                methodName = sigDot >= 0 ? cacheName.substring(0, sigDot + 1) + baseName : baseName;
                // 解析参数类型
                if (!sigStr.isEmpty()) {
                    String[] typeNames = sigStr.split(",");
                    explicitSig = new Class<?>[typeNames.length];
                    for (int i = 0; i < typeNames.length; i++) {
                        explicitSig[i] = parseType(typeNames[i].trim());
                        if (explicitSig[i] == null) {
                            throw new LuaException("无法解析类型: " + typeNames[i] + " in " + cacheName);
                        }
                    }
                } else {
                    explicitSig = new Class<?>[0];
                }
            }
            StringBuilder msgBuilder = new StringBuilder();
            Method method = null;
            int top = L.getTop();
            int methodType = -1;
            // 显式签名：直接查找匹配的方法
            if (explicitSig != null) {
                return callMethodExplicitSignature(L, obj, idx, methodName, explicitSig, top);
            }
            if (top == 0) {
                methodType = LuaState.LUA_TNIL;
                method = voidMethodCache.get(cacheName);
                if (method != null) {
                    Object ret;
                    try {
                        if (!Modifier.isPublic(method.getModifiers()))
                            method.setAccessible(true);
                        ret = method.invoke(obj);
                    } catch (Exception e) {
                        //e.printStackTrace();
                        msgBuilder.append("  at ").append(method).append("\n  -> ").append((e.getCause() != null) ? e.getCause() : e).append("\n");
                        throw new LuaException("Invalid method call.\n" + msgBuilder);
                    }
                    // Void function returns null
                    if (ret == null && method.getReturnType().equals(Void.TYPE))
                        return 0;
                    // push result
                    L.pushObjectValue(ret);
                    return 1;
                }
            }

            Object[] objs = new Object[top];

            if (top == 1) {
                switch (L.type(1)) {
                    case LuaState.LUA_TSTRING:
                        methodType = LuaState.LUA_TSTRING;
                        method = stringMethodCache.get(cacheName);
                        if (method != null)
                            objs[0] = L.toString(1);
                        break;
                    case LuaState.LUA_TBOOLEAN:
                        methodType = LuaState.LUA_TBOOLEAN;
                        method = boolMethodCache.get(cacheName);
                        if (method != null)
                            objs[0] = L.toBoolean(1);
                        break;
                    case LuaState.LUA_TNUMBER:
                        if (L.isInteger(1)) {
                            methodType = LuaState.LUA_TINTEGER;
                            method = integerMethodCache.get(cacheName);
                            if (method != null)
                                objs[0] = LuaState.convertLuaNumber(L.toInteger(1), method.getParameterTypes()[0]);
                        } else {
                            methodType = LuaState.LUA_TNUMBER;
                            method = doubleMethodCache.get(cacheName);
                            if (method != null)
                                objs[0] = LuaState.convertLuaNumber(L.toNumber(1), method.getParameterTypes()[0]);

                        }
                        break;
                }
                if (method != null) {
                    Object ret;
                    try {
                        if (!Modifier.isPublic(method.getModifiers()))
                            method.setAccessible(true);

                        ret = method.invoke(obj, objs);
                    } catch (Exception e) {
                        //e.printStackTrace();
                        msgBuilder.append("  at ").append(method).append("\n  -> ").append((e.getCause() != null) ? e.getCause() : e).append("\n");
                        throw new LuaException("Invalid method call.\n" + msgBuilder);
                    }

                    // Void function returns null
                    if (ret == null && method.getReturnType().equals(Void.TYPE))
                        return 0;

                    // push result
                    L.pushObjectValue(ret);
                    return 1;
                }
            }

            Method[] methods = methodCache.get(cacheName);
            // 缓存被清空后重建（clearAll 后 methodCache 为空，但 Lua 层面的方法闭包仍持有 cacheName）
            if (methods == null) {
                Class<?> clazz;
                boolean isClass = false;
                if (obj instanceof Class) {
                    clazz = (Class<?>) obj;
                    isClass = true;
                } else {
                    clazz = obj.getClass();
                }
                // 从 cacheName 中提取方法名：
                // 普通对象格式: 全限定类名@方法名
                // Class 对象格式: 全限定类名.方法名
                String simpleName;
                if (isClass) {
                    String className = clazz.getName();
                    simpleName = cacheName.substring(className.length() + 1);
                } else {
                    int atIndex = cacheName.indexOf('@');
                    simpleName = atIndex >= 0 ? cacheName.substring(atIndex + 1) : cacheName;
                }
                ArrayList<Method> list = getMethod(clazz, simpleName, isClass);
                methods = new Method[list.size()];
                list.toArray(methods);
                methodCache.put(cacheName, methods);
            }
            int[] type = new int[top];
            for (int i = 0; i < top; i++) {
                type[i] = L.type(i + 1);
            }
            // gets method and arguments
            for (Method m : methods) {

                Class[] parameters = m.getParameterTypes();
                if (parameters.length != top)
                    continue;

                boolean okMethod = true;

                for (int j = 0; j < parameters.length; j++) {
                    /*if(parameters[j].isPrimitive()&&objs[j] instanceof Number){
                        objs[j]=parameters[j].cast(objs[j]);
                    } else if(!parameters[j].isInstance(objs[j]) ){
                        okMethod=false;
                        break;
                    }*/
                    try {
                        objs[j] = compareTypes(L, parameters[j], type[j], j + 1);
                    } catch (Exception e) {
                        okMethod = false;
                        break;
                    }
                }

                if (okMethod) {
                    method = m;
                    Object ret;
                    try {
                        if (!Modifier.isPublic(method.getModifiers()))
                            method.setAccessible(true);
                        ret = method.invoke(obj, objs);
                    } catch (IllegalArgumentException e) {
                        // 参数不匹配，继续尝试下一个重载
                        msgBuilder.append("  at ").append(method).append("\n  -> ").append(e).append("\n");
                        continue;
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        // 方法内部抛出的业务异常，直接抛出，不继续尝试
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        throw new LuaException("方法调用异常: " + method + "\n  -> " + cause, cause);
                    } catch (Exception e) {
                        // 其他异常（如 IllegalAccessException），记录后继续
                        msgBuilder.append("  at ").append(method).append("\n  -> ").append(e).append("\n");
                        continue;
                    }

                    switch (methodType) {
                        case LuaState.LUA_TSTRING:
                            stringMethodCache.put(cacheName, method);
                            break;
                        case LuaState.LUA_TINTEGER:
                            integerMethodCache.put(cacheName, method);
                            break;
                        case LuaState.LUA_TNUMBER:
                            doubleMethodCache.put(cacheName, method);
                            break;
                        case LuaState.LUA_TBOOLEAN:
                            boolMethodCache.put(cacheName, method);
                            break;
                        case LuaState.LUA_TNIL:
                            voidMethodCache.put(cacheName, method);
                            break;
                    }
                    // Void function returns null
                    if (ret == null && method.getReturnType().equals(Void.TYPE))
                        return 0;

                    // push result
                    L.pushObjectValue(ret);
                    return 1;
                }
            }

            // === varargs 匹配：精确匹配失败后，尝试可变参数方法 ===
            for (Method m : methods) {
                if (!m.isVarArgs()) continue;
                Class<?>[] parameters = m.getParameterTypes();
                int fixedCount = parameters.length - 1;  // 最后一个是 vararg 数组
                if (top < fixedCount) continue;

                Class<?> varargType = parameters[fixedCount].getComponentType();
                Object[] varArgs = null;
                boolean okMethod = true;

                // 固定参数匹配
                for (int j = 0; j < fixedCount; j++) {
                    try {
                        objs[j] = compareTypes(L, parameters[j], type[j], j + 1);
                    } catch (Exception e) {
                        okMethod = false;
                        break;
                    }
                }
                if (!okMethod) continue;

                // === 模式1：最后一个参数是 Lua table 且为数组形式 → 自动展开 ===
                int tableVarArgLen = -1;
                if (top == fixedCount + 1) {
                    tableVarArgLen = isArrayTable(L, fixedCount + 1);
                }
                if (tableVarArgLen > 0) {
                    // 将 table 展开为 varargs
                    varArgs = new Object[tableVarArgLen];
                    for (int j = 0; j < tableVarArgLen; j++) {
                        try {
                            L.pushInteger(j + 1);
                            L.getTable(fixedCount + 1);
                            int valType = L.type(-1);
                            varArgs[j] = compareTypes(L, varargType, valType, -1);
                            L.pop(1);
                        } catch (Exception e) {
                            okMethod = false;
                            // 清理栈
                            if (L.type(-1) != LuaState.LUA_TNONE) {
                                L.pop(1);
                            }
                            break;
                        }
                    }
                } else {
                    // === 模式2：正常多参数 varargs ===
                    varArgs = new Object[top - fixedCount];
                    for (int j = 0; j < varArgs.length; j++) {
                        try {
                            varArgs[j] = compareTypes(L, varargType, type[fixedCount + j], fixedCount + j + 1);
                        } catch (Exception e) {
                            okMethod = false;
                            break;
                        }
                    }
                }
                if (!okMethod) continue;

                // 构造最终参数数组
                Object[] invokeArgs = new Object[fixedCount + 1];
                System.arraycopy(objs, 0, invokeArgs, 0, fixedCount);
                // 将 varArgs 转成正确类型的数组
                Object varargArray = java.lang.reflect.Array.newInstance(varargType, varArgs.length);
                System.arraycopy(varArgs, 0, varargArray, 0, varArgs.length);
                invokeArgs[fixedCount] = varargArray;

                Method varargMethod = m;
                Object ret;
                try {
                    if (!Modifier.isPublic(varargMethod.getModifiers()))
                        varargMethod.setAccessible(true);
                    ret = varargMethod.invoke(obj, invokeArgs);
                } catch (Exception e) {
                    msgBuilder.append("  at ").append(varargMethod).append(" (varargs)\n  -> ").append((e.getCause() != null) ? e.getCause() : e).append("\n");
                    continue;
                }

                // Void function returns null
                if (ret == null && varargMethod.getReturnType().equals(Void.TYPE))
                    return 0;

                // push result
                L.pushObjectValue(ret);
                return 1;
            }

            if (msgBuilder.length() > 0) {
                throw new LuaException("Invalid method call.\n" + msgBuilder);
            }
            // 构建清晰的错误信息：方法名、传入参数、期望参数
            msgBuilder.append("没有匹配的方法: ").append(cacheName).append("\n");
            msgBuilder.append("传入参数个数: ").append(top).append("\n");
            msgBuilder.append("传入参数类型: ");
            for (int i = 0; i < top; i++) {
                if (i > 0) msgBuilder.append(", ");
                msgBuilder.append(L.typeName(L.type(i + 1)));
            }
            msgBuilder.append("\n\n");
            msgBuilder.append("可用的方法签名 (共").append(methods.length).append("个):\n");
            for (Method m : methods) {
                msgBuilder.append("  ").append(m.getName()).append("(");
                Class<?>[] params = m.getParameterTypes();
                for (int j = 0; j < params.length; j++) {
                    if (j > 0) msgBuilder.append(", ");
                    msgBuilder.append(params[j].getSimpleName());
                }
                msgBuilder.append(")\n");
            }
            throw new LuaException("方法调用失败: 参数不匹配\n" + msgBuilder);

        }
    }

    /**
     * Java implementation of the metamethod __newindex
     *
     * @param luaState   int that indicates the state used
     * @param idx        Object to be indexed
     * @param searchName the name of the method
     * @return number of returned objects
     */

    public static int objectNewIndex(long luaState, int idx, String searchName, int type)
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            // 对象已被释放，抛明确错误
            if (obj == null) {
                throw new LuaException("Java 对象已被释放，无法设置字段: " + searchName);
            }
            int res;
            if (type == 0 || type == 1) {
                res = setFieldValue(L, obj, searchName);
                if (res != 0)
                    return 1;
            }

            if (type == 0 || type == 2) {
                res = javaSetter(L, obj, searchName);
                if (res != 0)
                    return 2;
            }
            if (type == 0 || type == 3) {
                if (obj instanceof LuaMetaTable) {
                    ((LuaMetaTable) obj).__newIndex(searchName, L.toJavaObject(-1));
                    return 3;
                }
            }
            return 0;
        }
    }

    public static int setFieldValue(LuaState L, Object obj, String fieldName) throws LuaException {
        synchronized (L) {
            Field field = null;
            Class objClass;
            boolean isClass = false;

            if (obj == null)
                return 0;

            if (obj instanceof Class) {
                objClass = (Class) obj;
                isClass = true;
            } else {
                objClass = obj.getClass();
            }

            try {
                field = objClass.getField(fieldName);
            } catch (NoSuchFieldException e) {
                //e.printStackTrace();
                return 0;
            }

            if (field == null)
                return 0;
            if (isClass && !Modifier.isStatic(field.getModifiers()))
                return 0;
            Class type = field.getType();
            try {
                if (!Modifier.isPublic(field.getModifiers()))
                    field.setAccessible(true);

                field.set(obj, compareTypes(L, type, L.getTop()));
            } catch (LuaException e) {
                argError(L, fieldName, -1, type);
            } catch (Exception e) {
                throw new LuaException(e);
            }

            return 1;
        }
    }

    private static String argError(LuaState L, String name, int idx, Class type) throws LuaException {
        throw new LuaException("bad argument to '" + name + "' (" + type.getName() + " expected, got " + typeName(L, idx) + " value)");

    }

    private static String argError(LuaState L, String name, int idx, String type) throws LuaException {
        throw new LuaException("bad argument #" + idx +
                " to '" + name + "' (" + type + " expected, got " + typeName(L, idx + 1) + " value)");

    }

    private static String typeName(LuaState L, int idx) throws LuaException {
        if (L.isObject(idx)) {
            return L.getObjectFromUserdata(idx).getClass().getName();
        }
        switch (L.type(idx)) {
            case LuaState.LUA_TSTRING:
                return "string";
            case LuaState.LUA_TNUMBER:
                return "number";
            case LuaState.LUA_TBOOLEAN:
                return "boolean";
            case LuaState.LUA_TFUNCTION:
                return "function";
            case LuaState.LUA_TTABLE:
                return "table";
            case LuaState.LUA_TTHREAD:
                return "thread";
            case LuaState.LUA_TLIGHTUSERDATA:
            case LuaState.LUA_TUSERDATA:
                return "userdata";
        }
        return "unkown";
    }

    /**
     * Java implementation of the metamethod __index
     *
     * @param luaState int that indicates the state used
     * @param idx      Object to be indexed
     * @param index    the Array index
     * @return number of returned objects
     */
    public static int setArrayValue(long luaState, int idx, int index) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);

        synchronized (L) {
            if (obj == null) {
                throw new LuaException("Java 对象已被释放，无法设置数组元素");
            }
            if (obj.getClass().isArray()) {
                Class<?> type = obj.getClass().getComponentType();
                try {
                    Object value = compareTypes(L, type, 3);
                    Array.set(obj, index, value);
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw new LuaException("数组索引越界: " + index + ", 数组长度: " + Array.getLength(obj));
                } catch (LuaException e) {
                    argError(L, obj.getClass().getName() + " [" + index + "]", 3, type);
                }
            } else if (obj instanceof List) {
                try {
                    ((List<Object>) obj).set(index, L.toJavaObject(3));
                } catch (IndexOutOfBoundsException e) {
                    throw new LuaException("List 索引越界: " + index + ", 大小: " + ((List<?>) obj).size());
                }
            } else if (obj instanceof Map) {
                ((Map<Long, Object>) obj).put((long) index, L.toJavaObject(3));
            } else {
                throw new LuaException("can not set " + obj.getClass().getName() + " value: " + L.toJavaObject(3) + " in " + index);
            }
            return 0;
        }
    }

    public static int setArrayValue(LuaState L, Object obj, int index) throws LuaException {

        synchronized (L) {
            if (obj == null) {
                throw new LuaException("Java 对象已被释放，无法设置数组元素");
            }
            if (obj.getClass().isArray()) {
                Class<?> type = obj.getClass().getComponentType();
                try {
                    Object value = compareTypes(L, type, -1);
                    Array.set(obj, index, value);
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw new LuaException("数组索引越界: " + index + ", 数组长度: " + Array.getLength(obj));
                } catch (LuaException e) {
                    argError(L, obj.getClass().getName() + " [" + index + "]", -1, type);
                }
            } else if (obj instanceof List) {
                try {
                    ((List<Object>) obj).set(index, L.toJavaObject(-1));
                } catch (IndexOutOfBoundsException e) {
                    throw new LuaException("List 索引越界: " + index + ", 大小: " + ((List<?>) obj).size());
                }
            } else if (obj instanceof Map) {
                ((Map<Long, Object>) obj).put((long) index, L.toJavaObject(-1));
            } else {
                throw new LuaException("can not set " + obj.getClass().getName() + " value: " + L.toJavaObject(-1) + " in " + index);
            }
            return 0;
        }
    }

    public static int getArrayValue(long luaState, int idx, int index) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);

        synchronized (L) {
            if (obj == null) {
                throw new LuaException("Java 对象已被释放，无法获取数组元素");
            }
            Object ret = null;
            if (obj.getClass().isArray()) {
                try {
                    ret = Array.get(obj, index);
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw new LuaException("数组索引越界: " + index + ", 数组长度: " + Array.getLength(obj));
                }
            } else if (obj instanceof List) {
                try {
                    ret = ((List) obj).get(index);
                } catch (IndexOutOfBoundsException e) {
                    throw new LuaException("List 索引越界: " + index + ", 大小: " + ((List<?>) obj).size());
                }
            } else if (obj instanceof Map) {
                ret = ((Map) obj).get((long) index);
            } else {
                throw new LuaException("can not get " + obj.getClass().getName() + " value in " + index);
            }
            L.pushObjectValue(ret);
            return 1;
        }
    }

    public static int asTable(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);

        synchronized (L) {
            if (L.isBoolean(-1) && L.toBoolean(-1)) {
                L.pop(1);
                return asTable(L, obj);
            }
            try {
                L.newTable();
                int ret = L.getTop();
                if (obj == null)
                    return 1;
                if (obj.getClass().isArray()) {
                    int n = Array.getLength(obj);
                    for (int i = 0; i <= n - 1; i++) {
                        Object v = Array.get(obj, i);
                        L.pushObjectValue(v);
                        L.rawSetI(-2, i + 1);
                    }
                } else if (obj instanceof Collection list) {
                    int i = 1;
                    for (Object v : list) {
                        L.pushObjectValue(v);
                        L.rawSetI(-2, i++);
                    }
                } else if (obj instanceof Map map) {
                    for (Object o : map.entrySet()) {
                        Map.Entry entry = (Map.Entry) o;
                        L.pushObjectValue(entry.getKey());
                        L.pushObjectValue(entry.getValue());
                        L.setTable(-3);
                    }
                }
                L.pushValue(ret);
                return 1;
            } catch (Exception e) {
                throw new LuaException("can not astable: " + e.getMessage());
            }
        }
    }

    private static int asTable(LuaState L, Object obj) throws LuaException {
        synchronized (L) {
            try {
                if (obj == null) {
                    L.pushNil();
                    return 1;
                }
                L.newTable();
                if (obj.getClass().isArray()) {
                    int n = Array.getLength(obj);
                    for (int i = 0; i < n; i++) {
                        asTable(L, Array.get(obj, i));
                        L.rawSetI(-2, i + 1);
                    }
                } else if (obj instanceof Collection list) {
                    int i = 1;
                    for (Object v : list) {
                        asTable(L, v);
                        L.rawSetI(-2, i++);
                    }
                } else if (obj instanceof Map map) {
                    for (Object o : map.entrySet()) {
                        Map.Entry entry = (Map.Entry) o;
                        L.pushObjectValue(entry.getKey());
                        asTable(L, entry.getValue());
                        L.setTable(-3);
                    }
                } else {
                    L.pop(1);
                    L.pushObjectValue(obj);
                }
                return 1;
            } catch (Exception e) {
                throw new LuaException("can not astable: " + e.getMessage(), e);
            }
        }
    }


    public static int newArray(long luaState, int idx, int size) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Class clazz = (Class) L.getJavaObject(idx);
        synchronized (L) {
            try {
                Object obj = Array.newInstance(clazz, size);
                L.pushJavaObject(obj);
            } catch (Exception e) {
                throw new LuaException("can not create a array: " + e.getMessage());
            }
            return 1;
        }
    }

    public static int newArray(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Class clazz = (Class) L.getJavaObject(idx);
        synchronized (L) {
            try {
                int top = L.getTop();
                int[] dimensions = new int[top - 1];
                for (int i = 0; i < top - 1; i++) {
                    dimensions[i] = (int) L.toInteger(i + 2);
                }
                Object obj = Array.newInstance(clazz, dimensions);
                L.pushJavaObject(obj);
            } catch (Exception e) {
                throw new LuaException("can not create a array: " + e.getMessage());
            }
            return 1;
        }
    }

    private static Class bindClass(String className) throws LuaException {
        Class clazz;
        try {
            clazz = Class.forName(className);
        } catch (Exception e) {
            switch (className) {
                case "boolean":
                    clazz = Boolean.TYPE;
                    break;
                case "byte":
                    clazz = Byte.TYPE;
                    break;
                case "char":
                    clazz = Character.TYPE;
                    break;
                case "short":
                    clazz = Short.TYPE;
                    break;
                case "int":
                    clazz = Integer.TYPE;
                    break;
                case "long":
                    clazz = Long.TYPE;
                    break;
                case "float":
                    clazz = Float.TYPE;
                    break;
                case "double":
                    clazz = Double.TYPE;
                    break;
                default:
                    throw new LuaException("Class not found: " + className);
            }
        }
        return clazz;
    }

    public static int javaBindClass(long luaState, String className) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        L.pushJavaObject(bindClass(className));
        return 1;
    }

    /**
     * Pushes a new instance of a java Object of the type className
     *
     * @param luaState  int that represents the state to be used
     * @param className name of the class
     * @return number of returned objects
     * @throws LuaException
     */
    public static int javaNewInstance(long luaState, String className) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {
            Class clazz;
            clazz = bindClass(className);
            if (clazz.isPrimitive())
                return toPrimitive(L, clazz, -1);
            else
                return getObjInstance(L, clazz);
        }
    }

    /**
     * javaNew returns a new instance of a given clazz
     *
     * @param luaState int that represents the state to be used
     * @param idx      class to be instanciated
     * @return number of returned objects
     * @throws LuaException
     */
    public static int javaNew(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Class<?> clazz = (Class<?>) L.getJavaObject(idx);

        synchronized (L) {
            if (clazz.isPrimitive()) {
                int top = L.getTop();
                for (int i = 2; i <= top; i++) {
                    toPrimitive(L, clazz, i);
                }
                return top - 1;
            } else if ((clazz.getModifiers() & Modifier.ABSTRACT) != 0) {
                if (!L.isTable(2))
                    argError(L, "javaOverride", 1, "table");
                return javaOverride(luaState, idx);
            } else {
                return getObjInstance(L, clazz);
            }
        }
    }

    public static int javaOverride(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Class<?> clazz = (Class<?>) L.getJavaObject(idx);

        synchronized (L) {
            final LuaTable<String, LuaFunction> t = new LuaTable<String, LuaFunction>(L, 2);
            L.remove(2);
            Class<?> cls = new LuaEnhancer(clazz).create(new MethodFilter() {
                @Override
                public boolean filter(Method method, String name) {
                    return t.containsKey(name);
                }
            });
            int r = getObjInstance(L, cls);
            if (r == 0)
                return 0;
            // getObjInstance 已经将对象压入栈，取出设置拦截器
            // 注意：不要重复 push，避免栈不平衡
            Object topObj = L.toJavaObject(-1);
            if (topObj instanceof EnhancerInterface) {
                ((EnhancerInterface) topObj).setMethodInterceptor_Enhancer(new LuaMethodInterceptor(t));
            }
            return r;
        }
    }

    /**
     * 判断给定名称是否是类的成员（方法或字段），用于 override 时区分重写方法和新增方法
     * @param clazz 要检查的类
     * @param name 成员名称
     * @return true 如果 name 是该类或其父类/接口的方法或字段
     */
    public static boolean javaIsClassMember(Class<?> clazz, String name) {
        // 检查所有 public 方法（包括继承的）
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name)) return true;
        }
        // 检查所有 public 字段（包括继承的）
        for (java.lang.reflect.Field f : clazz.getFields()) {
            if (f.getName().equals(name)) return true;
        }
        // 检查声明的方法（包括非 public，但不包括继承的）
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) return true;
        }
        // 检查声明的字段
        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
            if (f.getName().equals(name)) return true;
        }
        return false;
    }

    public static int javaCreate(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Class<?> clazz = (Class<?>) L.getJavaObject(idx);
        synchronized (L) {
            if (clazz.isPrimitive() || clazz == String.class) {
                return createArray(L, clazz);
            } else if (clazz.isArray()) {
                return createArray(L, clazz);
            } else if (List.class.isAssignableFrom(clazz)) {
                return createList(L, clazz);
            } else if (Map.class.isAssignableFrom(clazz)) {
                return createMap(L, clazz);
            } else if (clazz.isInterface()) {
                return createProxyObject(L, clazz);
            } else if ((clazz.getModifiers() & Modifier.ABSTRACT) != 0) {
                return createAbstractProxy(L, clazz);
            } else {
                // 具体类：调用构造器，table 作为参数传入
                // 如果第一个元素是该类实例，则创建数组
                if (L.objLen(-1) > 0) {
                    L.getI(-1, 1);
                    Object o = L.toJavaObject(-1);
                    L.pop(1);
                    if (o != null && clazz.isAssignableFrom(o.getClass())) {
                        return createArray(L, clazz);
                    }
                }
                // 否则调用构造器，table 作为构造参数
                return getObjInstance(L, clazz);
            }
        }
    }

    private static int createAbstractProxy(LuaState L, Class<?> clazz) throws LuaException {
        Class<?> cls = new LuaEnhancer(clazz).create(new MethodFilter() {
            @Override
            public boolean filter(Method method, String name) {
                return (method.getModifiers() & Modifier.ABSTRACT) == 0;
            }
        });
        try {
            EnhancerInterface obj = (EnhancerInterface) cls.newInstance();
            obj.setMethodInterceptor_Enhancer(new LuaAbstractMethodInterceptor(L.getLuaObject(-1)));
            L.pushJavaObject(obj);
            return 1;
        } catch (Exception e) {
            throw new LuaException("创建抽象类代理失败: " + e.toString(), e);
        }
    }

    public static int objectCall(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);

        synchronized (L) {
            if (obj == null) {
                throw new LuaException("Java 对象已被释放，无法调用");
            }
            if (obj instanceof LuaMetaTable) {
                int n = L.getTop();
                Object[] args = new Object[n - 1];
                for (int i = 2; i <= n; i++) {
                    args[i - 2] = L.toJavaObject(i);
                }
                Object ret = ((LuaMetaTable) obj).__call(args);
                L.pushObjectValue(ret);
                return 1;
            } else {
                if (L.isTable(2)) {
                    if (obj.getClass().isArray() && Array.getLength(obj) == 0)
                        return createArray(L, obj.getClass());
                    L.pushNil();
                    if (obj instanceof List list) {
                        while (L.next(2) != 0) {
                            list.add(L.toJavaObject(-1));
                            L.pop(1);
                        }
                    } else {
                        while (L.next(2) != 0) {
                            if (L.isNumber(-2))
                                setArrayValue(L, obj, (int) L.toInteger(-2));
                            else
                                javaSetter(L, obj, L.toString(-2));
                            L.pop(1);
                        }
                    }
                    L.setTop(1);
                    return 1;
                } else {
                    return 0;
                }
            }
        }
    }

    /**
     * Function that creates an object proxy and pushes it into the stack
     *
     * @param luaState int that represents the state to be used
     * @param implem   interfaces implemented separated by comma (<code>,</code>)
     * @return number of returned objects
     * @throws LuaException
     */
    public static int createProxy(long luaState, String implem)
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            return createProxyObject(L, implem);
        }
    }

    public static int createArray(long luaState, String className)
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            Class type = bindClass(className);
            return createArray(L, type);
        }
    }

    /**
     * Calls the static method <code>methodName</code> in class <code>className</code>
     * that receives a LuaState as first parameter.
     *
     * @param luaState   int that represents the state to be used
     * @param className  name of the class that has the open library method
     * @param methodName method to open library
     * @return number of returned objects
     * @throws LuaException
     */
    public static int javaLoadLib(long luaState, String className, String methodName)
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);

        synchronized (L) {
            Class<?> clazz;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new LuaException(e);
            }

            try {
                Method mt = clazz.getMethod(methodName, LuaState.class);
                Object obj = mt.invoke(null, L);

                if (obj != null && obj instanceof Integer) {
                    return (Integer) obj;
                } else
                    return 0;
            } catch (Exception e) {
                throw new LuaException("Error on calling method. Library could not be loaded. " + e.getMessage());
            }
        }
    }

    public static int javaToString(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);

        synchronized (L) {
            if (obj == null)
                L.pushString("null");
            else {
                String ret = obj.toString();
                if (ret != null)
                    L.pushString(ret);
                else
                    L.pushString(obj.getClass().getName());
            }
            return 1;
        }
    }

    public static void javaGc(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        L.removeJavaObject(idx);
    }

    /**
     * 全局清理：清空所有方法缓存 + 所有 Java 对象引用
     * Lua 调用: luajava.clearAll() 或 luajava.clear()（无参时）
     */
    public static int javaClearAll(long luaState) throws LuaException {
        try {
            LuaState L = LuaStateFactory.getExistingState(luaState);
            synchronized (L) {
                // 清理所有方法缓存
                clearCaches();
                // 清理 Java 对象映射表
                L.clearJavaObjects();
                return 0;
            }
        } catch (Exception e) {
            throw new LuaException("clearAll 失败: " + e.toString(), e);
        }
    }

    public static void javaClose(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        //Log.i("javaGc: ", obj + "");
        if (obj == null)
            return;
        try {
            if (obj instanceof LuaGcable)
                ((LuaGcable) obj).gc();
            else if (obj instanceof Bitmap) {
                LuaBitmap.removeBitmap((Bitmap) obj);
                ((Bitmap) obj).recycle();
            } else if (obj instanceof BitmapDrawable)
                ((BitmapDrawable) obj).getBitmap().recycle();
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && obj instanceof AutoCloseable)
                ((AutoCloseable) obj).close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Object javaGetObject(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        return L.getJavaObject(idx);
    }

    public static int javaGetType(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);

        synchronized (L) {
            if (obj == null)
                L.pushString("null");
            else
                L.pushString(obj.getClass().getName());
            return 1;
        }
    }

    public static int javaEquals(long luaState, int idx, int idx2) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        Object obj2 = L.getJavaObject(idx2);
        synchronized (L) {
            // 两个都为 null 则相等；一个为 null 一个不为则不等
            if (obj == null) {
                return obj2 == null ? 1 : 0;
            }
            boolean eq = obj.equals(obj2);
            return eq ? 1 : 0;
        }
    }

    public static int javaInstanceof(long luaState, int idx, int idx2) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        Object clazzObj = L.getJavaObject(idx2);
        synchronized (L) {
            // 第二个参数必须是 Class 对象
            if (!(clazzObj instanceof Class)) {
                return 0;
            }
            Class<?> clazz = (Class<?>) clazzObj;
            boolean eq = clazz.isInstance(obj);
            return eq ? 1 : 0;
        }
    }

    public static int javaObjectLength(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);

        synchronized (L) {
            // 对象已被释放，返回 0
            if (obj == null) {
                L.pushInteger(0);
                return 1;
            }
            int ret;
            try {
                if (obj instanceof CharSequence)
                    ret = ((CharSequence) obj).length();
                else if (obj instanceof Collection)
                    ret = ((Collection) obj).size();
                else if (obj instanceof Map)
                    ret = ((Map) obj).size();
                else
                    ret = Array.getLength(obj);
            } catch (Exception e) {
                throw new LuaException(e);
            }

            L.pushInteger(ret);

            return 1;
        }
    }

    private static int getObjInstance(LuaState L, Class<?> clazz) throws LuaException {
        synchronized (L) {
            int top = L.getTop();
            if (top == 1) {
                try {
                    // 使用 getDeclaredConstructor().newInstance() 替代已废弃的 Class.newInstance()
                    Constructor<?> noArgCtor = clazz.getDeclaredConstructor();
                    if (!java.lang.reflect.Modifier.isPublic(noArgCtor.getModifiers())) {
                        noArgCtor.setAccessible(true);
                    }
                    Object ret = noArgCtor.newInstance();
                    L.pushJavaObject(ret);
                    return 1;
                } catch (Exception e) {
                    try {
                        Constructor<?> ctr = clazz.getConstructor(Context.class);
                        Object ret = ctr.newInstance(L.getContext().getContext());
                        L.pushJavaObject(ret);
                        return 1;
                    } catch (Exception ignored) {
                    }
                }
            }
            Object[] objs = new Object[top - 1];

            Constructor[] constructors = clazz.getConstructors();
            Constructor constructor = null;

            StringBuilder msgBuilder = new StringBuilder();
            // gets method and arguments
            for (Constructor c : constructors) {
                Class<?>[] parameters = c.getParameterTypes();
                if (parameters.length != top - 1)
                    continue;

                boolean okConstructor = true;

                for (int j = 0; j < parameters.length; j++) {
                    try {
                        objs[j] = compareTypes(L, parameters[j], j + 2);
                    } catch (Exception e) {
                        okConstructor = false;
                        break;
                    }
                }

                if (okConstructor) {
                    constructor = c;
                    Object ret;
                    try {
                        ret = constructor.newInstance(objs);
                    } catch (Exception e) {
                        msgBuilder.append("  at ").append(constructor).append("\n  -> ").append((e.getCause() != null) ? e.getCause() : e).append("\n");
                        continue;
                    }
                    L.pushJavaObject(ret);
                    return 1;
                    //break;
                }
            }

            if (msgBuilder.length() > 0) {
                throw new LuaException("Invalid constructor method call.\n" + msgBuilder);
            }

            // 构建清晰的错误信息：类名、传入参数、期望参数
            msgBuilder.append("没有匹配的构造器: ").append(clazz.getSimpleName()).append("\n");
            msgBuilder.append("传入参数个数: ").append(top - 1).append("\n");
            msgBuilder.append("传入参数类型: ");
            for (int i = 0; i < top - 1; i++) {
                if (i > 0) msgBuilder.append(", ");
                msgBuilder.append(L.typeName(L.type(i + 2)));
            }
            msgBuilder.append("\n\n");
            msgBuilder.append("可用的构造器签名 (共").append(constructors.length).append("个):\n");
            for (Constructor c : constructors) {
                msgBuilder.append("  ").append(clazz.getSimpleName()).append("(");
                Class<?>[] params = c.getParameterTypes();
                for (int j = 0; j < params.length; j++) {
                    if (j > 0) msgBuilder.append(", ");
                    msgBuilder.append(params[j].getSimpleName());
                }
                msgBuilder.append(")\n");
            }
            throw new LuaException("构造器调用失败: 参数不匹配\n" + msgBuilder);

            /*// If method is null means there isn't one receiving the given arguments
            if (constructor == null) {
                StringBuilder msgBuilder = new StringBuilder();
                for (Constructor c : constructors) {
                    msgBuilder.append(c.toString());
                    msgBuilder.append("\n");
                }
                throw new LuaException("Invalid constructor method call. Invalid Parameters.\n" + msgBuilder.toString());
            }

            Object ret;
            try {
                ret = constructor.newInstance(objs);
            } catch (Exception e) {
                throw new LuaException(e);
            }

            if (ret == null) {
                throw new LuaException("Couldn't instantiate java Object");
            }
            L.pushJavaObject(ret);
            return 1;*/
        }
    }

    public static int getContext(long luaState) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            L.pushJavaObject(L.getContext());
        }
        return 1;
    }

    public static ArrayList<Method> getMethod(Class<?> clazz, String methodName, boolean isClass) {
        //String className = clazz.getName();
        Map<String, ArrayList<Method>> cList = methodCache3.get(clazz);
        if (cList == null) {
            cList = new ConcurrentHashMap<>();
            methodCache3.put(clazz, cList);
        }

        ArrayList<Method> mlist = cList.get(methodName);
        if (mlist == null) {
            Method[] methods = methodsMap.get(clazz);
            if (methods == null) {
                methods = clazz.getMethods();
                methodsMap.put(clazz, methods);
            }
            for (Method method : methods) {
                String name = method.getName();
                ArrayList<Method> list = cList.get(name);
                if (list == null) {
                    list = new ArrayList<Method>();
                    cList.put(name, list);
                }
                list.add(method);
            }
            mlist = cList.get(methodName);
        }

        if (mlist == null) {
            mlist = new ArrayList<Method>();
        }
        if (isClass) {
            ArrayList<Method> slist = new ArrayList<Method>();
            for (Method m : mlist) {
                if (Modifier.isStatic(m.getModifiers()))
                    slist.add(m);
            }

            if (slist.isEmpty()) {
                slist = getMethod(Class.class, methodName, false);
            }
            return slist;
        }
        return mlist;
    }


    /**
     * Checks if there is a field on the obj with the given name
     *
     * @param L         int that represents the state to be used
     * @param obj       object to be inspected
     * @param fieldName name of the field to be inpected
     * @return number of returned objects
     */
    public static int checkField(LuaState L, Object obj, String fieldName)
            throws LuaException {
        synchronized (L) {
            Field field = null;
            Class objClass;
            boolean isClass = false;

            if (obj instanceof Class) {
                objClass = (Class) obj;
                isClass = true;
            } else {
                objClass = obj.getClass();
            }

            try {
                field = objClass.getField(fieldName);
            } catch (NoSuchFieldException e) {
                return 0;
            }

            if (field == null)
                return 0;

            if (isClass && !Modifier.isStatic(field.getModifiers()))
                return 0;

            Object ret = null;
            try {
                if (!Modifier.isPublic(field.getModifiers()))
                    field.setAccessible(true);
                ret = field.get(obj);
            } catch (Exception e) {
                throw new LuaException(e);
            }

            L.pushObjectValue(ret);
            if (Modifier.isFinal(field.getModifiers()))
                return 5;
            else
                return 1;
        }
    }


    /**
     * Checks to see if there is a method with the given name.
     *
     * @param L          int that represents the state to be used
     * @param obj        object to be inspected
     * @param methodName name of the field to be inpected
     * @return number of returned objects
     */
    public static int checkMethod(LuaState L, Object obj, String methodName) throws LuaException {
        synchronized (L) {
            String cacheName = L.toString(-1);
            // 显式签名调用：methodName 可能包含 (typeName,...)，如 "append(String)"
            // 需要提取纯方法名来查找，但 cacheName 保持原样（含签名）用于缓存
            String pureMethodName = methodName;
            int parenIdx = methodName.indexOf('(');
            if (parenIdx > 0 && methodName.endsWith(")")) {
                pureMethodName = methodName.substring(0, parenIdx);
            }
            Method[] mlist = getJavaMethod(obj, pureMethodName, cacheName);
            if (mlist.length == 0)
                return 0;
            return 2;
        }
    }

    private static Method[] getJavaMethod(Object obj, String methodName, String cacheName) {
        Class<?> clazz;
        boolean isClass = false;
        if (obj instanceof Class) {
            clazz = (Class<?>) obj;
            isClass = true;
        } else {
            clazz = obj.getClass();
        }
        Method[] mlist = methodCache.get(cacheName);
        if (mlist == null) {
            ArrayList<Method> list = getMethod(clazz, methodName, isClass);
            mlist = new Method[list.size()];
            list.toArray(mlist);
            methodCache.put(cacheName, mlist);
        }
        return mlist;
    }

    /**
     * Checks to see if there is a class with the given name.
     *
     * @param L         int that represents the state to be used
     * @param obj       object to be inspected
     * @param className name of the field to be inpected
     * @return number of returned objects
     */
    public static int checkClass(LuaState L, Object obj, String className) throws LuaException {
        synchronized (L) {
            Class clazz;

            if (obj instanceof Class) {
                clazz = (Class) obj;
            } else {
                return 0;
            }

            try {
                Class<?> c = Class.forName(clazz.getName() + "$" + className);
                L.pushJavaObject(c);
                return 3;
            } catch (Exception e) {

            }
            Class[] clazzes = clazz.getClasses();
            for (Class c : clazzes) {
                if (c.getSimpleName().equals(className)) {
                    L.pushJavaObject(c);
                    return 3;
                }
            }
            return 0;
        }
    }

    public static int javaGetter(LuaState L, Object obj, String methodName) throws LuaException {
        synchronized (L) {
            Class<?> clazz;

            Method method = null;
            boolean isClass = false;
            if (obj instanceof Map map) {
                L.pushObjectValue(map.get(methodName));
                return 1;
            } else if (obj instanceof Class) {
                clazz = (Class) obj;
                isClass = true;
            } else {
                clazz = obj.getClass();
            }

            // 构造属性名（首字母大写）
            String propertyName = methodName;
            char c = propertyName.charAt(0);
            if (Character.isLowerCase(c)) {
                propertyName = Character.toUpperCase(c) + propertyName.substring(1);
            }

            // 使用类名 + 属性名构建 getter 缓存 key
            String cacheName = clazz.getName() + (isClass ? "$static" : "") + "$getter@" + propertyName;
            method = getterMethodCache.get(cacheName);
            if (method == null) {
                // 1. 优先尝试 isXxx（boolean 类型的标准 getter 命名）
                try {
                    Method isMethod = clazz.getMethod("is" + propertyName);
                    Class<?> retType = isMethod.getReturnType();
                    if (retType == boolean.class || retType == Boolean.class) {
                        if (!isClass || Modifier.isStatic(isMethod.getModifiers())) {
                            method = isMethod;
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                }

                // 2. 尝试 getXxx
                if (method == null) {
                    try {
                        Method getMethod = clazz.getMethod("get" + propertyName);
                        if (!isClass || Modifier.isStatic(getMethod.getModifiers())) {
                            method = getMethod;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }

                // 3. 尝试 hasXxx（部分类使用的命名方式）
                if (method == null) {
                    try {
                        Method hasMethod = clazz.getMethod("has" + propertyName);
                        Class<?> retType = hasMethod.getReturnType();
                        if (retType == boolean.class || retType == Boolean.class) {
                            if (!isClass || Modifier.isStatic(hasMethod.getModifiers())) {
                                method = hasMethod;
                            }
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }

                if (method == null) {
                    return 0;
                }
                getterMethodCache.put(cacheName, method);
            }

            Object ret;
            try {
                if (!Modifier.isPublic(method.getModifiers()))
                    method.setAccessible(true);
                ret = method.invoke(obj);
            } catch (Exception e) {
                throw new LuaException(e);
            }

            if (ret instanceof CharSequence)
                L.pushString(ret.toString());
            else
                L.pushObjectValue(ret);
            return 1;
        }
    }

    public static int javaSetter(LuaState L, Object obj, String methodName, Object value) throws LuaException {
        L.pushObjectValue(value);
        int ret = javaSetter(L, obj, methodName);
        L.pop(1);
        return ret;
    }

    public static int javaSetter(LuaState L, Object obj, String methodName) throws LuaException {
        synchronized (L) {
            Class clazz;
            boolean isClass = false;

            if (obj instanceof Map map) {
                map.put(methodName, L.toJavaObject(-1));
                return 1;
            } else if (obj instanceof Class) {
                clazz = (Class) obj;
                isClass = true;
            } else {
                clazz = obj.getClass();
            }

            if (methodName.length() > 2 && methodName.startsWith("on") && L.type(-1) == LuaState.LUA_TFUNCTION)
                return javaSetListener(L, obj, methodName, isClass);

            int ret = javaSetMethod(L, obj, methodName, isClass);
            if (ret != 0)
                return ret;
            return setDeclaredFieldValue(L, obj, methodName);
        }
    }

    private static int setDeclaredFieldValue(LuaState L, Object obj, String fieldName) throws LuaException {
        synchronized (L) {
            Field field = null;
            Class objClass;
            boolean isClass = false;

            if (obj == null)
                return 0;

            if (obj instanceof Class) {
                objClass = (Class) obj;
                isClass = true;
            } else {
                objClass = obj.getClass();
            }

            String name = null;
            if (!fieldName.startsWith("m")) {
                char c = fieldName.charAt(0);
                if (Character.isLowerCase(c)) {
                    name = Character.toUpperCase(c) + fieldName.substring(1);
                }
                name = "m" + name;
            }

            while (objClass != null) {
                try {
                    field = objClass.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    try {
                        if (name != null)
                            field = objClass.getDeclaredField(name);
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                if (field != null)
                    break;
                objClass = objClass.getSuperclass();
            }

            if (field == null)
                return 0;
            if (isClass && !Modifier.isStatic(field.getModifiers()))
                return 0;
            Class type = field.getType();
            try {
                if (!Modifier.isPublic(field.getModifiers()))
                    field.setAccessible(true);

                field.set(obj, compareTypes(L, type, L.getTop()));
            } catch (LuaException e) {
                argError(L, fieldName, 3, type);
            } catch (Exception e) {
                throw new LuaException(e);
            }

            return 1;
        }
    }


    /**
     * 增强版监听器设置，支持多种命名模式和自动 SAM 转换
     * 支持: setOnXxxListener, setXxxListener, addXxxListener, setXxxCallback 等
     * 支持: 直接传 Lua function 自动做 SAM 转换，或传 table 做全接口实现
     */
    private static int javaSetListener(LuaState L, Object obj, String methodName, boolean isClass) throws LuaException {
        synchronized (L) {
            Class<?> clazz = isClass ? (Class<?>) obj : obj.getClass();
            int valIdx = L.getTop();
            int valType = L.type(valIdx);
            // 生成多种可能的 setter 方法名
            String suffix = methodName;
            if (suffix.startsWith("on") && suffix.length() > 2) {
                suffix = suffix.substring(2);
            } else if (suffix.startsWith("set") && suffix.length() > 3 && suffix.endsWith("Listener")) {
                suffix = suffix.substring(3, suffix.length() - 8);
            }
            String[] setterNames = {
                "setOn" + suffix + "Listener",
                "set" + suffix + "Listener",
                "add" + suffix + "Listener",
                "setOn" + suffix + "Callback",
                "set" + suffix + "Callback",
                "set" + suffix + "Handler",
                "set" + suffix,
                methodName
            };
            for (String setterName : setterNames) {
                ArrayList<Method> methods = getMethod(clazz, setterName, isClass);
                for (Method m : methods) {
                    if (isClass && !Modifier.isStatic(m.getModifiers()))
                        continue;
                    Class<?>[] tp = m.getParameterTypes();
                    if (tp.length != 1 || !tp[0].isInterface())
                        continue;
                    try {
                        Object listener;
                        if (valType == LuaState.LUA_TFUNCTION) {
                            // 单个 Lua function，自动 SAM 转换
                            Method sam = findSamMethod(tp[0]);
                            if (sam == null) continue;
                            LuaObject luaObj = L.getLuaObject(valIdx);
                            listener = java.lang.reflect.Proxy.newProxyInstance(
                                    tp[0].getClassLoader(),
                                    new Class<?>[]{tp[0]},
                                    new LambdaInvocationHandler(L, luaObj, sam));
                        } else if (valType == LuaState.LUA_TTABLE) {
                            // table 形式，多方法接口实现
                            listener = createMultiMethodProxy(L, tp[0], valIdx);
                        } else {
                            // 已经是 Java 对象，直接使用
                            listener = L.toJavaObject(valIdx);
                            if (!tp[0].isInstance(listener)) continue;
                        }
                        if (!Modifier.isPublic(m.getModifiers()))
                            m.setAccessible(true);
                        m.invoke(obj, listener);
                        return 1;
                    } catch (Exception e) {
                        continue;
                    }
                }
            }
        }
        return 0;
    }

    private static int javaSetMethod(LuaState L, Object obj, String methodName, boolean isClass) throws LuaException {
        synchronized (L) {
            Class<?> clazz = (obj instanceof Class) ? (Class<?>) obj : obj.getClass();

            // 构造属性名（首字母大写）
            String propertyName = methodName;
            char c = propertyName.charAt(0);
            if (Character.isLowerCase(c)) {
                propertyName = Character.toUpperCase(c) + propertyName.substring(1);
            }
            String setterName = "set" + propertyName;

            // 使用独立的 setter 缓存
            int valType = L.type(-1);
            String cacheName = clazz.getName() + (isClass ? "$static" : "") + "$setter@" + propertyName + "#" + valType;
            Method cachedMethod = setterMethodCache.get(cacheName);

            if (cachedMethod != null) {
                try {
                    Object arg = compareTypes(L, cachedMethod.getParameterTypes()[0], L.getTop());
                    if (!Modifier.isPublic(cachedMethod.getModifiers()))
                        cachedMethod.setAccessible(true);
                    cachedMethod.invoke(obj, arg);
                    return 1;
                } catch (Exception e) {
                    // 缓存失效，重新查找
                    setterMethodCache.remove(cacheName);
                }
            }

            ArrayList<Method> methods = getMethod(clazz, setterName, isClass);
            Object arg = null;
            StringBuilder buf = new StringBuilder();
            Method matchedMethod = null;

            for (Method m : methods) {
                if (isClass && !Modifier.isStatic(m.getModifiers()))
                    continue;

                Class<?>[] tp = m.getParameterTypes();
                if (tp.length != 1)
                    continue;

                try {
                    arg = compareTypes(L, tp[0], L.getTop());
                    matchedMethod = m;
                    break;
                } catch (LuaException e) {
                    buf.append("-> ").append(tp[0].getName());
                    buf.append("\n");
                    continue;
                }
            }

            if (matchedMethod != null) {
                // 缓存匹配结果
                setterMethodCache.put(cacheName, matchedMethod);
                try {
                    if (!Modifier.isPublic(matchedMethod.getModifiers()))
                        matchedMethod.setAccessible(true);
                    matchedMethod.invoke(obj, arg);
                    return 1;
                } catch (Exception e) {
                    throw new LuaException(e);
                }
            }

            // table 形式的特殊处理（原有逻辑保留）
            int top = L.getTop();
            if (L.type(top) == LuaState.LUA_TTABLE) {
                L.getField(1, setterName);
                LuaFunction func = L.getFunction(-1);
                if (L.type(-1) == LuaState.LUA_TFUNCTION) {
                    if (func != null) {
                        int len = L.rawLen(top);
                        for (int i = 0; i < len; i++) {
                            L.getI(top, i + 1);
                        }
                        int ok = L.pcall(len, 0, 0);
                        if (ok == 0)
                            return 1;
                        else
                            throw new LuaException(L.toString(-1));
                    }
                }
            }
            if (buf.length() > 0) {
                // 构建清晰的 setter 错误信息
                StringBuilder msgBuilder = new StringBuilder();
                msgBuilder.append("Setter 属性 ").append(propertyName);
                msgBuilder.append(" 没有匹配的方法。\n");
                msgBuilder.append("传入值的类型: ").append(L.typeName(L.type(L.getTop())));
                msgBuilder.append("\n期望的 setter 参数类型:\n").append(buf);
                throw new LuaException(msgBuilder.toString());
            }
        }
        return 0;
    }

    private static int createProxyObject(LuaState L, String implem)
            throws LuaException {
        synchronized (L) {
            try {
                LuaObject luaObj = L.getLuaObject(2);
                Object proxy = luaObj.createProxy(implem);
                L.pushJavaObject(proxy);
            } catch (Exception e) {
                throw new LuaException(e);
            }

            return 1;
        }
    }

    private static int createProxyObject(LuaState L, Class implem) throws LuaException {
        synchronized (L) {
            L.pushJavaObject(createProxyObject(L, implem, 2));
            return 1;
        }
    }

    private static Object createProxyObject(LuaState L, Class implem, int idx) throws LuaException {
        synchronized (L) {
            try {
                LuaObject luaObj = L.getLuaObject(idx);
                return luaObj.createProxy(implem);
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }

    private static int createArray(LuaState L, Class<?> type) throws LuaException {
        synchronized (L) {
            L.pushJavaObject(createArray(L, type, 2));
            return 1;
        }
    }


    private static Object createArray(LuaState L, Class<?> type, int idx) throws LuaException {
        synchronized (L) {
            try {
                int n = L.objLen(idx);
                Object array = Array.newInstance(type, n);
                /*if(n==0)
                    return array.getClass();
*/
                if (type == String.class) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, L.toString(-1));
                        L.pop(1);
                    }
                } else if (type == Double.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, L.toNumber(-1));
                        L.pop(1);
                    }
                } else if (type == Float.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, (float) L.toNumber(-1));
                        L.pop(1);
                    }
                } else if (type == Long.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, L.toInteger(-1));
                        L.pop(1);
                    }
                } else if (type == Integer.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, (int) L.toInteger(-1));
                        L.pop(1);
                    }
                } else if (type == Short.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, (short) L.toInteger(-1));
                        L.pop(1);
                    }
                } else if (type == Character.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, (char) L.toInteger(-1));
                        L.pop(1);
                    }
                } else if (type == Byte.TYPE) {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, (byte) L.toInteger(-1));
                        L.pop(1);
                    }
                } else {
                    for (int i = 1; i <= n; i++) {
                        L.pushNumber(i);
                        L.getTable(idx);
                        Array.set(array, i - 1, compareTypes(L, type, L.getTop()));
                        L.pop(1);
                    }
                }
                return array;
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }

    /**
     * 将 Lua table 智能转换为 Java 对象
     * - 全整数键且从 1 开始连续 → ArrayList
     * - 否则 → HashMap
     * @param L Lua 状态
     * @param idx table 在栈上的索引
     * @return 转换后的 Java 对象（ArrayList 或 HashMap）
     */
    /**
     * 判断 Lua table 是否为纯数组形式（键为 1..n 连续整数）
     * @param L Lua 状态
     * @param idx table 在栈上的索引
     * @return 数组长度（空数组返回0），非数组形式返回 -1
     */
    private static int isArrayTable(LuaState L, int idx) {
        synchronized (L) {
            if (!L.isTable(idx)) return -1;
            int n = L.objLen(idx);
            if (n == 0) {
                // 空 table：检查是否有任何键
                L.pushNil();
                if (L.next(idx) != 0) {
                    L.pop(2);
                    return -1; // 有内容，不是纯数组
                }
                return 0; // 空数组
            }
            if (n < 0) return -1;
            // 检查 1..n 是否都有值
            for (int i = 1; i <= n; i++) {
                L.rawGetI(idx, i);
                if (L.isNil(-1)) {
                    L.pop(1);
                    return -1;
                }
                L.pop(1);
            }
            // 再检查是否有非数字键或超出范围的数字键
            L.pushNil();
            int count = 0;
            while (L.next(idx) != 0) {
                if (L.isNumber(-2)) {
                    long k = L.toInteger(-2);
                    if (k < 1 || k > n) {
                        L.pop(2);
                        return -1;
                    }
                } else {
                    L.pop(2);
                    return -1;
                }
                count++;
                L.pop(1);
            }
            if (count != n) return -1;
            return n;
        }
    }

    private static Object tableToJava(LuaState L, int idx) throws LuaException {
        synchronized (L) {
            int n = isArrayTable(L, idx);
            if (n > 0) {
                return createList(L, (Class<List<Object>>) (Class<?>) List.class, idx);
            } else {
                return createMap(L, (Class<Map<Object, Object>>) (Class<?>) Map.class, idx);
            }
        }
    }

    /**
     * 将 Java List/Map/数组转换为 Lua table 并压入栈
     * Lua 调用: luajava.toTable(javaObj)
     * @param luaState Lua 状态指针
     * @param objIdx Java 对象在栈上的索引
     * @return 返回值个数（1 个 table）
     */
    public static int javaToTable(long luaState, int objIdx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(objIdx);
        synchronized (L) {
            if (obj == null) {
                L.pushNil();
                return 1;
            }
            if (obj instanceof Map) {
                L.newTable();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                    L.pushObjectValue(entry.getKey());
                    L.pushObjectValue(entry.getValue());
                    L.setTable(-3);
                }
                return 1;
            }
            if (obj instanceof List) {
                List<?> list = (List<?>) obj;
                L.newTable();
                for (int i = 0; i < list.size(); i++) {
                    L.pushInteger(i + 1);
                    L.pushObjectValue(list.get(i));
                    L.setTable(-3);
                }
                return 1;
            }
            if (obj.getClass().isArray()) {
                int len = Array.getLength(obj);
                L.newTable();
                for (int i = 0; i < len; i++) {
                    L.pushInteger(i + 1);
                    L.pushObjectValue(Array.get(obj, i));
                    L.setTable(-3);
                }
                return 1;
            }
            if (obj instanceof Collection) {
                Collection<?> coll = (Collection<?>) obj;
                L.newTable();
                int i = 1;
                for (Object item : coll) {
                    L.pushInteger(i++);
                    L.pushObjectValue(item);
                    L.setTable(-3);
                }
                return 1;
            }
            // 不支持的类型，返回 nil
            L.pushNil();
            return 1;
        }
    }

    private static int createList(LuaState L, Class<?> type) throws LuaException {
        synchronized (L) {
            L.pushJavaObject(createList(L, (Class<List<Object>>) type, 2));
            return 1;
        }
    }


    private static Object createList(LuaState L, Class<List<Object>> type, int idx) throws LuaException {
        synchronized (L) {
            int n = L.objLen(idx);
            try {
                List<Object> list;
                if (type.equals(List.class))
                    list = new ArrayList<>();
                else
                    list = type.newInstance();
                for (int i = 1; i <= n; i++) {
                    L.pushNumber(i);
                    L.getTable(idx);
                    list.add(L.toJavaObject(-1));
                    L.pop(1);
                }
                return list;
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }


    private static int createMap(LuaState L, Class<?> clazz) throws LuaException {
        synchronized (L) {
            L.pushJavaObject(createMap(L, (Class<Map<Object, Object>>) clazz, 2));
            return 1;
        }
    }

    private static Object createMap(LuaState L, Class<Map<Object, Object>> clazz, int idx) throws LuaException {
        synchronized (L) {
            try {
                Map<Object, Object> map;
                if (clazz.equals(Map.class))
                    map = new HashMap<>();
                else {
                    Constructor<?> ctor = clazz.getDeclaredConstructor();
                    if (!java.lang.reflect.Modifier.isPublic(ctor.getModifiers())) {
                        ctor.setAccessible(true);
                    }
                    map = (Map<Object, Object>) ctor.newInstance();
                }
                L.pushNil();
                while (L.next(idx) != 0) {
                    map.put(L.toJavaObject(-2), L.toJavaObject(-1));
                    L.pop(1);
                }
                return map;
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }

    private static Object compareTypes(LuaState L, Class<?> parameter, int idx)
            throws LuaException {
        return compareTypes(L, parameter, L.type(idx), idx);
    }


    private static Object compareTypes(LuaState L, Class<?> parameter, int type, int idx)
            throws LuaException {
        boolean okType = true;
        Object obj = null;
        if (type == LuaState.LUA_TNIL)
            return null;
        switch (type) {
            case LuaState.LUA_TBOOLEAN: //boolean
            {
                if ((parameter.isPrimitive() && parameter != Boolean.TYPE) && !parameter.isAssignableFrom(Boolean.class)) {
                    okType = false;
                    break;
                }
                obj = L.toBoolean(idx);
            }
            break;
            case LuaState.LUA_TSTRING: //string
            {
                if (parameter.isEnum()) {
                    // 枚举值自动转换：Lua string → enum value
                    String name = L.toString(idx);
                    try {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        Object enumVal = Enum.valueOf((Class<Enum>) parameter, name);
                        obj = enumVal;
                    } catch (IllegalArgumentException e) {
                        okType = false;
                    }
                } else if (parameter == char.class || parameter == Character.class) {
                    String s = L.toString(idx);
                    // 只接受单字符字符串，多字符字符串应匹配 String 参数
                    if (s.length() != 1) {
                        okType = false;
                        break;
                    }
                    obj = s.charAt(0);
                } else if (!parameter.isAssignableFrom(String.class)) {
                    okType = false;
                } else {
                    obj = L.toString(idx);
                }
            }
            break;
            case LuaState.LUA_TFUNCTION: //function
            {
                if (parameter.isInterface()) {
                    // 自动 SAM 转换：检测是否是函数式接口
                    Method sam = findSamMethod(parameter);
                    if (sam != null) {
                        // 函数式接口，自动包装为 SAM 代理
                        LuaObject luaObj = L.getLuaObject(idx);
                        obj = java.lang.reflect.Proxy.newProxyInstance(
                                parameter.getClassLoader(),
                                new Class<?>[]{parameter},
                                new LambdaInvocationHandler(L, luaObj, sam));
                    } else {
                        // 多方法接口，单个 function 无法实现
                        okType = false;
                    }
                } else if (parameter.isAnnotationPresent(FunctionalInterface.class)) {
                    // 标记了 @FunctionalInterface 但不是接口？不可能
                    okType = false;
                } else if (!parameter.isAssignableFrom(LuaFunction.class)) {
                    okType = false;
                } else {
                    obj = L.getLuaObject(idx);
                }
            }
            break;
            case LuaState.LUA_TTABLE: //table
            {
                if (parameter.isAssignableFrom(LuaTable.class)) {
                    obj = L.getLuaObject(idx);
                } else if (parameter.isArray()) {
                    obj = createArray(L, parameter.getComponentType(), idx);
                } else if (List.class.isAssignableFrom(parameter)) {
                    obj = createList(L, (Class<List<Object>>) parameter, idx);
                } else if (Map.class.isAssignableFrom(parameter)) {
                    obj = createMap(L, (Class<Map<Object, Object>>) parameter, idx);
                } else if (Collection.class.isAssignableFrom(parameter)) {
                    // 参数是 Collection 或其父类型（如 Iterable），转 ArrayList
                    obj = createList(L, (Class<List<Object>>) (Class<?>) List.class, idx);
                } else if (parameter.isInterface()) {
                    // 多方法接口实现：检测 table 是否包含方法名→function 的映射
                    // 先检查是否是 SAM（单一抽象方法）且 table 只有一个 function 元素
                    Method sam = findSamMethod(parameter);
                    if (sam != null) {
                        // 检查 table 是否是 [1] = function 的形式（单函数作为 SAM）
                        L.rawGetI(idx, 1);
                        boolean isFuncTable = L.isFunction(-1);
                        L.pop(1);
                        if (isFuncTable) {
                            // table = {func} 形式，作为 SAM 处理
                            L.rawGetI(idx, 1);
                            LuaObject luaObj = L.getLuaObject(-1);
                            L.pop(1);
                            obj = java.lang.reflect.Proxy.newProxyInstance(
                                    parameter.getClassLoader(),
                                    new Class<?>[]{parameter},
                                    new LambdaInvocationHandler(L, luaObj, sam));
                        } else {
                            // table = {methodName = func, ...} 形式，多方法接口实现
                            obj = createMultiMethodProxy(L, parameter, idx);
                        }
                    } else {
                        // 非函数式接口，用多方法代理
                        obj = createMultiMethodProxy(L, parameter, idx);
                    }
                } else if (parameter.isAssignableFrom(Object.class)) {
                    // 参数类型为 Object 时，智能识别 Lua table 是 List 还是 Map
                    obj = tableToJava(L, idx);
                } else {
                    okType = false;
                }
            }
            break;
            case LuaState.LUA_TNUMBER: //number
            {
                // 数字类型兼容：primitive 数字、Number 的子类、Number 的父类/接口 都接受
                if (!parameter.isPrimitive() 
                    && !parameter.isAssignableFrom(Number.class)  // parameter 是 Number 的父类（如 Object、Serializable）
                    && !Number.class.isAssignableFrom(parameter)) // parameter 是 Number 的子类（如 Integer、Double）
                {
                    okType = false;
                    break;
                }
                if (L.isInteger(idx)) {
                    Long lg = L.toInteger(idx);
                    obj = LuaState.convertLuaNumber(lg, parameter);
                } else if (L.isNumber(idx)) {
                    Double db = L.toNumber(idx);
                    obj = LuaState.convertLuaNumber(db, parameter);
                }
            }
            break;
            case LuaState.LUA_TUSERDATA: //userdata
            {
                if (L.isObject(idx)) {
                    Object userObj = L.getObjectFromUserdata(idx);
                    if (userObj == null) {
                        return null;
                    } else if (parameter.isPrimitive()) {
                        Class<?> clazz = userObj.getClass();
                        if (parameter == byte.class && userObj instanceof Byte) {
                            obj = userObj;
                        } else if (parameter == short.class && userObj instanceof Short) {
                            obj = userObj;
                        } else if (parameter == int.class && userObj instanceof Integer) {
                            obj = userObj;
                        } else if (parameter == long.class && userObj instanceof Long) {
                            obj = userObj;
                        } else if (parameter == float.class && userObj instanceof Float) {
                            obj = userObj;
                        } else if (parameter == double.class && userObj instanceof Double) {
                            obj = userObj;
                        } else if (parameter == char.class && userObj instanceof Character) {
                            obj = userObj;
                        }
                    }
                    if (obj == null) {
                        if (parameter.isAssignableFrom(userObj.getClass())) {
                            obj = userObj;
                        } else {
                            okType = false;
                        }
                    }
                } else {
                    if (!parameter.isAssignableFrom(LuaObject.class)) {
                        okType = false;
                    } else {
                        obj = L.getLuaObject(idx);
                    }
                }
            }
            break;
            default: //other
            {
                throw new LuaException("参数类型转换失败: 不支持的类型 " + L.typeName(type) + " (索引 " + idx + ")，期望 " + parameter.getSimpleName());
            }
        }
        if (!okType || obj == null) {
            throw new LuaException("Invalid Parameter.");
        }
        return obj;
    }


    private static int toPrimitive(LuaState L, Class type, int idx) throws LuaException {
        Object obj = null;

        if (type == Character.TYPE && L.type(idx) == LuaState.LUA_TSTRING) {
            String s = L.toString(idx);
            if (s.length() == 1)
                obj = s.charAt(0);
            else
                obj = s.toCharArray();
        } else if (!L.isNumber(idx)) {
            throw new LuaException(L.toString(idx) + " is not number");
        } else if (type == Double.TYPE) {
            obj = L.toNumber(idx);
        } else if (type == Float.TYPE) {
            obj = (float) L.toNumber(idx);
        } else if (type == Long.TYPE) {
            obj = L.toInteger(idx);
        } else if (type == Integer.TYPE) {
            obj = (int) L.toInteger(idx);
        } else if (type == Short.TYPE) {
            obj = (short) L.toInteger(idx);
        } else if (type == Character.TYPE) {
            obj = (char) L.toInteger(idx);
        } else if (type == Byte.TYPE) {
            obj = (byte) L.toInteger(idx);
        } else if (type == Boolean.TYPE) {
            obj = L.toBoolean(idx);
        }
        L.pushJavaObject(obj);
        return 1;
    }

    public static void pushJavaObject(int idx, Object obj) {
        javaObjectMap.put(idx, obj);
    }

    public static Object getJavaObject2(int i) {
        Object obj = javaObjectMap.get(i);
        return obj;
    }

    // ==================== 反射扩展方法 ====================

    public static int javaGetClass(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            if (obj == null)
                L.pushNil();
            else
                L.pushJavaObject(obj.getClass());
            return 1;
        }
    }

    public static int javaGetClassName(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            if (obj == null)
                L.pushString("null");
            else if (obj instanceof Class)
                // obj 本身就是 Class 对象，直接取它代表的类的名字
                L.pushString(((Class<?>) obj).getName());
            else
                L.pushString(obj.getClass().getName());
            return 1;
        }
    }

    public static int javaIsNull(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            L.pushBoolean(obj == null);
            return 1;
        }
    }

    public static int javaGetSimpleName(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            if (obj == null)
                L.pushString("null");
            else if (obj instanceof Class)
                L.pushString(((Class<?>) obj).getSimpleName());
            else
                L.pushString(obj.getClass().getSimpleName());
            return 1;
        }
    }

    public static int javaGetPackageName(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            if (obj == null)
                L.pushString("null");
            else {
                Class<?> clazz;
                if (obj instanceof Class)
                    clazz = (Class<?>) obj;
                else
                    clazz = obj.getClass();
                Package pkg = clazz.getPackage();
                L.pushString(pkg != null ? pkg.getName() : "");
            }
            return 1;
        }
    }

    public static int javaGetSuperclass(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            if (obj == null) {
                L.pushNil();
            } else {
                // 如果传入的是 Class 对象就用 Class 自身，否则取其 class
                Class<?> clz = (obj instanceof Class) ? (Class<?>) obj : obj.getClass();
                Class<?> sup = clz.getSuperclass();
                if (sup != null)
                    L.pushJavaObject(sup);
                else
                    L.pushNil();
            }
            return 1;
        }
    }

    public static int javaGetInterfaces(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            if (obj == null) {
                L.pushNil();
            } else {
                // 如果传入的是 Class 对象就用 Class 自身，否则取其 class
                Class<?> clz = (obj instanceof Class) ? (Class<?>) obj : obj.getClass();
                Class<?>[] ifaces = clz.getInterfaces();
                L.newTable();
                for (int i = 0; i < ifaces.length; i++) {
                    L.pushJavaObject(ifaces[i]);
                    L.rawSetI(-2, i + 1);
                }
            }
            return 1;
        }
    }

    public static int javaIsInterface(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            boolean isInterface = (obj instanceof Class) && ((Class<?>) obj).isInterface();
            L.pushBoolean(isInterface);
            return 1;
        }
    }

    public static int javaIsArray(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            L.pushBoolean(obj != null && obj.getClass().isArray());
            return 1;
        }
    }

    public static int javaGetComponentType(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            if (obj == null || !obj.getClass().isArray()) {
                L.pushNil();
            } else {
                Class<?> compType = obj.getClass().getComponentType();
                L.pushJavaObject(compType);
            }
            return 1;
        }
    }

    private final static Map<String, Method> staticVoidMethodCache = new ConcurrentHashMap<>();
    private final static Map<String, Method> staticStringMethodCache = new ConcurrentHashMap<>();
    private final static Map<String, Method> staticIntegerMethodCache = new ConcurrentHashMap<>();
    private final static Map<String, Method> staticDoubleMethodCache = new ConcurrentHashMap<>();
    private final static Map<String, Method> staticBoolMethodCache = new ConcurrentHashMap<>();
    private final static Map<String, Method[]> staticMethodCache = new ConcurrentHashMap<>();

    /**
     * 调用静态方法，支持参数传递
     * Lua 调用: luajava.callStatic(className, methodName, arg1, arg2, ...)
     * 参数从栈索引 3 开始（1=className, 2=methodName）
     */
    public static int javaCallStatic(long luaState, String className, String methodName)
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            StringBuilder msgBuilder = new StringBuilder();
            try {
                Class<?> clazz = Class.forName(className);
                String cacheName = className + "." + methodName;

                // 参数从索引 3 开始（1=className, 2=methodName）
                int argCount = L.getTop() - 2;
                if (argCount < 0) argCount = 0;

                Method method = null;
                int methodType = -1;

                // 无参数快速路径
                if (argCount == 0) {
                    methodType = LuaState.LUA_TNIL;
                    method = staticVoidMethodCache.get(cacheName);
                    if (method != null) {
                        if (!Modifier.isPublic(method.getModifiers()))
                            method.setAccessible(true);
                        Object ret = method.invoke(null);
                        if (ret == null && method.getReturnType().equals(Void.TYPE))
                            return 0;
                        L.pushObjectValue(ret);
                        return 1;
                    }
                }

                Object[] objs = new Object[argCount];

                // 单参数快速路径
                if (argCount == 1) {
                    int argType = L.type(3);
                    switch (argType) {
                        case LuaState.LUA_TSTRING:
                            methodType = LuaState.LUA_TSTRING;
                            method = staticStringMethodCache.get(cacheName);
                            if (method != null)
                                objs[0] = L.toString(3);
                            break;
                        case LuaState.LUA_TBOOLEAN:
                            methodType = LuaState.LUA_TBOOLEAN;
                            method = staticBoolMethodCache.get(cacheName);
                            if (method != null)
                                objs[0] = L.toBoolean(3);
                            break;
                        case LuaState.LUA_TNUMBER:
                            if (L.isInteger(3)) {
                                methodType = LuaState.LUA_TINTEGER;
                                method = staticIntegerMethodCache.get(cacheName);
                                if (method != null)
                                    objs[0] = LuaState.convertLuaNumber(L.toInteger(3), method.getParameterTypes()[0]);
                            } else {
                                methodType = LuaState.LUA_TNUMBER;
                                method = staticDoubleMethodCache.get(cacheName);
                                if (method != null)
                                    objs[0] = LuaState.convertLuaNumber(L.toNumber(3), method.getParameterTypes()[0]);
                            }
                            break;
                    }
                    if (method != null) {
                        if (!Modifier.isPublic(method.getModifiers()))
                            method.setAccessible(true);
                        Object ret = method.invoke(null, objs);
                        if (ret == null && method.getReturnType().equals(Void.TYPE))
                            return 0;
                        L.pushObjectValue(ret);
                        return 1;
                    }
                }

                // 多参数：遍历所有静态方法匹配
                Method[] methods = staticMethodCache.get(cacheName);
                if (methods == null) {
                    // 收集所有同名静态方法
                    ArrayList<Method> staticMethods = new ArrayList<>();
                    for (Method m : clazz.getMethods()) {
                        if (m.getName().equals(methodName) && Modifier.isStatic(m.getModifiers())) {
                            staticMethods.add(m);
                        }
                    }
                    methods = staticMethods.toArray(new Method[0]);
                    staticMethodCache.put(cacheName, methods);
                }

                int[] types = new int[argCount];
                for (int i = 0; i < argCount; i++) {
                    types[i] = L.type(i + 3);  // 参数从索引 3 开始
                }

                for (Method m : methods) {
                    Class<?>[] parameters = m.getParameterTypes();
                    if (parameters.length != argCount)
                        continue;

                    boolean okMethod = true;
                    for (int j = 0; j < parameters.length; j++) {
                        try {
                            objs[j] = compareTypes(L, parameters[j], types[j], j + 3);
                        } catch (Exception e) {
                            okMethod = false;
                            break;
                        }
                    }

                    if (okMethod) {
                        method = m;
                        if (!Modifier.isPublic(method.getModifiers()))
                            method.setAccessible(true);
                        Object ret;
                        try {
                            ret = method.invoke(null, objs);
                        } catch (IllegalArgumentException e) {
                            // 参数不匹配，继续尝试下一个重载
                            msgBuilder.append("  at ").append(method).append("\n  -> ").append(e).append("\n");
                            continue;
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            // 方法内部抛出的业务异常，直接抛出
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            throw new LuaException("静态方法调用异常: " + method + "\n  -> " + cause, cause);
                        } catch (Exception e) {
                            // 其他异常，记录后继续
                            msgBuilder.append("  at ").append(method).append("\n  -> ").append(e).append("\n");
                            continue;
                        }

                        // 缓存匹配的方法
                        switch (methodType) {
                            case LuaState.LUA_TSTRING:
                                staticStringMethodCache.put(cacheName, method);
                                break;
                            case LuaState.LUA_TINTEGER:
                                staticIntegerMethodCache.put(cacheName, method);
                                break;
                            case LuaState.LUA_TNUMBER:
                                staticDoubleMethodCache.put(cacheName, method);
                                break;
                            case LuaState.LUA_TBOOLEAN:
                                staticBoolMethodCache.put(cacheName, method);
                                break;
                            case LuaState.LUA_TNIL:
                                staticVoidMethodCache.put(cacheName, method);
                                break;
                        }

                        if (ret == null && method.getReturnType().equals(Void.TYPE))
                            return 0;
                        L.pushObjectValue(ret);
                        return 1;
                    }
                }

                // === varargs 匹配：精确匹配失败后，尝试可变参数方法 ===
                for (Method m : methods) {
                    if (!m.isVarArgs()) continue;
                    Class<?>[] parameters = m.getParameterTypes();
                    int fixedCount = parameters.length - 1;  // 最后一个是 vararg 数组
                    if (argCount < fixedCount) continue;

                    Class<?> varargType = parameters[fixedCount].getComponentType();
                    Object[] varArgs = null;
                    boolean okMethod = true;

                    // 固定参数匹配（静态方法参数从栈索引 3 开始）
                    for (int j = 0; j < fixedCount; j++) {
                        try {
                            objs[j] = compareTypes(L, parameters[j], types[j], j + 3);
                        } catch (Exception e) {
                            okMethod = false;
                            break;
                        }
                    }
                    if (!okMethod) continue;

                    // === 模式1：最后一个参数是 Lua table 且为数组形式 → 自动展开 ===
                    int tableVarArgLen = -1;
                    if (argCount == fixedCount + 1) {
                        tableVarArgLen = isArrayTable(L, fixedCount + 3);
                    }
                    if (tableVarArgLen > 0) {
                        // 将 table 展开为 varargs
                        varArgs = new Object[tableVarArgLen];
                        for (int j = 0; j < tableVarArgLen; j++) {
                            try {
                                L.pushInteger(j + 1);
                                L.getTable(fixedCount + 3);
                                int valType = L.type(-1);
                                varArgs[j] = compareTypes(L, varargType, valType, -1);
                                L.pop(1);
                            } catch (Exception e) {
                                okMethod = false;
                                // 清理栈
                                if (L.type(-1) != LuaState.LUA_TNONE) {
                                    L.pop(1);
                                }
                                break;
                            }
                        }
                    } else {
                        // === 模式2：正常多参数 varargs ===
                        varArgs = new Object[argCount - fixedCount];
                        for (int j = 0; j < varArgs.length; j++) {
                            try {
                                varArgs[j] = compareTypes(L, varargType, types[fixedCount + j], fixedCount + j + 3);
                            } catch (Exception e) {
                                okMethod = false;
                                break;
                            }
                        }
                    }
                    if (!okMethod) continue;

                    // 构造最终参数数组
                    Object[] invokeArgs = new Object[fixedCount + 1];
                    System.arraycopy(objs, 0, invokeArgs, 0, fixedCount);
                    // 将 varArgs 转成正确类型的数组
                    Object varargArray = java.lang.reflect.Array.newInstance(varargType, varArgs.length);
                    System.arraycopy(varArgs, 0, varargArray, 0, varArgs.length);
                    invokeArgs[fixedCount] = varargArray;

                    method = m;
                    if (!Modifier.isPublic(method.getModifiers()))
                        method.setAccessible(true);
                    Object ret;
                    try {
                        ret = method.invoke(null, invokeArgs);
                    } catch (Exception e) {
                        msgBuilder.append("  at ").append(method).append(" (varargs)\n  -> ").append((e.getCause() != null) ? e.getCause() : e).append("\n");
                        continue;
                    }

                    if (ret == null && method.getReturnType().equals(Void.TYPE))
                        return 0;
                    L.pushObjectValue(ret);
                    return 1;
                }

                // 没有找到匹配的方法，构建错误信息
                if (msgBuilder.length() > 0) {
                    throw new LuaException("Invalid static method call.\n" + msgBuilder);
                }
                msgBuilder.append("没有匹配的静态方法: ").append(className).append(".").append(methodName).append("\n");
                msgBuilder.append("传入参数个数: ").append(argCount).append("\n");
                msgBuilder.append("传入参数类型: ");
                for (int i = 0; i < argCount; i++) {
                    if (i > 0) msgBuilder.append(", ");
                    msgBuilder.append(L.typeName(L.type(i + 3)));
                }
                msgBuilder.append("\n\n可用的静态方法签名 (共").append(methods.length).append("个):\n");
                for (Method m : methods) {
                    msgBuilder.append("  ").append(m.getName()).append("(");
                    Class<?>[] params = m.getParameterTypes();
                    for (int j = 0; j < params.length; j++) {
                        if (j > 0) msgBuilder.append(", ");
                        msgBuilder.append(params[j].getSimpleName());
                    }
                    msgBuilder.append(")\n");
                }
                throw new LuaException("静态方法调用失败: 参数不匹配\n" + msgBuilder);

            } catch (LuaException e) {
                throw e;
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }

    public static int javaGetStaticField(long luaState, String className, String fieldName) 
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            try {
                Class<?> clazz = Class.forName(className);
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(null);
                L.pushObjectValue(value);  // 用 pushObjectValue，自动转 Lua 基础类型
                return 1;
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }

    public static int javaSetStaticField(long luaState, String className, String fieldName) 
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            try {
                Class<?> clazz = Class.forName(className);
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                // 用 compareTypes 做类型转换，支持 int/long 等 primitive
                Object value = compareTypes(L, field.getType(), 3);
                field.set(null, value);
                return 0;
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }

    public static int javaGetMethods(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            if (obj == null) {
                L.pushNil();
                return 1;
            }
            Method[] methods = obj.getClass().getMethods();
            L.newTable();
            for (int i = 0; i < methods.length; i++) {
                L.pushString(methods[i].getName());
                L.rawSetI(-2, i + 1);
            }
            return 1;
        }
    }

    public static int javaGetFields(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            if (obj == null) {
                L.pushNil();
                return 1;
            }
            // 如果 obj 本身就是 Class 对象，直接用它，否则用 obj.getClass()
            Class<?> clazz = (obj instanceof Class) ? (Class<?>) obj : obj.getClass();
            Field[] fields = clazz.getFields();
            L.newTable();
            for (int i = 0; i < fields.length; i++) {
                L.pushString(fields[i].getName());
                L.rawSetI(-2, i + 1);
            }
            return 1;
        }
    }

    public static int javaGetConstructors(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            if (obj == null) {
                L.pushNil();
                return 1;
            }
            // 如果 obj 本身就是 Class 对象，直接用它，否则用 obj.getClass()
            Class<?> clazz = (obj instanceof Class) ? (Class<?>) obj : obj.getClass();
            Constructor<?>[] constructors = clazz.getConstructors();
            L.newTable();
            for (int i = 0; i < constructors.length; i++) {
                L.pushString(constructors[i].toGenericString());
                L.rawSetI(-2, i + 1);
            }
            return 1;
        }
    }

    public static int javaImportClass(long luaState, String className) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            try {
                Class<?> clazz = Class.forName(className);
                L.pushJavaObject(clazz);
                // 同时将简单类名注册为全局变量，方便直接使用
                String simpleName = clazz.getSimpleName();
                L.pushJavaObject(clazz);
                L.setGlobal(simpleName);
                return 1;
            } catch (ClassNotFoundException e) {
                L.pushNil();
                return 1;
            }
        }
    }

    public static int javaNewWithConstructor(long luaState, String className) 
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            try {
                Class<?> clazz = Class.forName(className);
                int nargs = L.getTop();
                if (nargs > 1) {
                    // 带参数构造：按参数类型匹配构造函数
                    int argCount = nargs - 1;
                    Object[] args = new Object[argCount];
                    Constructor<?>[] constructors = clazz.getConstructors();
                    
                    for (Constructor<?> c : constructors) {
                        if (c.getParameterCount() != argCount)
                            continue;
                        
                        Class<?>[] params = c.getParameterTypes();
                        boolean ok = true;
                        for (int i = 0; i < argCount; i++) {
                            try {
                                args[i] = compareTypes(L, params[i], i + 2);
                            } catch (Exception e) {
                                ok = false;
                                break;
                            }
                        }
                        
                        if (ok) {
                            c.setAccessible(true);
                            Object instance = c.newInstance(args);
                            L.pushJavaObject(instance);
                            return 1;
                        }
                    }
                    
                    // 没有匹配的构造函数，构建错误信息
                    StringBuilder sb = new StringBuilder();
                    sb.append("没有匹配的构造函数: ").append(className).append("\n");
                    sb.append("传入参数个数: ").append(argCount).append("\n");
                    sb.append("传入参数类型: ");
                    for (int i = 0; i < argCount; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(L.typeName(L.type(i + 2)));
                    }
                    sb.append("\n\n可用的构造函数签名 (共").append(constructors.length).append("个):\n");
                    for (Constructor<?> c : constructors) {
                        sb.append("  ").append(c.getName()).append("(");
                        Class<?>[] params = c.getParameterTypes();
                        for (int j = 0; j < params.length; j++) {
                            if (j > 0) sb.append(", ");
                            sb.append(params[j].getSimpleName());
                        }
                        sb.append(")\n");
                    }
                    throw new LuaException(sb.toString());
                }
                // 无参构造
                Constructor<?> noArgCtor = clazz.getDeclaredConstructor();
                if (!java.lang.reflect.Modifier.isPublic(noArgCtor.getModifiers())) {
                    noArgCtor.setAccessible(true);
                }
                Object instance = noArgCtor.newInstance();
                L.pushJavaObject(instance);
                return 1;
            } catch (LuaException e) {
                throw e;
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }

    public static int javaGetObjectMethods(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            if (obj == null) {
                L.pushNil();
                return 1;
            }
            Method[] methods = obj.getClass().getDeclaredMethods();
            L.newTable();
            for (int i = 0; i < methods.length; i++) {
                L.pushString(methods[i].getName());
                L.rawSetI(-2, i + 1);
            }
            return 1;
        }
    }

    public static int javaGetDeclaredField(long luaState, int objIdx, String fieldName) 
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(objIdx);
        synchronized (L) {
            try {
                if (obj == null) {
                    L.pushNil();
                    return 1;
                }
                Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(obj);
                L.pushJavaObject(value);
                return 1;
            } catch (NoSuchFieldException e) {
                L.pushNil();
                return 1;
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }

    public static int javaSetDeclaredField(long luaState, int objIdx, String fieldName) 
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(objIdx);
        synchronized (L) {
            try {
                if (obj == null)
                    return 0;
                Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = L.toJavaObject(3);
                // 类型窄化：toJavaObject 对 Lua 整数返回 Long，需转为字段实际类型
                value = narrowFieldValue(value, field.getType());
                field.set(obj, value);
                return 0;
            } catch (Exception e) {
                throw new LuaException(e);
            }
        }
    }

    /**
     * 将 toJavaObject 返回的值窄化为字段实际类型
     * 解决 Lua 整数 → Long 无法直接设置 int/short/byte 等字段的问题
     */
    private static Object narrowFieldValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        if (value instanceof Number && targetType.isPrimitive()) {
            Number num = (Number) value;
            if (targetType == int.class) return num.intValue();
            if (targetType == long.class) return num.longValue();
            if (targetType == short.class) return num.shortValue();
            if (targetType == byte.class) return num.byteValue();
            if (targetType == float.class) return num.floatValue();
            if (targetType == double.class) return num.doubleValue();
            if (targetType == char.class) return (char) num.intValue();
        }
        if (value instanceof Number && Number.class.isAssignableFrom(targetType)) {
            Number num = (Number) value;
            if (targetType == Integer.class) return num.intValue();
            if (targetType == Long.class) return num.longValue();
            if (targetType == Short.class) return num.shortValue();
            if (targetType == Byte.class) return num.byteValue();
            if (targetType == Float.class) return num.floatValue();
            if (targetType == Double.class) return num.doubleValue();
        }
        return value;
    }

    /**
     * 调用声明的方法（含私有），支持重载匹配、类型转换和显式签名
     * 显式签名格式: methodName(typ1,typ2,...) 如: getValue(int,String)
     */
    public static int javaCallDeclaredMethod(long luaState, int objIdx, String methodName)
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(objIdx);
        synchronized (L) {
            if (obj == null) {
                throw new LuaException("Java 对象已被释放，无法调用方法: " + methodName);
            }
            Class<?> clazz = (obj instanceof Class) ? (Class<?>) obj : obj.getClass();
            boolean isClass = obj instanceof Class;
            int argCount = L.getTop() - 2;
            Object[] objs = new Object[argCount];
            int[] types = new int[argCount];
            for (int i = 0; i < argCount; i++) {
                types[i] = L.type(i + 3);
            }
            // 解析显式签名：method(sig) 格式
            String actualMethodName = methodName;
            Class<?>[] explicitSig = null;
            int parenIdx = methodName.indexOf('(');
            if (parenIdx > 0 && methodName.endsWith(")")) {
                actualMethodName = methodName.substring(0, parenIdx);
                String sigStr = methodName.substring(parenIdx + 1, methodName.length() - 1);
                if (!sigStr.isEmpty()) {
                    String[] typeNames = sigStr.split(",");
                    explicitSig = new Class<?>[typeNames.length];
                    for (int i = 0; i < typeNames.length; i++) {
                        explicitSig[i] = parseType(typeNames[i].trim());
                        if (explicitSig[i] == null) {
                            throw new LuaException("无法解析类型: " + typeNames[i]);
                        }
                    }
                } else {
                    explicitSig = new Class<?>[0];
                }
            }
            // 收集所有匹配名称的方法（包括私有，从当前类和父类查找）
            java.util.List<Method> candidates = new java.util.ArrayList<>();
            Class<?> searchClass = clazz;
            while (searchClass != null) {
                try {
                    Method[] methods = searchClass.getDeclaredMethods();
                    for (Method m : methods) {
                        if (m.getName().equals(actualMethodName)) {
                            if (isClass && !Modifier.isStatic(m.getModifiers())) continue;
                            if (!isClass && Modifier.isStatic(m.getModifiers())) continue;
                            candidates.add(m);
                        }
                    }
                } catch (Exception ignored) {}
                searchClass = searchClass.getSuperclass();
            }
            if (candidates.isEmpty()) {
                throw new LuaException("找不到方法: " + clazz.getSimpleName() + "." + actualMethodName);
            }
            // 如果指定了显式签名，精确匹配
            if (explicitSig != null) {
                for (Method m : candidates) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length != explicitSig.length) continue;
                    boolean match = true;
                    for (int i = 0; i < params.length; i++) {
                        if (!isTypeCompatible(params[i], explicitSig[i])) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        return invokeDeclaredMethod(L, obj, m, argCount, types, objs);
                    }
                }
                throw new LuaException("找不到匹配签名的方法: " + methodName);
            }
            StringBuilder msgBuilder = new StringBuilder();
            // 1. 无参数
            if (argCount == 0) {
                for (Method m : candidates) {
                    if (m.getParameterTypes().length == 0) {
                        return invokeDeclaredMethod(L, obj, m, 0, types, objs);
                    }
                }
            }
            // 2. 精确参数匹配
            for (Method m : candidates) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length != argCount) continue;
                boolean ok = true;
                for (int j = 0; j < params.length; j++) {
                    try {
                        objs[j] = compareTypes(L, params[j], types[j], j + 3);
                    } catch (Exception e) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return invokeDeclaredMethod(L, obj, m, argCount, types, objs);
                }
            }
            // 3. varargs 匹配
            for (Method m : candidates) {
                if (!m.isVarArgs()) continue;
                Class<?>[] params = m.getParameterTypes();
                int fixedCount = params.length - 1;
                if (argCount < fixedCount) continue;
                Class<?> varargType = params[fixedCount].getComponentType();
                boolean ok = true;
                Object[] invokeArgs = new Object[params.length];
                for (int j = 0; j < fixedCount; j++) {
                    try {
                        invokeArgs[j] = compareTypes(L, params[j], types[j], j + 3);
                    } catch (Exception e) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;
                int tableVarArgLen = -1;
                if (argCount == fixedCount + 1) {
                    tableVarArgLen = isArrayTable(L, fixedCount + 3);
                }
                Object varargArray;
                if (tableVarArgLen > 0) {
                    varargArray = Array.newInstance(varargType, tableVarArgLen);
                    for (int j = 0; j < tableVarArgLen; j++) {
                        L.rawGetI(fixedCount + 3, j + 1);
                        try {
                            Array.set(varargArray, j, compareTypes(L, varargType, L.type(-1), -1));
                        } catch (Exception e) {
                            ok = false;
                            L.pop(1);
                            break;
                        }
                        L.pop(1);
                    }
                } else {
                    int varArgCount = argCount - fixedCount;
                    varargArray = Array.newInstance(varargType, varArgCount);
                    for (int j = 0; j < varArgCount; j++) {
                        try {
                            Array.set(varargArray, j, compareTypes(L, varargType, types[fixedCount + j], fixedCount + j + 3));
                        } catch (Exception e) {
                            ok = false;
                            break;
                        }
                    }
                }
                if (!ok) continue;
                invokeArgs[fixedCount] = varargArray;
                try {
                    if (!Modifier.isPublic(m.getModifiers()))
                        m.setAccessible(true);
                    Object ret = m.invoke(isClass ? null : obj, invokeArgs);
                    if (ret == null && m.getReturnType().equals(Void.TYPE))
                        return 0;
                    L.pushObjectValue(ret);
                    return 1;
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    throw new LuaException("方法调用异常: " + m + "\n  -> " + cause, cause);
                } catch (Exception e) {
                    msgBuilder.append("  at ").append(m).append(" (varargs)\n  -> ").append(e).append("\n");
                    continue;
                }
            }
            msgBuilder.append("没有匹配的方法: ").append(clazz.getSimpleName()).append(".").append(methodName).append("\n");
            msgBuilder.append("传入参数个数: ").append(argCount).append("\n");
            msgBuilder.append("可用方法:\n");
            for (Method m : candidates) {
                msgBuilder.append("  ").append(m.getName()).append("(");
                Class<?>[] params = m.getParameterTypes();
                for (int j = 0; j < params.length; j++) {
                    if (j > 0) msgBuilder.append(", ");
                    msgBuilder.append(params[j].getSimpleName());
                }
                if (m.isVarArgs()) msgBuilder.append("...");
                msgBuilder.append(")\n");
            }
            throw new LuaException("方法调用失败\n" + msgBuilder);
        }
    }

    /**
     * 调用已匹配的声明方法
     */
    private static int invokeDeclaredMethod(LuaState L, Object obj, Method m, int argCount, int[] types, Object[] objs) throws LuaException {
        boolean isClass = obj instanceof Class;
        try {
            if (!Modifier.isPublic(m.getModifiers()))
                m.setAccessible(true);
            for (int j = 0; j < argCount; j++) {
                objs[j] = compareTypes(L, m.getParameterTypes()[j], types[j], j + 3);
            }
            Object ret = m.invoke(isClass ? null : obj, objs);
            if (ret == null && m.getReturnType().equals(Void.TYPE))
                return 0;
            L.pushObjectValue(ret);
            return 1;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new LuaException("方法调用异常: " + m + "\n  -> " + cause, cause);
        } catch (Exception e) {
            throw new LuaException("调用方法失败: " + m + "\n  -> " + e, e);
        }
    }

    /**
     * 解析类型名称为 Class 对象
     * 支持：基本类型、完全限定类名、数组类型（如 int[]、String[]）
     */
    private static Class<?> parseType(String typeName) {
        if (typeName == null || typeName.isEmpty()) return null;
        typeName = typeName.trim();
        // 处理数组类型
        int arrayDim = 0;
        String componentName = typeName;
        while (componentName.endsWith("[]")) {
            arrayDim++;
            componentName = componentName.substring(0, componentName.length() - 2).trim();
        }
        Class<?> componentClass;
        switch (componentName) {
            case "int": componentClass = int.class; break;
            case "long": componentClass = long.class; break;
            case "float": componentClass = float.class; break;
            case "double": componentClass = double.class; break;
            case "boolean": componentClass = boolean.class; break;
            case "byte": componentClass = byte.class; break;
            case "short": componentClass = short.class; break;
            case "char": componentClass = char.class; break;
            case "void": componentClass = void.class; break;
            case "String": componentClass = String.class; break;
            case "Object": componentClass = Object.class; break;
            default:
                try {
                    componentClass = Class.forName(componentName);
                } catch (ClassNotFoundException e) {
                    // 尝试 java.lang 包
                    try {
                        componentClass = Class.forName("java.lang." + componentName);
                    } catch (ClassNotFoundException e2) {
                        return null;
                    }
                }
        }
        // 创建数组类型
        if (arrayDim > 0) {
            // 使用 Array.newInstance 创建数组获取其 Class
            int[] dims = new int[arrayDim];
            for (int i = 0; i < arrayDim; i++) dims[i] = 0;
            return Array.newInstance(componentClass, dims).getClass();
        }
        return componentClass;
    }

    /**
     * 类型兼容性检查（用于显式签名匹配）
     * 检查 explicit 类型的值能否赋值给 param 类型的参数
     * 支持：基本类型 ↔ 包装类型、数值类型 widening、子类 → 父类
     */
    private static boolean isTypeCompatible(Class<?> param, Class<?> explicit) {
        if (param == null || explicit == null) return param == explicit;
        if (param == explicit) return true;
        // 基本类型 ↔ 包装类型 双向兼容
        if (param.isPrimitive()) {
            if (param == int.class) {
                return explicit == Integer.class || explicit == long.class || explicit == Long.class
                        || explicit == short.class || explicit == Short.class
                        || explicit == byte.class || explicit == Byte.class
                        || explicit == float.class || explicit == Float.class
                        || explicit == double.class || explicit == Double.class
                        || explicit == char.class || explicit == Character.class
                        || Number.class.isAssignableFrom(explicit);
            }
            if (param == long.class) {
                return explicit == Long.class || explicit == int.class || explicit == Integer.class
                        || explicit == float.class || explicit == Float.class
                        || explicit == double.class || explicit == Double.class
                        || Number.class.isAssignableFrom(explicit);
            }
            if (param == float.class) {
                return explicit == Float.class || explicit == double.class || explicit == Double.class
                        || explicit == int.class || explicit == Integer.class
                        || explicit == long.class || explicit == Long.class
                        || Number.class.isAssignableFrom(explicit);
            }
            if (param == double.class) {
                return explicit == Double.class || explicit == float.class || explicit == Float.class
                        || explicit == int.class || explicit == Integer.class
                        || explicit == long.class || explicit == Long.class
                        || Number.class.isAssignableFrom(explicit);
            }
            if (param == boolean.class) return explicit == Boolean.class;
            if (param == byte.class) {
                return explicit == Byte.class || explicit == int.class || explicit == Integer.class
                        || explicit == short.class || explicit == Short.class
                        || explicit == long.class || explicit == Long.class
                        || Number.class.isAssignableFrom(explicit);
            }
            if (param == short.class) {
                return explicit == Short.class || explicit == int.class || explicit == Integer.class
                        || explicit == byte.class || explicit == Byte.class
                        || explicit == long.class || explicit == Long.class
                        || Number.class.isAssignableFrom(explicit);
            }
            if (param == char.class) {
                return explicit == Character.class || explicit == int.class || explicit == Integer.class
                        || explicit == short.class || explicit == Short.class;
                // 注意：不包含 String.class！char 只接受单字符，多字符字符串应匹配 String 参数
                // compareTypes 中已正确处理：单字符字符串可转为 char，多字符字符串拒绝
            }
        } else {
            // param 是引用类型：包装类型接受对应基本类型
            if (param == Integer.class) return explicit == int.class || isTypeCompatible(int.class, explicit);
            if (param == Long.class) return explicit == long.class || isTypeCompatible(long.class, explicit);
            if (param == Float.class) return explicit == float.class || isTypeCompatible(float.class, explicit);
            if (param == Double.class) return explicit == double.class || isTypeCompatible(double.class, explicit);
            if (param == Boolean.class) return explicit == boolean.class;
            if (param == Byte.class) return explicit == byte.class || isTypeCompatible(byte.class, explicit);
            if (param == Short.class) return explicit == short.class || isTypeCompatible(short.class, explicit);
            if (param == Character.class) return explicit == char.class;
        }
        // 数组类型兼容
        if (param.isArray() && explicit.isArray()) {
            return isTypeCompatible(param.getComponentType(), explicit.getComponentType());
        }
        // 引用类型：子类/实现类赋值给父类/接口
        return param.isAssignableFrom(explicit);
    }

    public static int javaIsInstance(long luaState, int objIdx, int targetClassIdx) 
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        // C 层传参：objIdx = 第一个 Lua 参数，targetClassIdx = 第二个 Lua 参数
        // Lua 调用: isInstance(clazz, obj) → clazz 是 Class，obj 是待检测对象
        Object clazz = L.getJavaObject(objIdx);
        Object obj = L.getJavaObject(targetClassIdx);
        synchronized (L) {
            if (clazz instanceof Class) {
                L.pushBoolean(((Class<?>) clazz).isInstance(obj));
            } else {
                L.pushBoolean(false);
            }
            return 1;
        }
    }

    public static int javaHashCode(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            if (obj == null)
                L.pushInteger(0);
            else
                L.pushInteger(obj.hashCode());
            return 1;
        }
    }

    /**
     * 类型转换：将 Lua 值转换为指定 Java 类型
     * Lua 调用: luajava.cast(value, className)
     * @param luaState Lua 状态
     * @param className 目标类型的全限定名
     * @return 转换后的 Java 对象数量
     */
    public static int javaCast(long luaState, String className) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            try {
                Class<?> targetClass = Class.forName(className);
                Object value = compareTypes(L, targetClass, 1);
                L.pushJavaObject(value);
                return 1;
            } catch (Exception e) {
                throw new LuaException("cast failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 获取枚举值
     * Lua 调用: luajava.enumValueOf(className, valueName)
     * @param luaState Lua 状态
     * @param className 枚举类的全限定名
     * @param valueName 枚举值名称
     * @return 枚举对象
     */
    public static int javaEnumValueOf(long luaState, String className, String valueName)
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Class<Enum> enumClass = (Class<Enum>) Class.forName(className);
                if (!enumClass.isEnum()) {
                    throw new LuaException(className + " is not an enum class");
                }
                @SuppressWarnings("unchecked")
                Enum<?> value = Enum.valueOf(enumClass, valueName);
                L.pushJavaObject(value);
                return 1;
            } catch (LuaException e) {
                throw e;
            } catch (Exception e) {
                throw new LuaException("enumValueOf failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 检查类型兼容性：class2 的对象能否赋值给 class1 类型的变量
     * （即 class1.isAssignableFrom(class2)，判断 class2 是否为 class1 的子类/实现类）
     * Lua 调用: luajava.isAssignableFrom(classObj1, classObj2)
     * @param luaState Lua 状态
     * @param classIdx1 第一个 Class 对象的索引（目标类型）
     * @param classIdx2 第二个 Class 对象的索引（源类型）
     * @return boolean class2 是否可赋值给 class1
     */
    public static int javaIsAssignableFrom(long luaState, int classIdx1, int classIdx2)
            throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object classObj1 = L.getJavaObject(classIdx1);
        Object classObj2 = L.getJavaObject(classIdx2);
        synchronized (L) {
            if (classObj1 instanceof Class && classObj2 instanceof Class) {
                L.pushBoolean(((Class<?>) classObj1).isAssignableFrom((Class<?>) classObj2));
            } else {
                L.pushBoolean(false);
            }
            return 1;
        }
    }

    /**
     * 克隆对象（对象必须实现 Cloneable 接口）
     * Lua 调用: luajava.clone(obj)
     * @param luaState Lua 状态
     * @param idx 对象索引
     * @return 克隆后的对象
     */
    public static int javaClone(long luaState, int idx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(idx);
        synchronized (L) {
            try {
                if (obj == null) {
                    L.pushNil();
                    return 1;
                }
                if (!(obj instanceof Cloneable)) {
                    throw new LuaException("object does not implement Cloneable");
                }
                // 查找 clone() 方法：先找 public，找不到再递归父类找 protected
                Method cloneMethod = null;
                Class<?> clazz = obj.getClass();
                try {
                    cloneMethod = clazz.getMethod("clone");
                } catch (NoSuchMethodException ignored) {
                    // getMethod 找不到（可能不是 public），逐级向上找声明的方法
                    while (clazz != null && cloneMethod == null) {
                        try {
                            cloneMethod = clazz.getDeclaredMethod("clone");
                        } catch (NoSuchMethodException ignored2) {
                            clazz = clazz.getSuperclass();
                        }
                    }
                }
                if (cloneMethod == null) {
                    throw new LuaException("clone() method not found");
                }
                cloneMethod.setAccessible(true);
                Object cloned = cloneMethod.invoke(obj);
                L.pushJavaObject(cloned);
                return 1;
            } catch (LuaException e) {
                throw e;
            } catch (Exception e) {
                throw new LuaException("clone failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 创建 Java 对象的弱引用，避免内存泄漏
     * Lua 调用: luajava.weakRef(obj)
     * 返回的 WeakReference 对象可通过 :get() 获取原对象（被 GC 后返回 nil）
     * @param luaState Lua 状态指针
     * @param objIdx Java 对象在栈上的索引
     * @return 返回值个数（1 个 WeakReference 对象）
     */
    public static int javaWeakRef(long luaState, int objIdx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(objIdx);
        synchronized (L) {
            if (obj == null) {
                L.pushNil();
                return 1;
            }
            java.lang.ref.WeakReference<Object> weakRef = new java.lang.ref.WeakReference<>(obj);
            L.pushJavaObject(weakRef);
            return 1;
        }
    }

    /**
     * 创建 Java 对象的软引用，适合缓存场景
     * Lua 调用: luajava.softRef(obj)
     * 返回的 SoftReference 对象可通过 :get() 获取原对象（内存不足时可能被回收）
     * @param luaState Lua 状态指针
     * @param objIdx Java 对象在栈上的索引
     * @return 返回值个数（1 个 SoftReference 对象）
     */
    public static int javaSoftRef(long luaState, int objIdx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(objIdx);
        synchronized (L) {
            if (obj == null) {
                L.pushNil();
                return 1;
            }
            java.lang.ref.SoftReference<Object> softRef = new java.lang.ref.SoftReference<>(obj);
            L.pushJavaObject(softRef);
            return 1;
        }
    }

    // === 异步执行相关 ===
    private static volatile java.util.concurrent.ExecutorService asyncExecutor;

    /**
     * 获取异步执行的线程池（懒加载）
     */
    private static synchronized java.util.concurrent.ExecutorService getAsyncExecutor() {
        if (asyncExecutor == null) {
            asyncExecutor = java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "luajava-async");
                t.setDaemon(true);
                return t;
            });
        }
        return asyncExecutor;
    }

    /**
     * 在 Java 线程中异步执行任务
     * Lua 调用: luajava.async(task)
     * 注意：task 必须是纯 Java 的 Runnable/Callable 对象，不能是 Lua lambda 代理
     *       （Lua 函数不是线程安全的，跨线程调用会死锁）
     * @param luaState Lua 状态指针
     * @param objIdx 任务对象在栈上的索引
     * @return 返回值个数（1 个 Future 对象）
     */
    public static int javaAsync(long luaState, int objIdx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(objIdx);
        synchronized (L) {
            if (obj == null) {
                throw new LuaException("async: 任务对象不能为 nil");
            }
            if (obj instanceof Runnable || obj instanceof java.util.concurrent.Callable) {
                // 安全检查：禁止 Lua lambda 代理跨线程执行（会导致死锁）
                if (isLuaProxy(obj)) {
                    throw new LuaException(
                        "async: 不能将 Lua 函数/lambda 跨线程提交到 Java 线程执行！\n" +
                        "原因：Lua 不是线程安全的，在 Java 线程中调用 Lua 函数会导致死锁。\n" +
                        "建议：使用纯 Java 的 Runnable/Callable 对象，或使用 Lua 协程实现异步。");
                }
                java.util.concurrent.Future<?> future = getAsyncExecutor().submit(() -> {
                    if (obj instanceof java.util.concurrent.Callable) {
                        try {
                            return ((java.util.concurrent.Callable<?>) obj).call();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        ((Runnable) obj).run();
                        return null;
                    }
                });
                L.pushJavaObject(future);
                return 1;
            } else {
                throw new LuaException("async: 不支持的任务类型: " + obj.getClass().getName() +
                    "，需要 Runnable 或 Callable");
            }
        }
    }

    /**
     * 检测对象是否是 Lua 代理对象（由 Lua 函数/table 生成的动态代理）
     * 用于防止跨线程调用 Lua 函数导致死锁
     */
    private static boolean isLuaProxy(Object obj) {
        if (obj == null) return false;
        if (java.lang.reflect.Proxy.isProxyClass(obj.getClass())) {
            java.lang.reflect.InvocationHandler handler =
                java.lang.reflect.Proxy.getInvocationHandler(obj);
            // LambdaInvocationHandler 是我们的 lua lambda 代理
            if (handler instanceof LambdaInvocationHandler) return true;
            // LuaInvocationHandler 是 luajava 原生的代理
            if (handler instanceof LuaInvocationHandler) return true;
            if (handler instanceof LuaAbstractMethodInterceptor) return true;
        }
        return false;
    }

    /**
     * 创建多方法接口代理
     * Lua table 的 key 是方法名（string），value 是 Lua function
     * 未实现的抽象方法调用时会抛出异常（default 方法正常调用）
     * @param L Lua 状态
     * @param iface 要实现的接口类
     * @param idx table 在 Lua 栈上的索引
     * @return 动态代理对象
     */
    private static Object createMultiMethodProxy(LuaState L, Class<?> iface, int idx) throws LuaException {
        synchronized (L) {
            // 收集 table 中所有方法名 → LuaObject 映射
            java.util.Map<String, LuaObject> methodMap = new java.util.HashMap<>();
            L.pushNil();
            while (L.next(idx) != 0) {
                if (L.isString(-2) && L.isFunction(-1)) {
                    String methodName = L.toString(-2);
                    LuaObject func = L.getLuaObject(-1);
                    methodMap.put(methodName, func);
                }
                L.pop(1);
            }
            if (methodMap.isEmpty()) {
                throw new LuaException("接口实现 table 为空，需要至少一个方法: {methodName = function() ... end}");
            }
            return java.lang.reflect.Proxy.newProxyInstance(
                    iface.getClassLoader(),
                    new Class<?>[]{iface},
                    new MultiMethodInvocationHandler(L, methodMap, iface));
        }
    }

    /**
     * 多方法接口调用处理器
     * 将接口方法调用分发到 table 中对应的 Lua 函数
     */
    private static class MultiMethodInvocationHandler implements java.lang.reflect.InvocationHandler {
        private final LuaState L;
        private final java.util.Map<String, LuaObject> methodMap;
        private final Class<?> iface;

        MultiMethodInvocationHandler(LuaState L, java.util.Map<String, LuaObject> methodMap, Class<?> iface) {
            this.L = L;
            this.methodMap = methodMap;
            this.iface = iface;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            // Object 方法
            if (isObjectMethod(method)) {
                if ("equals".equals(name)) return proxy == args[0];
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                if ("toString".equals(name)) return "LuaProxy[" + iface.getSimpleName() + "]@" + Integer.toHexString(System.identityHashCode(proxy));
                return null;
            }
            // default 方法
            if (method.isDefault()) {
                try {
                    java.lang.reflect.Constructor<java.lang.invoke.MethodHandles.Lookup> ctor =
                            java.lang.invoke.MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
                    ctor.setAccessible(true);
                    java.lang.invoke.MethodHandles.Lookup lookup = ctor.newInstance(
                            method.getDeclaringClass(),
                            java.lang.invoke.MethodHandles.Lookup.PRIVATE);
                    return lookup.unreflectSpecial(method, method.getDeclaringClass())
                            .bindTo(proxy)
                            .invokeWithArguments(args);
                } catch (Exception e) {
                    throw new LuaException("调用 default 方法失败: " + name, e);
                }
            }
            // 查找 Lua 函数实现
            LuaObject func = methodMap.get(name);
            if (func == null) {
                throw new LuaException("接口 " + iface.getName() + " 的方法 " + name + " 未实现");
            }
            // 调用 Lua 函数
            return invokeLuaFunc(func, args, method.getReturnType());
        }

        /**
         * 调用 Lua 函数并处理返回值（与 LambdaInvocationHandler 相同逻辑）
         */
        private Object invokeLuaFunc(LuaObject func, Object[] args, Class<?> returnType) throws LuaException {
            synchronized (L) {
                int oldTop = L.getTop();
                try {
                    func.push();
                    int argCount = (args == null) ? 0 : args.length;
                    for (int i = 0; i < argCount; i++) {
                        L.pushObjectValue(args[i]);
                    }
                    int result = L.pcall(argCount, returnType == void.class ? 0 : 1, 0);
                    if (result != 0) {
                        String err = L.toString(-1);
                        L.pop(1);
                        throw new LuaException("Lua 函数调用失败 (" + func + "): " + err);
                    }
                    if (returnType == void.class || returnType == Void.class) {
                        return getDefaultReturnValue(returnType);
                    }
                    // 基本类型和包装类型
                    if (returnType == boolean.class || returnType == Boolean.class) {
                        boolean val = L.toBoolean(-1); L.pop(1); return val;
                    }
                    if (returnType == int.class || returnType == Integer.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? 0 : null; }
                        int val = (int) L.toInteger(-1); L.pop(1); return val;
                    }
                    if (returnType == long.class || returnType == Long.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? 0L : null; }
                        long val = L.toInteger(-1); L.pop(1); return val;
                    }
                    if (returnType == double.class || returnType == Double.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? 0.0 : null; }
                        double val = L.toNumber(-1); L.pop(1); return val;
                    }
                    if (returnType == float.class || returnType == Float.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? 0.0f : null; }
                        float val = (float) L.toNumber(-1); L.pop(1); return val;
                    }
                    if (returnType == byte.class || returnType == Byte.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? (byte)0 : null; }
                        byte val = (byte) L.toInteger(-1); L.pop(1); return val;
                    }
                    if (returnType == short.class || returnType == Short.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? (short)0 : null; }
                        short val = (short) L.toInteger(-1); L.pop(1); return val;
                    }
                    if (returnType == char.class || returnType == Character.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? (char)0 : null; }
                        if (L.isString(-1)) {
                            String s = L.toString(-1);
                            char val = (s != null && !s.isEmpty()) ? s.charAt(0) : (char)0;
                            L.pop(1); return val;
                        }
                        char val = (char) L.toInteger(-1); L.pop(1); return val;
                    }
                    if (returnType == String.class || returnType == CharSequence.class) {
                        String val = L.isNil(-1) ? null : L.toString(-1); L.pop(1); return val;
                    }
                    // 其他类型：通过 toJavaObject 转换
                    if (!L.isNil(-1)) {
                        Object ret = L.toJavaObject(-1); L.pop(1); return ret;
                    }
                    L.pop(1);
                    return getDefaultReturnValue(returnType);
                } finally {
                    int newTop = L.getTop();
                    if (newTop > oldTop) L.pop(newTop - oldTop);
                }
            }
        }

        /**
         * 获取返回类型的默认值（基本类型返回0/false，引用类型返回null）
         */
        private Object getDefaultReturnValue(Class<?> returnType) {
            if (returnType == null) return null;
            if (!returnType.isPrimitive()) return null;
            if (returnType == boolean.class) return false;
            if (returnType == char.class) return (char) 0;
            return 0; // byte/short/int/long/float/double 都转为 0（自动装箱）
        }
    }

    /**
     * 创建函数式接口代理（SAM - Single Abstract Method）
     * Lua 调用: luajava.lambda(className, luaFunction)
     * 例如: luajava.lambda("java.util.function.Predicate", function(v) return v > 0 end)
     * @param luaState Lua 状态指针
     * @param classNameIdx 接口类名在栈上的索引
     * @param funcIdx Lua 函数在栈上的索引
     * @return 返回值个数（1 个 Java 对象）
     */
    public static int javaLambda(long luaState, int classNameIdx, int funcIdx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            String className = L.toString(classNameIdx);
            try {
                Class<?> clazz = Class.forName(className);
                if (!clazz.isInterface()) {
                    throw new LuaException("lambda 只支持接口类型: " + className);
                }
                // 找到接口的单一抽象方法（SAM）
                Method samMethod = findSamMethod(clazz);
                if (samMethod == null) {
                    throw new LuaException("接口不是函数式接口（没有单一抽象方法）: " + className);
                }
                // 获取 Lua 函数对象
                LuaObject luaObj = L.getLuaObject(funcIdx);
                if (!luaObj.isFunction()) {
                    throw new LuaException("lambda 第二个参数必须是函数");
                }
                // 创建动态代理
                Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                        clazz.getClassLoader(),
                        new Class<?>[]{clazz},
                        new LambdaInvocationHandler(L, luaObj, samMethod));
                L.pushJavaObject(proxy);
                return 1;
            } catch (ClassNotFoundException e) {
                throw new LuaException("找不到类: " + className, e);
            }
        }
    }

    /**
     * 查找接口的单一抽象方法（SAM / 函数式接口）
     * 正确处理：继承的方法、default 方法、static 方法、Object 方法、桥接方法
     * @param iface 接口类
     * @return 唯一的抽象方法，非函数式接口返回 null
     */
    private static Method findSamMethod(Class<?> iface) {
        if (!iface.isInterface()) return null;
        // 收集所有抽象方法签名（去重，考虑桥接方法和协变返回）
        java.util.Map<String, Method> abstractMethods = new java.util.LinkedHashMap<>();
        java.util.Set<String> defaultSignatures = new java.util.HashSet<>();
        // 递归收集所有接口层级的方法
        collectSamMethods(iface, abstractMethods, defaultSignatures);
        // 移除 Object 方法
        abstractMethods.entrySet().removeIf(e -> isObjectMethod(e.getValue()));
        // 移除有 default 实现的方法（子类覆盖父接口 default 方法的情况）
        abstractMethods.keySet().removeAll(defaultSignatures);
        if (abstractMethods.size() != 1) return null;
        return abstractMethods.values().iterator().next();
    }

    /**
     * 递归收集接口的所有方法
     */
    private static void collectSamMethods(Class<?> iface,
                                           java.util.Map<String, Method> abstractMethods,
                                           java.util.Set<String> defaultSignatures) {
        for (Method m : iface.getDeclaredMethods()) {
            // 跳过 static 方法
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
            // 跳过桥接方法（编译器生成的类型桥接）
            if (m.isBridge()) continue;
            String sig = getMethodSignature(m);
            if (m.isDefault()) {
                defaultSignatures.add(sig);
            } else {
                // 抽象方法：如果已存在同签名方法，不替换（优先保留子类/子接口的声明）
                if (!abstractMethods.containsKey(sig)) {
                    abstractMethods.put(sig, m);
                }
            }
        }
        // 递归父接口
        for (Class<?> parent : iface.getInterfaces()) {
            collectSamMethods(parent, abstractMethods, defaultSignatures);
        }
    }

    /**
     * 获取方法签名字符串（用于去重）
     */
    private static String getMethodSignature(Method m) {
        StringBuilder sb = new StringBuilder(m.getName());
        sb.append('(');
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(params[i].getName());
        }
        sb.append(')');
        return sb.toString();
    }

    /**
     * 判断方法是否是 Object 类的 public 方法（equals/hashCode/toString 等）
     */
    private static boolean isObjectMethod(Method m) {
        try {
            Method objMethod = Object.class.getMethod(m.getName(), m.getParameterTypes());
            return objMethod != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Lambda 调用处理器：将 Java 接口方法调用转发给 Lua 函数
     */
    private static class LambdaInvocationHandler implements java.lang.reflect.InvocationHandler {
        private final LuaState L;
        private final LuaObject luaFunc;
        private final Method samMethod;

        LambdaInvocationHandler(LuaState L, LuaObject luaFunc, Method samMethod) {
            this.L = L;
            this.luaFunc = luaFunc;
            this.samMethod = samMethod;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Object 方法：equals/hashCode/toString
            if (isObjectMethod(method)) {
                return handleObjectMethod(proxy, method, args);
            }
            // SAM 方法：转发给 Lua 函数
            if (method.getName().equals(samMethod.getName())
                    && java.util.Arrays.equals(method.getParameterTypes(), samMethod.getParameterTypes())) {
                return invokeLuaFunction(args, method.getReturnType());
            }
            // default 方法或其他方法：尝试通过反射直接调用（兼容 Java 8）
            if (method.isDefault()) {
                // Java 8 下用 MethodHandles 调用 default 方法
                try {
                    java.lang.reflect.Constructor<java.lang.invoke.MethodHandles.Lookup> ctor =
                            java.lang.invoke.MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
                    ctor.setAccessible(true);
                    java.lang.invoke.MethodHandles.Lookup lookup = ctor.newInstance(
                            method.getDeclaringClass(),
                            java.lang.invoke.MethodHandles.Lookup.PRIVATE);
                    return lookup.unreflectSpecial(method, method.getDeclaringClass())
                            .bindTo(proxy)
                            .invokeWithArguments(args);
                } catch (Exception e) {
                    throw new UnsupportedOperationException("无法调用 default 方法: " + method, e);
                }
            }
            throw new UnsupportedOperationException("方法不支持: " + method);
        }

        /**
         * 处理 Object 类的方法
         */
        private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("equals".equals(name)) {
                return proxy == args[0];
            } else if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            } else if ("toString".equals(name)) {
                return "LuaLambda@" + Integer.toHexString(System.identityHashCode(proxy));
            }
            return null;
        }

        /**
         * 调用 Lua 函数并处理返回值
         */
        private Object invokeLuaFunction(Object[] args, Class<?> returnType) throws LuaException {
            synchronized (L) {
                int oldTop = L.getTop();
                try {
                    // 压入 Lua 函数
                    luaFunc.push();
                    // 压入参数
                    int argCount = (args == null) ? 0 : args.length;
                    for (int i = 0; i < argCount; i++) {
                        L.pushObjectValue(args[i]);
                    }
                    // 调用 Lua 函数
                    int result = L.pcall(argCount, returnType == void.class ? 0 : 1, 0);
                    if (result != 0) {
                        String err = L.toString(-1);
                        L.pop(1);
                        throw new LuaException("Lua 函数调用失败: " + err);
                    }
                    // 处理返回值
                    if (returnType == void.class || returnType == Void.class) {
                        return null;
                    }
                    if (returnType == boolean.class || returnType == Boolean.class) {
                        boolean val = L.toBoolean(-1); L.pop(1); return val;
                    }
                    if (returnType == int.class || returnType == Integer.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? 0 : null; }
                        int val = (int) L.toInteger(-1); L.pop(1); return val;
                    }
                    if (returnType == long.class || returnType == Long.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? 0L : null; }
                        long val = L.toInteger(-1); L.pop(1); return val;
                    }
                    if (returnType == double.class || returnType == Double.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? 0.0 : null; }
                        double val = L.toNumber(-1); L.pop(1); return val;
                    }
                    if (returnType == float.class || returnType == Float.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? 0.0f : null; }
                        float val = (float) L.toNumber(-1); L.pop(1); return val;
                    }
                    if (returnType == byte.class || returnType == Byte.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? (byte)0 : null; }
                        byte val = (byte) L.toInteger(-1); L.pop(1); return val;
                    }
                    if (returnType == short.class || returnType == Short.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? (short)0 : null; }
                        short val = (short) L.toInteger(-1); L.pop(1); return val;
                    }
                    if (returnType == char.class || returnType == Character.class) {
                        if (L.isNil(-1)) { L.pop(1); return returnType.isPrimitive() ? (char)0 : null; }
                        if (L.isString(-1)) {
                            String s = L.toString(-1);
                            char val = (s != null && !s.isEmpty()) ? s.charAt(0) : (char)0;
                            L.pop(1); return val;
                        }
                        char val = (char) L.toInteger(-1); L.pop(1); return val;
                    }
                    if (returnType == String.class || returnType == CharSequence.class) {
                        String val = L.isNil(-1) ? null : L.toString(-1); L.pop(1); return val;
                    }
                    // 通用对象返回
                    if (!L.isNil(-1)) {
                        Object ret = L.toJavaObject(-1); L.pop(1); return ret;
                    }
                    L.pop(1);
                    if (returnType.isPrimitive()) {
                        if (returnType == boolean.class) return false;
                        if (returnType == char.class) return (char) 0;
                        return 0;
                    }
                    return null;
                } finally {
                    // 恢复栈平衡
                    int newTop = L.getTop();
                    if (newTop > oldTop) {
                        L.pop(newTop - oldTop);
                    }
                }
            }
        }
    }

    // ========================================================================
    // 新增高级 API
    // ========================================================================

    /**
     * 创建方法引用（等价于 Java 的 obj::method）
     * Lua 调用: luajava.methodRef(obj, methodName)
     * 返回一个可调用的对象，调用时转发到指定方法
     * @param luaState Lua 状态指针
     * @param objIdx 对象在 javaObjectMap 中的索引
     * @param methodName 方法名
     * @return 返回值个数（1 个 MethodRef 代理对象）
     */
    public static int javaMethodRef(long luaState, int objIdx, String methodName) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            Object target = L.getJavaObject(objIdx);
            if (target == null) {
                throw new LuaException("methodRef: 对象不能为 nil");
            }
            if (methodName == null || methodName.isEmpty()) {
                throw new LuaException("methodRef: 方法名不能为空");
            }
            // 创建一个通用的 Function 代理（Runnable/Callable/Consumer/Function 等都能适配）
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    target.getClass().getClassLoader(),
                    new Class<?>[]{Runnable.class, java.util.concurrent.Callable.class,
                            java.util.function.Consumer.class, java.util.function.Function.class,
                            java.util.function.Supplier.class, java.util.function.Predicate.class,
                            java.util.function.BiConsumer.class, java.util.function.BiFunction.class},
                    new MethodRefHandler(L, target, methodName));
            L.pushJavaObject(proxy);
            return 1;
        }
    }

    /**
     * 方法引用处理器：将任意接口方法调用转发到指定的目标方法
     */
    private static class MethodRefHandler implements java.lang.reflect.InvocationHandler {
        private final LuaState L;
        private final Object target;
        private final String methodName;

        MethodRefHandler(LuaState L, Object target, String methodName) {
            this.L = L;
            this.target = target;
            this.methodName = methodName;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Object 方法
            if (isObjectMethod(method)) {
                if ("equals".equals(method.getName())) return proxy == args[0];
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("toString".equals(method.getName()))
                    return "MethodRef[" + target.getClass().getSimpleName() + "::" + methodName + "]";
                return null;
            }
            // 查找目标方法：按名称匹配，忽略参数类型（用 compareTypes 转换）
            Method[] methods = target.getClass().getMethods();
            Method bestMatch = null;
            int argCount = (args == null) ? 0 : args.length;
            for (Method m : methods) {
                if (m.getName().equals(methodName)) {
                    Class<?>[] params = m.getParameterTypes();
                    // 匹配参数个数：考虑 varargs
                    boolean isVarArgs = m.isVarArgs();
                    if (params.length == argCount || (isVarArgs && argCount >= params.length - 1)) {
                        bestMatch = m;
                        break;
                    }
                    // 参数个数完全匹配优先
                    if (params.length == argCount) {
                        bestMatch = m;
                        break;
                    }
                }
            }
            if (bestMatch == null) {
                throw new LuaException("methodRef: 找不到方法 " + methodName + "，参数个数 " + argCount);
            }
            bestMatch.setAccessible(true);
            try {
                Object result = bestMatch.invoke(target, args);
                return result;
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }

    /**
     * 诊断内存：列出当前 Lua 持有的所有 Java 对象统计
     * Lua 调用: luajava.dumpObjects()
     * 返回一个 table: {class -> count}
     * @param luaState Lua 状态指针
     * @return 返回值个数（1 个 table）
     */
    public static int javaDumpObjects(long luaState) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            java.util.Map<String, Integer> stats = new java.util.TreeMap<>();
            java.util.Map<Integer, Object> objMap = L.getJavaObjectMap();
            for (Object obj : objMap.values()) {
                if (obj != null) {
                    String name = obj.getClass().getName();
                    stats.put(name, stats.getOrDefault(name, 0) + 1);
                }
            }
            // 创建 Lua table 返回
            L.newTable();
            for (java.util.Map.Entry<String, Integer> e : stats.entrySet()) {
                L.pushString(e.getKey());
                L.pushInteger(e.getValue());
                L.setTable(-3);
            }
            // 总数
            L.pushString("__total__");
            L.pushInteger(objMap.size());
            L.setTable(-3);
            return 1;
        }
    }

    /**
     * 安全的主线程回调（用于 Android 等 UI 线程环境）
     * Lua 调用: luajava.post(runnable)
     * 将 Runnable 投递到主线程/UI 线程执行（通过 Android Handler）
     * 如果无法获取主线程，则在当前线程同步执行
     * @param luaState Lua 状态指针
     * @param objIdx Runnable 在栈上的索引
     * @return 返回值个数（0）
     */
    public static int javaPost(long luaState, int objIdx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object task = L.getJavaObject(objIdx);
        synchronized (L) {
            if (task == null) {
                throw new LuaException("post: 任务对象不能为 nil");
            }
            if (!(task instanceof Runnable)) {
                throw new LuaException("post: 任务必须是 Runnable 类型");
            }
            final Runnable runnable = (Runnable) task;
            // 尝试通过反射获取 Android 主线程 Looper 并 post
            try {
                Class<?> looperClass = Class.forName("android.os.Looper");
                Object mainLooper = looperClass.getMethod("getMainLooper").invoke(null);
                if (mainLooper != null) {
                    Class<?> handlerClass = Class.forName("android.os.Handler");
                    Object handler = handlerClass.getConstructor(looperClass).newInstance(mainLooper);
                    handlerClass.getMethod("post", Runnable.class).invoke(handler, (Runnable) () -> {
                        synchronized (L) {
                            runnable.run();
                        }
                    });
                    return 0;
                }
            } catch (Throwable t) {
                // Android 类不可用，降级为同步执行
            }
            // 非 Android 环境：直接同步执行
            runnable.run();
            return 0;
        }
    }

    /**
     * 获取所有声明的方法名（含私有方法）
     * Lua 调用: luajava.getDeclaredMethods(obj)
     * 返回一个 table，包含所有方法名字符串（去重）
     * @param luaState Lua 状态指针
     * @param objIdx 对象在栈上的索引
     * @return 返回值个数（1 个 table）
     */
    public static int javaGetDeclaredMethods(long luaState, int objIdx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        Object obj = L.getJavaObject(objIdx);
        synchronized (L) {
            Class<?> clazz = (obj instanceof Class) ? (Class<?>) obj : obj.getClass();
            Method[] methods = clazz.getDeclaredMethods();
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            for (Method m : methods) {
                names.add(m.getName());
            }
            L.newTable();
            int i = 1;
            for (String name : names) {
                L.pushString(name);
                L.rawSetI(-2, i);
                i++;
            }
            return 1;
        }
    }

    /**
     * 创建 Optional 包装
     * Lua 调用: luajava.optional(value)
     * nil → Optional.empty()，非 nil → Optional.ofNullable(value)
     * @param luaState Lua 状态指针
     * @param stackIdx 值在 Lua 栈上的索引（可以是任意类型）
     * @return 返回值个数（1 个 Optional 对象）
     */
    public static int javaOptional(long luaState, int stackIdx) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            if (L.isNil(stackIdx)) {
                L.pushJavaObject(java.util.Optional.empty());
            } else {
                Object val = L.toJavaObject(stackIdx);
                L.pushJavaObject(java.util.Optional.ofNullable(val));
            }
            return 1;
        }
    }

    // ==================== try-with-resources 支持 ====================

    /**
     * 内置 try-with-resources 语法糖
     * Lua 调用: luajava.using(resource, fn) 或 luajava.using({r1,r2,...}, fn)
     * 自动调用 AutoCloseable.close()，异常时也保证关闭
     * @param luaState Lua 状态指针
     * @return 返回值个数（返回 fn 的返回值）
     */
    public static int javaUsing(long luaState) throws LuaException {
        LuaState L = LuaStateFactory.getExistingState(luaState);
        synchronized (L) {
            int top = L.getTop();
            if (top < 2) {
                throw new LuaException("using 需要 2 个参数: using(resource(s), function)");
            }
            if (!L.isFunction(top)) {
                throw new LuaException("using 最后一个参数必须是函数");
            }
            java.util.List<Object> resources = new java.util.ArrayList<>();
            if (L.isTable(1)) {
                // 多个资源：按数组顺序遍历 1..n（保证参数顺序正确）
                int len = L.objLen(1);
                for (int i = 1; i <= len; i++) {
                    L.rawGetI(1, i);
                    Object res = L.toJavaObject(-1);
                    resources.add(res);
                    L.pop(1);
                }
            } else {
                // 单个资源或多个资源直接作为参数
                for (int i = 1; i < top; i++) {
                    Object res = L.toJavaObject(i);
                    resources.add(res);
                }
            }
            L.pushValue(top);
            for (int i = 0; i < resources.size(); i++) {
                L.pushJavaObject(resources.get(i));
            }
            Object firstError = null;
            int resultCount = 0;
            int baseTop = L.getTop() - resources.size() - 1;
            try {
                int result = L.pcall(resources.size(), LuaState.LUA_MULTRET, 0);
                if (result != 0) {
                    firstError = L.toString(-1);
                    L.pop(1);
                } else {
                    resultCount = L.getTop() - baseTop;
                }
            } catch (Throwable t) {
                firstError = t;
            } finally {
                for (int i = resources.size() - 1; i >= 0; i--) {
                    Object res = resources.get(i);
                    if (res instanceof AutoCloseable) {
                        try {
                            ((AutoCloseable) res).close();
                        } catch (Throwable t) {
                            if (firstError == null) {
                                firstError = t;
                            }
                        }
                    }
                }
            }
            if (firstError != null) {
                if (firstError instanceof LuaException) {
                    throw (LuaException) firstError;
                }
                throw new LuaException("using 执行异常: " + firstError,
                        firstError instanceof Throwable ? (Throwable) firstError : null);
            }
            return resultCount;
        }
    }
}

