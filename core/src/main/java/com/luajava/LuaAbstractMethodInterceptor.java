package com.luajava;

import android.util.Log;

import com.android.cglib.proxy.MethodInterceptor;
import com.android.cglib.proxy.MethodProxy;
import com.nirithy.lxclua.LuaContext;

import java.lang.reflect.Method;

/**
 * Created by nirenr on 2018/12/21.
 */

public class LuaAbstractMethodInterceptor implements MethodInterceptor {
    private final LuaContext mContext;
    private final LuaObject obj;

    public LuaAbstractMethodInterceptor(LuaObject obj) {
        this.obj = obj;
        mContext = obj.getLuaState().getContext();
    }

    @Override
    public Object intercept(Object object, Object[] args, MethodProxy methodProxy) throws Exception {
        synchronized (obj.L) {
            Method method = methodProxy.getOriginalMethod();
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
                Log.w("LuaAbstractMethodInterceptor", String.format(
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
                // Checks if returned type is void. if it is returns null.
                if (retType.equals(Void.class) || retType.equals(void.class)) {
                    func.call(args);
                    ret = null;
                } else {
                    ret = func.call(args);
                    if (ret != null && ret instanceof Double) {
                        ret = LuaState.convertLuaNumber((Double) ret, retType);
                    }
                }
            } catch (LuaException e) {
                if (mContext != null) {
                    mContext.sendError(methodName, e);
                } else {
                    Log.e("LuaAbstractMethodInterceptor", "Error in " + methodName, e);
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
