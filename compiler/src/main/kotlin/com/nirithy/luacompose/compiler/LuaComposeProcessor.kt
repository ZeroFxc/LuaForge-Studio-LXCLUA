@file:OptIn(com.google.devtools.ksp.KspExperimental::class)

package com.nirithy.luacompose.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * Nirithy Compose 编译期组件桥接生成器。
 *
 * 扫描 @LuaComposeScan 标记的包内所有 @Composable 函数，
 * 为每个函数生成运行时 Method 调用桥。
 *
 * Method 解析策略：通过 composerIndex 检测
 * （parameterTypes.indexOfFirst { it.name == "Composer" }）定位 Composer 参数位置，
 * 结合 firstParamName 去重，避免依赖不稳定的 parameterCount。
 */
class LuaComposeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private val classPathToComponent = mutableMapOf<String, String>()
    private var registryGenerated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val scanSymbols = resolver.getSymbolsWithAnnotation(
            "com.nirithy.luacompose.annotations.LuaComposeScan"
        )
        for (symbol in scanSymbols) {
            if (symbol !is KSClassDeclaration) continue
            for (annotation in symbol.annotations) {
                if (annotation.shortName.asString() != "LuaComposeScan") continue
                val pkg = annotation.arguments
                    .find { it.name?.asString() == "packageName" }?.value as? String
                    ?: continue
                logger.warn("[NirithyKSP] $pkg")
                processPackage(resolver, pkg)
            }
        }
        if (classPathToComponent.isNotEmpty() && !registryGenerated) {
            generateRegistry()
            registryGenerated = true
        }
        return emptyList()
    }

    private fun processPackage(resolver: Resolver, packageName: String) {
        val composables = resolver.getDeclarationsFromPackage(packageName)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.isComposable() && it.hasPublicModifier() }
            .toList()
        if (composables.isEmpty()) return
        logger.warn("[NirithyKSP] $packageName → ${composables.size} 个函数")
        composables.groupBy { it.simpleName.asString() }.forEach { (name, overloads) ->
            generateBridge(resolver, packageName, name, overloads)
        }
    }

    private fun generateBridge(
        resolver: Resolver,
        packageName: String,
        funcName: String,
        overloads: List<KSFunctionDeclaration>
    ) {
        val safePkg = packageName.replace(".", "_").replace("-", "_")
        val className = "${safePkg}_${funcName}"
        val classPath = "$packageName.$funcName"
        val sorted = overloads.sortedByDescending { it.parameters.count { p -> !p.hasDefault } }

        val fileSpec = FileSpec.builder("com.nirithy.luacompose.generated", className)
            .addType(
                TypeSpec.objectBuilder(className)
                    .addSuperinterface(ClassName("com.nirithy.luacompose.render", "BridgeRenderer"))
                    .addFunction(generateRenderMethod(resolver, packageName, funcName, sorted))
                    .build()
            )
            .build()
        fileSpec.writeTo(codeGenerator, Dependencies(true))
        classPathToComponent[classPath] = className
    }

    /**
     * 生成 render(node: ComposeNode) 方法。
     * 使用 functionCache 运行时懒加载 Method，
     * 通过 composerIndex + firstParamName 精确匹配重载。
     */
    private fun generateRenderMethod(
        resolver: Resolver,
        packageName: String,
        funcName: String,
        overloads: List<KSFunctionDeclaration>
    ): FunSpec {
        val composeNodeClass = ClassName("com.nirithy.luacompose.node", "ComposeNode")

        return FunSpec.builder("render")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("node", composeNodeClass)
            .addAnnotation(ClassName("androidx.compose.runtime", "Composable"))
            .apply {
                addStatement("val children = node.children")
                addStatement("val childrenFunc = node.childrenFunc")
                addStatement("val props = node.props")
                addStatement("val functionCache = mutableMapOf<String, %T>()",
                    ClassName("java.lang.reflect", "Method"))

                for ((idx, func) in overloads.withIndex()) {
                    val fullClassName = ownerClassName(resolver, func, packageName, funcName)
                    val numParams = func.parameters.size

                    // firstParamName 用于重载去重：同名函数通过第一个参数类型区分
                    val firstParamName = func.parameters.firstOrNull()
                        ?.type?.resolve()?.declaration?.simpleName?.asString()
                    val firstParamCheck = if (firstParamName != null) {
                        "it.parameterTypes.getOrNull(0)?.simpleName == \"$firstParamName\""
                    } else { "true" }

                    // 条件匹配：所有必填参数都存在才命中此重载
                    val requiredParams = func.parameters.filter { !it.hasDefault }
                    val conditions = requiredParams.map { p ->
                        "props.containsKey(\"${p.name?.asString()}\")"
                    }
                    val hasContent = func.parameters.any {
                        it.name?.asString() == "content" && !it.hasDefault
                    }
                    val conditionsList = conditions.toMutableList()
                    if (hasContent) {
                        conditionsList.add("(children.isNotEmpty() || childrenFunc != null)")
                    }
                    val condition = if (conditionsList.isEmpty()) "true"
                    else conditionsList.joinToString(" && ")

                    if (idx == 0) beginControlFlow("if (%L)", condition)
                    else beginControlFlow("else if (%L)", condition)

                    // ★ 核心：通过 composerIndex + firstParamName 精确匹配 Method
                    addStatement(
                        "val m = functionCache.getOrPut(%S) {\n" +
                        "    java.lang.Class.forName(%S).declaredMethods.first {\n" +
                        "        (it.name == %S || it.name.startsWith(%S) || it.name.startsWith(%S)) &&\n" +
                        "        it.parameterTypes.indexOfFirst { pt -> pt.name == %S } == %L &&\n" +
                        "        %L\n" +
                        "    }\n" +
                        "}",
                        "$funcName-$idx",
                        fullClassName,
                        funcName, "$funcName-", "${funcName}_",
                        "androidx.compose.runtime.Composer", numParams,
                        firstParamCheck
                    )

                    // 构建 args 数组
                    addStatement("val args = arrayOfNulls<Any?>(m.parameterTypes.size)")
                    addStatement(
                        "val composerIndex = m.parameterTypes.indexOfFirst { it.name == %S }",
                        "androidx.compose.runtime.Composer"
                    )
                    addStatement("args[composerIndex] = %M",
                        MemberName("androidx.compose.runtime", "currentComposer"))

                    // 默认值位掩码
                    addStatement("val defaultBitmasks = IntArray(10)")
                    addStatement("val realParams = %L", numParams)

                    // 填充 Kotlin 参数
                    for ((pi, param) in func.parameters.withIndex()) {
                        val pName = param.name?.asString() ?: "p$pi"
                        val safeName = sanitize(pName)
                        addStatement("val _%L = props[%S]", safeName, pName)

                        when {
                            pName == "modifier" ->
                                addStatement("args[%L] = (_%L as? %T)?.build()",
                                    pi, safeName,
                                    ClassName("com.nirithy.luacompose.modifier", "ModifierChain"))
                            pName == "content" || pName == "children" ->
                                addStatement("args[%L] = @%T { %T.RenderChildren(node) }",
                                    pi,
                                    ClassName("androidx.compose.runtime", "Composable"),
                                    ClassName("com.nirithy.luacompose.render", "ComposeRenderer"))
                            param.type.resolve().isFunctionType ->
                                addStatement("args[%L] = node.callbacks[%S]?.let { cb -> { v: kotlin.Any? -> cb.call(v) } }",
                                    pi, pName)
                            param.hasDefault ->
                                // 有默认值的参数：props 中有则用，否则标记到 bitmask
                                addStatement(
                                    "if (props.containsKey(%S)) { args[%L] = _%L } else { val di = %L / 31; defaultBitmasks[di] = defaultBitmasks[di] or (1 shl (%L %% 31)) }",
                                    pName, pi, safeName, pi, pi)
                            else ->
                                addStatement("args[%L] = _%L", pi, safeName)
                        }
                    }

                    // 处理默认值位掩码和 changed 参数：填充 Composer 后面的 Int 参数
                    addCode("""
                        |val defaultParamsCount = kotlin.math.ceil(realParams / 31.0).toInt()
                        |val intIndices = mutableListOf<Int>()
                        |for (i in realParams until m.parameterTypes.size) {
                        |    if (i != composerIndex && m.parameterTypes[i] == Int::class.javaPrimitiveType) {
                        |        intIndices.add(i)
                        |    }
                        |}
                        |val actualDefaultCount = kotlin.math.min(defaultParamsCount, intIndices.size)
                        |val defaultIndices = intIndices.takeLast(actualDefaultCount)
                        |val changedIndices = intIndices.dropLast(actualDefaultCount)
                        |for (idx in changedIndices) { args[idx] = 0 }
                        |for ((i, idx) in defaultIndices.withIndex()) { args[idx] = defaultBitmasks[i] }
                        |
                    """.trimMargin())

                    addStatement("m.isAccessible = true")
                    addStatement("try { m.invoke(null, *args) } catch (e: Exception) { android.util.Log.e(%S, %S + e.message, e) }",
                        "NirithyCompose", "调用 $funcName 失败: ")
                    endControlFlow()
                }
                addStatement(
                    "else { android.util.Log.w(%S, %S + node.type + %S + %L) }",
                    "NirithyCompose", "组件未匹配: ", " 重载: ", overloads.size
                )
            }
            .build()
    }

    private fun generateRegistry() {
        val registryClass = ClassName("com.nirithy.luacompose.render", "ComponentRegistry")
        val type = TypeSpec.objectBuilder("GeneratedComponentRegistry")
            .addFunction(
                FunSpec.builder("registerAll").apply {
                    for ((classPath, className) in classPathToComponent.toSortedMap()) {
                        addStatement("%T.register(%S, %T::render)",
                            registryClass, classPath,
                            ClassName("com.nirithy.luacompose.generated", className))
                    }
                }.build()
            ).build()
        FileSpec.builder("com.nirithy.luacompose.generated", "GeneratedComponentRegistry")
            .addType(type).build()
            .writeTo(codeGenerator, Dependencies(true))
    }

    /** 获取 @Composable 函数在 JVM 中的所属类名 */
    private fun ownerClassName(
        resolver: Resolver, func: KSFunctionDeclaration, packageName: String, funcName: String
    ): String {
        resolver.getOwnerJvmClassName(func)?.let { return it.replace('/', '.') }
        val fileName = func.containingFile?.fileName ?: "$funcName.kt"
        return if (fileName.endsWith(".kt")) "$packageName.${fileName.removeSuffix(".kt")}Kt"
        else "$packageName.$funcName"
    }

    private fun KSFunctionDeclaration.isComposable(): Boolean =
        annotations.any { it.shortName.asString() == "Composable" }

    private fun KSFunctionDeclaration.hasPublicModifier(): Boolean =
        Modifier.PUBLIC in modifiers

    /** 变量名安全化：替换特殊字符 */
    private fun sanitize(name: String): String =
        name.replace("-", "_").replace(".", "_")
}