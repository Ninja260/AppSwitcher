package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout

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


    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isDragging: Boolean = false
    private val TAG = "DraggableLinearLayout"

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
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Only attempt to intercept if windowManager is initialized (i.e., setWindowManagerParams has been called)
        if (!this::windowManager.isInitialized) {
            return super.onInterceptTouchEvent(ev)
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                isDragging = false
                Log.d(TAG, "onInterceptTouchEvent: ACTION_DOWN at rawX=${ev.rawX}, rawY=${ev.rawY}. initialParams: x=$initialX, y=$initialY")
                return false // Give children a chance
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - initialTouchX
                val dy = ev.rawY - initialTouchY
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                    isDragging = true
                    Log.d(TAG, "onInterceptTouchEvent: ACTION_MOVE - Drag started. dx=$dx, dy=$dy")
                    return true // Intercept
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    Log.d(TAG, "onInterceptTouchEvent: ACTION_UP - Drag was in progress, intercepting.")
                    // isDragging will be reset in onTouchEvent
                    return true // Intercept to handle in onTouchEvent
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!this::windowManager.isInitialized) {
            Log.w(TAG, "onTouchEvent: WindowManager not initialized")
            return super.onTouchEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // This is reached if ACTION_DOWN was not on a clickable child OR
                // if onInterceptTouchEvent decided not to intercept for children initially,
                // and no child consumed the event.
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false // Not yet dragging
                Log.d(TAG, "onTouchEvent: ACTION_DOWN directly. rawX=${event.rawX}, rawY=${event.rawY}")
                return true // Crucial: Return true to receive subsequent ACTION_MOVE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    isDragging = true
                    Log.d(TAG, "onTouchEvent: ACTION_MOVE - Drag confirmed (was not set by intercept). dx=$dx, dy=$dy")
                }

                if (isDragging) {
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    Log.d(TAG, "onTouchEvent: ACTION_MOVE - Updating layout to x=${params.x}, y=${params.y}")
                    try {
                        if (isAttachedToWindow) {
                           windowManager.updateViewLayout(this, params)
                        } else {
                            Log.w(TAG, "onTouchEvent: ACTION_MOVE - View not attached, cannot update layout.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "onTouchEvent: Error updating view layout in ACTION_MOVE: ${e.message}", e)
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "onTouchEvent: ACTION_UP. Final position: x=${params.x}, y=${params.y}. isDragging=$isDragging")
                if (isDragging) {
                    sharedPreferences.edit()
                        .putInt(prefsKeyX, params.x)
                        .putInt(prefsKeyY, params.y)
                        .apply()
                    Log.i(TAG, "Saved position: x=${params.x}, y=${params.y}")
                    isDragging = false
                    return true // Consumed the event
                }
                // If not dragging, it might be a tap on this layout.
                // If this layout has its own OnClickListener, super.onTouchEvent will handle it.
                // Otherwise, the event is consumed if isClickable is true, or passed up if false.
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    Log.d(TAG, "onTouchEvent: ACTION_CANCEL - Dragging cancelled.")
                }
            }
        }
        return super.onTouchEvent(event)
    }
}