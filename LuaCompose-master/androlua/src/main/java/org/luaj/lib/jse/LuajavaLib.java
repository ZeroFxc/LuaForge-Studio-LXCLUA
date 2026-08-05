package org.luaj.lib.jse;

import android.util.Log;

import com.androlua.LuaActivity;
import com.androlua.LuaEnhancer;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.luaj.LuaError;
import org.luaj.LuaTable;
import org.luaj.LuaUserdata;
import org.luaj.LuaValue;
import org.luaj.Varargs;
import org.luaj.lib.OneArgFunction;
import org.luaj.lib.VarArgFunction;

public class LuajavaLib extends VarArgFunction {
    static final String[] d = {"bindClass", "newInstance", "new", "createProxy", "loadLib", "astable", "instanceof"};
    public ArrayList<ClassLoader> e = new ArrayList<>();
    public HashMap<String, LuaValue> f = new HashMap<>();

    private static final class ProxyInvocationHandler implements InvocationHandler {
        private final LuaValue a;

        private ProxyInvocationHandler(LuaValue luaValue) {
            this.a = luaValue;
        }

        @Override
        public Object invoke(Object obj, Method method, Object[] objArr) {
            LuaValue[] luaValueArr;
            Object objCoerce;
            int i;
            String name = method.getName();
            LuaValue luaValue = this.a.isfunction() ? this.a : this.a.get(name);
            if (luaValue.isnil()) {
                objCoerce = CoerceLuaToJava.coerce(LuaValue.NIL, method.getReturnType());
            } else {
                boolean z = (method.getModifiers() & 128) != 0;
                int length = objArr != null ? objArr.length : 0;
                try {
                    if (z) {
                        int i2 = length - 1;
                        Object obj2 = objArr[i2];
                        int length2 = Array.getLength(obj2);
                        LuaValue[] luaValueArr2 = new LuaValue[i2 + length2];
                        int i3 = 0;
                        while (true) {
                            i = 0;
                            if (i3 >= i2) {
                                break;
                            }
                            luaValueArr2[i3] = CoerceJavaToLua.coerce(objArr[i3]);
                            i3++;
                        }
                        while (true) {
                            luaValueArr = luaValueArr2;
                            if (i < length2) {
                                luaValueArr2[i + i2] = CoerceJavaToLua.coerce(Array.get(obj2, i));
                                i++;
                            } else {
                                break;
                            }
                        }
                    } else {
                        LuaValue[] luaValueArr3 = new LuaValue[length];
                        int i4 = 0;
                        while (true) {
                            luaValueArr = luaValueArr3;
                            if (i4 < length) {
                                luaValueArr3[i4] = CoerceJavaToLua.coerce(objArr[i4]);
                                i4++;
                            } else {
                                break;
                            }
                        }
                    }
                    objCoerce = CoerceLuaToJava.coerce(luaValue.invoke(luaValueArr).arg1(), method.getReturnType());
                } catch (Exception e) {
                    LuaActivity.logError(name, e);
                    objCoerce = CoerceLuaToJava.coerce(LuaValue.NIL, method.getReturnType());
                }
            }
            return objCoerce;
        }
    }

    public static final class override extends OneArgFunction {
        private final Class d;

        public override(JavaClass javaClass) {
            this.d = (Class) javaClass.<Class>touserdata(Class.class);
        }

        @Override
        public LuaValue call(LuaValue luaValue) {
            try {
                return LuajavaLib.override(this.d, luaValue);
            } catch (Exception e) {
                throw new LuaError(e);
            }
        }
    }

    public static LuaValue asTable(Object obj) {
        LuaValue luaValueCoerce;
        int i = 0;
        LuaTable luaTable = new LuaTable();
        if (obj.getClass().isArray()) {
            int length = Array.getLength(obj);
            while (i <= length - 1) {
                int i2 = i + 1;
                luaTable.set(i2, asTable(Array.get(obj, i)));
                i = i2;
            }
        } else if (obj instanceof Collection) {
            Iterator it = ((Collection) obj).iterator();
            int i3 = 1;
            while (it.hasNext()) {
                luaTable.set(i3, asTable(it.next()));
                i3++;
            }
        } else if (obj instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                luaTable.set(CoerceJavaToLua.coerce(entry.getKey()), asTable(entry.getValue()));
            }
        } else if (obj instanceof JSONObject) {
            JSONObject jSONObject = (JSONObject) obj;
            Iterator<String> itKeys = jSONObject.keys();
            while (itKeys.hasNext()) {
                String next = itKeys.next();
                try {
                    luaTable.set(next, asTable(jSONObject.get(next)));
                } catch (JSONException e) {
                }
            }
        } else {
            if (!(obj instanceof JSONArray)) {
                luaValueCoerce = CoerceJavaToLua.coerce(obj);
                return luaValueCoerce;
            }
            JSONArray jSONArray = (JSONArray) obj;
            int length2 = jSONArray.length();
            int i4 = 0;
            while (true) {
                int i5 = i4;
                if (i5 >= length2) {
                    break;
                }
                int i6 = i5 + 1;
                try {
                    luaTable.set(i6, asTable(jSONArray.get(i5)));
                } catch (JSONException e2) {
                }
                i4 = i6;
            }
        }
        luaValueCoerce = luaTable;
        return luaValueCoerce;
    }

    public static LuaValue asTable(Object obj, boolean isDeep) {
        if (isDeep) {
            return asTable(obj);
        }
        LuaTable luaTable = new LuaTable();
        if (obj.getClass().isArray()) {
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                luaTable.set(i + 1, CoerceJavaToLua.coerce(Array.get(obj, i)));
            }
        } else if (obj instanceof Collection) {
            Iterator<?> it = ((Collection<?>) obj).iterator();
            int index = 1;
            while (it.hasNext()) {
                luaTable.set(index, CoerceJavaToLua.coerce(it.next()));
                index++;
            }
        } else if (obj instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                luaTable.set(CoerceJavaToLua.coerce(entry.getKey()), CoerceJavaToLua.coerce(entry.getValue()));
            }
        } else if (obj instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) obj;
            Iterator<String> it = jsonObject.keys();
            while (it.hasNext()) {
                String key = it.next();
                try {
                    luaTable.set(key, CoerceJavaToLua.coerce(jsonObject.get(key)));
                } catch (JSONException e) {
                }
            }
        } else if (obj instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) obj;
            int length = jsonArray.length();
            for (int i = 0; i < length; i++) {
                try {
                    luaTable.set(i, CoerceJavaToLua.coerce(jsonArray.get(i)));
                } catch (JSONException e) {
                }
            }
        } else {
            return CoerceJavaToLua.coerce(obj);
        }
        return luaTable;
    }

    public static LuaUserdata createProxy(Class cls, LuaValue luaValue) {
        return LuaValue.userdataOf(Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, new ProxyInvocationHandler(luaValue)));
    }

    public static LuaValue override(Class cls, LuaValue luaValue) {
        return JavaClass.a(new LuaEnhancer(cls).create(luaValue));
    }


    // import 方法
    public LuaValue bindClassForName(String str) throws ClassNotFoundException {
        JavaClass javaClassA;
        try {
            javaClassA = JavaClass.f(str);
        } catch (Exception e) {
            Iterator<ClassLoader> it = this.e.iterator();
            while (it.hasNext()) {
                try {
                    javaClassA = JavaClass.a(str, it.next());
                } catch (Exception e2) {
                }
            }
            throw new ClassNotFoundException(str);
        }
        return javaClassA;
    }

    protected Class<?> f(String str) throws ClassNotFoundException {
        return Class.forName(str);
    }

    @Override
    public Varargs invoke(Varargs varargs) {
        Varargs luaTable;
        int i = 0;
        try {
            switch (this.b) {
                case 0:
                    final LuaValue luaValueArg = varargs.arg(2);
                    luaValueArg.checkglobals().s = this;
                    LuaTable env = new LuaTable();
                    a(env, getClass(), d, 1);
                    luaTable = env;
                    luaValueArg.set("luajava", env);
                    luaValueArg.get("package").get("loaded").set("luajava", env);
                    luaValueArg.set("boolean", JavaClass.a(Boolean.TYPE));
                    luaValueArg.set("byte", JavaClass.a(Byte.TYPE));
                    luaValueArg.set("char", JavaClass.a(Character.TYPE));
                    luaValueArg.set("short", JavaClass.a(Short.TYPE));
                    luaValueArg.set("int", JavaClass.a(Integer.TYPE));
                    luaValueArg.set("long", JavaClass.a(Long.TYPE));
                    luaValueArg.set("float", JavaClass.a(Float.TYPE));
                    luaValueArg.set("double", JavaClass.a(Double.TYPE));
                    luaValueArg.set("import", new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs varargs2) {
                            try {
                                // TODO: 在这里修改 import 逻辑
                                String strCheckjstring = varargs2.checkjstring(1123);
                                String strReplaceFirst = strCheckjstring.replaceFirst(".*?[$\\.]([^$\\.]*)$", "$1");
                                LuaValue luaValueA = JavaClass.a(LuajavaLib.this.f(strCheckjstring));
                                luaValueArg.set(strReplaceFirst, luaValueA);
                                return luaValueA;
                            } catch (ClassNotFoundException e) {
                                throw new LuaError(e);
                            }
                        }
                    });
                    break;
                case 1:
                    luaTable = JavaClass.a(f(varargs.checkjstring(1)));
                    break;
                case 2:
                case 3:
                    LuaValue luaValueCheckvalue = varargs.checkvalue(1);
                    luaTable = JavaClass.a(this.b == 2 ? f(luaValueCheckvalue.tojstring()) : (Class) luaValueCheckvalue.checkuserdata(Class.class)).getConstructor().invoke(varargs.subargs(2));
                    break;
                case 4:
                    int iNarg = varargs.narg() - 1;
                    if (iNarg <= 0) {
                        throw new LuaError("no interfaces");
                    }
                    LuaTable luaTableChecktable = varargs.checktable(iNarg + 1);
                    Class[] clsArr = new Class[iNarg];
                    while (i < iNarg) {
                        int i2 = i + 1;
                        clsArr[i] = f(varargs.checkjstring(i2));
                        i = i2;
                    }
                    luaTable = LuaValue.userdataOf(Proxy.newProxyInstance(getClass().getClassLoader(), clsArr, new ProxyInvocationHandler(luaTableChecktable)));
                    break;
                case 5:
                    String strCheckjstring = varargs.checkjstring(1);
                    String strCheckjstring2 = varargs.checkjstring(2);
                    Class<?> clsF = f(strCheckjstring);
                    Object objInvoke = clsF.getMethod(strCheckjstring2, new Class[0]).invoke(clsF, new Object[0]);
                    luaTable = !(objInvoke instanceof LuaValue) ? LuaValue.NIL : (LuaValue) objInvoke;
                    break;
                case 6:
                    luaTable = !varargs.istable(1) ? asTable(varargs.checkuserdata(1), varargs.optboolean(2, false)) : varargs.checktable(1);
                    break;
                case 7:
                    luaTable = LuaValue.valueOf(((Class) varargs.arg(2).<Class>touserdata(Class.class)).isInstance(varargs.checkuserdata(1)));
                    break;
                default:
                    throw new LuaError("not yet supported: " + this);
            }
            return luaTable;
        } catch (InvocationTargetException e) {
            throw new LuaError(e.getTargetException());
        } catch (LuaError e2) {
            throw e2;
        } catch (Exception e3) {
            throw new LuaError(e3);
        }
    }
}
