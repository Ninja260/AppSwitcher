package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Point // For screen dimensions
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
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
    private val dockPaddingDp = 0 // Padding from screen edge in dp when docked

    private var dockPaddingPx: Int = 0


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

        // Get screen width
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
        Log.d(tag, "Screen width set to: $screenWidth")
        dockPaddingPx = (dockPaddingDp * resources.displayMetrics.density).toInt()

    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!this::windowManager.isInitialized) { // Check if setWindowManagerParams has been called
            return super.onInterceptTouchEvent(ev)
        }
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                isDragging = false
                Log.d(
                    tag,
                    "onInterceptTouchEvent: ACTION_DOWN at rawX=${ev.rawX}, rawY=${ev.rawY}. initialParams: x=$initialX, y=$initialY"
                )
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - initialTouchX
                val dy = ev.rawY - initialTouchY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    isDragging = true
                    Log.d(tag, "onInterceptTouchEvent: ACTION_MOVE - Drag started. dx=$dx, dy=$dy")
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    Log.d(
                        tag,
                        "onInterceptTouchEvent: ACTION_UP - Drag was in progress, intercepting."
                    )
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!this::windowManager.isInitialized) {
            Log.w(tag, "onTouchEvent: WindowManager not initialized")
            return super.onTouchEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                Log.d(
                    tag,
                    "onTouchEvent: ACTION_DOWN directly. rawX=${event.rawX}, rawY=${event.rawY}"
                )
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                    Log.d(
                        tag,
                        "onTouchEvent: ACTION_MOVE - Drag confirmed (was not set by intercept). dx=$dx, dy=$dy"
                    )
                }

                if (isDragging) {
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    Log.d(
                        tag,
                        "onTouchEvent: ACTION_MOVE - Updating layout to x=${params.x}, y=${params.y}"
                    )
                    try {
                        if (isAttachedToWindow) {
                            windowManager.updateViewLayout(this, params)
                        } else {
                            Log.w(
                                tag,
                                "onTouchEvent: ACTION_MOVE - View not attached, cannot update layout."
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(
                            tag,
                            "onTouchEvent: Error updating view layout in ACTION_MOVE: ${e.message}",
                            e
                        )
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                Log.d(
                    tag,
                    "onTouchEvent: ACTION_UP. Before docking: x=${params.x}, y=${params.y}. isDragging=$isDragging"
                )
                if (isDragging) {
                    // Docking Logic
                    val viewWidth = this.width
                    if (viewWidth > 0 && screenWidth > 0) { // Ensure we have valid widths
                        val viewCenterX = params.x + viewWidth / 2
                        val screenCenterX = screenWidth / 2

                        if (viewCenterX < screenCenterX) {
                            // Snap to left edge
                            params.x = dockPaddingPx
                            Log.d(tag, "Docking to LEFT. New params.x: ${params.x}")
                        } else {
                            // Snap to right edge
                            params.x = screenWidth - viewWidth - dockPaddingPx
                            Log.d(tag, "Docking to RIGHT. New params.x: ${params.x}")
                        }

                        try {
                            if (isAttachedToWindow) {
                                windowManager.updateViewLayout(this, params)
                            } else {
                                Log.w(
                                    tag,
                                    "onTouchEvent: ACTION_UP - View not attached, cannot update layout for docking."
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(
                                tag,
                                "onTouchEvent: Error updating view layout for docking: ${e.message}",
                                e
                            )
                        }
                    } else {
                        Log.w(
                            tag,
                            "onTouchEvent: ACTION_UP - Cannot dock, viewWidth ($viewWidth) or screenWidth ($screenWidth) is zero."
                        )
                    }

                    // Save the (potentially docked) position
                    sharedPreferences.edit {
                        putInt(prefsKeyX, params.x)
                            .putInt(prefsKeyY, params.y) // Y hasn't changed in this logic
                    }
                    Log.i(tag, "Saved docked position: x=${params.x}, y=${params.y}")
                    isDragging = false
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    Log.d(tag, "onTouchEvent: ACTION_CANCEL - Dragging cancelled.")
                }
            }
        }
        return super.onTouchEvent(event)
    }

}
