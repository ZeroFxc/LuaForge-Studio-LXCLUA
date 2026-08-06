# Lua VM 与 Android 集成文档

## 1. 集成架构概览

LuaForge Studio 的 Android 应用通过 JNI (Java Native Interface) 与 Lua VM 核心进行交互。整个集成层负责：

- Lua 虚拟机的生命周期管理
- Java 与 Lua 之间的类型转换
- 双向方法调用（Java ↔ Lua）
- 异常处理与传播
- 脚本执行与结果返回

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android 应用 (Kotlin)                    │
│                                                                 │
│    val result = luaExecutor.execute("print('Hello')")           │
│                          │                                      │
├──────────────────────────┼──────────────────────────────────────┤
│                        JNI 层 (C)                               │
│                          │                                      │
│    ┌─────────────────────▼───────────────────────────────────┐  │
│    │              LXCLuaCore (luajava)                        │  │
│    │                                                         │  │
│    │  Java 类型转换 → Lua 栈操作 → lua_pcall → 返回结果      │  │
│    └─────────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                        Lua VM 核心 (C23)                        │
│                                                                 │
│    llex → lparser → lcodegen → lasm → lvm 执行 → 返回结果       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. JNI 桥接层详解

### 2.1 源文件结构

**目录**: `app/src/main/jni/luajava/`

| 文件 | 功能 |
|------|------|
| `LXCLuaCore.c` | JNI 主实现，所有 Java→Lua 的桥接函数 |
| `luajava.h` | 头文件，定义导出函数和常量 |

### 2.2 核心数据结构

```c
/* Lua 状态管理 */
static lua_State *main_L = NULL;        /* 主 Lua 状态 */

/* JNI 方法缓存 */
static jmethodID object_index_method;      /* 对象属性访问 */
static jmethodID call_method;              /* 方法调用 */
static jmethodID java_function_method;     /* Java 函数执行 */
/* ... 更多方法引用缓存 ... */
```

### 2.3 主要 JNI 函数

| 功能分类 | JNI 函数 | 说明 |
|----------|----------|------|
| 初始化/关闭 | `Java_*_luaOpen` | 创建 Lua VM 实例 |
| | `Java_*_luaClose` | 销毁 Lua VM 实例 |
| 脚本执行 | `Java_*_luaLoadString` | 执行 Lua 代码字符串 |
| | `Java_*_luaLoadFile` | 执行 Lua 文件 |
| 函数调用 | `Java_*_luaCallFunction` | 调用 Lua 全局函数 |
| 变量访问 | `Java_*_luaSetGlobal` | 设置 Lua 全局变量 |
| | `Java_*_luaGetGlobal` | 获取 Lua 全局变量 |
| 类型注册 | `Java_*_luaRegisterJavaMethod` | 注册 Java 方法到 Lua |
| | `Java_*_luaBindClass` | 绑定 Java 类到 Lua |

### 2.4 类型映射规则

```
┌─────────────────────────────────────────────────────────────────┐
│                     Java ↔ Lua 类型映射                          │
├──────────────────┬──────────────────────────────────────────────┤
│   Java 类型      │              Lua 类型                       │
├──────────────────┼──────────────────────────────────────────────┤
│  boolean         │  boolean                                     │
│  byte/short/int  │  number (integer)                            │
│  long            │  number (integer, 需处理 64 位)              │
│  float/double    │  number (float)                              │
│  String          │  string                                      │
│  byte[]          │  string (binary) / userdata                  │
│  Object[]        │  table (array)                               │
│  Map<K,V>        │  table (dictionary)                          │
│  List<E>         │  table (array)                               │
│  自定义类         │  userdata (通过 JNI 访问)                    │
│  函数/接口        │  function (通过 luajava 闭包)                 │
│  null            │  nil                                         │
└──────────────────┴──────────────────────────────────────────────┘
```

---

## 3. 初始化流程

### 3.1 Lua VM 启动序列

```
Android 应用启动
    │
    ▼
MainActivity.onCreate()
    │
    ▼
System.loadLibrary("luajava")
    │
    ▼
JNI_OnLoad() (如有定义)
    │
    ▼
Java_com_luaforge_studio_lxclua_LuaForgeNative_luaOpen()
    │
    ├── luaL_newstate()          // 创建新 Lua 状态
    ├── luaL_openlibs()           // 加载标准库
    ├── 加载扩展库                // crypto, http, thread, etc.
    ├── 注册 JNI 桥接函数          // Java 方法注册
    ├── 注册自定义模块             // luajava 模块
    │
    ▼
lua_State* ready
    │
    ▼
返回给 Java 层存储
```

### 3.2 初始化代码示例

**Java/Kotlin 侧**:

```kotlin
class LuaForge private constructor() {
    
    companion object {
        init {
            System.loadLibrary("luajava")
        }
        
        @JvmStatic
        fun init(context: Context) {
            nativeInit(context)
        }
        
        @JvmStatic
        private external fun nativeInit(context: Context)
    }
    
    fun executeScript(code: String): String {
        return nativeExecute(code)
    }
    
    private external fun nativeExecute(code: String): String
}
```

**JNI C 侧**:

```c
JNIEXPORT void JNICALL 
Java_com_luaforge_studio_lxclua_LuaForgeNative_nativeInit(
    JNIEnv *env, jobject thiz, jobject context) 
{
    // 创建 Lua VM
    main_L = luaL_newstate();
    if (main_L == NULL) {
        // 抛出 Java 异常
        return;
    }
    
    // 加载标准库
    luaL_openlibs(main_L);
    
    // 加载扩展库
    loadExtensionLibraries(main_L);
    
    // 注册 Java 桥接
    registerJavaBridgeFunctions(main_L, env, context);
    
    // 设置 Android 日志
    registerAndroidLog(main_L);
}
```

---

## 4. 脚本执行流程

### 4.1 字符串执行

```kotlin
// Kotlin
val result = luaExecute("""
    local http = require("http")
    local resp = http.get("https://api.example.com/data")
    return resp.body
""")
```

**内部流程**:

```
luaExecute(codeString)
    │
    ▼
JNI: Java_..._nativeExecute(code)
    │
    ├── luaL_loadstring(L, code)     // 编译代码
    │   ├── llex: 词法分析
    │   ├── lparser: 语法分析
    │   └── lcode+lasm: 代码生成
    │
    ├── lua_pcall(L, 0, 1, 0)       // 执行
    │   └── lvm: 字节码解释执行
    │
    ├── lua_tostring(L, -1)         // 获取返回值
    │
    ▼
return resultString
```

### 4.2 文件执行

```kotlin
fun executeFile(path: String): String {
    val code = File(path).readText()
    return luaExecute(code)
}
```

---

## 5. Java ↔ Lua 双向调用

### 5.1 Lua 调用 Java

**注册 Java 方法**:

```kotlin
// Kotlin
luaForge.registerMethod("showToast", activity) { context, msg ->
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
```

**Lua 中调用**:

```lua
-- 调用注册的函数
showToast("Hello from Lua!")

-- 通过 luajava 包获取 Java 类
local System = bindClass("java.lang.System")
print(System:currentTimeMillis())

-- 创建 Java 对象
ArrayList = bindClass("java.util.ArrayList")
local list = ArrayList()
list:add("item1")
print(list:size())  -- 输出 1
```

### 5.2 Java 调用 Lua

```kotlin
kotlin
// 调用 Lua 全局函数
luaForge.callFunction("onCreate", mapOf("width" to 1080, "height" to 1920))

// 获取 Lua 全局变量
val version = luaForge.getGlobalVariable("APP_VERSION") as? String
```

```lua
-- Lua 端定义
APP_VERSION = "1.3.5"

function onCreate(screenInfo)
    print("Screen: " .. screenInfo.width .. "x" .. screenInfo.height)
end
```

### 5.3 回调机制

**从 Lua 到 Java 的异步回调**:

```kotlin
// 注册回调
luaForge.registerCallback("onProgress") { progress ->
    // 在 UI 线程处理
    withContext(Dispatchers.Main) {
        progressBar.progress = progress
    }
}
```

```lua
-- Lua 中触发回调
function downloadFile(url, callback)
    for i = 0, 100, 10 do
        -- 模拟下载进度
        callback(i)
        -- 可能需要 yield 让出执行权
    end
end
```

---

## 6. LuaCompose UI 集成

### 6.1 架构概览

LuaCompose 是一种声明式 UI 框架，允许使用 Lua 脚本定义 Compose UI：

```
LuaCompose 脚本
    │
    ▼
Compose 解析器
    │
    ▼
Compose Modifier 链
    │
    ▼
Modifier.padding(16.dp)
    .fillMaxWidth()
    .clickable { ... }
    │
    ▼
Compose 渲染引擎
```

### 6.2 LuaCompose 脚本示例

```lua
local Compose = require("compose")

return Compose.Column {
    modifier = Modifier.padding(16),
    
    children = {
        Compose.Text {
            text = "Hello, LuaCompose!",
            style = TextStyle(
                fontSize = 24.sp,
                color = Color.Blue
            )
        },
        
        Compose.Button {
            text = "Click Me",
            onClick = function()
                showToast("Button clicked!")
            end
        },
        
        Compose.LazyColumn {
            items = {"Item 1", "Item 2", "Item 3"},
            item = function(item)
                return Compose.Text {
                    text = item,
                    modifier = Modifier.padding(8).fillMaxWidth()
                }
            end
        }
    }
}
```

### 6.3 Kotlin Compose 桥接实现

```kotlin
// LuaComposeBridge.kt

class LuaComposeBridge {
    
    @Composable
    fun RenderLuaTree(luaTable: LuaTable) {
        val type = luaTable.get("type") as? String
        
        when (type) {
            "Column" -> Column {
                RenderChildren(luaTable)
            }
            "Text" -> {
                val text = luaTable.get("text") as String
                val color = parseColor(luaTable.get("color"))
                Text(text = text, color = color)
            }
            "Button" -> {
                val text = luaTable.get("text") as String
                val onClick = luaTable.get("onClick") as LuaFunction
                
                Button(onClick = { onClick.invoke() }) {
                    Text(text)
                }
            }
            // ... 更多组件
        }
    }
    
    @Composable
    private fun ColumnScope.RenderChildren(luaTable: LuaTable) {
        val children = luaTable.get("children") as? LuaTable ?: return
        for (i in 1..children.length()) {
            val child = children.get(i) as LuaTable
            RenderLuaTree(child)
        }
    }
}
```

---

## 7. 异常处理

### 7.1 Lua 异常 → Java 异常

```c
// JNI 层异常转换

int executeLuaCode(lua_State *L, const char *code) {
    int status = luaL_loadstring(L, code) || lua_pcall(L, 0, LUA_MULTRET, 0);
    
    if (status != LUA_OK) {
        const char *errorMsg = lua_tostring(L, -1);
        
        // 销毁错误消息
        lua_pop(L, 1);
        
        // 抛出 Java 异常
        jclass exceptionClass = (*env)->FindClass(env, "com/luaforge/studio/lxclua/exception/LuaException");
        (*env)->ThrowNew(env, exceptionClass, errorMsg);
        
        return -1;
    }
    
    return 0;
}
```

### 7.2 Kotlin 异常处理

```kotlin
try {
    luaExecutor.execute(code)
} catch (e: LuaException) {
    // Lua 运行时错误
    val errorDialog = AlertDialog.Builder(context)
        .setTitle("Lua 执行错误")
        .setMessage(e.message)
        .setPositiveButton("确定", null)
        .create()
    errorDialog.show()
} catch (e: LuaSyntaxException) {
    // Lua 语法错误
    val line = e.lineNumber
    highlightErrorLine(line, e.message)
} catch (e: JNIException) {
    // JNI 调用失败
    Log.e("LuaVM", "JNI error", e)
}
```

---

## 8. 内存管理

### 8.1 引用管理

```kotlin
// LuaReferenceManager.kt - 管理 Lua 对象在 Java 端的引用

class LuaReferenceManager {
    private val luaRefs = mutableMapOf<String, Long>()
    
    /**
     * 获取 Lua 对象的长期引用
     * 防止被 GC 回收
     */
    fun retain(key: String, luaRef: Int) {
        luaRefs[key] = nativeRetainGlobalRef(luaRef)
    }
    
    /**
     * 释放 Lua 对象引用
     */
    fun release(key: String) {
        luaRefs.remove(key)?.let { nativeReleaseGlobalRef(it) }
    }
    
    /**
     * 调用之前保留的 Lua 函数
     */
    fun call(key: String, vararg args: Any?): Any? {
        val ref = luaRefs[key] ?: return null
        return nativeCallRef(ref, args)
    }
    
    private external fun nativeRetainGlobalRef(localRef: Int): Long
    private external fun nativeReleaseGlobalRef(globalRef: Long)
    private external fun nativeCallRef(ref: Long, args: Array<out Any?>): Any?
}
```

### 8.2 GC 交互

```kotlin
// 手动触发 Lua GC
luaExecutor.gc(LuaGC.COLLECT, 0)

// 获取内存使用信息
val memoryKB = luaExecutor.gc(LuaGC.COUNT, 0)  // KB
val memoryBytes = memoryKB * 1024
```

---

## 9. 线程安全

### 9.1 Lua VM 线程模型

Lua VM **不是线程安全的**。LuaForge Studio 采用以下策略管理并发：

```
┌─────────────────────────────────────────────────────────────────┐
│                    主线程 (UI Thread)                            │
│                                                                 │
│   Lua 代码执行短任务  (快速返回)                                 │
│   UI 渲染和交互                                                  │
├─────────────────────────────────────────────────────────────────┤
│                    Lua 执行线程池                                 │
│                                                                 │
│   长任务在后台线程执行                                            │
│   每个线程独立的 lua_State                                       │
│   结果通过 Handler/协程返回主线程                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 9.2 多线程执行示例

```kotlin
class LuaExecutor {
    
    private val luaDispatcher = Dispatchers.IO.limitedParallelism(4)
    
    suspend fun executeAsync(code: String): String = withContext(luaDispatcher) {
        execute(code)  // 在 IO 线程执行 Lua 代码
    }
    
    fun executeBlocking(code: String): String {
        // 创建独立 Lua 状态用于此线程
        val threadLua = luaNewThread()
        try {
            return threadLua.execute(code)
        } finally {
            threadLua.close()
        }
    }
}
```

---

## 10. 调试支持

### 10.1 Android 日志集成

```lua
-- Lua 代码中使用 Android 日志
local log = require("logtable")
log.d("TAG", "Debug message")
log.i("TAG", "Info message")
log.w("TAG", "Warning message")
log.e("TAG", "Error message")
```

**C 层实现**:

```c
// 注册 Android 日志函数
static int lua_android_log(lua_State *L) {
    const char *tag = luaL_checkstring(L, 1);
    const char *msg = luaL_checkstring(L, 2);
    int priority = luaL_checkinteger(L, 3);
    
    __android_log_print(priority, tag, "%s", msg);
    return 0;
}
```

### 10.2 远程调试

```kotlin
// 启动 Lua 调试服务器
class LuaDebugger {
    fun start() {
        luaExecutor.execute([[
            local dbg = require("debugger")
            dbg.listen("0.0.0.0", 9168)
        ]])
    }
    
    fun stop() {
        luaExecutor.execute('require("debugger").stop()')
    }
}
```

---

## 11. 性能优化建议

### 11.1 JNI 调用优化

| 策略 | 说明 |
|------|------|
| 批量操作 | 减少 JNI 调用次数，合并多次操作为一次 |
| 方法 ID 缓存 | 在 `JNI_OnLoad` 缓存所有 `jmethodID`/`jfieldID` |
| 局部引用管理 | 及时调用 `DeleteLocalRef` 释放局部引用 |
| 直接缓冲区 | 大数据传输使用 `GetDirectBufferAddress` |

### 11.2 Lua 代码优化

| 策略 | 说明 |
|------|------|
| 减少全局变量 | 使用 `local` 声明局部变量 |
| 预编译 | 使用 `luac` 预编译脚本为字节码 |
| JIT 启用 | 在支持的平台上启用 JIT (`jit.on()`) |
| 模块缓存 | 避免重复 `require()` 同一模块 |

---

## 12. 故障排除

### 12.1 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| `UnsatisfiedLinkError` | 架构不匹配或库未打包 | 检查 `abiFilters` 和 APK 内容 |
| Lua 执行无响应 | 死循环或无限递归 | 设置执行超时 |
| 内存泄漏 | Lua 对象未释放 | 检查引用管理 |
| JNI 崩溃 (`SIGSEGV`) | 直接使用野指针 | 检查 JNI 引用有效性 |

### 12.2 诊断日志

```lua
-- 启用调试模式
require("vmprotect").debug(true)

-- 设置日志级别
local log = require("logtable")
log.level = log.LEVEL_DEBUG

-- 追踪 Lua 调用
debug.sethook(function(event)
    local info = debug.getinfo(2, "nS")
    log.d("TRACE", string.format("%s %s:%d", event, info.name or "?", info.linedefined or 0))
end, "crl")
```

---

## 13. 最佳实践总结

1. **生命周期管理**: 在 `Activity.onDestroy()` 中释放 Lua 引用
2. **错误处理**: 所有 Lua 调用都应该有异常处理
3. **线程分离**: 长任务不在主线程的 Lua 状态执行
4. **类型安全**: 明确 Java/Lua 类型映射，避免隐式转换
5. **资源释放**: Lua 对象不再使用时及时释放引用
6. **日志记录**: 关键路径添加调试日志便于排查问题
7. **测试覆盖**: JNI 桥接函数需要充分的单元测试和集成测试
