package com.simplemobiletools.voicerecorder.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.simplemobiletools.commons.extensions.adjustAlpha
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.helpers.LOWER_ALPHA
import com.visualizer.amplitude.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class AudioEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val chunkPaint = Paint()
    private val highlightPaint = Paint()
    private val progressPaint = Paint()

    private var chunks = ArrayList<Float>()
    private var topBottomPadding = 6.dp()

    private var startX: Float = -1f
    private var endX: Float = -1f

    private var currentProgress: Float = 0f

    private enum class Dragging {
        START,
        END,
        NONE
    }

    private var dragging = Dragging.NONE

    var startPosition: Float = -1f
    var endPosition: Float = -1f

    var editListener: (() -> Unit)? = null

    var chunkColor = Color.RED
        set(value) {
            chunkPaint.color = value
            field = value
        }
    private var chunkWidth = 20.dp()
        set(value) {
            chunkPaint.strokeWidth = value
            field = value
        }
    private var chunkSpace = 1.dp()
    var chunkMinHeight = 3.dp()  // recommended size > 10 dp
    var chunkRoundedCorners = false
        set(value) {
            if (value) {
                chunkPaint.strokeCap = Paint.Cap.ROUND
            } else {
                chunkPaint.strokeCap = Paint.Cap.BUTT
            }
            field = value
        }

    init {
        chunkPaint.strokeWidth = chunkWidth
        chunkPaint.color = chunkColor
        chunkRoundedCorners = false
        highlightPaint.color = context.getProperPrimaryColor().adjustAlpha(LOWER_ALPHA)
        progressPaint.color = context.getProperTextColor()
        progressPaint.strokeWidth = 4.dp()
    }

    fun recreate() {
        chunks.clear()
        invalidate()
    }

    fun clearEditing() {
        startX = -1f
        endX = -1f
        startPosition = -1f
        endPosition = -1f
        editListener?.invoke()
        invalidate()
    }

    fun putAmplitudes(amplitudes: List<Int>) {
        val maxAmp = amplitudes.max()
        chunkWidth = (1.0f / amplitudes.size) * (2.0f / 3)
        chunkSpace = chunkWidth / 2

        chunks.addAll(amplitudes.map { it.toFloat() / maxAmp })

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (chunkWidth < 1f) {
            chunkWidth *= width
            chunkSpace = chunkWidth / 2
        }
        val verticalCenter = height / 2
        var x = chunkSpace
        val maxHeight = height - (topBottomPadding * 2)
        val verticalDrawScale = maxHeight - chunkMinHeight
        if (verticalDrawScale == 0f) {
            return
        }

        chunks.forEach {
            val chunkHeight = it * verticalDrawScale + chunkMinHeight
            val startY = verticalCenter - chunkHeight / 2
            val stopY = verticalCenter + chunkHeight / 2

            canvas.drawLine(x, startY, x, stopY, chunkPaint)
            x += chunkWidth + chunkSpace
        }

        if (startPosition >= 0f || startX >= 0f ) {
            val start: Float
            val end: Float
            if (startX >= 0f) {
                start = startX
                end = endX
            } else {
                start = width * startPosition
                end = width * endPosition
            }

            canvas.drawRect(start, 0f, end, height.toFloat(), highlightPaint)
        }

        canvas.drawLine(width * currentProgress, 0f, width * currentProgress, height.toFloat(), progressPaint)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (abs(event.x - startPosition * width) < 50.0f) {
                    startX = event.x
                    endX = endPosition * width
                    dragging = Dragging.START
                } else if (abs(event.x - endPosition * width) < 50.0f) {
                    endX = event.x
                    startX = startPosition * width
                    dragging = Dragging.END
                } else {
                    startX = event.x
                    endX = event.x
                    dragging = Dragging.END
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging == Dragging.START) {
                    startX = event.x
                } else if (dragging == Dragging.END) {
                    endX = event.x
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging == Dragging.START) {
                    startX = event.x
                } else if (dragging == Dragging.END) {
                    endX = event.x
                }
                dragging = Dragging.NONE
                startPosition = min(startX, endX) / width
                endPosition = max(startX, endX) / width
                startX = -1f
                endX = -1f
            }
        }
        invalidate()
        editListener?.invoke()
        return true
    }

    fun updateStartPosition(newPosition: Float) {
        if (newPosition > endPosition) {
            startPosition = endPosition
            endPosition = newPosition
        } else {
            startPosition = newPosition
        }
        invalidate()
        editListener?.invoke()
    }

    fun updateEndPosition(newPosition: Float) {
        if (newPosition < startPosition) {
            endPosition = startPosition
            startPosition = newPosition
        } else {
            endPosition = newPosition
        }
        invalidate()
        editListener?.invoke()
    }

    fun updateProgress(progress: Float) {
        currentProgress = progress
        invalidate()
    }
}
