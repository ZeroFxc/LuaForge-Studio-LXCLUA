package com.luaforge.studio.lxclua.ui.editor.designer

/**
 * 设计器模式枚举
 *
 * 定义编辑器的三种工作模式：
 * - OFF: 纯代码编辑模式，隐藏设计预览
 * - CODE_DESIGN: 代码与设计分屏模式，左右/上下分割显示
 * - DESIGN_ONLY: 纯设计模式，隐藏代码编辑器，全屏显示预览
 */
enum class DesignerMode {
    /** 纯代码模式 */
    OFF,
    /** 代码+设计分屏模式 */
    CODE_DESIGN,
    /** 纯设计模式 */
    DESIGN_ONLY
}
