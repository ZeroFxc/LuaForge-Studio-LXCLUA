package com.kulipai.luacompose.compiler

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.KspExperimental
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.writeTo

class LuaComposeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    private val generatedPluginClassNames = linkedSetOf<String>()
    private var registryGenerated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val packageSymbols = resolver.getSymbolsWithAnnotation("com.kulipai.luacompose.annotations.LuaBridgePackage")
        
        for (symbol in packageSymbols) {
            if (symbol !is KSClassDeclaration) continue
            
            val bridgeAnnotations = symbol.annotations.filter { 
                it.shortName.asString() == "LuaBridgePackage" 
            }
            
            for (annotation in bridgeAnnotations) {
                val packageName = annotation.arguments.find { it.name?.asString() == "packageName" }?.value as? String ?: continue
                val category = annotation.arguments.find { it.name?.asString() == "category" }?.value as? String ?: "ui"
                
                logger.warn("Processing LuaBridgePackage for: $packageName, category: $category")
                
                generateBridgeForPackage(resolver, packageName, category)
            }
        }

        val bridgeClassSymbols = resolver.getSymbolsWithAnnotation("com.kulipai.luacompose.annotations.LuaBridgeClass")
        for (symbol in bridgeClassSymbols) {
            if (symbol !is KSClassDeclaration) continue
            val annotations = symbol.annotations.filter { it.shortName.asString() == "LuaBridgeClass" }
            for (annotation in annotations) {
                val ksType = annotation.arguments.find { it.name?.asString() == "targetClass" }?.value as? KSType ?: continue
                val targetDecl = ksType.declaration as? KSClassDeclaration ?: continue
                val category = annotation.arguments.find { it.name?.asString() == "category" }?.value as? String ?: "ui"
                AuxiliaryGenerator.generateBridgeForClass(resolver, targetDecl, category, codeGenerator, logger, generatedPluginClassNames)
            }
        }

        val bridgeModifiersSymbols = resolver.getSymbolsWithAnnotation("com.kulipai.luacompose.annotations.LuaBridgeModifiers")
        for (symbol in bridgeModifiersSymbols) {
            if (symbol !is KSClassDeclaration) continue
            val annotations = symbol.annotations.filter { it.shortName.asString() == "LuaBridgeModifiers" }
            for (annotation in annotations) {
                val packageName = annotation.arguments.find { it.name?.asString() == "packageName" }?.value as? String ?: continue
                AuxiliaryGenerator.generateBridgeForModifiers(resolver, packageName, codeGenerator, logger, generatedPluginClassNames)
            }
        }

        val bridgeLocalsSymbols = resolver.getSymbolsWithAnnotation("com.kulipai.luacompose.annotations.LuaBridgeLocals")
        for (symbol in bridgeLocalsSymbols) {
            if (symbol !is KSClassDeclaration) continue
            val annotations = symbol.annotations.filter { it.shortName.asString() == "LuaBridgeLocals" }
            for (annotation in annotations) {
                val packageName = annotation.arguments.find { it.name?.asString() == "packageName" }?.value as? String ?: continue
                val category = annotation.arguments.find { it.name?.asString() == "category" }?.value as? String
                AuxiliaryGenerator.generateBridgeForLocals(resolver, packageName, category, codeGenerator, logger, generatedPluginClassNames)
            }
        }
        
        if (!registryGenerated && generatedPluginClassNames.isNotEmpty()) {
            generatePluginRegistry()
            registryGenerated = true
        }
        
        return emptyList()
    }

    @OptIn(KspExperimental::class)
    private fun generateBridgeForPackage(resolver: Resolver, packageName: String, category: String) {
        // Fetch all declarations from the given package
        val decls = resolver.getDeclarationsFromPackage(packageName)
        
        // Filter out functions that have @Composable annotation
        val composableFunctions = decls.filterIsInstance<KSFunctionDeclaration>()
            .filter { func ->
                func.annotations.any { it.shortName.asString() == "Composable" } &&
                func.returnType?.resolve()?.declaration?.simpleName?.asString() == "Unit" &&
                func.isPublic()
            }
            .toList()

        if (composableFunctions.isEmpty()) {
            logger.warn("No Composable functions found in package $packageName")
            return
        }

        val categorySafe = category.split('.').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        val pluginClassName = categorySafe + "GeneratedPlugin"
        generatedPluginClassNames += pluginClassName
        
        val compNames = mutableListOf<String>()
        val grouped = composableFunctions.groupBy { it.simpleName.asString() }
        
        for ((funcName, overloads) in grouped) {
            val compClassName = "$categorySafe${funcName}Component"
            compNames.add(compClassName)
            
            val compTypeSpec = TypeSpec.objectBuilder(compClassName)
                .addFunction(
                    FunSpec.builder("register")
                        .returns(
                            LambdaTypeName.get(
                                parameters = arrayOf(
                                    ParameterSpec.unnamed(MAP.parameterizedBy(STRING, ANY.copy(nullable = true))),
                                    ParameterSpec.unnamed(ClassName("com.kulipai.luacompose.compose.runtime", "ComposeScope").copy(nullable = true))
                                ),
                                returnType = UNIT
                            ).copy(annotations = listOf(AnnotationSpec.builder(ClassName("androidx.compose.runtime", "Composable")).build()))
                        )
                        .apply {
                            addStatement("val functionCache = mutableMapOf<String, java.lang.reflect.Method>()")
                            addStatement("return { props, childScope ->")
                            
                            val sortedOverloads = overloads.sortedByDescending { it.parameters.count { p -> !p.hasDefault } }
                            val codeBlock = StringBuilder()
                            
                            for ((idx, func) in sortedOverloads.withIndex()) {
                                var fullClassName = resolver.getOwnerJvmClassName(func)?.replace('/', '.')
                                if (fullClassName == null) {
                                    val fileName = func.containingFile?.fileName ?: ""
                                    val jvmClassName = if (fileName.endsWith(".kt")) fileName.replace(".kt", "Kt") else "${funcName}Kt"
                                    fullClassName = "$packageName.$jvmClassName"
                                }
                                
                                val numParams = func.parameters.size
                                val firstParamName = func.parameters.firstOrNull()?.type?.resolve()?.declaration?.simpleName?.asString()
                                val firstParamCheck = if (firstParamName != null) {
                                    "it.parameterTypes.getOrNull(0)?.simpleName == \"$firstParamName\""
                                } else { "true" }
                                
                                val requiredNames = func.parameters.filter { !it.hasDefault && it.name?.asString() != "content" }.map { it.name?.asString() ?: "" }
                                val requiresContent = func.parameters.any { !it.hasDefault && it.name?.asString() == "content" }
                                
                                val conditions = mutableListOf<String>()
                                for (req in requiredNames) {
                                    conditions.add("props.containsKey(\"$req\")")
                                }
                                if (requiresContent) {
                                    conditions.add("(props.containsKey(\"content\") || childScope != null)")
                                }
                                val condition = if (conditions.isEmpty()) "true" else conditions.joinToString(" && ")
                                
                                if (idx > 0) codeBlock.append("    else ")
                                codeBlock.append("if ($condition) {\n")
                                
                                codeBlock.append("""
                                |        val method = functionCache.getOrPut("$funcName-$idx") {
                                |            Class.forName("$fullClassName").declaredMethods.first { 
                                |                (it.name == "$funcName" || it.name.startsWith("$funcName-") || it.name.startsWith("${funcName}_")) && 
                                |                it.parameterTypes.indexOfFirst { pt -> pt.name == "androidx.compose.runtime.Composer" } == $numParams &&
                                |                $firstParamCheck
                                |            }
                                |        }
                                |        val composer = androidx.compose.runtime.currentComposer
                                |        val args = arrayOfNulls<Any>(method.parameterTypes.size)
                                |        val composerIndex = method.parameterTypes.indexOfFirst { it.name == "androidx.compose.runtime.Composer" }
                                |        args[composerIndex] = composer
                                |        
                                |        val defaultBitmasks = IntArray(10) // generous size
                                |        val realParams = $numParams
                                |
                                """.trimMargin())
                                
                                for ((i, param) in func.parameters.withIndex()) {
                                    val pName = param.name?.asString()
                                    val isComposable = param.annotations.any { it.shortName.asString() == "Composable" } || param.type.annotations.any { it.shortName.asString() == "Composable" } || param.type.resolve().annotations.any { it.shortName.asString() == "Composable" }
                                    val paramKotlinType = param.type.resolve().declaration.qualifiedName?.asString()
                                    val resolveCall = when (paramKotlinType) {
                                        "androidx.compose.ui.unit.TextUnit" -> "com.kulipai.luacompose.compose.TypeResolver.resolve(com.kulipai.luacompose.compose.ui.resolveSp(val_$pName), method.parameterTypes[$i])"
                                        "androidx.compose.ui.unit.Dp" -> "com.kulipai.luacompose.compose.TypeResolver.resolve(com.kulipai.luacompose.compose.ui.resolveDp(val_$pName), method.parameterTypes[$i])"
                                        "androidx.compose.ui.graphics.Color" -> "com.kulipai.luacompose.compose.TypeResolver.resolve(com.kulipai.luacompose.compose.ui.resolveColor(val_$pName, androidx.compose.ui.graphics.Color.Unspecified), method.parameterTypes[$i])"
                                        else -> "com.kulipai.luacompose.compose.TypeResolver.resolve(val_$pName, method.parameterTypes[$i])"
                                    }
                                    codeBlock.append("""
                                |        val val_$pName = props["$pName"]
                                |        if (val_$pName is com.kulipai.luacompose.compose.script.ScriptFunction && method.parameterTypes[$i].name.startsWith("kotlin.jvm.functions.Function")) {
                                |            val scopeToUse = if ($isComposable) com.kulipai.luacompose.compose.runtime.ComposeBridge.getActiveScope()?.getOrCreateChildScope(val_$pName) else null
                                |            args[$i] = androidx.compose.runtime.remember(scopeToUse, val_$pName) {
                                |                com.kulipai.luacompose.compose.runtime.FunctionWrappers.wrap(scopeToUse, val_$pName, method.parameterTypes[$i].name, $isComposable)
                                |            }
                                |        } else if (props.containsKey("$pName")) {
                                |            args[$i] = $resolveCall
                                |        } else if ("$pName" == "content" && childScope != null) {
                                |            args[$i] = androidx.compose.runtime.remember(childScope) {
                                |                com.kulipai.luacompose.compose.runtime.FunctionWrappers.wrap(childScope, null, method.parameterTypes[$i].name, true)
                                |            }
                                |        } else {
                                |            val defaultMaskIndex = $i / 31
                                |            defaultBitmasks[defaultMaskIndex] = defaultBitmasks[defaultMaskIndex] or (1 shl ($i % 31))
                                |            args[$i] = when (method.parameterTypes[$i]) {
                                |                Boolean::class.javaPrimitiveType -> false
                                |                Int::class.javaPrimitiveType -> 0
                                |                Float::class.javaPrimitiveType -> 0f
                                |                Long::class.javaPrimitiveType -> 0L
                                |                Double::class.javaPrimitiveType -> 0.0
                                |                Short::class.javaPrimitiveType -> 0.toShort()
                                |                Byte::class.javaPrimitiveType -> 0.toByte()
                                |                Char::class.javaPrimitiveType -> '\u0000'
                                |                else -> null
                                |            }
                                |        }
                                """.trimMargin() + "\n")
                                }
                                
                                codeBlock.append("""
                                |        val defaultParamsCount = Math.ceil(realParams / 31.0).toInt()
                                |        val intIndices = mutableListOf<Int>()
                                |        for (i in realParams until method.parameterTypes.size) {
                                |            if (i != composerIndex && method.parameterTypes[i] == Int::class.javaPrimitiveType) {
                                |                intIndices.add(i)
                                |            }
                                |        }
                                |        val actualDefaultCount = Math.min(defaultParamsCount, intIndices.size)
                                |        val defaultIndices = intIndices.takeLast(actualDefaultCount)
                                |        val changedIndices = intIndices.dropLast(actualDefaultCount)
                                |        
                                |        for (idx in changedIndices) {
                                |            args[idx] = 0
                                |        }
                                |        for ((i, idx) in defaultIndices.withIndex()) {
                                |            args[idx] = defaultBitmasks[i]
                                |        }
                                |        
                                |        method.isAccessible = true
                                |        try {
                                |            com.kulipai.luacompose.compose.runtime.ComposeBridge.invokeSafe(method, null, args)
                                |        } catch (e: Exception) {
                                |            android.util.Log.e("LuaCompose", "Error invoking ${"$funcName"}: ${"$"}{e.message}", e)
                                |        }
                                |    }
                                """.trimMargin() + "\n")
                            }
                            addCode("%L\n", codeBlock.toString())
                            addStatement("}")
                        }
                        .build()
                )
                .build()
            
            val compFileSpec = FileSpec.builder("com.kulipai.luacompose.generated", compClassName)
                .addType(compTypeSpec)
                .build()
            compFileSpec.writeTo(codeGenerator, Dependencies(true))
        }

        // Now generate the Plugin
        val pluginTypeSpec = TypeSpec.classBuilder(pluginClassName)
            .addSuperinterface(ClassName("com.kulipai.luacompose.compose.runtime", "ComposeScriptPlugin"))
            .addProperty(
                PropertySpec.builder("namespace", STRING.copy(nullable = true), KModifier.OVERRIDE)
                    .initializer("%S", category)
                    .build()
            )
            .addFunction(
                FunSpec.builder("injectGlobals")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("scriptTable", ClassName("com.kulipai.luacompose.compose.script", "ScriptTable"))
                    .build()
            )
            .addFunction(
                FunSpec.builder("getComponents")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(MAP.parameterizedBy(
                        STRING,
                        LambdaTypeName.get(
                            parameters = arrayOf(
                                ParameterSpec.unnamed(MAP.parameterizedBy(STRING, ANY.copy(nullable = true))),
                                ParameterSpec.unnamed(ClassName("com.kulipai.luacompose.compose.runtime", "ComposeScope").copy(nullable = true))
                            ),
                            returnType = UNIT
                        ).copy(annotations = listOf(AnnotationSpec.builder(ClassName("androidx.compose.runtime", "Composable")).build()))
                    ))
                    .apply {
                        addStatement("val map = mutableMapOf<String, @androidx.compose.runtime.Composable (Map<String, Any?>, com.kulipai.luacompose.compose.runtime.ComposeScope?) -> Unit>()")
                        for ((funcName, _) in grouped) {
                            val compClassName = "$categorySafe${funcName}Component"
                            addStatement("map[\"$funcName\"] = $compClassName.register()")
                        }
                        addStatement("return map")
                    }
                    .build()
                )
            .build()
            
        val pluginFileSpec = FileSpec.builder("com.kulipai.luacompose.generated", pluginClassName)
            .addType(pluginTypeSpec)
            .build()
        pluginFileSpec.writeTo(codeGenerator, Dependencies(true))
    }

    private fun generatePluginRegistry() {
        val composeScriptPlugin = ClassName("com.kulipai.luacompose.compose.runtime", "ComposeScriptPlugin")
        val registerFunctionType = LambdaTypeName.get(
            parameters = arrayOf(ParameterSpec.unnamed(composeScriptPlugin)),
            returnType = UNIT
        )

        val registryTypeSpec = TypeSpec.objectBuilder("GeneratedPluginRegistry")
            .addFunction(
                FunSpec.builder("registerAll")
                    .addParameter("register", registerFunctionType)
                    .apply {
                        for (pluginClassName in generatedPluginClassNames) {
                            addStatement("register(%L())", pluginClassName)
                        }
                    }
                    .build()
            )
            .build()

        val registryFileSpec = FileSpec.builder("com.kulipai.luacompose.generated", "GeneratedPluginRegistry")
            .addType(registryTypeSpec)
            .build()

        registryFileSpec.writeTo(codeGenerator, Dependencies(true))
    }
}
