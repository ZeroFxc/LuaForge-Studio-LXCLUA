/******************************************************************************
* $Id$
* Copyright (C) 2026-2099 DifierLine.
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
******************************************************************************/

/***************************************************************************
*
* $ED
*    This module is the implementation of luajava's dinamic library.
*    In this module lua's functions are exported to be used in java by jni,
*    and also the functions that will be used and exported to lua so that
*    Java Objects' functions can be called.
*
*****************************************************************************/

#include "luajava.h"
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/core/lua.h"
#include "src/core/lualib.h"
#include "src/core/lauxlib.h"

/* Call metamethod name */
#define LUACALLMETAMETHODTAG "__call"
#define LUAGCMETAMETHODTAG "__gc"
#define LUATOSTRINGMETAMETHODTAG "__tostring"

/* Forward declaration for luaparser module */
extern int luaopen_luaparser(lua_State *L);

#include <android/log.h>
#include <memory.h>
#include <zlib.h>

#define LOG_TAG "lua"
#define LOGD(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)


/* Constant that is used to index the JNI Environment */
#define LUAJAVAJNIENVTAG "_JNIEnv"
/* Defines the lua State Index Property Name */
#define LUAJAVASTATEINDEX "_LuaJavaStateIndex"
/* Defines wheter the metatable is of a java Object */
#define LUAJAVAOBJECTIND "__IsJavaObject"
/* Constant that defines where in the metatable should I place the function
   name */
#define LUAJAVAOBJECTMETA "JavaObject"

static jclass throwable_class = NULL;
static jmethodID get_message_method = NULL;
static jmethodID throwable_tostring_method = NULL;
static jmethodID get_stack_trace_method = NULL;

static jclass java_function_class = NULL;
static jmethodID java_function_method = NULL;

static jclass luajava_api_class = NULL;
static jclass java_lang_class = NULL;
static jclass java_string_class = NULL;

static jmethodID object_index_method = NULL;
static jmethodID call_method = NULL;
static jmethodID object_newindex_method = NULL;
static jmethodID new_array_method = NULL;
static jmethodID new_multiarray_method = NULL;
static jmethodID get_array_method = NULL;
static jmethodID set_array_method = NULL;
static jmethodID bind_class_method = NULL;
static jmethodID create_proxy_method = NULL;
static jmethodID create_array_method = NULL;
static jmethodID java_create_method = NULL;
static jmethodID java_new_method = NULL;
static jmethodID java_override_method = NULL;
static jmethodID java_isclassmember_method = NULL;
static jmethodID object_call_method = NULL;
static jmethodID java_newinstance_method = NULL;
static jmethodID as_table_method = NULL;
static jmethodID to_string_method = NULL;
static jmethodID get_type_method = NULL;
static jmethodID object_length_method = NULL;
static jmethodID object_instanceof_method = NULL;
static jmethodID string_init_method = NULL;
static jmethodID string_getbytes_method = NULL;
static jmethodID class_getname_method = NULL;
static jmethodID object_equals_method = NULL;
static jmethodID java_gc_method = NULL;
static jmethodID java_close_method = NULL;
static jmethodID java_clear_all_method = NULL;
static jmethodID get_object_method = NULL;

/* 动态加载方法缓存 */
static jmethodID java_loadlib_method = NULL;

/* 反射扩展方法缓存 */
static jmethodID java_getclass_method = NULL;
static jmethodID java_getclassname_method = NULL;
static jmethodID java_isnull_method = NULL;
static jmethodID java_getsimplename_method = NULL;
static jmethodID java_getpackagename_method = NULL;
static jmethodID java_getsuperclass_method = NULL;
static jmethodID java_getinterfaces_method = NULL;
static jmethodID java_isinterface_method = NULL;
static jmethodID java_isarray_method = NULL;
static jmethodID java_getcomponenttype_method = NULL;
static jmethodID java_callstatic_method = NULL;
static jmethodID java_getstaticfield_method = NULL;
static jmethodID java_setstaticfield_method = NULL;
static jmethodID java_getmethods_method = NULL;
static jmethodID java_getfields_method = NULL;
static jmethodID java_getconstructors_method = NULL;
static jmethodID java_importclass_method = NULL;
static jmethodID java_newwithconstructor_method = NULL;
static jmethodID java_getobjectmethods_method = NULL;
static jmethodID java_getdeclaredfield_method = NULL;
static jmethodID java_setdeclaredfield_method = NULL;
static jmethodID java_calldeclaredmethod_method = NULL;
static jmethodID java_isinstance_method = NULL;
static jmethodID java_hashcode_method = NULL;
static jmethodID java_cast_method = NULL;
static jmethodID java_enumvalueof_method = NULL;
static jmethodID java_isassignablefrom_method = NULL;
static jmethodID java_clone_method = NULL;
static jmethodID java_to_table_method = NULL;
static jmethodID java_lambda_method = NULL;
static jmethodID java_weak_ref_method = NULL;
static jmethodID java_soft_ref_method = NULL;
static jmethodID java_async_method = NULL;
static jmethodID java_method_ref_method = NULL;
static jmethodID java_dump_objects_method = NULL;
static jmethodID java_post_method = NULL;
static jmethodID java_get_declared_methods_method = NULL;
static jmethodID java_optional_method = NULL;
static jmethodID java_using_method = NULL;

static int objectIndex(lua_State *L);

static int callMethod(lua_State *L);

static int objectNewIndex(lua_State *L);

static int javaBindClass(lua_State *L);

static int createProxy(lua_State *L);

static int newArray(lua_State *L);

static int createArray(lua_State *L);

static int javaNew(lua_State *L);

static int javaNewInstance(lua_State *L);

static int javaLoadLib(lua_State *L);

static int asTable(lua_State *L);

static int javaToString(lua_State *L);

static int javaObjectLength(lua_State *L);

static int coding(lua_State *L);

static int javaGetClass(lua_State *L);
static int javaGetClassName(lua_State *L);
static int javaIsNull(lua_State *L);
static int javaGetSimpleName(lua_State *L);
static int javaGetPackageName(lua_State *L);
static int javaGetSuperclass(lua_State *L);
static int javaGetInterfaces(lua_State *L);
static int javaIsInterface(lua_State *L);
static int javaIsArray(lua_State *L);
static int javaGetComponentType(lua_State *L);
static int javaCallStatic(lua_State *L);
static int javaGetStaticField(lua_State *L);
static int javaSetStaticField(lua_State *L);
static int javaGetMethods(lua_State *L);
static int javaGetFields(lua_State *L);
static int javaGetConstructors(lua_State *L);
static int javaImportClass(lua_State *L);
static int javaNewWithConstructor(lua_State *L);
static int javaGetObjectMethods(lua_State *L);
static int javaGetDeclaredField(lua_State *L);
static int javaSetDeclaredField(lua_State *L);
static int javaCallDeclaredMethod(lua_State *L);
static int javaIsInstance(lua_State *L);
static int javaHashCode(lua_State *L);
static int javaCast(lua_State *L);
static int javaEnumValueOf(lua_State *L);
static int javaIsAssignableFrom(lua_State *L);
static int javaClone(lua_State *L);
static int javaUsing(lua_State *L);

static int javaIPairs_iter(lua_State *L);

static lua_State *getStateFromCPtr(JNIEnv *env, jlong cptr);

static int luaJavaFunctionCall(lua_State *L);

void pushJNIEnv(JNIEnv *env, lua_State *L);

int pushJavaObject(lua_State *L, const char *name,int idx,int isclass);

JNIEnv *checkEnv(lua_State *L);

jlong checkIndex(lua_State *L);

java_object *checkJavaObject(lua_State *L, int idx);

jobject *toJavaObject(lua_State *L, int idx);

void checkError(JNIEnv *javaEnv, lua_State *L);

static int gc(lua_State *L);
static int jclose(lua_State *L);

JNIEnv *getEnvFromState(lua_State *L);

int isJavaObject(lua_State *L, int idx);


JNIEnv *checkEnv(lua_State *L) {
    JNIEnv *javaEnv = (JNIEnv *) 0;
    lua_pushstring(L, LUAJAVAJNIENVTAG);
    lua_rawget(L, LUA_REGISTRYINDEX);

    if (lua_isuserdata(L, -1))
        javaEnv = *(JNIEnv **) lua_touserdata(L, -1);
    lua_pop(L, 1);
    if (!javaEnv)
        luaL_error(L, "Invalid JNI Environment.");
    return javaEnv;
}

jlong checkIndex(lua_State *L) {
    return (jlong) L;
}

java_object *checkJavaObject(lua_State *L, int idx) {
    //if (!isJavaObject(L, idx))
    //luaL_typerror(L, idx, "java Object");
    //return (jobject *)lua_touserdata(L, idx);
    java_object *obj;
    obj = (java_object *) luaL_checkudata(L, idx, LUAJAVAOBJECTMETA);
    if (obj == NULL) {
        luaL_argerror(L, idx, "JavaObject expected, got null");
    }
    //LOGD("checkJavaObject %s %d %d",obj->name,obj->index,obj->type);
    return obj;
}

jobject *toJavaObject(lua_State *L, int idx){
    JNIEnv *env = checkEnv(L);
    jlong stateIndex = checkIndex(L);
    java_object *obj= (java_object *) luaL_checkudata(L, idx, LUAJAVAOBJECTMETA);
    if (obj == NULL) {
        luaL_argerror(L, idx, "JavaObject expected, got null");
    }
    //LOGD("checkJavaObject %s %d %d",obj->name,obj->index,obj->type);
    (void)obj->index; // 显式忽略未使用的表达式
    jobject ret = (*env)->CallStaticObjectMethod(env, luajava_api_class,
                                          get_object_method,
                                          stateIndex, obj->index);
    checkError(env, L);
    jobject jobj = (*env)->NewGlobalRef(env, ret);
    (*env)->DeleteLocalRef(env,ret);
    /* 返回的 jobject 指针由调用方作为 userdata 压入 Lua 栈，
     * __gc 元方法会自动调用 DeleteGlobalRef 释放 */
    return jobj;
}

void checkError(JNIEnv *javaEnv, lua_State *L) {
    jthrowable exp = (*javaEnv)->ExceptionOccurred(javaEnv);

    /* Handles exception */
    if (exp != NULL) {
        jobject jstr = NULL;
        const char *cStr;

        (*javaEnv)->ExceptionClear(javaEnv);

        /* 优先获取完整堆栈（包含 cause 链） */
        if (get_stack_trace_method != NULL && luajava_api_class != NULL) {
            jstr = (*javaEnv)->CallStaticObjectMethod(javaEnv, luajava_api_class,
                                                       get_stack_trace_method, exp);
        }

        /* 回退到 getMessage 或 toString */
        if (jstr == NULL) {
            if (get_message_method != NULL) {
                jstr = (*javaEnv)->CallObjectMethod(javaEnv, exp, get_message_method);
            }

            if (jstr == NULL) {
                if (throwable_tostring_method == NULL)
                    throwable_tostring_method = (*javaEnv)->GetMethodID(javaEnv, throwable_class,
                                                                        "toString",
                                                                        "()Ljava/lang/String;");
                jstr = (*javaEnv)->CallObjectMethod(javaEnv, exp, throwable_tostring_method);
            }
        }

        if (jstr != NULL) {
            cStr = (*javaEnv)->GetStringUTFChars(javaEnv, jstr, NULL);
            lua_pushstring(L, cStr);
            (*javaEnv)->ReleaseStringUTFChars(javaEnv, jstr, cStr);
            (*javaEnv)->DeleteLocalRef(javaEnv, jstr);
        } else {
            lua_pushstring(L, "Unknown Java exception");
        }

        (*javaEnv)->DeleteLocalRef(javaEnv, exp);

        lua_error(L);
    }
}

/********************* Implementations ***************************/

static void init(JNIEnv *javaEnv, lua_State *L) {
    jclass tempClass;

    if (luajava_api_class == NULL) {
        tempClass = (*javaEnv)->FindClass(javaEnv, "com/luajava/LuaJavaAPI");

        if (tempClass == NULL) {
            luaL_error(L, "Could not find LuaJavaAPI class");
        }

        if ((luajava_api_class = (*javaEnv)->NewGlobalRef(javaEnv, tempClass)) == NULL) {
            luaL_error(L, "Could not bind to LuaJavaAPI class");
        }
        (*javaEnv)->DeleteLocalRef(javaEnv, tempClass);
    }

    if (java_function_class == NULL) {
        tempClass = (*javaEnv)->FindClass(javaEnv, "com/luajava/JavaFunction");

        if (tempClass == NULL) {
            luaL_error(L, "Could not find JavaFunction interface");
        }

        if ((java_function_class = (*javaEnv)->NewGlobalRef(javaEnv, tempClass)) == NULL) {
            luaL_error(L, "Could not bind to JavaFunction interface");
        }
        (*javaEnv)->DeleteLocalRef(javaEnv, tempClass);
    }

    if (java_function_method == NULL) {
        java_function_method =
                (*javaEnv)->GetMethodID(javaEnv, java_function_class, "execute", "()I");
        if (!java_function_method) {
            luaL_error(L, "Could not find <execute> method in JavaFunction");
        }
    }

    if (throwable_class == NULL) {
        tempClass = (*javaEnv)->FindClass(javaEnv, "java/lang/Throwable");

        if (tempClass == NULL) {
            luaL_error(L, "Error. Couldn't bind java class java.lang.Throwable");
        }

        throwable_class = (*javaEnv)->NewGlobalRef(javaEnv, tempClass);
        (*javaEnv)->DeleteLocalRef(javaEnv, tempClass);

        if (throwable_class == NULL) {
            luaL_error(L, "Error. Couldn't bind java class java.lang.Throwable");
        }
    }

    if (get_message_method == NULL) {
        get_message_method = (*javaEnv)->GetMethodID(javaEnv, throwable_class, "getMessage",
                                                     "()Ljava/lang/String;");

        if (get_message_method == NULL) {
            luaL_error(L,
                    "Could not find <getMessage> method in java.lang.Throwable");
        }
    }

    /* 获取完整堆栈的静态方法（LuaJavaAPI.getStackTrace） */
    if (get_stack_trace_method == NULL) {
        get_stack_trace_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "getStackTrace",
                "(Ljava/lang/Throwable;)Ljava/lang/String;");
    }

    if (java_lang_class == NULL) {
        tempClass = (*javaEnv)->FindClass(javaEnv, "java/lang/Class");

        if (tempClass == NULL) {
            luaL_error(L, "Error. Couldn't bind java class java.lang.Class");
        }

        java_lang_class = (*javaEnv)->NewGlobalRef(javaEnv, tempClass);
        (*javaEnv)->DeleteLocalRef(javaEnv, tempClass);

        if (java_lang_class == NULL) {
            luaL_error(L, "Error. Couldn't bind java class java.lang.Class");
        }
    }

    if (java_string_class == NULL) {
        tempClass = (*javaEnv)->FindClass(javaEnv, "java/lang/String");

        if (tempClass == NULL) {
            luaL_error(L, "Could not find String class");
        }

        if ((java_string_class = (*javaEnv)->NewGlobalRef(javaEnv, tempClass)) == NULL) {
            luaL_error(L, "Could not bind to String class");
        }
        (*javaEnv)->DeleteLocalRef(javaEnv, tempClass);
    }

    /* Gets method */
    if (call_method == NULL)
        call_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "callMethod",
                "(JILjava/lang/String;)I");
    if (object_index_method == NULL)
        object_index_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "objectIndex",
                "(JILjava/lang/String;I)I");
    if (object_newindex_method == NULL)
        object_newindex_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "objectNewIndex",
                "(JILjava/lang/String;I)I");
    if (new_array_method == NULL)
        new_array_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "newArray", "(JII)I");
    if (new_multiarray_method == NULL)
        new_multiarray_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "newArray", "(JI)I");
    if (get_array_method == NULL)
        get_array_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "getArrayValue", "(JII)I");
    if (set_array_method == NULL)
        set_array_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "setArrayValue", "(JII)I");
    if (bind_class_method == NULL)
        bind_class_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaBindClass",
                "(JLjava/lang/String;)I");
    if (create_proxy_method == NULL)
        create_proxy_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "createProxy", "(JLjava/lang/String;)I");
    if (create_array_method == NULL)
        create_array_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "createArray", "(JLjava/lang/String;)I");
    if (java_create_method == NULL)
        java_create_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaCreate", "(JI)I");
    if (java_new_method == NULL)
        java_new_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaNew", "(JI)I");
    if (java_override_method == NULL)
        java_override_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaOverride", "(JI)I");
    if (java_isclassmember_method == NULL)
        java_isclassmember_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaIsClassMember", "(Ljava/lang/Class;Ljava/lang/String;)Z");
    if (object_call_method == NULL)
        object_call_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "objectCall", "(JI)I");
    if (java_newinstance_method == NULL)
        java_newinstance_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaNewInstance",
                "(JLjava/lang/String;)I");
    if (as_table_method == NULL)
        as_table_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "asTable", "(JI)I");
    if (to_string_method == NULL)
        to_string_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaToString", "(JI)I");
    if (get_type_method == NULL)
        get_type_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetType", "(JI)I");
    if (object_length_method == NULL)
        object_length_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaObjectLength",
                "(JI)I");
    if (object_equals_method == NULL)
        object_equals_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaEquals",
                "(JII)I");
    if (object_instanceof_method == NULL)
        object_instanceof_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaInstanceof",
                "(JII)I");
    if (java_gc_method == NULL)
        java_gc_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGc", "(JI)V");
    if (java_close_method == NULL)
        java_close_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaClose", "(JI)V");
    if (java_clear_all_method == NULL)
        java_clear_all_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaClearAll", "(J)I");
    if (get_object_method == NULL)
        get_object_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetObject", "(JI)Ljava/lang/Object;");

    if (string_init_method == NULL)
        string_init_method = (*javaEnv)->GetMethodID(
                javaEnv, java_string_class, "<init>", "([BLjava/lang/String;)V");
    if (string_getbytes_method == NULL)
        string_getbytes_method = (*javaEnv)->GetMethodID(
                javaEnv, java_string_class, "getBytes", "(Ljava/lang/String;)[B");
    if (class_getname_method == NULL)
        class_getname_method = (*javaEnv)->GetMethodID(
                javaEnv, java_lang_class, "getName", "()Ljava/lang/String;");

    /* 反射扩展方法初始化 */
    if (java_getclass_method == NULL)
        java_getclass_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetClass", "(JI)I");
    if (java_getclassname_method == NULL)
        java_getclassname_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetClassName", "(JI)I");
    if (java_isnull_method == NULL)
        java_isnull_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaIsNull", "(JI)I");
    if (java_getsimplename_method == NULL)
        java_getsimplename_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetSimpleName", "(JI)I");
    if (java_getpackagename_method == NULL)
        java_getpackagename_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetPackageName", "(JI)I");
    if (java_getsuperclass_method == NULL)
        java_getsuperclass_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetSuperclass", "(JI)I");
    if (java_getinterfaces_method == NULL)
        java_getinterfaces_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetInterfaces", "(JI)I");
    if (java_isinterface_method == NULL)
        java_isinterface_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaIsInterface", "(JI)I");
    if (java_isarray_method == NULL)
        java_isarray_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaIsArray", "(JI)I");
    if (java_getcomponenttype_method == NULL)
        java_getcomponenttype_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetComponentType", "(JI)I");
    if (java_callstatic_method == NULL)
        java_callstatic_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaCallStatic", "(JLjava/lang/String;Ljava/lang/String;)I");
    if (java_getstaticfield_method == NULL)
        java_getstaticfield_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetStaticField", "(JLjava/lang/String;Ljava/lang/String;)I");
    if (java_setstaticfield_method == NULL)
        java_setstaticfield_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaSetStaticField", "(JLjava/lang/String;Ljava/lang/String;)I");
    if (java_getmethods_method == NULL)
        java_getmethods_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetMethods", "(JI)I");
    if (java_getfields_method == NULL)
        java_getfields_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetFields", "(JI)I");
    if (java_getconstructors_method == NULL)
        java_getconstructors_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetConstructors", "(JI)I");
    if (java_importclass_method == NULL)
        java_importclass_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaImportClass", "(JLjava/lang/String;)I");
    if (java_newwithconstructor_method == NULL)
        java_newwithconstructor_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaNewWithConstructor", "(JLjava/lang/String;)I");
    if (java_getobjectmethods_method == NULL)
        java_getobjectmethods_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetObjectMethods", "(JI)I");
    if (java_getdeclaredfield_method == NULL)
        java_getdeclaredfield_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetDeclaredField", "(JILjava/lang/String;)I");
    if (java_setdeclaredfield_method == NULL)
        java_setdeclaredfield_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaSetDeclaredField", "(JILjava/lang/String;)I");
    if (java_calldeclaredmethod_method == NULL)
        java_calldeclaredmethod_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaCallDeclaredMethod", "(JILjava/lang/String;)I");
    if (java_isinstance_method == NULL)
        java_isinstance_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaIsInstance", "(JII)I");
    if (java_hashcode_method == NULL)
        java_hashcode_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaHashCode", "(JI)I");
    if (java_cast_method == NULL)
        java_cast_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaCast", "(JLjava/lang/String;)I");
    if (java_enumvalueof_method == NULL)
        java_enumvalueof_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaEnumValueOf", "(JLjava/lang/String;Ljava/lang/String;)I");
    if (java_isassignablefrom_method == NULL)
        java_isassignablefrom_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaIsAssignableFrom", "(JII)I");
    if (java_clone_method == NULL)
        java_clone_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaClone", "(JI)I");
    checkError(javaEnv, L);

    if (java_to_table_method == NULL)
        java_to_table_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaToTable", "(JI)I");
    checkError(javaEnv, L);

    if (java_lambda_method == NULL)
        java_lambda_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaLambda", "(JII)I");
    checkError(javaEnv, L);

    if (java_weak_ref_method == NULL)
        java_weak_ref_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaWeakRef", "(JI)I");
    checkError(javaEnv, L);

    if (java_soft_ref_method == NULL)
        java_soft_ref_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaSoftRef", "(JI)I");
    checkError(javaEnv, L);

    if (java_async_method == NULL)
        java_async_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaAsync", "(JI)I");
    checkError(javaEnv, L);

    if (java_method_ref_method == NULL)
        java_method_ref_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaMethodRef", "(JILjava/lang/String;)I");
    checkError(javaEnv, L);

    if (java_dump_objects_method == NULL)
        java_dump_objects_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaDumpObjects", "(J)I");
    checkError(javaEnv, L);

    if (java_post_method == NULL)
        java_post_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaPost", "(JI)I");
    checkError(javaEnv, L);

    if (java_get_declared_methods_method == NULL)
        java_get_declared_methods_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaGetDeclaredMethods", "(JI)I");
    checkError(javaEnv, L);

    if (java_optional_method == NULL)
        java_optional_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaOptional", "(JI)I");
    checkError(javaEnv, L);

    if (java_using_method == NULL)
        java_using_method = (*javaEnv)->GetStaticMethodID(
                javaEnv, luajava_api_class, "javaUsing", "(J)I");
    checkError(javaEnv, L);
}

#define NAME "__name"

static inline const char *getObjectName(lua_State *L, JNIEnv *env,
                                        jobject obj) {
    const char *name;

    lua_pushstring(L, NAME);
    lua_rawget(L, -2);
    if (lua_isnil(L, -1)) {
        lua_pop(L, 1);
        jclass cls;
        char c;
        if ((*env)->IsInstanceOf(env, obj, java_lang_class) == JNI_TRUE) {
            c = '.';
            cls = (jclass) obj;
        } else {
            c = '@';
            cls = (*env)->GetObjectClass(env, obj);
            checkError(env, L);
        }

        /*lua_pushstring(L, "class");
        pushJavaObject(L,cls);
        lua_rawset(L, -3);*/

        jstring jstr = (*env)->CallObjectMethod(env, cls, class_getname_method);
        checkError(env, L);
        const char *cStr = (*env)->GetStringUTFChars(env, jstr, NULL);
        lua_pushstring(L, NAME);
        name = lua_pushfstring(L, "%s%c", cStr, c);
        lua_rawset(L, -3);

        (*env)->ReleaseStringUTFChars(env, jstr, cStr);
        (*env)->DeleteLocalRef(env, jstr);

        if (c == '@')
            (*env)->DeleteLocalRef(env, cls);
    } else {
        name = lua_tostring(L, -1);
        lua_pop(L, 1);
    }
    return name;
}

/***************************************************************************
*
*  Function: objectIndex
*  ****/

int objectIndex(lua_State *L) {
    jlong stateIndex;
    const char *key;
    const char *tag;
    jint ret = 0;
    java_object *obj;
    jstring str;
    JNIEnv *javaEnv;

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    /* Gets the object reference */
    obj = checkJavaObject(L, 1);
    /* if key is number get array value, if key is string get object field or
     * method. */
    if (lua_type(L, 2) == LUA_TNUMBER) {
        lua_Number akey = lua_tonumber(L, 2);
        jmethodID method;
        if (obj->type)
            method = new_array_method;
        else
            method = get_array_method;

        ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, method,
                                              stateIndex, obj->index, (jint) akey);
        checkError(javaEnv, L);
        return 1;
    } else if (lua_type(L, 2) == LUA_TSTRING) {
        key = lua_tostring(L, 2);
        lua_getuservalue(L,1);
        const char *name=lua_tostring(L,-1);
        if (name == NULL) name = "?";
        lua_pop(L,1);
        lua_getmetatable(L, 1);
        /* lua stack：1,object;2,key;3,metatable */
        if (lua_rawgeti(L, 3, (long long) obj->index) == LUA_TNIL) {
            lua_pop(L, 1);
            lua_newtable(L);
            lua_pushvalue(L, -1);
            lua_rawseti(L, 3, (long long) obj->index);
        }
        lua_pushvalue(L, 2);
        int mtype = lua_rawget(L, -2);
        //int mtype = lua_type(L, -1);
        if (mtype == LUA_TFUNCTION) {
            lua_pushvalue(L, 1);
            lua_setupvalue(L, -2, 2);
            return 1;
        }
        if (mtype != LUA_TNIL)
            return 1;

        lua_pop(L, 1);
        //const char *name = obj->name;//getObjectName(L, javaEnv, *obj);
        //luaL_getsubtable(L, 3, "_CACHE");
        if (lua_rawgeti(L, 3, 0) == LUA_TNIL) {
            lua_pop(L, 1);
            lua_newtable(L);
            lua_pushvalue(L, -1);
            lua_rawseti(L, 3, 0);
        }
        lua_remove(L, 3);
        tag = lua_pushfstring(L,  "%s%c%s", name,obj->type?'.':'@', key);
        //LOGD("objectIndex: %s %d %d %s",obj->name,obj->index,obj->type,tag);
        /* lua stack：1,object;2,key;3,objtable;4,cache;5,tag */

        lua_pushvalue(L, -1);
        lua_rawget(L, 4);
        int ctype = lua_type(L, -1);
        int type = 0;
        if (ctype == LUA_TNUMBER)
            type = (int) lua_tointeger(L, -1);

        lua_pop(L, 1);
        //lua_pushstring(L, tag);

        if (type != 2) {
            str = (*javaEnv)->NewStringUTF(javaEnv, key);
            ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
                                                  object_index_method,
                                                  stateIndex, obj->index, str, type);
            (*javaEnv)->DeleteLocalRef(javaEnv, str);
            checkError(javaEnv, L);
        }

        if (ctype == LUA_TNIL) {
            lua_pushvalue(L, 5);
            //lua_pushstring(L, tag);
            lua_pushinteger(L, ret);
            lua_rawset(L, 4);
        }

        if (ret == 2 || type == 2) {
            //lua_pushinteger(L, (int)obj);
            lua_pushvalue(L, 5);  /* 推入 tag 作为 upvalue 1(方法缓存键) */
            lua_pushvalue(L, 1);  /* 推入 object 作为 upvalue 2 */
            lua_pushcclosure(L, &callMethod, 2);
            lua_pushvalue(L, 2);
            lua_pushvalue(L, -2);
            lua_rawset(L, 3);
        } else if (ret == 3 || ret == 5) {
            lua_pushvalue(L, 2);
            lua_pushvalue(L, -2);
            lua_rawset(L, 3);
        } else if (ret == 0) {
            luaL_error(L, "%s is not a field or mothod", tag);
        }

    } else {
        lua_pushstring(L, "Invalid object index. Must be integer or string.");
        lua_error(L);
    }
    return 1;
}

/***************************************************************************
*
*  Function: callMethod
*  ****/

int callMethod(lua_State *L) {
    jlong stateIndex;
    java_object *obj;
    const char *methodName;
    jint ret;
    jstring str;
    JNIEnv *javaEnv;

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    /* Gets the methodName */
    methodName = lua_tostring(L, lua_upvalueindex(1));
    if (methodName == NULL) {
        luaL_error(L, "invalid method name in callMethod");
        return 0;
    }

    /* Gets the object reference */
    int udx = lua_upvalueindex(2);
    //obj = checkJavaObject(L, udx);
    //checkJavaObject(L, lua_upvalueindex(1));
    //obj = (jobject*)(int)lua_tointeger(L, lua_upvalueindex(2));
    obj = (java_object *) luaL_testudata(L, udx, LUAJAVAOBJECTMETA);
    if (obj == NULL) {
        luaL_error(L, "can not call the function %s", methodName);
    }

    str = (*javaEnv)->NewStringUTF(javaEnv, methodName);

    ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, call_method,
                                          stateIndex, obj->index, str);
    (*javaEnv)->DeleteLocalRef(javaEnv, str);
    checkError(javaEnv, L);

    /* if no ret, return self */
    if (ret == 0) {
        lua_pushvalue(L, udx);
        ret = 1;
    }

    return ret;
}

/***************************************************************************
*
*  Function: objectNewIndex
*  ****/

int objectNewIndex(lua_State *L) {
    jlong stateIndex;
    java_object *obj;
    const char *fieldName;
    const char *tag;
    lua_Number key;
    jstring str;
    jint ret;
    JNIEnv *javaEnv;

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    /* Gets the object reference */
    obj = checkJavaObject(L, 1);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    /* if key is number set array value, if key is string set object field value.
     */
    if (lua_type(L, 2) == LUA_TNUMBER) {
        key = lua_tonumber(L, 2);
        ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
                                              set_array_method, stateIndex,
                                              obj->index, (jint) key);
        checkError(javaEnv, L);
    } else if (lua_type(L, 2) == LUA_TSTRING) {
        /* Gets the field Name */
        fieldName = lua_tostring(L, 2);
        lua_getuservalue(L,1);
        const char *name=lua_tostring(L,-1);
        if (name == NULL) name = "?";
        lua_pop(L,1);
        lua_getmetatable(L, 1);

        /* lua stack：1,object;2,key;3,value;4,metatable */
        if (lua_rawgeti(L, 4, (long long) obj->index) == LUA_TNIL) {
            lua_pop(L, 1);
            lua_newtable(L);
            lua_pushvalue(L, -1);
            lua_rawseti(L, 4, (long long) obj->index);
        }
        //const char *name = obj->name;//getObjectName(L, javaEnv, *obj);
        //luaL_getsubtable(L, 3, "_CACHE");
        if (lua_rawgeti(L, 4, 0) == LUA_TNIL) {
            lua_pop(L, 1);
            lua_newtable(L);
            lua_pushvalue(L, -1);
            lua_rawseti(L, 4, 0);
        }
        lua_remove(L, 4);
        tag = lua_pushfstring(L, "%s%c%s ", name,obj->type?'.':'@', fieldName);
        /* lua stack：1,object;2,key;3,value;4,objtable;5,cache;6,tag */

        //LOGD("objectNewIndex: %s %d %d %s",obj->name,obj->index,obj->type,tag);
        lua_pushvalue(L, -1);
        lua_rawget(L, 5);
        int ctype = lua_type(L, -1);
        int type = 0;
        if (ctype == LUA_TNUMBER)
            type = (int) lua_tointeger(L, -1);

        lua_pop(L, 1);

        lua_pushvalue(L, 3);
        lua_remove(L, 3);

        str = (*javaEnv)->NewStringUTF(javaEnv, fieldName);

        ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
                                              object_newindex_method,
                                              stateIndex, obj->index, str, type);
        if (ctype == LUA_TNIL) {
            lua_pushvalue(L, 5);
            //lua_pushstring(L, tag);
            lua_pushinteger(L, ret);
            lua_rawset(L, 4);
        }
        if (ret == 0) {
            /* 不是 Java 字段/方法，存储到 per-object table（Lua 扩展属性） */
            /* 栈：[object, key, perObjTable, cache, tag, value] */
            /* 需要将 key-value 设置到 perObjTable（位置3） */
            lua_pushvalue(L, 2);    /* key */
            lua_pushvalue(L, 6);    /* value */
            lua_rawset(L, 3);       /* perObjTable[key] = value */
            ret = 1;  /* 标记成功 */
        }
        (*javaEnv)->DeleteLocalRef(javaEnv, str);
        checkError(javaEnv, L);
    } else {
        lua_pushstring(L, "Invalid object index. Must be integer or string.");
        lua_error(L);
    }
    return 0;
}

/***************************************************************************
*
*  Function: gc
*  ****/

int gc(lua_State *L) {
    jlong stateIndex = checkIndex(L);

    // 无参调用：执行全局清理（清理缓存 + 所有 Java 对象引用）
    if (lua_gettop(L) == 0 || !isJavaObject(L, 1)) {
        JNIEnv *javaEnv = checkEnv(L);
        (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
                                        java_clear_all_method, stateIndex);
        checkError(javaEnv, L);
        return 0;
    }

    // 有参调用：释放单个 Java 对象
    java_object *pObj;
    jobject *pJobj;
    JNIEnv *javaEnv;
    if (luaL_testudata(L, 1, LUAJAVAOBJECTMETA) != NULL) {
        pObj = (java_object *) lua_touserdata(L, 1);
        lua_getmetatable(L, 1);
        lua_pushnil(L);
        lua_rawseti(L, -2, (long long) pObj);
        lua_pop(L, 1);

        javaEnv = checkEnv(L);
        (*javaEnv)->CallStaticVoidMethod(javaEnv, luajava_api_class,
                                         java_gc_method, stateIndex,
                                         pObj->index);
    } else {
        pJobj = (jobject *) lua_touserdata(L, 1);
        javaEnv = checkEnv(L);
        if (pJobj != NULL && *pJobj != NULL) {
            (*javaEnv)->DeleteGlobalRef(javaEnv, *pJobj);
            *pJobj = NULL;
        }
    }
    checkError(javaEnv, L);
    return 0;
}

/**
 * 全局清理：清空所有方法缓存 + 所有 Java 对象引用
 * Lua 调用: luajava.clearAll()
 */
static int javaClearAll(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
                                    java_clear_all_method, stateIndex);
    checkError(javaEnv, L);
    return 0;
}

int jclose(lua_State *L) {
    java_object *pObj;
    jobject *pJobj;
    JNIEnv *javaEnv;
    if (!isJavaObject(L, 1)) {
        return 0;
    }
    jlong stateIndex = checkIndex(L);

    if (luaL_testudata(L, 1, LUAJAVAOBJECTMETA) != NULL) {
        pObj = (java_object *) lua_touserdata(L, 1);
        lua_getmetatable(L, 1);
        lua_pushnil(L);
        lua_rawseti(L, -2, (long long) pObj);
        lua_pop(L, 1);

        javaEnv = checkEnv(L);
        (*javaEnv)->CallStaticVoidMethod(javaEnv, luajava_api_class,
                                         java_close_method, stateIndex,
                                         pObj->index);
    } else {
        pJobj = (jobject *) lua_touserdata(L, 1);
        javaEnv = checkEnv(L);
        if (pJobj != NULL && *pJobj != NULL) {
            (*javaEnv)->DeleteGlobalRef(javaEnv, *pJobj);
            *pJobj = NULL;
        }
    }
    checkError(javaEnv, L);
    return 0;
}

/***************************************************************************
*
*  Function: javaBindClass
*  ****/

int javaBindClass(lua_State *L) {
    int top;
    jlong stateIndex;
    const char *className;
    jstring javaClassName;
    int ret;
    JNIEnv *javaEnv;

    top = lua_gettop(L);

    if (top != 1) {
        luaL_error(
                L, "Error. Function javaBindClass received %d arguments, expected 1.",
                top);
    }
    stateIndex = checkIndex(L);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    /* get the string parameter */
    className = luaL_checkstring(L, 1);

    javaClassName = (*javaEnv)->NewStringUTF(javaEnv, className);

    ret = (*javaEnv)->CallStaticIntMethod(
            javaEnv, luajava_api_class, bind_class_method, stateIndex, javaClassName);

    (*javaEnv)->DeleteLocalRef(javaEnv, javaClassName);
    checkError(javaEnv, L);
    return 1;
}

/***************************************************************************
*
*  Function: createProxy
*  ****/
int createProxy(lua_State *L) {
    jint ret;
    jlong stateIndex;
    const char *impl;
    jstring str;
    JNIEnv *javaEnv;

    if (lua_gettop(L) != 2) {
        lua_pushstring(L, "Error. Function createProxy expects 2 arguments.");
        lua_error(L);
    }

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    luaL_checktype(L, 2, LUA_TTABLE);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    impl = luaL_checkstring(L, 1);

    str = (*javaEnv)->NewStringUTF(javaEnv, impl);

    ret = (*javaEnv)->CallStaticIntMethod(
            javaEnv, luajava_api_class, create_proxy_method, stateIndex, str);

    (*javaEnv)->DeleteLocalRef(javaEnv, str);
    checkError(javaEnv, L);

    return 1;
}

/***************************************************************************
*
*  Function: createProxy
*  ****/
int newArray(lua_State *L) {
    jint ret;
    jlong stateIndex;
    java_object *clazz;
    JNIEnv *javaEnv;

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    /* Gets the object reference */
    clazz = checkJavaObject(L, 1);

    ret = (*javaEnv)->CallStaticIntMethod(
            javaEnv, luajava_api_class, new_multiarray_method, stateIndex, clazz->index);

    checkError(javaEnv, L);

    return 1;
}

/***************************************************************************
*
*  Function: createArray
*  ****/
int createArray(lua_State *L) {
    jint ret;
    jlong stateIndex;
    const char *className;
    jstring str;
    JNIEnv *javaEnv;

    if (lua_gettop(L) != 2) {
        lua_pushstring(L, "Error. Function createArray expects 2 arguments.");
        lua_error(L);
    }


    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    luaL_checktype(L, 2, LUA_TTABLE);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    className = luaL_checkstring(L, 1);

    str = (*javaEnv)->NewStringUTF(javaEnv, className);

    ret = (*javaEnv)->CallStaticIntMethod(
            javaEnv, luajava_api_class, create_array_method, stateIndex, str);

    (*javaEnv)->DeleteLocalRef(javaEnv, str);
    checkError(javaEnv, L);

    return 1;
}

/***************************************************************************
*
*  Function: javaNew
*  ****/

int javaNew(lua_State *L) {
    int top;
    jint ret;
    java_object *classInstance;
    jlong stateIndex;
    JNIEnv *javaEnv;
    top = lua_gettop(L);

    if (top == 0) {
        lua_pushstring(L, "Error. Invalid number of parameters.");
        lua_error(L);
    }

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    /* Gets the java Class reference */
    classInstance = checkJavaObject(L, 1);
    //LOGD("javaNew %s %d %d",classInstance->name,classInstance->index,classInstance->type);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    if (classInstance->type==0) {
        ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
                                              object_call_method, stateIndex,
                                              classInstance->index);
        checkError(javaEnv, L);
        if (ret == 0) {
            lua_pushstring(L, "Can not call a Java Object.");
            lua_error(L);
        }
    }
        /* if arg is table create array or interface, else create calss instance. */
    else if (lua_type(L, 2) == LUA_TTABLE && top == 2) {
        ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
                                              java_create_method, stateIndex,
                                              classInstance->index);
        checkError(javaEnv, L);
    } else {
        ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
                                              java_new_method, stateIndex,
                                              classInstance->index);
        checkError(javaEnv, L);
    }
    return ret;
}

int javaOverride(lua_State *L) {
    int top;
    jint ret = 0;
    java_object *classInstance;
    jlong stateIndex;
    JNIEnv *javaEnv;

    top = lua_gettop(L);

    if (top == 0) {
        lua_pushstring(L, "Error. Invalid number of parameters.");
        lua_error(L);
    }

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    /* Gets the java Class reference */
    classInstance = checkJavaObject(L, 1);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    if (classInstance->type == JNI_FALSE) {
            lua_pushstring(L, "Can not Override a Java Object.");
            lua_error(L);
    }
        /* if arg is table create array or interface, else create calss instance. */
    else if (lua_type(L, 2) == LUA_TTABLE) {
        /* 保存 override table 的引用，供后续注册额外方法使用 */
        lua_pushvalue(L, 2);
        int tableRef = luaL_ref(L, LUA_REGISTRYINDEX);

        /* 调用 Java 层创建增强类实例 */
        ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
                                              java_override_method, stateIndex,
                                              classInstance->index);
        checkError(javaEnv, L);

        /* Java 返回后，栈为 [Class, instance]（ret=1时），将额外方法/字段注册到 instance 的 per-object table */
        if (ret == 1) {
            /* 获取 Class 对象的 jobject 引用，用于 javaIsClassMember 检查 */
            jobject classObj = (*javaEnv)->CallStaticObjectMethod(javaEnv, luajava_api_class,
                                          get_object_method, stateIndex, classInstance->index);
            checkError(javaEnv, L);

            if (classObj != NULL) {
                java_object *instObj = (java_object *)lua_touserdata(L, -1);
                if (instObj != NULL) {
                    /* 获取 instance 的 metatable */
                    if (lua_getmetatable(L, -1) != 0) {
                        /* 获取或创建 per-object table: metatable[obj->index] */
                        lua_rawgeti(L, -1, (long long) instObj->index);
                        if (lua_isnil(L, -1)) {
                            lua_pop(L, 1);
                            lua_newtable(L);
                            lua_pushvalue(L, -1);
                            lua_rawseti(L, -3, (long long) instObj->index);
                        }
                        /* 栈状态：[Class, instance, metatable, perObjTable] */
                        int perObjIdx = lua_gettop(L);

                        /* 获取保存的 override table */
                        lua_rawgeti(L, LUA_REGISTRYINDEX, tableRef);
                        /* 栈：[..., perObjTable, overrideTable] */
                        int overrideIdx = lua_gettop(L);

                        /* 遍历 override table */
                        lua_pushnil(L);
                        while (lua_next(L, overrideIdx) != 0) {
                            /* 栈：[..., ..., key, value] */
                            if (lua_type(L, -2) == LUA_TSTRING) {
                                const char *key = lua_tostring(L, -2);
                                /* 去除显式方法签名部分，如 "append(String)" -> "append" */
                                char memberName[256];
                                strncpy(memberName, key, sizeof(memberName) - 1);
                                memberName[sizeof(memberName) - 1] = '\0';
                                char *paren = strchr(memberName, '(');
                                if (paren != NULL) {
                                    *paren = '\0';
                                }

                                jstring jkey = (*javaEnv)->NewStringUTF(javaEnv, memberName);

                                /* 检查是否是类的已有成员 */
                                jboolean isMember = (*javaEnv)->CallStaticBooleanMethod(
                                    javaEnv, luajava_api_class, java_isclassmember_method,
                                    classObj, jkey);
                                checkError(javaEnv, L);
                                (*javaEnv)->DeleteLocalRef(javaEnv, jkey);

                                if (!isMember) {
                                    /* 不是类成员，添加到 per-object table */
                                    /* 栈：[..., perObjTable, overrideTable, key, value] */
                                    lua_pushvalue(L, -2);  /* key */
                                    lua_pushvalue(L, -2);  /* value */
                                    lua_rawset(L, perObjIdx);  /* perObjTable[key] = value */
                                }
                            }
                            lua_pop(L, 1);  /* pop value, keep key for next */
                        }
                        /* 弹出 overrideTable、perObjTable 和 metatable，恢复到 [Class, instance] */
                        lua_pop(L, 3);
                    }
                }
                (*javaEnv)->DeleteLocalRef(javaEnv, classObj);
            }
        }
        /* 释放 registry 引用（无论成功失败） */
        luaL_unref(L, LUA_REGISTRYINDEX, tableRef);
    } else {
        luaL_typerror(L,2,"table");
    }
    return ret;
}
/***************************************************************************
*
*  Function: javaNewInstance
*  ****/

int javaNewInstance(lua_State *L) {
    jint ret;
    const char *className;
    jstring javaClassName;
    jlong stateIndex;
    JNIEnv *javaEnv;

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    /* get the string parameter */
    className = luaL_checkstring(L, 1);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    javaClassName = (*javaEnv)->NewStringUTF(javaEnv, className);

    ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
                                          java_newinstance_method,
                                          stateIndex, javaClassName);

    (*javaEnv)->DeleteLocalRef(javaEnv, javaClassName);
    checkError(javaEnv, L);

    return 1;
}

/***************************************************************************
*
*  Function: javaLoadLib
*  ****/

int javaLoadLib(lua_State *L) {
    jint ret;
    int top;
    const char *className, *methodName;
    jlong stateIndex;
    jstring javaClassName, javaMethodName;
    JNIEnv *javaEnv;

    top = lua_gettop(L);

    if (top != 2) {
        lua_pushstring(L, "Error. Invalid number of parameters.");
        lua_error(L);
    }

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    className = luaL_checkstring(L, 1);
    methodName = luaL_checkstring(L, 2);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    /* 首次调用时缓存方法ID */
    if (java_loadlib_method == NULL)
        java_loadlib_method = (*javaEnv)->GetStaticMethodID(javaEnv, luajava_api_class, "javaLoadLib",
                                          "(JLjava/lang/String;Ljava/lang/String;)I");

    javaClassName = (*javaEnv)->NewStringUTF(javaEnv, className);
    javaMethodName = (*javaEnv)->NewStringUTF(javaEnv, methodName);

    ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, java_loadlib_method,
                                          stateIndex, javaClassName,
                                          javaMethodName);

    (*javaEnv)->DeleteLocalRef(javaEnv, javaClassName);
    (*javaEnv)->DeleteLocalRef(javaEnv, javaMethodName);
    checkError(javaEnv, L);

    return ret;
}

int asTable(lua_State *L) {
    jint ret;
    java_object *obj;
    jlong stateIndex;
    JNIEnv *javaEnv;

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    /* Gets the java Object reference */
    obj = checkJavaObject(L, 1);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    ret = (*javaEnv)->CallStaticIntMethod(
            javaEnv, luajava_api_class, as_table_method, stateIndex, obj->index);

    checkError(javaEnv, L);

    return 1;
}

/***************************************************************************
*
*  Function: javaToString
*  ****/

int javaToString(lua_State *L) {
    jint ret;
    java_object *obj;
    jobject *jobj;
    jlong stateIndex;
    JNIEnv *javaEnv;

    stateIndex = checkIndex(L);

    if (!isJavaObject(L, 1)) {
        luaL_tolstring(L, 1, NULL);
        return 1;
    }

    if (luaL_testudata(L, 1, LUAJAVAOBJECTMETA) != NULL) {
        obj = (java_object *) lua_touserdata(L, 1);
        javaEnv = checkEnv(L);
        ret = (*javaEnv)->CallStaticIntMethod(
                javaEnv, luajava_api_class, to_string_method, stateIndex, obj->index);
    } else {
        jobj = (jobject *) lua_touserdata(L, 1);
        javaEnv = checkEnv(L);
        if (jobj != NULL && *jobj != NULL) {
            if ((*javaEnv)->IsInstanceOf(javaEnv, *jobj, java_function_class) == JNI_TRUE) {
                lua_pushstring(L, "JavaFunction");
            } else {
                lua_pushfstring(L, "JavaObject: %p", (void *)*jobj);
            }
        } else {
            lua_pushstring(L, "null");
        }
        ret = 1;
    }

    checkError(javaEnv, L);

    return ret;
}

/***************************************************************************
*
*  Function: javaToString
*  ****/

int javaGetType(lua_State *L) {
    jint ret;
    java_object *obj;
    jobject *jobj;
    jlong stateIndex;
    JNIEnv *javaEnv;

    stateIndex = checkIndex(L);

    if (!isJavaObject(L, 1)) {
        luaL_tolstring(L, 1, NULL);
        return 1;
    }

    if (luaL_testudata(L, 1, LUAJAVAOBJECTMETA) != NULL) {
        obj = (java_object *) lua_touserdata(L, 1);
        javaEnv = checkEnv(L);
        ret = (*javaEnv)->CallStaticIntMethod(
                javaEnv, luajava_api_class, get_type_method, stateIndex, obj->index);
    } else {
        jobj = (jobject *) lua_touserdata(L, 1);
        javaEnv = checkEnv(L);
        if (jobj != NULL && *jobj != NULL) {
            if ((*javaEnv)->IsInstanceOf(javaEnv, *jobj, java_function_class) == JNI_TRUE) {
                lua_pushstring(L, "function");
            } else {
                lua_pushstring(L, "userdata");
            }
        } else {
            lua_pushstring(L, "nil");
        }
        ret = 1;
    }

    checkError(javaEnv, L);

    return ret;
}


/***************************************************************************
*
*  Function: javaEquals
*  ****/

int javaEquals(lua_State *L) {
    jint ret;
    java_object *obj;
    java_object *obj2;
    jlong stateIndex;
    JNIEnv *javaEnv;

    if (!isJavaObject(L, 1) || !isJavaObject(L, 2)) {
        lua_pushboolean(L, lua_rawequal(L, 1, 2));
        return 1;
    }

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    /* Gets the java Object reference */
    obj = (java_object *) lua_touserdata(L, 1);
    obj2 = (java_object *) lua_touserdata(L, 2);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    ret = (*javaEnv)->CallStaticIntMethod(
            javaEnv, luajava_api_class, object_equals_method, stateIndex, obj->index, obj2->index);

    lua_pushboolean(L, ret);
    checkError(javaEnv, L);

    return 1;
}

/***************************************************************************
*
*  Function: javaObjectLength
*  ****/

int javaObjectLength(lua_State *L) {
    jint ret;
    java_object *obj;
    jlong stateIndex;
    JNIEnv *javaEnv;

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    /* Gets the java Class reference */
    obj = checkJavaObject(L, 1);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    ret = (*javaEnv)->CallStaticIntMethod(
            javaEnv, luajava_api_class, object_length_method, stateIndex, obj->index);

    checkError(javaEnv, L);

    return 1;
}

/***************************************************************************
*
*  Function: javaGetContext
*  ****/

int javaGetContext(lua_State *L) {
    lua_pushstring(L, "_LuaContext");
    lua_gettable(L, LUA_REGISTRYINDEX);
    return 1;
}

int coding(lua_State *L) {
    /* Gets t((he JNI Environment */
    JNIEnv *env = checkEnv(L);

    size_t size = 0;
    const char *str = luaL_checklstring(L, 1, &size);
    const char *srcEncoding = luaL_optstring(L, 2, "GBK");
    const char *destEncoding = luaL_optstring(L, 3, "UTF8");

    jbyteArray arr = (*env)->NewByteArray(env, size);
    jbyte *data = (*env)->GetByteArrayElements(env, arr, 0);
    memcpy(data, str, size);
    (*env)->ReleaseByteArrayElements(env, arr, data, 0);

    jstring strEncoding = (*env)->NewStringUTF(env, srcEncoding);
    jstring newstr = (jstring) (*env)->NewObject(
            env, java_string_class, string_init_method, arr, strEncoding);
    (*env)->DeleteLocalRef(env, arr);
    (*env)->DeleteLocalRef(env, strEncoding);
    checkError(env, L);

    jstring strDestEncoding = (*env)->NewStringUTF(env, destEncoding);
    jbyteArray byteArr = (*env)->CallObjectMethod(
            env, newstr, string_getbytes_method, strDestEncoding);
    (*env)->DeleteLocalRef(env, strDestEncoding);
    (*env)->DeleteLocalRef(env, newstr);
    checkError(env, L);

    jbyte *newArr = (*env)->GetByteArrayElements(env, byteArr, 0);
    jsize newLen = (*env)->GetArrayLength(env, byteArr);

    lua_pushlstring(L, (const char *) newArr, newLen);
    (*env)->ReleaseByteArrayElements(env, byteArr, newArr, 0);
    (*env)->DeleteLocalRef(env, byteArr);

    return 1;
}

int javaIsInstanceOf(lua_State *L) {
    jint ret;
    java_object *obj;
    java_object *obj2;
    jlong stateIndex;
    JNIEnv *javaEnv;

    if (!isJavaObject(L, 1) || !isJavaObject(L, 2)) {
        lua_pushboolean(L, lua_rawequal(L, 1, 2));
        return 1;
    }

    /* Gets the luaState index */
    stateIndex = checkIndex(L);

    /* Gets the java Object reference */
    obj = (java_object *) lua_touserdata(L, 1);
    obj2 = (java_object *) lua_touserdata(L, 2);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    ret = (*javaEnv)->CallStaticIntMethod(
            javaEnv, luajava_api_class, object_instanceof_method, stateIndex, obj->index, obj2->index);
    lua_pushboolean(L, ret);
    checkError(javaEnv, L);

    return 1;
}

int javaGetClass(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_getclass_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaGetClassName(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_getclassname_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaIsNull(lua_State *L) {
    // 如果参数是 Lua nil，直接返回 true，不调用 Java 层
    if (lua_isnil(L, 1)) {
        lua_pushboolean(L, 1);
        return 1;
    }
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_isnull_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaGetSimpleName(lua_State *L) {
    // 直接在 C 层通过 JNI 获取 SimpleName，绕过 CallStaticIntMethod 路径
    if (lua_isnil(L, 1)) {
        lua_pushstring(L, "null");
        return 1;
    }
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    
    // 通过 Java API 获取对象引用
    jobject localObj = (*javaEnv)->CallStaticObjectMethod(javaEnv, luajava_api_class,
                                          get_object_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    if (localObj == NULL) {
        lua_pushstring(L, "null");
        return 1;
    }
    
    // 获取对象的 Class
    jclass objClass = (*javaEnv)->GetObjectClass(javaEnv, localObj);
    if (objClass == NULL) {
        (*javaEnv)->ExceptionClear(javaEnv);
        (*javaEnv)->DeleteLocalRef(javaEnv, localObj);
        lua_pushstring(L, "GetObjectClass_failed");
        return 1;
    }
    
    // 查找 Class.getSimpleName() 方法
    jclass classCls = (*javaEnv)->FindClass(javaEnv, "java/lang/Class");
    jmethodID getSimpleNameMid = (*javaEnv)->GetMethodID(javaEnv, classCls,
        "getSimpleName", "()Ljava/lang/String;");
    (*javaEnv)->DeleteLocalRef(javaEnv, classCls);
    
    if (getSimpleNameMid == NULL) {
        (*javaEnv)->ExceptionClear(javaEnv);
        (*javaEnv)->DeleteLocalRef(javaEnv, objClass);
        (*javaEnv)->DeleteLocalRef(javaEnv, localObj);
        lua_pushstring(L, "GetMethodID_failed");
        return 1;
    }
    
    // 调用 getSimpleName() - 这是 Class 类的方法，在 objClass 上调用
    jstring nameStr = (jstring)(*javaEnv)->CallObjectMethod(javaEnv, objClass, getSimpleNameMid);
    (*javaEnv)->DeleteLocalRef(javaEnv, localObj);
    (*javaEnv)->DeleteLocalRef(javaEnv, objClass);
    
    if (nameStr == NULL) {
        (*javaEnv)->ExceptionClear(javaEnv);
        lua_pushstring(L, "null");
        return 1;
    }
    
    const char *nameChars = (*javaEnv)->GetStringUTFChars(javaEnv, nameStr, NULL);
    lua_pushstring(L, nameChars ? nameChars : "");
    if (nameChars) {
        (*javaEnv)->ReleaseStringUTFChars(javaEnv, nameStr, nameChars);
    }
    (*javaEnv)->DeleteLocalRef(javaEnv, nameStr);
    
    return 1;
}

int javaGetPackageName(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_getpackagename_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaGetSuperclass(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_getsuperclass_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaGetInterfaces(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_getinterfaces_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaIsInterface(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_isinterface_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaIsArray(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_isarray_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaGetComponentType(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_getcomponenttype_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaCallStatic(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    const char *className = luaL_checkstring(L, 1);
    const char *methodName = luaL_checkstring(L, 2);
    jstring jClassName = (*javaEnv)->NewStringUTF(javaEnv, className);
    jstring jMethodName = (*javaEnv)->NewStringUTF(javaEnv, methodName);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_callstatic_method, stateIndex, jClassName, jMethodName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jClassName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jMethodName);
    checkError(javaEnv, L);
    return ret;
}

int javaGetStaticField(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    const char *className = luaL_checkstring(L, 1);
    const char *fieldName = luaL_checkstring(L, 2);
    jstring jClassName = (*javaEnv)->NewStringUTF(javaEnv, className);
    jstring jFieldName = (*javaEnv)->NewStringUTF(javaEnv, fieldName);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_getstaticfield_method, stateIndex, jClassName, jFieldName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jClassName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jFieldName);
    checkError(javaEnv, L);
    return ret;
}

int javaSetStaticField(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    const char *className = luaL_checkstring(L, 1);
    const char *fieldName = luaL_checkstring(L, 2);
    jstring jClassName = (*javaEnv)->NewStringUTF(javaEnv, className);
    jstring jFieldName = (*javaEnv)->NewStringUTF(javaEnv, fieldName);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_setstaticfield_method, stateIndex, jClassName, jFieldName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jClassName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jFieldName);
    checkError(javaEnv, L);
    return ret;
}

int javaGetMethods(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_getmethods_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaGetFields(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_getfields_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaGetConstructors(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_getconstructors_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaImportClass(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    const char *className = luaL_checkstring(L, 1);
    jstring jClassName = (*javaEnv)->NewStringUTF(javaEnv, className);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_importclass_method, stateIndex, jClassName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jClassName);
    checkError(javaEnv, L);
    return ret;
}

int javaNewWithConstructor(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    const char *className = luaL_checkstring(L, 1);
    jstring jClassName = (*javaEnv)->NewStringUTF(javaEnv, className);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_newwithconstructor_method, stateIndex, jClassName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jClassName);
    checkError(javaEnv, L);
    return ret;
}

int javaGetObjectMethods(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_getobjectmethods_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

int javaGetDeclaredField(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    if (!isJavaObject(L, 1)) {
        luaL_error(L, "expected java object");
        return 0;
    }
    java_object *obj = (java_object *) lua_touserdata(L, 1);
    const char *fieldName = luaL_checkstring(L, 2);
    jstring jFieldName = (*javaEnv)->NewStringUTF(javaEnv, fieldName);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_getdeclaredfield_method, stateIndex, obj->index, jFieldName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jFieldName);
    checkError(javaEnv, L);
    return ret;
}

int javaSetDeclaredField(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    if (!isJavaObject(L, 1)) {
        luaL_error(L, "expected java object");
        return 0;
    }
    java_object *obj = (java_object *) lua_touserdata(L, 1);
    const char *fieldName = luaL_checkstring(L, 2);
    jstring jFieldName = (*javaEnv)->NewStringUTF(javaEnv, fieldName);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_setdeclaredfield_method, stateIndex, obj->index, jFieldName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jFieldName);
    checkError(javaEnv, L);
    return ret;
}

int javaCallDeclaredMethod(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    if (!isJavaObject(L, 1)) {
        luaL_error(L, "expected java object");
        return 0;
    }
    java_object *obj = (java_object *) lua_touserdata(L, 1);
    const char *methodName = luaL_checkstring(L, 2);
    jstring jMethodName = (*javaEnv)->NewStringUTF(javaEnv, methodName);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_calldeclaredmethod_method, stateIndex, obj->index, jMethodName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jMethodName);
    checkError(javaEnv, L);
    return ret;
}

int javaIsInstance(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    if (!isJavaObject(L, 1) || !isJavaObject(L, 2)) {
        lua_pushboolean(L, 0);
        return 1;
    }
    java_object *obj = (java_object *) lua_touserdata(L, 1);
    java_object *targetClass = (java_object *) lua_touserdata(L, 2);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_isinstance_method, stateIndex, obj->index, targetClass->index);
    checkError(javaEnv, L);
    return ret;
}

int javaHashCode(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class, 
        java_hashcode_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaCast
*  类型转换：将 Lua 值转换为指定 Java 类型
*  Lua 调用: luajava.cast(value, className)
*  ****/
int javaCast(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    const char *className = luaL_checkstring(L, 2);
    jstring jClassName = (*javaEnv)->NewStringUTF(javaEnv, className);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_cast_method, stateIndex, jClassName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jClassName);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaEnumValueOf
*  获取枚举值
*  Lua 调用: luajava.enumValueOf(className, valueName)
*  ****/
int javaEnumValueOf(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    const char *className = luaL_checkstring(L, 1);
    const char *valueName = luaL_checkstring(L, 2);
    jstring jClassName = (*javaEnv)->NewStringUTF(javaEnv, className);
    jstring jValueName = (*javaEnv)->NewStringUTF(javaEnv, valueName);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_enumvalueof_method, stateIndex, jClassName, jValueName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jClassName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jValueName);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaIsAssignableFrom
*  检查类型兼容性：class1 是否可以赋值给 class2
*  Lua 调用: luajava.isAssignableFrom(classObj1, classObj2)
*  ****/
int javaIsAssignableFrom(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj1 = checkJavaObject(L, 1);
    java_object *obj2 = checkJavaObject(L, 2);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_isassignablefrom_method, stateIndex, obj1->index, obj2->index);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaClone
*  克隆对象（对象必须实现 Cloneable 接口）
*  Lua 调用: luajava.clone(obj)
*  ****/
int javaClone(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_clone_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaToTable
*  将 Java List/Map/数组转换为 Lua table
*  Lua 调用: luajava.toTable(obj)
*  ****/
int javaToTable(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_to_table_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaLambda
*  创建函数式接口代理（SAM）
*  Lua 调用: luajava.lambda(className, func)
*  ****/
int javaLambda(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    // className 在栈索引 1，Lua function 在栈索引 2
    // Java 层通过 Lua 栈索引直接取值
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_lambda_method, stateIndex, 1, 2);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaWeakRef
*  创建 Java 对象的弱引用
*  Lua 调用: luajava.weakRef(obj)
*  ****/
static int javaWeakRef(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_weak_ref_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaSoftRef
*  创建 Java 对象的软引用
*  Lua 调用: luajava.softRef(obj)
*  ****/
static int javaSoftRef(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_soft_ref_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaAsync
*  在 Java 线程中异步执行任务
*  Lua 调用: luajava.async(runnable)
*  ****/
static int javaAsync(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_async_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaMethodRef
*  创建方法引用 obj::methodName
*  Lua 调用: luajava.methodRef(obj, methodName)
*  ****/
static int javaMethodRef(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    const char *methodName = luaL_checkstring(L, 2);
    jstring jMethodName = (*javaEnv)->NewStringUTF(javaEnv, methodName);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_method_ref_method, stateIndex, obj->index, jMethodName);
    (*javaEnv)->DeleteLocalRef(javaEnv, jMethodName);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaDumpObjects
*  诊断内存：列出 Lua 持有的所有 Java 对象统计
*  Lua 调用: luajava.dumpObjects()
*  ****/
static int javaDumpObjects(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_dump_objects_method, stateIndex);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaPost
*  主线程回调：将 Runnable 投递到 UI 线程执行
*  Lua 调用: luajava.post(runnable)
*  ****/
static int javaPost(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_post_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaGetDeclaredMethods
*  获取对象/类的所有声明方法名（含私有）
*  Lua 调用: luajava.getDeclaredMethods(obj)
*  ****/
static int javaGetDeclaredMethods(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    java_object *obj = checkJavaObject(L, 1);
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_get_declared_methods_method, stateIndex, obj->index);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaOptional
*  创建 Optional 包装：nil → Optional.empty()，否则 Optional.of(value)
*  Lua 调用: luajava.optional(value)
*  ****/
static int javaOptional(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    // 值在栈索引 1（可以是任意类型，nil 也允许）
    // Java 层通过 Lua 栈索引直接读取
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_optional_method, stateIndex, 1);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaUsing
*  try-with-resources 语法糖：using(resource, fn) 或 using({r1,r2}, fn)
*  自动调用 AutoCloseable.close()，异常时也保证关闭
*  Lua 调用: luajava.using(res, function(res) ... end)
*  ****/
static int javaUsing(lua_State *L) {
    jlong stateIndex = checkIndex(L);
    JNIEnv *javaEnv = checkEnv(L);
    // Java 层直接从 Lua 栈读取所有参数
    jint ret = (*javaEnv)->CallStaticIntMethod(javaEnv, luajava_api_class,
        java_using_method, stateIndex);
    checkError(javaEnv, L);
    return ret;
}

/***************************************************************************
*
*  Function: javaPairs
*  __pairs 元方法：将 Java 对象转为 Lua 表后支持 for k,v in pairs() 遍历
*  ****/
int javaPairs(lua_State *L) {
    if (!isJavaObject(L, 1)) {
        lua_pushnil(L);
        return 1;
    }
    /* 将 Java 对象转为 Lua 表 */
    asTable(L);
    /* 返回 next 函数, table, nil 用于标准 pairs 迭代 */
    lua_pushglobaltable(L);
    lua_getfield(L, -1, "next");
    lua_remove(L, -2);
    lua_pushvalue(L, -2);
    lua_pushnil(L);
    return 3;
}

/***************************************************************************
*
*  Function: javaIPairs
*  __ipairs 元方法：将 Java 数组/List 转为 Lua 表后支持 for i,v in ipairs() 遍历
*  ****/
int javaIPairs(lua_State *L) {
    if (!isJavaObject(L, 1)) {
        lua_pushnil(L);
        return 1;
    }
    /* 将 Java 对象转为 Lua 表 */
    asTable(L);
    /* 返回 ipairs 迭代器：迭代函数, table, 起始索引0 */
    lua_pushcfunction(L, javaIPairs_iter);
    lua_pushvalue(L, -2);
    lua_pushinteger(L, 0);
    return 3;
}

/* ipairs 辅助迭代器：返回 table[i+1] 的值，遇到 nil 则停止 */
static int javaIPairs_iter(lua_State *L) {
    lua_Integer i = lua_tointeger(L, 2) + 1;
    lua_pushinteger(L, i);
    lua_geti(L, 1, i);
    if (lua_isnil(L, -1)) {
        return 1;
    }
    return 2;
}

static const luaL_Reg ljobjectmeta[] = {{"__index",    objectIndex},
                                        {"__newindex", objectNewIndex},
                                        {"__call",     javaNew},
                                        {"__len",      javaObjectLength},
                                        {"__tostring", javaToString},
                                        {"__pairs",    javaPairs},
                                        {"__ipairs",   javaIPairs},
                                        {"__type",     javaGetType},
                                        {"__gc",       gc},
                                        {"__close",    jclose},
                                        {"__eq",       javaEquals},
                                        {NULL, NULL}};

/***************************************************************************
*
*  Function: pushJavaObject
*  ****/

int pushJavaObject(lua_State *L, const char *name,int idx,int isclass) {
    /* Gets the JNI Environment */
    //LOGD("objectIndex Java object %s %d",name,idx);
    java_object *userData = (java_object *)lua_newuserdata(L, sizeof(java_object));
    lua_pushstring(L,name);
    //lua_pushvalue(L,-1);
    lua_setuservalue(L,-2);
    //const char *name2=lua_tostring(L,-1);
    //lua_pop(L,1);
    userData->type=isclass;
    //userData->name=name2;
    userData->index=idx;
    luaL_setmetatable(L, LUAJAVAOBJECTMETA);

    //LOGD("objectIndex pushJavaObject %s %d %x",userData->name,userData->index,userData->type);
    return 1;
}

/***************************************************************************
*
*  Function: getStateFromCPtr
*  ****/
lua_State *getStateFromCPtr(JNIEnv *env, jlong cptr) {
    lua_State *L;
    L = (lua_State *) cptr;

    pushJNIEnv(env, L);

    return L;
}

/***************************************************************************
*
*  Function: luaJavaFunctionCall
*  ****/

int luaJavaFunctionCall(lua_State *L) {
    jobject *obj;
    int ret;
    JNIEnv *javaEnv;

    if (!isJavaObject(L, 1)) {
        lua_pushstring(L, "Not a java Function.");
        lua_error(L);
    }

    obj = lua_touserdata(L, 1);

    /* Gets the JNI Environment */
    javaEnv = checkEnv(L);

    /* the Object must be an instance of the JavaFunction class */
    if ((*javaEnv)->IsInstanceOf(javaEnv, *obj, java_function_class) ==
        JNI_FALSE) {
        luaL_error(L, "Called Java object is not a JavaFunction");
        return 0;
    }

    ret = (*javaEnv)->CallIntMethod(javaEnv, *obj, java_function_method);

    checkError(javaEnv, L);

    return ret;
}

/***************************************************************************
*
*  Function: luaJavaFunctionCall
*  ****/

JNIEnv *getEnvFromState(lua_State *L) {
    JNIEnv **udEnv;

    lua_pushstring(L, LUAJAVAJNIENVTAG);
    lua_rawget(L, LUA_REGISTRYINDEX);

    if (!lua_isuserdata(L, -1)) {
        lua_pop(L, 1);
        return NULL;
    }

    udEnv = (JNIEnv **) lua_touserdata(L, -1);

    lua_pop(L, 1);

    return *udEnv;
}

/***************************************************************************
*
*  Function: pushJNIEnv
*  ****/

void pushJNIEnv(JNIEnv *env, lua_State *L) {
    JNIEnv **udEnv;

    lua_pushstring(L, LUAJAVAJNIENVTAG);
    lua_rawget(L, LUA_REGISTRYINDEX);

    if (!lua_isnil(L, -1)) {
        udEnv = (JNIEnv **) lua_touserdata(L, -1);
        *udEnv = env;
        lua_pop(L, 1);
    } else {
        lua_pop(L, 1);
        udEnv = (JNIEnv **) lua_newuserdata(L, sizeof(JNIEnv *));
        *udEnv = env;

        lua_pushstring(L, LUAJAVAJNIENVTAG);
        lua_insert(L, -2);
        lua_rawset(L, LUA_REGISTRYINDEX);
    }
}

/*
 ** Assumes the table is on top of the stack.
 */
static void set_info(lua_State *L) {
    lua_pushliteral(L, "_COPYRIGHT");
    lua_pushliteral(L, "Copyright (C) 2026-2099 DifierLine");
    lua_settable(L, -3);
    lua_pushliteral(L, "_DESCRIPTION");
    lua_pushliteral(L, "LuaJava Pro超进化版本");
    lua_settable(L, -3);
    lua_pushliteral(L, "_NAME");
    lua_pushliteral(L, "LuaJavaPro");
    lua_settable(L, -3);
    lua_pushliteral(L, "_Ultra");
    lua_pushliteral(L, "DifierLine");
    lua_settable(L, -3);
    lua_pushliteral(L, "_VERSION");
    lua_pushliteral(L, "20260625");
    lua_settable(L, -3);
}

static const luaL_Reg ljlib[] = {{"bindClass",       javaBindClass},
                                 {"new",            javaNew},
                                 {"newInstance",    javaNewInstance},
                                 {"loadLib",        javaLoadLib},
                                 {"createProxy",    createProxy},
                                 {"newArray",       newArray},
                                 {"createArray",    createArray},
                                 {"astable",        asTable},
                                 {"tostring",       javaToString},
                                 {"coding",         coding},
                                 {"clear",          gc},
                                {"clearAll",       javaClearAll},
                                {"instanceof",     javaIsInstanceOf},
                                 {"getContext",     javaGetContext},
                                 {"override",       javaOverride},
                                 {"getClass",       javaGetClass},
                                 {"getClassName",   javaGetClassName},
                                 {"isNull",         javaIsNull},
                                 {"getSimpleName",  javaGetSimpleName},
                                 {"getPackageName", javaGetPackageName},
                                 {"getSuperclass",  javaGetSuperclass},
                                 {"getInterfaces",  javaGetInterfaces},
                                 {"isInterface",    javaIsInterface},
                                 {"isArray",       javaIsArray},
                                 {"getComponentType", javaGetComponentType},
                                 {"callStatic",     javaCallStatic},
                                 {"getStaticField", javaGetStaticField},
                                 {"setStaticField", javaSetStaticField},
                                 {"getMethods",     javaGetMethods},
                                 {"getFields",      javaGetFields},
                                 {"getConstructors", javaGetConstructors},
                                 {"importClass",    javaImportClass},
                                 {"newInstance2",   javaNewWithConstructor},
                                 {"getObjectMethods", javaGetObjectMethods},
                                 {"getDeclaredField", javaGetDeclaredField},
                                 {"setDeclaredField", javaSetDeclaredField},
                                 {"callDeclaredMethod", javaCallDeclaredMethod},
                                 {"isInstance",     javaIsInstance},
                                 {"hashCode",       javaHashCode},
                                 {"cast",           javaCast},
                                 {"enumValueOf",    javaEnumValueOf},
                                 {"isAssignableFrom", javaIsAssignableFrom},
                                 {"clone",          javaClone},
                                 {"toTable",        javaToTable},
                                 {"lambda",         javaLambda},
                                 {"weakRef",        javaWeakRef},
                                 {"softRef",        javaSoftRef},
                                 {"async",          javaAsync},
                                 {"methodRef",      javaMethodRef},
                                 {"dumpObjects",    javaDumpObjects},
                                 {"post",           javaPost},
                                 {"getDeclaredMethods", javaGetDeclaredMethods},
                                 {"optional",       javaOptional},
                                 {"using",          javaUsing},
                                 {NULL, NULL}};

LUALIB_API int luaopen_luajava(lua_State *L) {
    JNIEnv *env;

    luaL_newlib(L, ljlib);
    set_info(L);

    luaL_newmetatable(L, LUAJAVAOBJECTMETA);
    luaL_setfuncs(L, ljobjectmeta, 0);
    lua_pushstring(L, LUAJAVAOBJECTIND);
    lua_pushboolean(L, 1);
    lua_rawset(L, -3);
    lua_pop(L, 1);
    env = checkEnv(L);
    init(env, L);
    return 1;
}

/**************************** JNI FUNCTIONS ****************************/

/************************************************************************
*   JNI Called function
*      LuaJava API Functin
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1openLuajava(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L;


    L = getStateFromCPtr(env, cptr);

    lua_pushstring(L, LUAJAVASTATEINDEX);
    lua_pushinteger(L, (lua_Integer) cptr);
    lua_settable(L, LUA_REGISTRYINDEX);
    pushJNIEnv(env, L);

    // luaopen_luajava( L );
    luaL_requiref(L, "luajava", luaopen_luajava, 1);
    luaL_requiref(L, "luaparser", luaopen_luaparser, 1);
    //luaL_dostring(L,"=IAPYthkQWr2DFAH7uAABwAAA5BA10aAGv/rgrrj5i5y1ecuj6/ng8yyiFrClR9qNwsbCUq01/nyZjdKVhk5e515rTSGP0ufy4ZIwtpFmM0NglReNhp+17czKdHGMLecc36Vqb7wZWcVcQEBcfCED4GAAQAlJAGAYeobBMLDBQWWjMZKtCd8BBAQABx6FnMIU/Qglq5BUFxs4UNZWiHSgDCQySlgoOzssMASQs9ATHH34mJAAL+/oKC2qDsQcY5NkDPEbqziBhDQUlk9hULBY7Ci4Ty/IKCY2KLHwABQJutaEBCQ11D1m6P0aRYQ4Ch8RzhMcUlw+KHt4HdwhWup7kpFR1T/cXa91idxWyr/PSIYBfsw9FI/8hbwuloQwJKVLNsKjkeqvbBklgffRf3uI8NCZUtGqudJrEqIVH1eJmxmflYKrActgC3YTv3cwFUlzxL3M6Xy/6lXnkMublQbC0Pv3dxembzb3FoaEDnuMiZ0QJ1MkBSz9YUgGLC89OahMYAAWbVxSOURt0KShn1A6H4TG4mAVB10Lyxh2ImAhHGngudgctTSWc/x+hUGOVGWtbiRkHqRfzRxanuPVyiGggcgG34GUROIlXlS+M7HacTwlQphwbdj0sAPSuhPBX+Yj2ffPMPJ5P74v2m5mW7FlRx+Pwsf77xeiTBJZ4mV9qBr2X8wPJY2qnmnxgDlYjf5");
}

/************************************************************************
*   JNI Called function
*      LuaJava API Functin
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1getObjectFromUserdata(JNIEnv *env, jobject jobj,
                                                  jlong cptr, jint index) {
    /* Get luastate */
    lua_State *L = getStateFromCPtr(env, cptr);
    java_object *obj;

    if (!isJavaObject(L, index)) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"),
                         "Index is not a java object");
        return 0;
    }

    obj = (java_object *) lua_touserdata(L, index);

    return obj->index;
}

/************************************************************************
*   JNI Called function
*      LuaJava API Functin
************************************************************************/

JNIEXPORT jboolean JNICALL
Java_com_luajava_LuaState__1isObject(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint index) {
    /* Get luastate */
    lua_State *L = getStateFromCPtr(env, cptr);

    return (isJavaObject(L, index) ? JNI_TRUE : JNI_FALSE);
}

/************************************************************************
*   JNI Called function
*      LuaJava API Functin
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1pushJavaObject(JNIEnv *env, jobject jobj,
                                           jlong cptr, jstring name, jint objidx,jboolean isclass) {
    /* Get luastate */
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *uts = (*env)->GetStringUTFChars(env, name, NULL);
    //LOGD("pushJavaObject %s %d %x",uts,objidx,isclass);
    pushJavaObject(L , uts, objidx, isclass);
    (*env)->ReleaseStringUTFChars(env, name, uts);
}

/************************************************************************
*   JNI Called function
*      LuaJava API Functin
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1pushJavaFunction(JNIEnv *env, jobject jobj,
                                             jlong cptr, jobject obj) {
    /* Get luastate */
    lua_State *L = getStateFromCPtr(env, cptr);

    jobject *userData, globalRef;

    globalRef = (*env)->NewGlobalRef(env, obj);

    userData = (jobject *) lua_newuserdata(L, sizeof(jobject));
    *userData = globalRef;

    /* Creates metatable */
    lua_newtable(L);

    /* pushes the __call metamethod */
    lua_pushstring(L, LUACALLMETAMETHODTAG);
    lua_pushcfunction(L, &luaJavaFunctionCall);
    lua_rawset(L, -3);

    /* pusher the __gc metamethod */
    lua_pushstring(L, LUAGCMETAMETHODTAG);
    lua_pushcfunction(L, &gc);
    lua_rawset(L, -3);

    /* pushes the __tostring metamethod */
    lua_pushstring(L, LUATOSTRINGMETAMETHODTAG);
    lua_pushcfunction(L, &javaToString);
    lua_rawset(L, -3);

    lua_pushstring(L, LUAJAVAOBJECTIND);
    lua_pushboolean(L, 1);
    lua_rawset(L, -3);

    if (lua_setmetatable(L, -2) == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "com/luajava/LuaException"),
                         "Index is not a java object");
    }
}

/************************************************************************
*   JNI Called function
*      LuaJava API Functin
************************************************************************/

JNIEXPORT jboolean JNICALL
Java_com_luajava_LuaState__1isJavaFunction(JNIEnv *env, jobject jobj,
                                           jlong cptr, jint idx) {
    /* Get luastate */
    lua_State *L = getStateFromCPtr(env, cptr);
    jobject *obj;

    if (!isJavaObject(L, idx)) {
        return JNI_FALSE;
    }

    obj = (jobject *) lua_touserdata(L, idx);

    return (*env)->IsInstanceOf(env, *obj, java_function_class);
}

/*********************** LUA API FUNCTIONS ******************************/

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jlong JNICALL
Java_com_luajava_LuaState__1newstate(JNIEnv *env, jobject jobj) {
    lua_State *L = luaL_newstate();
    return (jlong) L;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1openBase(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    // luaopen_base( L );
    luaL_requiref(L, "", luaopen_base, 1);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1openTable(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    // luaopen_table( L );
    luaL_requiref(L, LUA_TABLIBNAME, luaopen_table, 1);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1openIo(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    // luaopen_io( L );
    luaL_requiref(L, LUA_IOLIBNAME, luaopen_io, 1);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1openOs(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    // luaopen_os( L );
    luaL_requiref(L, LUA_OSLIBNAME, luaopen_os, 1);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL Java_com_luajava_LuaState__1openString(JNIEnv *env,
                                                              jobject jobj,
                                                              jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    // luaopen_string( L );
    luaL_requiref(L, LUA_STRLIBNAME, luaopen_string, 1);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1openMath(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    // luaopen_math( L );
    luaL_requiref(L, LUA_MATHLIBNAME, luaopen_math, 1);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1openDebug(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    // luaopen_debug( L );
    luaL_requiref(L, LUA_DBLIBNAME, luaopen_debug, 1);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL Java_com_luajava_LuaState__1openPackage(JNIEnv *env,
                                                               jobject jobj,
                                                               jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    // luaopen_package( L );
    luaL_requiref(L, LUA_LOADLIBNAME, luaopen_package, 1);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1openLibs(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    luaL_openlibs(L);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1close(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_close(L);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jlong JNICALL
Java_com_luajava_LuaState__1newthread(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_State *newThread;

    newThread = lua_newthread(L);
    return (jlong) newThread;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1getTop(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_gettop(L);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1setTop(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint top) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_settop(L, (int) top);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1pushValue(JNIEnv *env, jobject jobj, jlong cptr,
                                      jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_pushvalue(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1rotate(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint idx, jint n) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_rotate(L, (int) idx, n);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1copy(JNIEnv *env, jobject jobj, jlong cptr,
                                 jint fromidx, jint toidx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_copy(L, (int) fromidx, (int) toidx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1remove(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_remove(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1insert(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_insert(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1replace(JNIEnv *env, jobject jobj, jlong cptr,
                                    jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_replace(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1checkStack(JNIEnv *env, jobject jobj, jlong cptr,
                                       jint sz) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_checkstack(L, (int) sz);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1xmove(JNIEnv *env, jobject jobj, jlong from,
                                  jlong to, jint n) {
    lua_State *fr = getStateFromCPtr(env, from);
    lua_State *t = getStateFromCPtr(env, to);

    lua_xmove(fr, t, (int) n);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1isNumber(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_isnumber(L, (int) idx);
}

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1isInteger(JNIEnv *env, jobject jobj, jlong cptr,
                                      jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_isinteger(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1isString(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_isstring(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1isFunction(JNIEnv *env, jobject jobj, jlong cptr,
                                       jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_isfunction(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1isCFunction(JNIEnv *env, jobject jobj, jlong cptr,
                                        jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_iscfunction(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1isUserdata(JNIEnv *env, jobject jobj, jlong cptr,
                                       jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_isuserdata(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1isTable(JNIEnv *env, jobject jobj, jlong cptr,
                                    jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_istable(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1isBoolean(JNIEnv *env, jobject jobj, jlong cptr,
                                      jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_isboolean(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1isNil(JNIEnv *env, jobject jobj, jlong cptr,
                                  jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_isnil(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1isNone(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_isnone(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1isNoneOrNil(JNIEnv *env, jobject jobj, jlong cptr,
                                        jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_isnoneornil(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1type(JNIEnv *env, jobject jobj, jlong cptr,
                                 jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_type(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jstring JNICALL
Java_com_luajava_LuaState__1typeName(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint tp) {
    lua_State *L = getStateFromCPtr(env, cptr);

    const char *name = lua_typename(L, tp);

    return (*env)->NewStringUTF(env, name);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1equal(JNIEnv *env, jobject jobj, jlong cptr,
                                  jint idx1, jint idx2) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_equal(L, idx1, idx2);
}

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1compare(JNIEnv *env, jobject jobj, jlong cptr,
                                    jint idx1, jint idx2, jint op) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_compare(L, idx1, idx2, op);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1rawequal(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint idx1, jint idx2) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_rawequal(L, idx1, idx2);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1lessThan(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint idx1, jint idx2) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_lessthan(L, idx1, idx2);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jdouble JNICALL
Java_com_luajava_LuaState__1toNumber(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jdouble) lua_tonumber(L, idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jlong JNICALL
Java_com_luajava_LuaState__1toInteger(JNIEnv *env, jobject jobj, jlong cptr,
                                      jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    if (lua_isinteger(L, idx))
        return (jlong) lua_tointeger(L, idx);
    else
        return (jlong) lua_tonumber(L, idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1toBoolean(JNIEnv *env, jobject jobj, jlong cptr,
                                      jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_toboolean(L, idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jbyteArray JNICALL
Java_com_luajava_LuaState__1toString(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    size_t size = 0;
    const char *str = lua_tolstring(L, idx, &size);
    jbyteArray arr = (*env)->NewByteArray(env, size);
    jbyte *data = (*env)->GetByteArrayElements(env, arr, 0);
    memcpy(data, str, size);
    (*env)->ReleaseByteArrayElements(env, arr, data, 0);
    return arr;
}

JNIEXPORT jbyteArray JNICALL
Java_com_luajava_LuaState__1LtoString(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    size_t size = 0;
    const char *str = luaL_tolstring(L, idx, &size);
    lua_pop(L,1);
    jbyteArray arr = (*env)->NewByteArray(env, size);
    jbyte *data = (*env)->GetByteArrayElements(env, arr, 0);
    memcpy(data, str, size);
    (*env)->ReleaseByteArrayElements(env, arr, data, 0);
    return arr;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1strlen(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_strlen(L, idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1objlen(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_objlen(L, idx);
}

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1rawlen(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_rawlen(L, idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jlong JNICALL
Java_com_luajava_LuaState__1toThread(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint idx) {
    lua_State *L, *thr;
    L = getStateFromCPtr(env, cptr);

    thr = lua_tothread(L, (int) idx);
    return (jlong) thr;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1pushNil(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_pushnil(L);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1pushNumber(JNIEnv *env, jobject jobj, jlong cptr,
                                       jdouble number) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_pushnumber(L, (lua_Number) number);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1pushInteger(JNIEnv *env, jobject jobj, jlong cptr,
                                        jlong integer) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_pushinteger(L, (lua_Integer) integer);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1pushString(
        JNIEnv *env, jobject jobj, jlong cptr, jstring str) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *uniStr;

    uniStr = (*env)->GetStringUTFChars(env, str, NULL);

    lua_pushstring(L, uniStr);

    (*env)->ReleaseStringUTFChars(env, str, uniStr);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1pushLString(
        JNIEnv *env, jobject jobj, jlong cptr, jbyteArray bytes, jint n) {
    lua_State *L = getStateFromCPtr(env, cptr);
    char *cBytes;

    cBytes = (char *) (*env)->GetByteArrayElements(env, bytes, NULL);

    lua_pushlstring(L, cBytes, n);

    (*env)->ReleaseByteArrayElements(env, bytes, (jbyte *)cBytes, 0);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1pushBoolean(JNIEnv *env, jobject jobj, jlong cptr,
                                        jint jbool) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_pushboolean(L, (int) jbool);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1getTable(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_gettable(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1getField(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint idx, jstring k) {
    lua_State *L = getStateFromCPtr(env, cptr);

    const char *uniStr;
    uniStr = (*env)->GetStringUTFChars(env, k, NULL);

    int ret = lua_getfield(L, (int) idx, uniStr);

    (*env)->ReleaseStringUTFChars(env, k, uniStr);
    return (jint) ret;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1getI(JNIEnv *env, jobject jobj, jlong cptr,
                                 jint idx, jlong n) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_geti(L, (int) idx, (lua_Integer) n);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1rawGet(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_rawget(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1rawGetI(JNIEnv *env, jobject jobj, jlong cptr,
                                    jint idx, jlong n) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_rawgeti(L, idx, (lua_Integer) n);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1createTable(JNIEnv *env, jobject jobj, jlong cptr,
                                        jint narr, jint nrec) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_createtable(L, (int) narr, (int) nrec);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1newTable(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_newtable(L);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1getMetaTable(JNIEnv *env, jobject jobj,
                                         jlong cptr, jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return lua_getmetatable(L, idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1getUserValue(JNIEnv *env, jobject jobj,
                                         jlong cptr, jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return lua_getuservalue(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1setTable(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_settable(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1setField(JNIEnv *env, jobject jobj, jlong cptr,
                                     jint idx, jstring k) {
    lua_State *L = getStateFromCPtr(env, cptr);

    const char *uniStr;
    uniStr = (*env)->GetStringUTFChars(env, k, NULL);

    lua_setfield(L, (int) idx, uniStr);

    (*env)->ReleaseStringUTFChars(env, k, uniStr);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1setI(JNIEnv *env, jobject jobj, jlong cptr,
                                 jint idx, jlong n) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_seti(L, (int) idx, (lua_Integer) n);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1rawSet(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_rawset(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1rawSetI(JNIEnv *env, jobject jobj, jlong cptr,
                                    jint idx, jlong n) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_rawseti(L, idx, (lua_Integer) n);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1setMetaTable(JNIEnv *env, jobject jobj,
                                         jlong cptr, jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return lua_setmetatable(L, idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1setUserValue(JNIEnv *env, jobject jobj,
                                         jlong cptr, jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_setuservalue(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1call(JNIEnv *env, jobject jobj, jlong cptr,
                                 jint nArgs, jint nResults) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_call(L, nArgs, nResults);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1pcall(JNIEnv *env, jobject jobj, jlong cptr,
                                  jint nArgs, jint nResults, jint errFunc) {
    lua_State *L = getStateFromCPtr(env, cptr);
    return (jint) lua_pcall(L, nArgs, nResults, errFunc);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1yield(JNIEnv *env, jobject jobj, jlong cptr,
                                  jint nResults) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_yield(L, nResults);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1resume(JNIEnv *env, jobject jobj, jlong cptr,
                                   jlong cptr2, jint nArgs) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_State *L2 = getStateFromCPtr(env, cptr2);
#if defined(LUA_VERSION_NUM) && LUA_VERSION_NUM >= 502
    int nres;
    // Lua 5.3 及更新版本
    return (jint) lua_resume(L, L2, nArgs, &nres);
#else
    (void)L2; // 忽略未使用的参数
    return (jint) lua_resume(L, nArgs);
#endif
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL Java_com_luajava_LuaState__1isYieldable(JNIEnv *env,
                                                               jobject jobj,
                                                               jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_isyieldable(L);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1status(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_status(L);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL Java_com_luajava_LuaState__1gc(JNIEnv *env, jobject jobj,
                                                      jlong cptr, jint what,
                                                      jint data) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_gc(L, what, data);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1next(JNIEnv *env, jobject jobj, jlong cptr,
                                 jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_next(L, idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1error(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) lua_error(L);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1concat(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint n) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_concat(L, n);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL Java_com_luajava_LuaState__1pop(JNIEnv *env,
                                                       jobject jobj,
                                                       jlong cptr, jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);

    lua_pop(L, (int) idx);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1pushGlobalTable(JNIEnv *env, jobject jobj, jlong cptr) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_pushglobaltable(L);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1setGlobal(JNIEnv *env, jobject jobj, jlong cptr,
                                      jstring name) {
    lua_State *L = getStateFromCPtr(env, cptr);

    const char *str = (*env)->GetStringUTFChars(env, name, NULL);

    lua_setglobal(L, str);

    (*env)->ReleaseStringUTFChars(env, name, str);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1getGlobal(JNIEnv *env, jobject jobj, jlong cptr,
                                      jstring name) {
    lua_State *L = getStateFromCPtr(env, cptr);

    const char *str = (*env)->GetStringUTFChars(env, name, NULL);

    int ret = lua_getglobal(L, str);

    (*env)->ReleaseStringUTFChars(env, name, str);
    return (jint) ret;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1LdoFile(JNIEnv *env, jobject jobj, jlong cptr,
                                    jstring fileName) {
    lua_State *L = getStateFromCPtr(env, cptr);

    const char *file = (*env)->GetStringUTFChars(env, fileName, NULL);

    int ret;

    ret = luaL_loadfile(L, file);
    if (ret != LUA_OK) {
        (*env)->ReleaseStringUTFChars(env, fileName, file);
        return (jint) ret;
    }

    ret = lua_pcall(L, 0, LUA_MULTRET, 0);
    if (ret != LUA_OK) {
        (*env)->ReleaseStringUTFChars(env, fileName, file);
        return (jint) ret;
    }

    (*env)->ReleaseStringUTFChars(env, fileName, file);

    return (jint) ret;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1LdoString(JNIEnv *env, jobject jobj, jlong cptr,
                                      jstring str) {
    lua_State *L = getStateFromCPtr(env, cptr);

    const char *utfStr = (*env)->GetStringUTFChars(env, str, NULL);

    int ret;

    ret = luaL_dostring(L, utfStr);

    (*env)->ReleaseStringUTFChars(env, str, utfStr);

    return (jint) ret;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1LgetMetaField(JNIEnv *env, jobject jobj,
                                          jlong cptr, jint obj, jstring e) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *str = (*env)->GetStringUTFChars(env, e, NULL);
    int ret;

    ret = luaL_getmetafield(L, (int) obj, str);

    (*env)->ReleaseStringUTFChars(env, e, str);

    return (jint) ret;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1LcallMeta(JNIEnv *env, jobject jobj, jlong cptr,
                                      jint obj, jstring e) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *str = (*env)->GetStringUTFChars(env, e, NULL);
    int ret;

    ret = luaL_callmeta(L, (int) obj, str);

    (*env)->ReleaseStringUTFChars(env, e, str);

    return (jint) ret;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1LargError(JNIEnv *env, jobject jobj, jlong cptr,
                                      jint numArg, jstring extraMsg) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *msg = (*env)->GetStringUTFChars(env, extraMsg, NULL);
    int ret;

    ret = luaL_argerror(L, (int) numArg, msg);

    (*env)->ReleaseStringUTFChars(env, extraMsg, msg);

    return (jint) ret;;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jstring JNICALL
Java_com_luajava_LuaState__1LcheckString(JNIEnv *env, jobject jobj,
                                         jlong cptr, jint numArg) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *res;

    res = luaL_checkstring(L, (int) numArg);

    return (*env)->NewStringUTF(env, res);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jstring JNICALL
Java_com_luajava_LuaState__1LoptString(JNIEnv *env, jobject jobj, jlong cptr,
                                       jint numArg, jstring def) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *d = (*env)->GetStringUTFChars(env, def, NULL);
    const char *res;
    jstring ret;

    res = luaL_optstring(L, (int) numArg, d);

    ret = (*env)->NewStringUTF(env, res);

    (*env)->ReleaseStringUTFChars(env, def, d);

    return ret;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jdouble JNICALL
Java_com_luajava_LuaState__1LcheckNumber(JNIEnv *env, jobject jobj,
                                         jlong cptr, jint numArg) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jdouble) luaL_checknumber(L, (int) numArg);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jdouble JNICALL
Java_com_luajava_LuaState__1LoptNumber(JNIEnv *env, jobject jobj, jlong cptr,
                                       jint numArg, jdouble def) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jdouble) luaL_optnumber(L, (int) numArg, (lua_Number) def);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1LcheckInteger(JNIEnv *env, jobject jobj,
                                          jlong cptr, jint numArg) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) luaL_checkinteger(L, (int) numArg);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1LoptInteger(JNIEnv *env, jobject jobj, jlong cptr,
                                        jint numArg, jint def) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) luaL_optinteger(L, (int) numArg, (lua_Integer) def);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1LcheckStack(JNIEnv *env, jobject jobj, jlong cptr,
                                        jint sz, jstring msg) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *m = (*env)->GetStringUTFChars(env, msg, NULL);

    luaL_checkstack(L, (int) sz, m);

    (*env)->ReleaseStringUTFChars(env, msg, m);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1LcheckType(JNIEnv *env, jobject jobj, jlong cptr,
                                       jint nArg, jint t) {
    lua_State *L = getStateFromCPtr(env, cptr);

    luaL_checktype(L, (int) nArg, (int) t);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1LcheckAny(JNIEnv *env, jobject jobj, jlong cptr,
                                      jint nArg) {
    lua_State *L = getStateFromCPtr(env, cptr);

    luaL_checkany(L, (int) nArg);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1LnewMetatable(JNIEnv *env, jobject jobj,
                                          jlong cptr, jstring tName) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *name = (*env)->GetStringUTFChars(env, tName, NULL);
    int ret;

    ret = luaL_newmetatable(L, name);

    (*env)->ReleaseStringUTFChars(env, tName, name);

    return (jint) ret;;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1LgetMetatable(JNIEnv *env, jobject jobj,
                                          jlong cptr, jstring tName) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *name = (*env)->GetStringUTFChars(env, tName, NULL);

    luaL_getmetatable(L, name);

    (*env)->ReleaseStringUTFChars(env, tName, name);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1Lwhere(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint lvl) {
    lua_State *L = getStateFromCPtr(env, cptr);

    luaL_where(L, (int) lvl);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL Java_com_luajava_LuaState__1Lref(JNIEnv *env,
                                                        jobject jobj,
                                                        jlong cptr, jint t) {
    lua_State *L = getStateFromCPtr(env, cptr);

    return (jint) luaL_ref(L, (int) t);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT void JNICALL
Java_com_luajava_LuaState__1LunRef(JNIEnv *env, jobject jobj, jlong cptr,
                                   jint t, jint ref) {
    lua_State *L = getStateFromCPtr(env, cptr);

    luaL_unref(L, (int) t, (int) ref);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1LloadFile(JNIEnv *env, jobject jobj, jlong cptr,
                                      jstring fileName) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *fn = (*env)->GetStringUTFChars(env, fileName, NULL);
    int ret;

    ret = luaL_loadfile(L, fn);

    (*env)->ReleaseStringUTFChars(env, fileName, fn);

    return (jint) ret;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1LloadBuffer(JNIEnv *env, jobject jobj, jlong cptr,
                                        jbyteArray buff, jlong sz, jstring n) {
    lua_State *L = getStateFromCPtr(env, cptr);
    jbyte *cBuff = (*env)->GetByteArrayElements(env, buff, NULL);
    const char *name = (*env)->GetStringUTFChars(env, n, NULL);
    int ret;

    ret = luaL_loadbuffer(L, (const char *) cBuff, (int) sz, name);

    (*env)->ReleaseStringUTFChars(env, n, name);

    (*env)->ReleaseByteArrayElements(env, buff, cBuff, 0);

    return (jint) ret;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1LloadString(JNIEnv *env, jobject jobj, jlong cptr,
                                        jstring str) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *fn = (*env)->GetStringUTFChars(env, str, NULL);
    int ret;

    ret = luaL_loadstring(L, fn);

    (*env)->ReleaseStringUTFChars(env, str, fn);

    return (jint) ret;
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jstring JNICALL
Java_com_luajava_LuaState__1Lgsub(JNIEnv *env, jobject jobj, jlong cptr,
                                  jstring s, jstring p, jstring r) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *utS = (*env)->GetStringUTFChars(env, s, NULL);
    const char *utP = (*env)->GetStringUTFChars(env, p, NULL);
    const char *utR = (*env)->GetStringUTFChars(env, r, NULL);

    const char *sub = luaL_gsub(L, utS, utP, utR);

    (*env)->ReleaseStringUTFChars(env, s, utS);
    (*env)->ReleaseStringUTFChars(env, p, utP);
    (*env)->ReleaseStringUTFChars(env, r, utR);

    return (*env)->NewStringUTF(env, sub);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jstring JNICALL
Java_com_luajava_LuaState__1getUpValue(JNIEnv *env, jobject jobj, jlong cptr,
                                       jint funcindex, jint n) {
    lua_State *L = getStateFromCPtr(env, cptr);

    const char *name = lua_getupvalue(L, (int) funcindex, (int) n);
    return (*env)->NewStringUTF(env, name);
}

/************************************************************************
*   JNI Called function
*      Lua Exported Function
************************************************************************/

JNIEXPORT jstring JNICALL
Java_com_luajava_LuaState__1setUpValue(JNIEnv *env, jobject jobj, jlong cptr,
                                       jint funcindex, jint n) {
    lua_State *L = getStateFromCPtr(env, cptr);

    const char *name = lua_setupvalue(L, (int) funcindex, (int) n);
    return (*env)->NewStringUTF(env, name);
}


// 写入函数，把 dump 出来的数据写入 Lua buffer
static int writer(lua_State *L, const void *p, size_t size, void *ud) {
    luaL_addlstring((luaL_Buffer *)ud, (const char *)p, size);
    return 0;
}

JNIEXPORT jbyteArray JNICALL
Java_com_luajava_LuaState__1dump(JNIEnv *env, jobject jobj, jlong cptr, jint funcindex) {
    lua_State *L = getStateFromCPtr(env, cptr);
    int idx = lua_absindex(L, funcindex);

    if (!lua_isfunction(L, idx)) {
        // 返回 null 以避免 JNI crash（不要直接 luaL_error）
        return NULL;
    }

    luaL_Buffer b;
    luaL_buffinit(L, &b);

    // 需要将函数移到栈顶（有些情况下 dump 会找错）
    lua_pushvalue(L, idx);

    if (lua_dump(L, writer, &b, 0) != 0) {
        lua_pop(L, 1); // 弹掉函数
        return NULL;
    }

    luaL_pushresult(&b);
    size_t size = 0;
    const char *data = lua_tolstring(L, -1, &size);

    jbyteArray result = (*env)->NewByteArray(env, size);
    if (result && data) {
        (*env)->SetByteArrayRegion(env, result, 0, size, (const jbyte *)data);
    }

    lua_pop(L, 2); // 弹掉结果字符串 和 函数
    return result;
}



JNIEXPORT jint JNICALL
Java_com_luajava_LuaState__1isThread(JNIEnv *env, jobject instance, jlong ptr, jint idx) {

    lua_State *L = getStateFromCPtr(env, ptr);

    return (jint) lua_isthread(L, (int) idx);

}

JNIEXPORT jbyteArray JNICALL
Java_com_luajava_LuaState__1toBuffer(JNIEnv *env, jobject instance, jlong cptr, jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    size_t size = 0;
    const char *str = lua_tolstring(L, idx, &size);
    jbyteArray arr = (*env)->NewByteArray(env, size);
    jbyte *data = (*env)->GetByteArrayElements(env, arr, 0);
    memcpy(data, str, size);
    (*env)->ReleaseByteArrayElements(env, arr, data, 0);
    return arr;
}

/************************************************************************
*   JNI Called function
*      分析Lua代码，检测潜在的运行时错误
*      直接使用luaD_protectedparser进行底层解析
*      返回格式: "行号:列号:错误信息"，如果没有错误则返回null
************************************************************************/

#include "src/core/lzio.h"
#include "src/core/ldo.h"

/* 字符串读取器结构体 */
typedef struct AnalyzeReaderState {
    const char *str;
    size_t size;
} AnalyzeReaderState;

/* 字符串读取器函数 */
static const char *analyzeStringReader(lua_State *L, void *ud, size_t *size) {
    AnalyzeReaderState *state = (AnalyzeReaderState *)ud;
    (void)L;  /* 未使用 */
    if (state->size == 0) return NULL;
    *size = state->size;
    state->size = 0;
    return state->str;
}

JNIEXPORT jstring JNICALL
Java_com_luajava_LuaState__1analyzeCode(JNIEnv *env, jobject instance, jlong cptr, jstring code) {
    lua_State *L = getStateFromCPtr(env, cptr);
    
    if (code == NULL) {
        return NULL;
    }
    
    const char *codeStr = (*env)->GetStringUTFChars(env, code, NULL);
    if (codeStr == NULL) {
        return NULL;
    }
    
    size_t codeLen = strlen(codeStr);
    
    /* 设置ZIO结构体用于luaD_protectedparser */
    ZIO z;
    AnalyzeReaderState readerState;
    readerState.str = codeStr;
    readerState.size = codeLen;
    
    luaZ_init(L, &z, analyzeStringReader, &readerState);
    
    /* 直接调用luaD_protectedparser进行底层解析 */
    int status = luaD_protectedparser(L, &z, "=analyzeCode", NULL);
    
    (*env)->ReleaseStringUTFChars(env, code, codeStr);
    
    if (status != LUA_OK) {
        /* 获取错误信息 */
        const char *errMsg = lua_tostring(L, -1);
        jstring errorResult = NULL;
        
        if (errMsg != NULL) {
            errorResult = (*env)->NewStringUTF(env, errMsg);
        }
        
        lua_pop(L, 1); /* 弹出错误信息 */
        return errorResult;
    }
    
    /* 编译成功，弹出编译后的函数 */
    lua_pop(L, 1);
    return NULL;
}

/************************************************************************
*   JNI Called function
*      解析Lua代码获取Proto信息，返回JSON格式的字节码结构
*      用于LuaToJava转换器
************************************************************************/

#include "src/core/lobject.h"
#include "src/core/lopcodes.h"
#include "src/core/lopnames.h"

/* 递归收集Proto信息并生成JSON */
static void protoToJson(lua_State *L, luaL_Buffer *B, Proto *p, int indent);

/* 添加缩进 */
static void addIndent(luaL_Buffer *B, int indent) {
    for (int i = 0; i < indent; i++) {
        luaL_addstring(B, "  ");
    }
}

/* 添加转义字符串 */
static void addEscapedString(luaL_Buffer *B, const char *s) {
    luaL_addchar(B, '"');
    if (s) {
        while (*s) {
            switch (*s) {
                case '"': luaL_addstring(B, "\\\""); break;
                case '\\': luaL_addstring(B, "\\\\"); break;
                case '\n': luaL_addstring(B, "\\n"); break;
                case '\r': luaL_addstring(B, "\\r"); break;
                case '\t': luaL_addstring(B, "\\t"); break;
                default:
                    if ((unsigned char)*s < 32) {
                        char buf[8];
                        snprintf(buf, sizeof(buf), "\\u%04x", (unsigned char)*s);
                        luaL_addstring(B, buf);
                    } else {
                        luaL_addchar(B, *s);
                    }
            }
            s++;
        }
    }
    luaL_addchar(B, '"');
}

/* 添加TValue到JSON */
static void addTValue(luaL_Buffer *B, TValue *v) {
    if (ttisnil(v)) {
        luaL_addstring(B, "null");
    } else if (ttisboolean(v)) {
        luaL_addstring(B, l_isfalse(v) ? "false" : "true");
    } else if (ttisinteger(v)) {
        char buf[32];
        snprintf(buf, sizeof(buf), "%lld", (long long)ivalue(v));
        luaL_addstring(B, buf);
    } else if (ttisnumber(v)) {
        char buf[64];
        snprintf(buf, sizeof(buf), "%.17g", fltvalue(v));
        luaL_addstring(B, buf);
    } else if (ttisstring(v)) {
        addEscapedString(B, getstr(tsvalue(v)));
    } else {
        luaL_addstring(B, "null");
    }
}

/* 解析指令获取操作码信息 */
static void addInstruction(luaL_Buffer *B, Instruction i, int pc) {
    OpCode op = GET_OPCODE(i);
    char buf[256];
    
    snprintf(buf, sizeof(buf), 
        "{\"pc\":%d,\"op\":%d,\"opname\":\"%s\",\"a\":%d",
        pc, (int)op, opnames[op], GETARG_A(i));
    luaL_addstring(B, buf);
    
    switch (getOpMode(op)) {
        case iABC: {
            int b = GETARG_B(i);
            int c = GETARG_C(i);
            snprintf(buf, sizeof(buf), ",\"b\":%d,\"c\":%d,\"k\":%d", b, c, TESTARG_k(i) ? 1 : 0);
            luaL_addstring(B, buf);
            if (op == OP_ADDI || op == OP_SHRI || op == OP_SHLI) {
                snprintf(buf, sizeof(buf), ",\"sc\":%d", GETARG_sC(i));
                luaL_addstring(B, buf);
            }
            if (op == OP_EQI || op == OP_LTI || op == OP_LEI || op == OP_GTI || op == OP_GEI) {
                snprintf(buf, sizeof(buf), ",\"sb\":%d", GETARG_sB(i));
                luaL_addstring(B, buf);
            }
            break;
        }
        case ivABC: {
            snprintf(buf, sizeof(buf), ",\"vb\":%d,\"vc\":%d,\"k\":%d", 
                GETARG_vB(i), GETARG_vC(i), TESTARG_k(i) ? 1 : 0);
            luaL_addstring(B, buf);
            break;
        }
        case iABx: {
            int bx = GETARG_Bx(i);
            int sbx = bx - OFFSET_sBx;
            snprintf(buf, sizeof(buf), ",\"bx\":%d,\"sbx\":%d", bx, sbx);
            luaL_addstring(B, buf);
            break;
        }
        case iAsBx: {
            int sbx = GETARG_sBx(i);
            int bx = sbx + OFFSET_sBx;
            snprintf(buf, sizeof(buf), ",\"sbx\":%d,\"bx\":%d", sbx, bx);
            luaL_addstring(B, buf);
            break;
        }
        case iAx: {
            snprintf(buf, sizeof(buf), ",\"ax\":%d", GETARG_Ax(i));
            luaL_addstring(B, buf);
            break;
        }
        case isJ: {
            snprintf(buf, sizeof(buf), ",\"sj\":%d", GETARG_sJ(i));
            luaL_addstring(B, buf);
            break;
        }
    }
    luaL_addchar(B, '}');
}

/* 递归收集Proto信息并生成JSON */
static void protoToJson(lua_State *L, luaL_Buffer *B, Proto *p, int indent) {
    char buf[256];
    
    addIndent(B, indent);
    luaL_addstring(B, "{\n");
    
    /* 基本信息 */
    addIndent(B, indent + 1);
    snprintf(buf, sizeof(buf), "\"numparams\":%d,\n", p->numparams);
    luaL_addstring(B, buf);
    
    addIndent(B, indent + 1);
    snprintf(buf, sizeof(buf), "\"is_vararg\":%d,\n", p->is_vararg ? 1 : 0);
    luaL_addstring(B, buf);
    
    addIndent(B, indent + 1);
    snprintf(buf, sizeof(buf), "\"maxstacksize\":%d,\n", p->maxstacksize);
    luaL_addstring(B, buf);
    
    addIndent(B, indent + 1);
    snprintf(buf, sizeof(buf), "\"linedefined\":%d,\n", p->linedefined);
    luaL_addstring(B, buf);
    
    addIndent(B, indent + 1);
    snprintf(buf, sizeof(buf), "\"lastlinedefined\":%d,\n", p->lastlinedefined);
    luaL_addstring(B, buf);
    
    /* 源文件名 */
    addIndent(B, indent + 1);
    luaL_addstring(B, "\"source\":");
    if (p->source) {
        addEscapedString(B, getstr(p->source));
    } else {
        luaL_addstring(B, "null");
    }
    luaL_addstring(B, ",\n");
    
    /* 代码指令 */
    addIndent(B, indent + 1);
    snprintf(buf, sizeof(buf), "\"sizecode\":%d,\n", p->sizecode);
    luaL_addstring(B, buf);
    
    addIndent(B, indent + 1);
    luaL_addstring(B, "\"code\":[\n");
    for (int i = 0; i < p->sizecode; i++) {
        addIndent(B, indent + 2);
        addInstruction(B, p->code[i], i);
        if (i < p->sizecode - 1) luaL_addchar(B, ',');
        luaL_addchar(B, '\n');
    }
    addIndent(B, indent + 1);
    luaL_addstring(B, "],\n");
    
    /* 常量 */
    addIndent(B, indent + 1);
    snprintf(buf, sizeof(buf), "\"sizek\":%d,\n", p->sizek);
    luaL_addstring(B, buf);
    
    addIndent(B, indent + 1);
    luaL_addstring(B, "\"k\":[\n");
    for (int i = 0; i < p->sizek; i++) {
        addIndent(B, indent + 2);
        addTValue(B, &p->k[i]);
        if (i < p->sizek - 1) luaL_addchar(B, ',');
        luaL_addchar(B, '\n');
    }
    addIndent(B, indent + 1);
    luaL_addstring(B, "],\n");
    
    /* Upvalues */
    addIndent(B, indent + 1);
    snprintf(buf, sizeof(buf), "\"sizeupvalues\":%d,\n", p->sizeupvalues);
    luaL_addstring(B, buf);
    
    addIndent(B, indent + 1);
    luaL_addstring(B, "\"upvalues\":[\n");
    for (int i = 0; i < p->sizeupvalues; i++) {
        addIndent(B, indent + 2);
        snprintf(buf, sizeof(buf), "{\"instack\":%d,\"idx\":%d,\"name\":",
            p->upvalues[i].instack ? 1 : 0, p->upvalues[i].idx);
        luaL_addstring(B, buf);
        if (p->upvalues[i].name) {
            addEscapedString(B, getstr(p->upvalues[i].name));
        } else {
            luaL_addstring(B, "null");
        }
        luaL_addstring(B, "}");
        if (i < p->sizeupvalues - 1) luaL_addchar(B, ',');
        luaL_addchar(B, '\n');
    }
    addIndent(B, indent + 1);
    luaL_addstring(B, "],\n");
    
    /* 子函数 */
    addIndent(B, indent + 1);
    snprintf(buf, sizeof(buf), "\"sizep\":%d,\n", p->sizep);
    luaL_addstring(B, buf);
    
    addIndent(B, indent + 1);
    luaL_addstring(B, "\"p\":[\n");
    for (int i = 0; i < p->sizep; i++) {
        protoToJson(L, B, p->p[i], indent + 2);
        if (i < p->sizep - 1) luaL_addchar(B, ',');
        luaL_addchar(B, '\n');
    }
    addIndent(B, indent + 1);
    luaL_addstring(B, "]\n");
    
    addIndent(B, indent);
    luaL_addchar(B, '}');
}

JNIEXPORT jstring JNICALL
Java_com_luajava_LuaState__1parseProto(JNIEnv *env, jobject instance, jlong cptr, jstring code) {
    lua_State *L = getStateFromCPtr(env, cptr);
    
    if (code == NULL) {
        return NULL;
    }
    
    const char *codeStr = (*env)->GetStringUTFChars(env, code, NULL);
    if (codeStr == NULL) {
        return NULL;
    }
    
    /* 编译Lua代码 */
    if (luaL_loadstring(L, codeStr) != LUA_OK) {
        (*env)->ReleaseStringUTFChars(env, code, codeStr);
        const char *err = lua_tostring(L, -1);
        jstring errStr = err ? (*env)->NewStringUTF(env, err) : NULL;
        lua_pop(L, 1);
        return errStr;
    }
    
    (*env)->ReleaseStringUTFChars(env, code, codeStr);
    
    /* 获取Proto */
    const LClosure *cl = (const LClosure *)lua_topointer(L, -1);
    if (!cl || !isLfunction(s2v(L->top.p-1))) {
        lua_pop(L, 1);
        return (*env)->NewStringUTF(env, "{\"error\":\"Not a Lua function\"}");
    }
    
    Proto *p = cl->p;
    
    /* 生成JSON */
    luaL_Buffer B;
    luaL_buffinit(L, &B);
    protoToJson(L, &B, p, 0);
    luaL_pushresult(&B);
    
    const char *json = lua_tostring(L, -1);
    jstring result = json ? (*env)->NewStringUTF(env, json) : NULL;
    
    lua_pop(L, 2); /* 弹出JSON字符串和函数 */
    return result;
}

/************************************************************************
*   JNI Called function
*      注册编译后的函数
************************************************************************/

#define MAX_COMPILED_FUNCS 256

static jobject compiledFuncs[MAX_COMPILED_FUNCS];
static jmethodID compiledFuncExecute = NULL;

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1registerCompiledFunc(JNIEnv *env, jobject instance, jlong cptr, jint funcId, jobject func) {
    if (funcId < 0 || funcId >= MAX_COMPILED_FUNCS) {
        return;
    }
    
    if (compiledFuncs[funcId] != NULL) {
        (*env)->DeleteGlobalRef(env, compiledFuncs[funcId]);
    }
    
    compiledFuncs[funcId] = (*env)->NewGlobalRef(env, func);
    
    if (compiledFuncExecute == NULL) {
        jclass cls = (*env)->GetObjectClass(env, func);
        compiledFuncExecute = (*env)->GetMethodID(env, cls, "execute", "(Lcom/luajava/LuaJava;)I");
    }
}

static int compiledFuncWrapper(lua_State *L) {
    /* 获取 funcId */
    int funcId = (int)(intptr_t)lua_touserdata(L, lua_upvalueindex(1));
    
    if (funcId < 0 || funcId >= MAX_COMPILED_FUNCS || compiledFuncs[funcId] == NULL) {
        luaL_error(L, "Invalid compiled function id: %d", funcId);
        return 0;
    }
    
    /* 获取 JNIEnv */
    JNIEnv *env = checkEnv(L);
    if (env == NULL) {
        luaL_error(L, "Cannot get JNIEnv");
        return 0;
    }
    
    /* 获取 LuaJava 实例 - 从状态索引获取 */
    lua_pushstring(L, LUAJAVASTATEINDEX);
    lua_rawget(L, LUA_REGISTRYINDEX);
    jlong stateIndex = lua_tointeger(L, -1);
    lua_pop(L, 1);
    
    jobject luaJavaInstance = (*env)->CallStaticObjectMethod(env, luajava_api_class,
                                                              get_object_method,
                                                              stateIndex, 0);
    checkError(env, L);
    
    if (luaJavaInstance == NULL) {
        luaL_error(L, "Cannot get LuaJava instance");
        return 0;
    }
    
    /* 调用 execute 方法 */
    jobject func = compiledFuncs[funcId];
    (*env)->CallIntMethod(env, func, compiledFuncExecute, luaJavaInstance);
    checkError(env, L);
    
    (*env)->DeleteLocalRef(env, luaJavaInstance);
    
    return 0; /* 返回值数量由 execute 方法处理 */
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1pushClosure(JNIEnv *env, jobject instance, jlong cptr, jint funcId, jint nUpvalues) {
    lua_State *L = getStateFromCPtr(env, cptr);
    
    if (funcId < 0 || funcId >= MAX_COMPILED_FUNCS || compiledFuncs[funcId] == NULL) {
        luaL_error(L, "Invalid compiled function id: %d", funcId);
        return;
    }
    
    /* 创建闭包并推送到栈上 */
    lua_pushlightuserdata(L, (void*)(intptr_t)funcId);
    lua_pushcclosure(L, compiledFuncWrapper, 1 + nUpvalues);
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1callCompiled(JNIEnv *env, jobject instance, jlong cptr, jint funcId, jint nArgs, jint nResults) {
    lua_State *L = getStateFromCPtr(env, cptr);
    
    if (funcId < 0 || funcId >= MAX_COMPILED_FUNCS || compiledFuncs[funcId] == NULL) {
        luaL_error(L, "Invalid compiled function id: %d", funcId);
        return;
    }
    
    jobject func = compiledFuncs[funcId];
    
    (*env)->CallIntMethod(env, func, compiledFuncExecute, instance);
}

/* 存储Java函数的包装器 */
#define MAX_JAVA_FUNCS 256
static jobject javaFuncObjs[MAX_JAVA_FUNCS];
static jmethodID javaFuncMethods[MAX_JAVA_FUNCS];
static jobject javaFuncLuaJava[MAX_JAVA_FUNCS];
static int javaFuncCount = 0;

static int javaFuncWrapper(lua_State *L) {
    /* 获取函数索引 */
    int idx = (int)(intptr_t)lua_touserdata(L, lua_upvalueindex(1));
    
    if (idx < 0 || idx >= MAX_JAVA_FUNCS || javaFuncObjs[idx] == NULL) {
        luaL_error(L, "Invalid Java function index: %d", idx);
        return 0;
    }
    
    JNIEnv *env = checkEnv(L);
    if (env == NULL) {
        luaL_error(L, "Cannot get JNIEnv");
        return 0;
    }
    
    /* 直接使用存储的 LuaJava 实例 */
    jobject luaJavaInstance = javaFuncLuaJava[idx];
    
    if (luaJavaInstance == NULL) {
        luaL_error(L, "Cannot get LuaJava instance");
        return 0;
    }
    
    /* 调用 Java 方法并返回返回值数量 */
    jint nRet = (*env)->CallIntMethod(env, javaFuncObjs[idx], javaFuncMethods[idx], luaJavaInstance);
    
    return (int)nRet;
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1pushJavaFunction(JNIEnv *env, jobject instance, jlong cptr, jobject obj, jstring methodName, jint nUpvalues) {
    lua_State *L = getStateFromCPtr(env, cptr);
    
    const char *methodStr = (*env)->GetStringUTFChars(env, methodName, NULL);
    
    /* 查找方法 */
    jclass cls = (*env)->GetObjectClass(env, obj);
    jmethodID method = (*env)->GetMethodID(env, cls, methodStr, "(Lcom/luajava/LuaJava;)I");
    
    (*env)->ReleaseStringUTFChars(env, methodName, methodStr);
    
    if (method == NULL) {
        luaL_error(L, "Cannot find method: %s", methodStr);
        return;
    }
    
    /* 存储函数引用 */
    int idx = javaFuncCount++;
    if (idx >= MAX_JAVA_FUNCS) {
        luaL_error(L, "Too many Java functions");
        return;
    }
    
    javaFuncObjs[idx] = (*env)->NewGlobalRef(env, obj);
    javaFuncMethods[idx] = method;
    javaFuncLuaJava[idx] = (*env)->NewGlobalRef(env, instance);
    
    /* 创建闭包 */
    lua_pushlightuserdata(L, (void*)(intptr_t)idx);
    lua_pushcclosure(L, javaFuncWrapper, 1 + nUpvalues);
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1len(JNIEnv *env, jobject jobj, jlong cptr, jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_len(L, idx);
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1concat(JNIEnv *env, jobject jobj, jlong cptr, jint n) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_concat(L, n);
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1arith(JNIEnv *env, jobject jobj, jlong cptr, jint op) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_arith(L, op);
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1toClose(JNIEnv *env, jobject jobj, jlong cptr, jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    /* 标记为需要关闭的 to-be-closed 变量 */
    lua_toclose(L, idx);
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1closeSlot(JNIEnv *env, jobject jobj, jlong cptr, jint idx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_closeslot(L, idx);
}

JNIEXPORT jint JNICALL
Java_com_luajava_LuaJava__1tccIn(JNIEnv *env, jobject jobj, jlong cptr, jint valIdx, jint containerIdx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_pushvalue(L, containerIdx);
    lua_pushvalue(L, valIdx);
    lua_gettable(L, -2);
    int result = lua_toboolean(L, -1) ? 1 : 0;
    lua_pop(L, 2);
    return result;
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1pushCClosure(JNIEnv *env, jobject jobj, jlong cptr, jlong func, jint nUpvalues) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_pushcclosure(L, (lua_CFunction)(intptr_t)func, nUpvalues);
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1setFEnv(JNIEnv *env, jobject jobj, jlong cptr, jint funcIdx, jint envIdx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_pushvalue(L, envIdx);
    lua_setupvalue(L, funcIdx, 1);
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1getFEnv(JNIEnv *env, jobject jobj, jlong cptr, jint funcIdx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_getupvalue(L, funcIdx, 1);
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1setUpvalue(JNIEnv *env, jobject jobj, jlong cptr, jint funcIdx, jint upvalIdx, jint valIdx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_pushvalue(L, valIdx);
    lua_setupvalue(L, funcIdx, upvalIdx);
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1getUpvalue(JNIEnv *env, jobject jobj, jlong cptr, jint funcIdx, jint upvalIdx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_getupvalue(L, funcIdx, upvalIdx);
}

JNIEXPORT jint JNICALL
Java_com_luajava_LuaJava__1spaceship(JNIEnv *env, jobject jobj, jlong cptr, jint idx1, jint idx2) {
    lua_State *L = getStateFromCPtr(env, cptr);
    int cmp = lua_compare(L, idx1, idx2, LUA_OPLT);
    if (cmp) return -1;
    cmp = lua_compare(L, idx1, idx2, LUA_OPEQ);
    if (cmp) return 0;
    return 1;
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1errNotNil(JNIEnv *env, jobject jobj, jlong cptr, jint valIdx, jstring name) {
    lua_State *L = getStateFromCPtr(env, cptr);
    if (lua_isnil(L, valIdx)) {
        const char *nameStr = (*env)->GetStringUTFChars(env, name, NULL);
        luaL_error(L, "bad argument #%s (value expected)", nameStr);
        (*env)->ReleaseStringUTFChars(env, name, nameStr);
    }
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1tccPrologue(JNIEnv *env, jobject jobj, jlong cptr, jint nparams, jint maxstack) {
    lua_State *L = getStateFromCPtr(env, cptr);
    lua_settop(L, maxstack);
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1tccGettabup(JNIEnv *env, jobject jobj, jlong cptr, jint upval, jstring k, jint dest) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *key = (*env)->GetStringUTFChars(env, k, NULL);
    lua_rawgeti(L, LUA_REGISTRYINDEX, upval);
    lua_getfield(L, -1, key);
    lua_replace(L, dest);
    lua_pop(L, 1);
    (*env)->ReleaseStringUTFChars(env, k, key);
}

JNIEXPORT void JNICALL
Java_com_luajava_LuaJava__1tccSettabup(JNIEnv *env, jobject jobj, jlong cptr, jint upval, jstring k, jint valIdx) {
    lua_State *L = getStateFromCPtr(env, cptr);
    const char *key = (*env)->GetStringUTFChars(env, k, NULL);
    lua_rawgeti(L, LUA_REGISTRYINDEX, upval);
    lua_pushvalue(L, valIdx);
    lua_setfield(L, -2, key);
    lua_pop(L, 1);
    (*env)->ReleaseStringUTFChars(env, k, key);
}
