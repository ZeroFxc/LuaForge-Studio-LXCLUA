package com.luaforge.studio.lxclua.plugin.floating

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 悬浮球视图
 *
 * 一个可拖拽的圆形悬浮球，支持点击展开/收起输入面板。
 * 质感优化：渐变背景、投影、按压动画。
 */
class FloatingBallView(
    context: Context,
    private val ballId: String,
    label: String,
    private val iconText: String,
    private val onBallClick: ((String) -> Unit)?
) : FrameLayout(context) {

    /** 默认渐变起始色 */
    private val defaultColorStart = Color.parseColor("#6366F1")
    /** 默认渐变结束色 */
    private val defaultColorEnd = Color.parseColor("#8B5CF6")

    private val ballView: TextView
    private var floatingPanel: FloatingInputPanel? = null
    private var isPanelVisible = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val dragThreshold = 10f
    private var touchStartTime = 0L

    /** 悬浮球拖拽移动回调 (x: Int, y: Int) */
    var onBallMoved: ((Int, Int) -> Unit)? = null

    init {
        ballView = TextView(context).apply {
            text = if (iconText.isNotEmpty()) iconText else label.take(2)
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            // 文字发光效果
            setShadowLayer(3f, 0f, 1f, 0x40000000)
        }
        ballView.background = createBallDrawable()
        addView(ballView, LayoutParams(dpToPx(56), dpToPx(56)))
    }

    /**
     * 创建悬浮球背景 Drawable
     * 使用 LayerDrawable：底层阴影 + 顶层渐变圆
     */
    private fun createBallDrawable(): LayerDrawable {
        // 阴影层 —— 半透明，略大，右下偏移
        val shadow = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x40000000)
        }
        // 主体渐变层 —— 左上到右下渐变，白色半透明描边
        val ball = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            orientation = GradientDrawable.Orientation.TL_BR
            colors = intArrayOf(defaultColorStart, defaultColorEnd)
            setStroke(1, Color.parseColor("#33FFFFFF"))
        }
        return LayerDrawable(arrayOf(shadow, ball)).apply {
            // 阴影向右下偏移 2px，主体向左上偏移 2px，形成投影效果
            setLayerInset(0, 1, 2, 0, 0)
            setLayerInset(1, 0, 0, 1, 2)
        }
    }

    /**
     * 按压动画 —— 缩小到 0.9x，松开复原
     */
    private fun animatePress(scaleDown: Boolean) {
        val anim = ValueAnimator.ofFloat(
            if (scaleDown) 1f else 0.9f,
            if (scaleDown) 0.9f else 1f
        )
        anim.duration = 80
        anim.addUpdateListener { a ->
            val s = a.animatedValue as Float
            ballView.scaleX = s
            ballView.scaleY = s
        }
        anim.start()
    }

    fun setFloatingPanel(panel: FloatingInputPanel) {
        this.floatingPanel = panel
    }

    /** 移动悬浮球到指定位置（由面板拖拽触发） */
    fun moveTo(x: Int, y: Int) {
        val params = layoutParams as? WindowManager.LayoutParams ?: return
        params.x = x
        params.y = y
        post {
            try {
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .updateViewLayout(this, params)
            } catch (_: Exception) {}
        }
    }

    /** 更新悬浮球文字（线程安全） */
    fun updateLabel(label: String) {
        post {
            ballView.text = label.take(2)
        }
    }

    /**
     * 更新悬浮球背景颜色（线程安全，支持 #RRGGBB 或 #AARRGGBB 格式）
     * 传入单色时自动生成同色系渐变，保持质感
     */
    fun updateBallColor(colorStr: String) {
        post {
            try {
                val color = Color.parseColor(colorStr)
                val bg = ballView.background
                if (bg is LayerDrawable && bg.numberOfLayers >= 2) {
                    // 更新主体渐变层的颜色：传入色 → 加深 30%
                    (bg.getDrawable(1) as? GradientDrawable)?.colors = intArrayOf(
                        color,
                        darkenColor(color, 0.7f)
                    )
                }
            } catch (_: Exception) {}
        }
    }

    /** 更新悬浮球文字颜色（线程安全） */
    fun updateBallTextColor(colorStr: String) {
        post {
            try {
                ballView.setTextColor(Color.parseColor(colorStr))
            } catch (_: Exception) {}
        }
    }

    /** 更新悬浮球大小（线程安全，dp 单位） */
    fun updateBallSize(sizeDp: Int) {
        post {
            val px = dpToPx(sizeDp.coerceIn(40, 80))
            val params = LayoutParams(px, px).apply {
                gravity = Gravity.CENTER
            }
            ballView.layoutParams = params
            ballView.textSize = (sizeDp / 3.5f)
        }
    }

    /** 更新悬浮球文字内容（线程安全，不截断） */
    fun updateBallText(text: String) {
        post {
            ballView.text = text
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                animatePress(true)
                initialX = (layoutParams as WindowManager.LayoutParams).x
                initialY = (layoutParams as WindowManager.LayoutParams).y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                touchStartTime = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (kotlin.math.abs(dx) > dragThreshold || kotlin.math.abs(dy) > dragThreshold) {
                    isDragging = true
                }
                if (isDragging) {
                    val params = layoutParams as WindowManager.LayoutParams
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                        .updateViewLayout(this, params)
                    floatingPanel?.updatePosition(params.x, params.y)
                    onBallMoved?.invoke(params.x, params.y)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animatePress(false)
                val elapsed = System.currentTimeMillis() - touchStartTime
                if (!isDragging && elapsed < 300) {
                    onBallClick?.invoke(ballId)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 颜色加深
     * @param factor 0~1，越小越深
     */
    private fun darkenColor(color: Int, factor: Float): Int {
        val r = ((color shr 16) and 0xFF) * factor
        val g = ((color shr 8) and 0xFF) * factor
        val b = (color and 0xFF) * factor
        return Color.rgb(r.toInt(), g.toInt(), b.toInt())
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}