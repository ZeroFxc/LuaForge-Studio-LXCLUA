package com.nirithy.luacompose.compiler

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * KSP SymbolProcessorProvider 入口。
 * SPI 服务注册文件：META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider
 */
class LuaComposeProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return LuaComposeProcessor(environment.codeGenerator, environment.logger)
    }
}