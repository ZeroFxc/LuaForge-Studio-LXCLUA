package com.nirithy.luacompose.component

import android.content.Context
import android.text.TextWatcher
import android.text.Editable
import android.view.View
import android.widget.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin
import com.nirithy.luacompose.render.ComposeRenderer
import com.luajava.LuaObject

/**
 * AndroidView 原生视图嵌入组件
 * 将 Android 原生 View 嵌入到 Compose UI 树中
 *
 * Lua 用法：
 *   compose.AndroidView {
 *     factory = function(ctx) return android.widget.Button(ctx) end,
 *     update = function(view) view:setText("Hello") end,
 *     modifier = compose.Modifier().fillMaxWidth().height(48)
 *   }
 */
object AndroidViewComponent : ComposePlugin {
    override val namespace = "viewinterop"

    override fun getComponents() = mapOf<String, @Composable (ComposeNode) -> Unit>(
        "AndroidView" to { node -> AndroidViewRenderer(node) },
    )
}

@Composable
private fun AndroidViewRenderer(node: ComposeNode) {
    val factoryObj = node.callback("factory")
    val updateObj = node.callback("update")
    val modifier = ComposeRenderer.resolveModifier(node)

    if (factoryObj == null) return

    AndroidView(
        modifier = modifier,
        factory = { ctx: Context ->
            val view: View = try {
                (factoryObj.call(ctx) as? View) ?: TextView(ctx)
            } catch (e: Exception) {
                TextView(ctx)
            }
            setupViewCallbacks(view, node)
            // 通用 onAttach，给用户完全控制权
            node.callback("onAttach")?.call(view)
            view
        },
        update = { view: View ->
            if (updateObj != null) {
                try { updateObj.call(view) } catch (_: Exception) {}
            }
        }
    )
}

/** 为 View 设置所有回调 */
private fun setupViewCallbacks(view: View, node: ComposeNode) {
    val cbs = node.callbacks

    // --- 点击 / 长按 ---
    cbs["onClick"]?.let { lua -> view.setOnClickListener { lua.call() } }
    // 右键/手写笔长按
    cbs["onContextClick"]?.let { lua ->
        view.setOnContextClickListener { (lua.call() as? Boolean) ?: true }
    }
    cbs["onLongClick"]?.let { lua ->
        view.setOnLongClickListener { (lua.call() as? Boolean) ?: true }
    }
    // 上下文菜单
    cbs["onCreateContextMenu"]?.let { lua ->
        view.setOnCreateContextMenuListener { menu, _, info ->
            lua.call(menu, info)
        }
    }

    // --- 触摸 / 手势 ---
    cbs["onTouch"]?.let { lua ->
        view.setOnTouchListener { _, event -> (lua.call(event) as? Boolean) ?: false }
    }
    // 通用运动事件（鼠标、手写笔等）
    cbs["onGenericMotion"]?.let { lua ->
        view.setOnGenericMotionListener { _, event ->
            (lua.call(event) as? Boolean) ?: false
        }
    }
    // 指针捕获（游戏手柄、鼠标拖拽等）
    cbs["onCapturedPointer"]?.let { lua ->
        view.setOnCapturedPointerListener { _, event ->
            (lua.call(event) as? Boolean) ?: false
        }
    }

    // --- 焦点 ---
    cbs["onFocusChange"]?.let { lua ->
        view.setOnFocusChangeListener { _, hasFocus -> lua.call(hasFocus) }
    }

    // --- 按键 ---
    cbs["onKey"]?.let { lua ->
        view.setOnKeyListener { _, keyCode, event ->
            (lua.call(keyCode, event) as? Boolean) ?: false
        }
    }

    // --- 拖拽 / 悬停 ---
    cbs["onDrag"]?.let { lua ->
        view.setOnDragListener { _, event ->
            (lua.call(event) as? Boolean) ?: false
        }
    }
    cbs["onHover"]?.let { lua ->
        view.setOnHoverListener { _, event ->
            (lua.call(event) as? Boolean) ?: false
        }
    }

    // --- 布局 / 尺寸 ---
    cbs["onLayoutChange"]?.let { lua ->
        view.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            lua.call(l, t, r, b, ol, ot, or_, ob)
        }
    }
    cbs["onSizeChanged"]?.let { lua ->
        view.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) {
                lua.call(r - l, b - t, or_ - ol, ob - ot)
            }
        }
    }

    // --- 滚动（仅 ViewGroup） ---
    if (view is android.view.ViewGroup) {
        cbs["onScroll"]?.let { lua ->
            view.setOnScrollChangeListener { _, scrollX, scrollY, oldX, oldY ->
                (lua.call(scrollX, scrollY, oldX, oldY) as? Boolean) ?: false
            }
        }
    }

    // --- 窗口附着 ---
    cbs["onAttachStateChange"]?.let { lua ->
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { lua.call("attached", v) }
            override fun onViewDetachedFromWindow(v: View) { lua.call("detached", v) }
        })
    }
    // WindowInsets
    cbs["onApplyWindowInsets"]?.let { lua ->
        view.setOnApplyWindowInsetsListener { _, insets ->
            lua.call(insets)
            insets
        }
    }
    // 系统 UI 可见性变化
    cbs["onSystemUiVisibilityChange"]?.let { lua ->
        view.setOnSystemUiVisibilityChangeListener { visibility ->
            lua.call(visibility)
        }
    }

    // --- 动画 ---
    cbs["onAnimationStart"]?.let { lua ->
        view.animation?.let { lua.call() }
    }
    cbs["onAnimationEnd"]?.let { lua ->
        view.animation?.let { lua.call() }
    }

    // ========== 控件特定回调 ==========
    setupWidgetCallbacks(view, cbs)
}

/** 根据 View 具体类型设置控件特定回调 */
private fun setupWidgetCallbacks(view: View, cbs: Map<String, LuaObject>) {
    // --- TextView / EditText ---
    if (view is TextView) {
        val tv = view
        // 文本变化（TextWatcher 三合一）
        if (cbs.containsKey("onTextChanged") || cbs.containsKey("onBeforeTextChanged") || cbs.containsKey("onAfterTextChanged")) {
            tv.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    cbs["onBeforeTextChanged"]?.call(s?.toString(), start, count, after)
                }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    cbs["onTextChanged"]?.call(s?.toString(), start, before, count)
                }
                override fun afterTextChanged(s: Editable?) {
                    cbs["onAfterTextChanged"]?.call(s?.toString())
                }
            })
        }
        // IME 编辑器动作（搜索、完成、下一项等）
        cbs["onEditorAction"]?.let { lua ->
            tv.setOnEditorActionListener { _, actionId, event ->
                (lua.call(actionId, event) as? Boolean) ?: false
            }
        }
    }

    // --- CompoundButton（CheckBox、Switch、RadioButton、ToggleButton） ---
    if (view is CompoundButton) {
        cbs["onCheckedChanged"]?.let { lua ->
            view.setOnCheckedChangeListener { _, isChecked -> lua.call(isChecked) }
        }
    }

    // --- SeekBar ---
    if (view is SeekBar) {
        cbs["onSeekBarChanged"]?.let { lua ->
            view.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    lua.call(progress, fromUser)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    cbs["onSeekBarStartTracking"]?.call()
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    cbs["onSeekBarStopTracking"]?.call()
                }
            })
        }
    }

    // --- RatingBar ---
    if (view is RatingBar) {
        cbs["onRatingChanged"]?.let { lua ->
            view.setOnRatingBarChangeListener { _, rating, fromUser ->
                lua.call(rating.toDouble(), fromUser)
            }
        }
    }

    // --- AdapterView（ListView、Spinner、GridView、Gallery） ---
    if (view is AdapterView<*>) {
        cbs["onItemClick"]?.let { lua ->
            view.onItemClickListener = AdapterView.OnItemClickListener { _, itemView, position, _ ->
                lua.call(position, itemView?.let { v -> getAdapterItem(view, position) }, itemView)
            }
        }
        cbs["onItemLongClick"]?.let { lua ->
            view.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, itemView, position, _ ->
                (lua.call(position, itemView?.let { v -> getAdapterItem(view, position) }, itemView) as? Boolean) ?: false
            }
        }
        if (cbs.containsKey("onItemSelected") || cbs.containsKey("onNothingSelected")) {
            view.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, itemView: View?, position: Int, id: Long) {
                    cbs["onItemSelected"]?.call(position, itemView?.let { getAdapterItem(parent!!, position) }, itemView)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    cbs["onNothingSelected"]?.call()
                }
            }
        }
    }

    // --- NumberPicker ---
    if (view is NumberPicker) {
        cbs["onValueChanged"]?.let { lua ->
            view.setOnValueChangedListener { _, oldVal, newVal -> lua.call(newVal, oldVal) }
        }
    }

    // --- TimePicker（支持 API 23+） ---
    if (view is TimePicker) {
        cbs["onTimeChanged"]?.let { lua ->
            view.setOnTimeChangedListener { _, hourOfDay, minute -> lua.call(hourOfDay, minute) }
        }
    }

    // --- SearchView ---
    if (view is SearchView) {
        cbs["onQueryTextChange"]?.let { lua ->
            view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    cbs["onQueryTextSubmit"]?.call(query)
                    return true
                }
                override fun onQueryTextChange(newText: String?): Boolean {
                    (lua.call(newText) as? Boolean) ?: true
                    return true
                }
            })
        }
    }

    // --- CalendarView ---
    if (view is CalendarView) {
        cbs["onDateChange"]?.let { lua ->
            view.setOnDateChangeListener { _, year, month, dayOfMonth ->
                lua.call(year, month, dayOfMonth)
            }
        }
    }

    // --- RadioGroup ---
    if (view is RadioGroup) {
        cbs["onCheckedChanged"]?.let { lua ->
            view.setOnCheckedChangeListener { _, checkedId -> lua.call(checkedId) }
        }
    }

    // --- ViewTreeObserver（绘制前回调、全局布局完成） ---
    cbs["onPreDraw"]?.let { lua ->
        view.viewTreeObserver.addOnPreDrawListener {
            (lua.call() as? Boolean) ?: true
        }
    }
    cbs["onGlobalLayout"]?.let { lua ->
        view.viewTreeObserver.addOnGlobalLayoutListener {
            lua.call()
        }
    }
}

/** 从 AdapterView 获取指定位置的 item 对象 */
private fun getAdapterItem(view: AdapterView<*>, position: Int): Any? {
    return try {
        view.adapter?.getItem(position)
    } catch (e: Exception) { null }
}