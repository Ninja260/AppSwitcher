package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Point // For screen dimensions
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.edit
import kotlin.math.abs

class DraggableLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var prefsKeyX: String
    private lateinit var prefsKeyY: String

    private var screenWidth: Int = 0
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isDragging: Boolean = false
    private val tag = "DraggableLinearLayout"
    private val dockPaddingDp = 0
    private var dockPaddingPx: Int = 0

    private var minimizeExpandButton: ImageView
    var appIconsContainer: LinearLayout

    init {
        this.orientation = VERTICAL
        this.gravity = Gravity.CENTER_HORIZONTAL // Center children like the button

        minimizeExpandButton = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                // Margins can be adjusted via padding on the parent or margins here
            }
            val buttonPadding = 12.dpToPx() // Increased padding for easier tap
            setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding)
        }

        appIconsContainer = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                // If you want appIconsContainer to fill width, use MATCH_PARENT
            }
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL // Center app icons if container is wider
        }

        addView(minimizeExpandButton)
        addView(appIconsContainer)
    }

    fun setMinimizeButtonClickListener(listener: OnClickListener) {
        minimizeExpandButton.setOnClickListener(listener)
    }

    fun refresh(isMinimized: Boolean) {
        minimizeExpandButton.setImageResource(if (isMinimized) android.R.drawable.arrow_down_float else android.R.drawable.arrow_up_float)
        appIconsContainer.visibility = if (isMinimized) GONE else VISIBLE

        if (this::windowManager.isInitialized && isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(this, params)
                Log.d(tag, "refresh: WindowManager.updateViewLayout() called.")
            } catch (e: Exception) {
                Log.e(tag, "refresh: Error calling updateViewLayout: ${e.message}", e)
            }
        }
    }

    fun setWindowManagerParams(
        wm: WindowManager,
        p: WindowManager.LayoutParams,
        prefs: SharedPreferences,
        keyX: String,
        keyY: String
    ) {
        this.windowManager = wm
        this.params = p
        this.sharedPreferences = prefs
        this.prefsKeyX = keyX
        this.prefsKeyY = keyY

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            screenWidth = windowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            val size = Point()
            @Suppress("DEPRECATION")
            display.getSize(size)
            screenWidth = size.x
        }
        dockPaddingPx = (dockPaddingDp * resources.displayMetrics.density).toInt()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!this::windowManager.isInitialized) return super.onInterceptTouchEvent(ev)
        if (isTouchInsideView(minimizeExpandButton, ev)) {
            return false
        }
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                isDragging = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - initialTouchX
                val dy = ev.rawY - initialTouchY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    isDragging = true
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!this::windowManager.isInitialized) return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                }
                if (isDragging) {
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    if (isAttachedToWindow) windowManager.updateViewLayout(this, params)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    val viewWidth = this.width
                    if (viewWidth > 0 && screenWidth > 0) {
                        val viewCenterX = params.x + viewWidth / 2
                        val screenCenterX = screenWidth / 2
                        if (viewCenterX < screenCenterX) params.x = dockPaddingPx
                        else params.x = screenWidth - viewWidth - dockPaddingPx
                        if (isAttachedToWindow) windowManager.updateViewLayout(this, params)
                    }
                    sharedPreferences.edit {
                        putInt(prefsKeyX, params.x)
                        putInt(prefsKeyY, params.y)
                    }
                    isDragging = false
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) isDragging = false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isTouchInsideView(view: android.view.View, event: MotionEvent): Boolean {
        val localX = event.x
        val localY = event.y
        return localX >= view.left && localX <= view.right && localY >= view.top && localY <= view.bottom
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
