/*
 * $Id: LuaInvocationHandler.java,v 1.4 2006/12/22 14:06:40 thiago Exp $
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
 * included in all copies or substantial portions of the Software.
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

import android.util.Log;

import com.nirithy.lxclua.LuaContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Class that implements the InvocationHandler interface.
 * This class is used in the LuaJava's proxy system.
 * When a proxy object is accessed, the method invoked is
 * called from Lua
 *
 * @author Rizzato
 * @author Thiago Ponte
 */
public class LuaInvocationHandler implements InvocationHandler {
    private final LuaContext mContext;
    private final LuaObject obj;
    // 移除静态 cache 防止内存泄漏：
    // InvocationHandler 本身已经持有 obj 的强引用，只要代理对象存在 obj 就不会被 GC

    public LuaInvocationHandler(LuaObject obj) {
        this.obj = obj;
        mContext = obj.getLuaState().getContext();
    }

    /**
     * Function called when a proxy object function is invoked.
     */
    public Object invoke(Object proxy, Method method, Object[] args) throws LuaException {
        synchronized (obj.L) {
            if (obj.L.isClosed()) {
                String methodName = method.getName();
                Class<?> retType = method.getReturnType();
                if (retType.equals(boolean.class) || retType.equals(Boolean.class))
                    return false;
                else if (retType.isPrimitive() || Number.class.isAssignableFrom(retType))
                    return 0;
                else
                    return null;
            }

            String methodName = method.getName();
            LuaObject func;
            if (obj.isFunction()) {
                func = obj;
            } else {
                func = obj.getField(methodName);
            }
            Class<?> retType = method.getReturnType();

            if (func.isNil()) {
                // 方法未在 Lua table 中实现，返回默认值并打印警告日志
                // 保留默认返回值是为了兼容性：用户只需重写关心的方法，其余自动返回默认值
                Log.w("LuaInvocationHandler", String.format(
                        "[%s] 未实现方法 '%s'，返回默认值",
                        method.getDeclaringClass().getSimpleName(), methodName));
                if (retType.equals(boolean.class) || retType.equals(Boolean.class))
                    return false;
                else if (retType.isPrimitive() || Number.class.isAssignableFrom(retType))
                    return 0;
                else
                    return null;
            }

            Object ret = null;
            try {
                if (retType.equals(Void.class) || retType.equals(void.class)) {
                    func.call(args);
                    ret = null;
                } else {
                    ret = func.call(args);
                    // Lua 函数返回的数字可能是 Double、Integer、Long 等，
                    // 需要根据方法返回类型统一转换
                    if (ret != null && ret instanceof Number) {
                        Number numRet = (Number) ret;
                        if (numRet instanceof Long) {
                            ret = LuaState.convertLuaNumber((Long) numRet, retType);
                        } else {
                            ret = LuaState.convertLuaNumber(numRet.doubleValue(), retType);
                        }
                    }
                }
            } catch (Exception e) {
                if (mContext != null) {
                    mContext.sendError(methodName, e);
                } else {
                    Log.e("LuaInvocationHandler", "Error in " + methodName, e);
                }
            }
            if (ret == null)
                if (retType.equals(boolean.class) || retType.equals(Boolean.class))
                    return false;
                else if (retType.isPrimitive() || Number.class.isAssignableFrom(retType))
                    return 0;
            return ret;
        }
    }
}
