#ifndef luajava_h
#define luajava_h


#include <jni.h>
#include <stdio.h>
#include <stdlib.h>

#include "src/core/lua.h"
#include "src/core/lualib.h"
#include "src/core/lauxlib.h"
typedef struct {
    //const char* name;
    int type;
    int index;
} java_object;

 JNIEnv *checkEnv(lua_State *L);

 void pushJNIEnv(JNIEnv *env, lua_State *L);

 jlong checkIndex(lua_State *L);

 java_object *checkJavaObject(lua_State *L, int idx);

 jobject *toJavaObject(lua_State *L, int idx);

 void checkError(JNIEnv *javaEnv, lua_State *L);

 JNIEnv *getEnvFromState(lua_State * L);

/**
 * 检查 Lua 栈上指定索引处的值是否为 Java 对象
 * 使用 static inline 确保每个编译单元都自带实现，
 * 避免静态库链接时出现 undefined symbol
 * @param L Lua 状态机指针
 * @param idx 栈索引
 * @return 1 表示是 Java 对象，0 表示不是
 */
#define LUAJAVAOBJECTIND "__IsJavaObject"
static inline int isJavaObject(lua_State *L, int idx) {
    if (!lua_isuserdata(L, idx))
        return 0;
    if (lua_getmetatable(L, idx) == 0)
        return 0;
    lua_pushstring(L, LUAJAVAOBJECTIND);
    lua_rawget(L, -2);
    if (lua_isnil(L, -1)) {
        lua_pop(L, 2);
        return 0;
    }
    lua_pop(L, 2);
    return 1;
}

 int pushJavaObject(lua_State *L, const char *name,int idx,int isclass);

#endif
