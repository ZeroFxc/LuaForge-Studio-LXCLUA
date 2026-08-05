package com.kulipai.luacompose.compiler

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.isConstructor
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

object AuxiliaryGenerator {
    @OptIn(com.google.devtools.ksp.KspExperimental::class)
    fun generateBridgeForClass(
        resolver: Resolver,
        classDecl: KSClassDeclaration,
        category: String,
        codeGenerator: CodeGenerator,
        logger: KSPLogger,
        generatedPluginClassNames: MutableSet<String>
    ) {
        val className = classDecl.simpleName.asString()
        val fullClassName = classDecl.qualifiedName?.asString() ?: return
        
        val categorySafe = category.split('.').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        val pluginClassName = "${categorySafe}${className}AuxiliaryPlugin"
        generatedPluginClassNames += pluginClassName
        
        val pluginTypeSpec = TypeSpec.classBuilder(pluginClassName)
            .addSuperinterface(ClassName("com.kulipai.luacompose.compose.runtime", "ComposeScriptPlugin"))
            .addProperty(PropertySpec.builder("namespace", String::class.asTypeName().copy(nullable = true), KModifier.OVERRIDE)
                .initializer("%S", category)
                .build()
            )
            .addFunction(FunSpec.builder("getComponents")
                .addModifiers(KModifier.OVERRIDE)
                .returns(ClassName("kotlin.collections", "Map").parameterizedBy(
                    String::class.asTypeName(),
                    LambdaTypeName.get(
                        parameters = arrayOf(
                            ParameterSpec.unnamed(ClassName("kotlin.collections", "Map").parameterizedBy(String::class.asTypeName(), Any::class.asTypeName().copy(nullable = true))),
                            ParameterSpec.unnamed(ClassName("com.kulipai.luacompose.compose.runtime", "ComposeScope").copy(nullable = true))
                        ),
                        returnType = Unit::class.asTypeName()
                    ).copy(annotations = listOf(AnnotationSpec.builder(ClassName("androidx.compose.runtime", "Composable")).build()))
                ))
                .addStatement("return emptyMap()")
                .build()
            )
            .addFunction(FunSpec.builder("injectGlobals")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("scriptTable", ClassName("com.kulipai.luacompose.compose.script", "ScriptTable"))
                .apply {
                    addStatement("val engine = com.kulipai.luacompose.compose.runtime.ComposeBridge.engine")
                    addStatement("val classTable = engine.createTable()")
                    addStatement("val classMeta = engine.createTable()")
                    
                    // Companion properties (approximated for now)
                    addStatement("classMeta.set(%S, engine.createFunction { args ->", "__index")
                    addStatement("    val key = args[1].toStringValue()")
                    addStatement("    when (key) {")
                    // We can list companion properties here
                    addStatement("        else -> engine.createNil()")
                    addStatement("    }")
                    addStatement("})")
                    
                    addStatement("classTable.setMetatable(classMeta)")
                    addStatement("scriptTable.set(%S, classTable)", className)
                    
                    // Converters for instances
                    addStatement("com.kulipai.luacompose.compose.runtime.ComposeBridge.converters[%T::class.java] = { obj ->", ClassName.bestGuess(fullClassName))
                    addStatement("    val instance = obj as %T", ClassName.bestGuess(fullClassName))
                    addStatement("    val instTable = engine.createTable()")
                    addStatement("    instTable.set(%S, engine.createUserdata(instance))", "_javaObj")
                    addStatement("    val instMeta = engine.createTable()")
                    addStatement("    instMeta.set(%S, engine.createFunction { instArgs ->", "__index")
                    addStatement("        val key = instArgs[1].toStringValue()")
                    addStatement("        when (key) {")
                    
                    val properties = classDecl.getAllProperties().filter { it.isPublic() && it.extensionReceiver == null }
                    for (prop in properties) {
                        addStatement("            %S -> com.kulipai.luacompose.compose.runtime.ComposeBridge.javaToScript(instance.%L)", prop.simpleName.asString(), prop.simpleName.asString())
                    }
                    
                    val methods = classDecl.getAllFunctions().filter { it.isPublic() && !it.isConstructor() && it.simpleName.asString() !in listOf("equals", "hashCode", "toString") && it.extensionReceiver == null }
                    val groupedMethods = methods.groupBy { it.simpleName.asString() }
                    for ((mName, overloads) in groupedMethods) {
                        addStatement("            %S -> engine.createFunction { funcArgs ->", mName)
                        addStatement("                try {")
                        addStatement("                    val isKwargs = funcArgs.size == 2 && funcArgs[1].isTable() && funcArgs[1].asTable().get(%S).isNil()", "_javaObj")
                        addStatement("                    val kwargs = if (isKwargs) com.kulipai.luacompose.compose.runtime.ComposeBridge.scriptTableToMap(funcArgs[1].asTable()) else null")
                        
                        // Just take the first overload for simplicity if multiple, or sort by arg count
                        val func = overloads.sortedByDescending { it.parameters.size }.first()
                        
                        for ((idx, param) in func.parameters.withIndex()) {
                            val pName = param.name?.asString() ?: "arg$idx"
                            var typeName = param.type.resolve().declaration.qualifiedName?.asString() ?: "kotlin.Any"
                            if (typeName.startsWith("kotlin.Function")) typeName = "kotlin.Function"
                            addStatement("                    val p_$pName = if (kwargs != null && kwargs.containsKey(%S)) {", pName)
                            
                            val typeNamePoet = param.type.toTypeName()
                            if (typeName == "androidx.compose.ui.graphics.Color") {
                                addStatement("                    com.kulipai.luacompose.compose.ui.resolveColor(kwargs[%S], androidx.compose.ui.graphics.Color.Unspecified)", pName)
                            } else if (typeName == "androidx.compose.ui.unit.Dp") {
                                addStatement("                    com.kulipai.luacompose.compose.ui.resolveDp(kwargs[%S])", pName)
                            } else if (typeName == "kotlin.Float") {
                                addStatement("                    (kwargs[%S] as? Number)?.toFloat() ?: 0f", pName)
                            } else if (typeName == "kotlin.Function") {
                                addStatement("                    (if (kwargs[%S] is com.kulipai.luacompose.compose.script.ScriptFunction) com.kulipai.luacompose.compose.runtime.FunctionWrappers.wrap(null, kwargs[%S] as com.kulipai.luacompose.compose.script.ScriptFunction, %S, false) else kwargs[%S]) as? %T", pName, pName, typeName, pName, typeNamePoet.copy(nullable = true))
                            } else {
                                addStatement("                    kwargs[%S] as? %T", pName, typeNamePoet.copy(nullable = true))
                            }
                            addStatement("                    } else if (!isKwargs && funcArgs.size > ${idx + 1}) {")
                            addStatement("                        val argVal = funcArgs[${idx + 1}]")
                            if (typeName == "androidx.compose.ui.graphics.Color") {
                                addStatement("                        com.kulipai.luacompose.compose.ui.resolveColor(argVal, androidx.compose.ui.graphics.Color.Unspecified)")
                            } else if (typeName == "androidx.compose.ui.unit.Dp") {
                                addStatement("                        com.kulipai.luacompose.compose.ui.resolveDp(argVal)")
                            } else if (typeName == "kotlin.Float") {
                                addStatement("                        (com.kulipai.luacompose.compose.runtime.ComposeBridge.scriptToJava(argVal) as? Number)?.toFloat() ?: 0f")
                            } else if (typeName == "kotlin.Function") {
                                addStatement("                        (if (argVal is com.kulipai.luacompose.compose.script.ScriptFunction) com.kulipai.luacompose.compose.runtime.FunctionWrappers.wrap(null, argVal, %S, false) else argVal) as? %T", typeName, typeNamePoet.copy(nullable = true))
                            } else {
                                addStatement("                        com.kulipai.luacompose.compose.runtime.ComposeBridge.scriptToJava(argVal) as? %T", typeNamePoet.copy(nullable = true))
                            }
                            addStatement("                    } else { null }")
                        }
                        
                        val argsCall = func.parameters.mapNotNull { param ->
                            val pName = param.name?.asString()
                            if (pName != null) {
                                if (!param.hasDefault) {
                                    "$pName = p_$pName!!"
                                } else {
                                    // if it has default, we can't easily omit it in Kotlin if we don't use reflection, unless we use KCallable.callBy.
                                    // But actually, we can't easily conditionally omit args in direct calls!
                                    // Wait! We MUST cast it or just require it if we don't use callBy?
                                    // If we use !! here, it means we force it.
                                    "$pName = p_$pName!!"
                                }
                            } else null
                        }.joinToString(", ")
                        
                        addStatement("                    val result = instance.%L(%L)", mName, argsCall)
                        addStatement("                    com.kulipai.luacompose.compose.runtime.ComposeBridge.javaToScript(result)")
                        addStatement("                } catch (e: Exception) {")
                        addStatement("                    android.util.Log.e(%S, %S, e)", "LUA_ERROR", "Error calling $mName")
                        addStatement("                    engine.createNil()")
                        addStatement("                }")
                        addStatement("            }")
                    }
                    
                    addStatement("            else -> engine.createNil()")
                    addStatement("        }")
                    addStatement("    })")
                    addStatement("    instTable.setMetatable(instMeta)")
                    addStatement("    instTable")
                    addStatement("}")
                }
                .build()
            )
            .build()
            
        val fileSpec = FileSpec.builder("com.kulipai.luacompose.generated", pluginClassName)
            .addType(pluginTypeSpec)
            .build()
            
        fileSpec.writeTo(codeGenerator, Dependencies(true))
    }

    @OptIn(com.google.devtools.ksp.KspExperimental::class)
    fun generateBridgeForModifiers(
        resolver: Resolver,
        packageName: String,
        codeGenerator: CodeGenerator,
        logger: KSPLogger,
        generatedPluginClassNames: MutableSet<String>
    ) {
        val packageSafe = packageName.split('.').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        val pluginClassName = "${packageSafe}ModifiersPlugin"
        generatedPluginClassNames += pluginClassName
        
        val decls = resolver.getDeclarationsFromPackage(packageName)
        val modifierExtensions = decls.filterIsInstance<KSFunctionDeclaration>()
            .filter { it.isPublic() && it.extensionReceiver?.resolve()?.declaration?.qualifiedName?.asString() == "androidx.compose.ui.Modifier" }
            .toList()
            
        if (modifierExtensions.isEmpty()) return
        
        val pluginTypeSpec = TypeSpec.classBuilder(pluginClassName)
            .addSuperinterface(ClassName("com.kulipai.luacompose.compose.runtime", "ComposeScriptPlugin"))
            .addProperty(PropertySpec.builder("namespace", String::class.asTypeName().copy(nullable = true), KModifier.OVERRIDE)
                .initializer("null")
                .build()
            )
            .addFunction(FunSpec.builder("getComponents")
                .addModifiers(KModifier.OVERRIDE)
                .returns(ClassName("kotlin.collections", "Map").parameterizedBy(
                    String::class.asTypeName(),
                    LambdaTypeName.get(
                        parameters = arrayOf(
                            ParameterSpec.unnamed(ClassName("kotlin.collections", "Map").parameterizedBy(String::class.asTypeName(), Any::class.asTypeName().copy(nullable = true))),
                            ParameterSpec.unnamed(ClassName("com.kulipai.luacompose.compose.runtime", "ComposeScope").copy(nullable = true))
                        ),
                        returnType = Unit::class.asTypeName()
                    ).copy(annotations = listOf(AnnotationSpec.builder(ClassName("androidx.compose.runtime", "Composable")).build()))
                ))
                .addStatement("return emptyMap()")
                .build()
            )
            .addFunction(FunSpec.builder("injectGlobals")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("scriptTable", ClassName("com.kulipai.luacompose.compose.script", "ScriptTable"))
                .apply {
                    addStatement("val engine = com.kulipai.luacompose.compose.runtime.ComposeBridge.engine")
                    addStatement("val proto = com.kulipai.luacompose.compose.runtime.ComposeBridge.classPrototypes.getOrPut(%S) { engine.createTable() }", "androidx.compose.ui.Modifier")
                    
                    val grouped = modifierExtensions.groupBy { it.simpleName.asString() }
                    for ((mName, overloads) in grouped) {
                        addStatement("proto.set(%S, engine.createFunction { funcArgs ->", mName)
                        addStatement("    try {")
                        addStatement("        val instance = com.kulipai.luacompose.compose.runtime.ComposeBridge.scriptToJava(funcArgs[0]) as androidx.compose.ui.Modifier")
                        addStatement("        val isKwargs = funcArgs.size == 2 && funcArgs[1].isTable() && funcArgs[1].asTable().get(%S).isNil()", "_javaObj")
                        addStatement("        val kwargs = if (isKwargs) com.kulipai.luacompose.compose.runtime.ComposeBridge.scriptTableToMap(funcArgs[1].asTable()) else null")
                        
                        val func = overloads.sortedByDescending { it.parameters.size }.first()
                        
                        for ((idx, param) in func.parameters.withIndex()) {
                            val pName = param.name?.asString() ?: "arg$idx"
                            var typeName = param.type.resolve().declaration.qualifiedName?.asString() ?: "kotlin.Any"
                            if (typeName.startsWith("kotlin.Function")) typeName = "kotlin.Function"
                            addStatement("        val p_$pName = if (kwargs != null && kwargs.containsKey(%S)) {", pName)
                            
                            val typeNamePoet = param.type.toTypeName()
                            if (typeName == "androidx.compose.ui.graphics.Color") {
                                addStatement("            com.kulipai.luacompose.compose.ui.resolveColor(kwargs[%S], androidx.compose.ui.graphics.Color.Unspecified)", pName)
                            } else if (typeName == "androidx.compose.ui.unit.Dp") {
                                addStatement("            com.kulipai.luacompose.compose.ui.resolveDp(kwargs[%S])", pName)
                            } else if (typeName == "kotlin.Float") {
                                addStatement("            (kwargs[%S] as? Number)?.toFloat() ?: 0f", pName)
                            } else if (typeName == "kotlin.Function") {
                                addStatement("            (if (kwargs[%S] is com.kulipai.luacompose.compose.script.ScriptFunction) com.kulipai.luacompose.compose.runtime.FunctionWrappers.wrap(null, kwargs[%S] as com.kulipai.luacompose.compose.script.ScriptFunction, %S, false) else kwargs[%S]) as? %T", pName, pName, typeName, pName, typeNamePoet.copy(nullable = true))
                            } else {
                                addStatement("            kwargs[%S] as? %T", pName, typeNamePoet.copy(nullable = true))
                            }
                            addStatement("        } else if (!isKwargs && funcArgs.size > ${idx + 1}) {")
                            addStatement("            val argVal = funcArgs[${idx + 1}]")
                            if (typeName == "androidx.compose.ui.graphics.Color") {
                                addStatement("            com.kulipai.luacompose.compose.ui.resolveColor(argVal, androidx.compose.ui.graphics.Color.Unspecified)")
                            } else if (typeName == "androidx.compose.ui.unit.Dp") {
                                addStatement("            com.kulipai.luacompose.compose.ui.resolveDp(argVal)")
                            } else if (typeName == "kotlin.Float") {
                                addStatement("            (com.kulipai.luacompose.compose.runtime.ComposeBridge.scriptToJava(argVal) as? Number)?.toFloat() ?: 0f")
                            } else if (typeName == "kotlin.Function") {
                                addStatement("            (if (argVal is com.kulipai.luacompose.compose.script.ScriptFunction) com.kulipai.luacompose.compose.runtime.FunctionWrappers.wrap(null, argVal, %S, false) else argVal) as? %T", typeName, typeNamePoet.copy(nullable = true))
                            } else {
                                addStatement("            com.kulipai.luacompose.compose.runtime.ComposeBridge.scriptToJava(argVal) as? %T", typeNamePoet.copy(nullable = true))
                            }
                            addStatement("        } else { null }")
                        }
                        
                        val argsCall = func.parameters.mapNotNull { param ->
                            val pName = param.name?.asString()
                            if (pName != null) {
                                if (!param.hasDefault) "$pName = p_$pName!!" else "$pName = p_$pName!!"
                            } else null
                        }.joinToString(", ")
                        
                        addStatement("        val result = instance.%L(%L)", mName, argsCall)
                        addStatement("        com.kulipai.luacompose.compose.runtime.ComposeBridge.javaToScript(result)")
                        addStatement("    } catch (e: Exception) {")
                        addStatement("        android.util.Log.e(%S, %S, e)", "LUA_ERROR", "Error calling modifier $mName")
                        addStatement("        engine.createNil()")
                        addStatement("    }")
                        addStatement("})")
                    }
                }
                .build()
            )
            .build()
            
        val fileSpec = FileSpec.builder("com.kulipai.luacompose.generated", pluginClassName)
            .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S, %S", "OPT_IN_USAGE", "OPT_IN_USAGE_ERROR").build())
            .addType(pluginTypeSpec)
            .addImport(packageName, modifierExtensions.map { it.simpleName.asString() }.distinct())
            .build()
            
        fileSpec.writeTo(codeGenerator, Dependencies(true))
    }

    @OptIn(com.google.devtools.ksp.KspExperimental::class)
    fun generateBridgeForLocals(resolver: Resolver, packageName: String, category: String?, codeGenerator: CodeGenerator, logger: KSPLogger, generatedPluginClassNames: MutableSet<String>) {
        val packageSafe = packageName.split('.').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        val pluginClassName = "${packageSafe}LocalsPlugin"
        generatedPluginClassNames += pluginClassName
        
        val decls = resolver.getDeclarationsFromPackage(packageName)
        val localProps = decls.filterIsInstance<KSPropertyDeclaration>()
            .filter { it.isPublic() && it.simpleName.asString().startsWith("Local") }
            .toList()
            
        if (localProps.isEmpty()) return
        
        val pluginTypeSpec = TypeSpec.classBuilder(pluginClassName)
            .addSuperinterface(ClassName("com.kulipai.luacompose.compose.runtime", "ComposeScriptPlugin"))
            .addProperty(PropertySpec.builder("namespace", String::class.asTypeName().copy(nullable = true), KModifier.OVERRIDE)
                .initializer(if (category.isNullOrEmpty()) "null" else "%S", category ?: "")
                .build()
            )
            .addFunction(FunSpec.builder("getComponents")
                .addModifiers(KModifier.OVERRIDE)
                .returns(ClassName("kotlin.collections", "Map").parameterizedBy(
                    String::class.asTypeName(),
                    LambdaTypeName.get(
                        parameters = arrayOf(
                            ParameterSpec.unnamed(ClassName("kotlin.collections", "Map").parameterizedBy(String::class.asTypeName(), Any::class.asTypeName().copy(nullable = true))),
                            ParameterSpec.unnamed(ClassName("com.kulipai.luacompose.compose.runtime", "ComposeScope").copy(nullable = true))
                        ),
                        returnType = Unit::class.asTypeName()
                    ).copy(annotations = listOf(AnnotationSpec.builder(ClassName("androidx.compose.runtime", "Composable")).build()))
                ))
                .addStatement("return emptyMap()")
                .build()
            )
            .addFunction(FunSpec.builder("injectGlobals")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("scriptTable", ClassName("com.kulipai.luacompose.compose.script", "ScriptTable"))
                .apply {
                    addStatement("val engine = com.kulipai.luacompose.compose.runtime.ComposeBridge.engine")
                    for (prop in localProps) {
                        val propName = prop.simpleName.asString()
                        addStatement("val %LTable = engine.createTable()", propName)
                        addStatement("val %LMeta = engine.createTable()", propName)
                        addStatement("%LMeta.set(%S, engine.createFunction { args ->", propName, "__index")
                        addStatement("    val key = args[1].toStringValue()")
                        addStatement("    if (key == %S) {", "current")
                        addStatement("        val activeScope = com.kulipai.luacompose.compose.runtime.ComposeBridge.getActiveScope()")
                        addStatement("        if (activeScope == null) throw RuntimeException(%S)", "Local$propName.current 必须在 Composable 作用域内调用，不能在 onClick、onDraw 等回调阶段直接获取！请在组件外层提早获取并用局部变量捕获它。")
                        addStatement("        val localVal = activeScope.locals.get(%S)", propName)
                        addStatement("        return@createFunction com.kulipai.luacompose.compose.runtime.ComposeBridge.javaToScript(localVal)")
                        addStatement("    }")
                        addStatement("    return@createFunction engine.createNil()")
                        addStatement("})")
                        addStatement("%LTable.setMetatable(%LMeta)", propName, propName)
                        addStatement("scriptTable.set(%S, %LTable)", propName, propName)
                    }
                }
                .build()
            )
            .addFunction(FunSpec.builder("injectLocals")
                .addModifiers(KModifier.OVERRIDE)
                .addAnnotation(AnnotationSpec.builder(ClassName("androidx.compose.runtime", "Composable")).build())
                .addParameter("scope", ClassName("com.kulipai.luacompose.compose.runtime", "ComposeScope"))
                .apply {
                    for (prop in localProps) {
                        val propName = prop.simpleName.asString()
                        addStatement("scope.locals[%S] = %L.current", propName, propName)
                    }
                }
                .build()
            )
            .build()
            
        val fileSpec = FileSpec.builder("com.kulipai.luacompose.generated", pluginClassName)
            .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S, %S", "OPT_IN_USAGE", "OPT_IN_USAGE_ERROR").build())
            .addType(pluginTypeSpec)
            .addImport(packageName, localProps.map { it.simpleName.asString() }.distinct())
            .build()
            
        fileSpec.writeTo(codeGenerator, Dependencies(true))
    }
}
