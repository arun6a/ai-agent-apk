package com.ai.agent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ai.agent.R

/**
 * StatusOrb — animated agent state indicator (v6.0.0).
 *
 * A small circular orb that changes color + animation based on agent state:
 * - IDLE: grey, static
 * - LISTENING: mint, soft pulse
 * - THINKING: amber, slow pulse (LLM call in flight)
 * - WORKING: mint, quick pulse (tool executing)
 * - DONE: emerald, fade-in solid
 * - ERROR: red, solid
 *
 * Usage: place in header, call setState() to change.
 */
class StatusOrb @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class AgentState {
        IDLE, LISTENING, THINKING, WORKING, DONE, ERROR
    }

    private var currentState = AgentState.IDLE
    private var pulseAnimator: ValueAnimator? = null
    private var pulseValue = 0f  // 0..1

    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        updateColor()
    }

    fun setState(state: AgentState) {
        if (state == currentState) return
        currentState = state
        updateColor()
        updateAnimation()
        invalidate()
    }

    private fun updateColor() {
        val colorRes = when (currentState) {
            AgentState.IDLE -> R.color.status_idle
            AgentState.LISTENING -> R.color.status_listening
            AgentState.THINKING -> R.color.status_thinking
            AgentState.WORKING -> R.color.status_working
            AgentState.DONE -> R.color.status_done
            AgentState.ERROR -> R.color.status_error
        }
        val color = ContextCompat.getColor(context, colorRes)
        orbPaint.color = color
        glowPaint.color = color
    }

    private fun updateAnimation() {
        pulseAnimator?.cancel()
        when (currentState) {
            AgentState.LISTENING, AgentState.WORKING -> {
                // Quick pulse (0.6s cycle)
                pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 600
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener {
                        pulseValue = it.animatedValue as Float
                        invalidate()
                    }
                }
                pulseAnimator?.start()
            }
            AgentState.THINKING -> {
                // Slow pulse (1.2s cycle)
                pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1200
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener {
                        pulseValue = it.animatedValue as Float
                        invalidate()
                    }
                }
                pulseAnimator?.start()
            }
            AgentState.DONE -> {
                // Fade in solid, then back to idle after 2s
                pulseAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = 2000
                    startDelay = 1000
                    addUpdateListener {
                        pulseValue = it.animatedValue as Float
                        invalidate()
                    }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            setState(AgentState.IDLE)
                        }
                    })
                }
                pulseAnimator?.start()
            }
            else -> {
                pulseValue = 0f
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = (minOf(width, height) / 2f) - 4f

        // Glow (pulsing) — larger circle with radial gradient
        if (pulseValue > 0 && currentState != AgentState.IDLE && currentState != AgentState.ERROR) {
            val glowRadius = baseRadius * (1.5f + pulseValue * 0.5f)
            glowPaint.alpha = (pulseValue * 80).toInt()
            canvas.drawCircle(cx, cy, glowRadius, glowPaint)
        }

        // Main orb
        orbPaint.alpha = if (currentState == AgentState.DONE) (255 * (1 - pulseValue)).toInt().coerceIn(80, 255) else 255
        canvas.drawCircle(cx, cy, baseRadius, orbPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = 28  // 28dp default
        val px = (size * resources.displayMetrics.density).toInt()
        setMeasuredDimension(px, px)
    }
}
