package com.rutamercaderistas.views

import android.animation.ValueAnimator
import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.animation.addListener
import androidx.core.view.GestureDetectorCompat

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    companion object {
        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 10f
        private const val MEDIUM_SCALE = 3f
        private const val ANIM_DURATION = 300L
    }

    private var bmpW = 0f
    private var bmpH = 0f
    private var viewW = 0f
    private var viewH = 0f

    private val baseMatrix = Matrix()
    private val workMatrix = Matrix()
    private var scale = 1f
    private var panX = 0f
    private var panY = 0f
    private var isAnimating = false

    private var lastX = 0f
    private var lastY = 0f
    private var pointerCount = 0

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetectorCompat

    var onTap: (() -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null
    var onScaleChanged: ((Float) -> Unit)? = null

    private val defaultInterpolator = TimeInterpolator { t ->
        if (t < 0.5f) 2f * t * t else 1f - ((-2f * t + 2f) * (-2f * t + 2f)) / 2f
    }

    private var savedScale = 1f
    private var savedPanX = 0f
    private var savedPanY = 0f
    private var doubleTapX = 0f
    private var doubleTapY = 0f

    init {
        scaleType = ScaleType.MATRIX
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var initialScale = 1f
            private var initialPanX = 0f
            private var initialPanY = 0f

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (isAnimating) return false
                initialScale = scale
                initialPanX = panX
                initialPanY = panY
                pointerCount = 2
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isAnimating) return false
                val focusX = detector.focusX
                val focusY = detector.focusY
                val newScale = (scale * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                val ratio = newScale / scale
                panX = focusX - ratio * (focusX - panX)
                panY = focusY - ratio * (focusY - panY)
                scale = newScale
                rebuildMatrix()
                onScaleChanged?.invoke(scale)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                pointerCount = 0
                if (scale <= MIN_SCALE + 0.05f) {
                    scale = MIN_SCALE
                    panX = 0f
                    panY = 0f
                    rebuildMatrix()
                }
            }
        })

        gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isAnimating) return false
                doubleTapX = e.x
                doubleTapY = e.y
                animateScale()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onTap?.invoke()
                return true
            }
        })
    }

    fun loadPage(bitmap: Bitmap) {
        bmpW = bitmap.width.toFloat()
        bmpH = bitmap.height.toFloat()
        scale = 1f
        panX = 0f
        panY = 0f
        isAnimating = false
        setImageBitmap(bitmap)
        post { rebuildBaseMatrix(); rebuildMatrix() }
    }

    fun resetZoom() {
        scale = 1f
        panX = 0f
        panY = 0f
        rebuildMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewW = w.toFloat()
        viewH = h.toFloat()
        rebuildBaseMatrix()
    }

    private fun rebuildBaseMatrix() {
        if (bmpW <= 0 || bmpH <= 0 || viewW <= 0) return
        baseMatrix.reset()
        val s = minOf(viewW / bmpW, viewH / bmpH)
        val ox = (viewW - bmpW * s) / 2f
        val oy = (viewH - bmpH * s) / 2f
        baseMatrix.postScale(s, s)
        baseMatrix.postTranslate(ox, oy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isAnimating) return true

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                pointerCount = 1
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                pointerCount++
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1 && scale > MIN_SCALE + 0.05f) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    panX += dx
                    panY += dy
                    rebuildMatrix()
                } else if (event.pointerCount == 1 && scale <= MIN_SCALE + 0.05f) {
                    parent.requestDisallowInterceptTouchEvent(false)
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointerCount = 0
                if (scale <= MIN_SCALE + 0.05f) {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
        return true
    }

    private fun rebuildMatrix() {
        rebuildWorkMatrix()
        clampEdges()
        imageMatrix = workMatrix
        invalidate()
    }

    private fun rebuildWorkMatrix() {
        if (viewW <= 0 || bmpW <= 0) return
        workMatrix.set(baseMatrix)
        workMatrix.postScale(scale, scale, viewW / 2f, viewH / 2f)
        workMatrix.postTranslate(panX, panY)
    }

    private fun clampEdges() {
        val pts = floatArrayOf(0f, 0f, bmpW, bmpH)
        workMatrix.mapPoints(pts)
        val l = pts[0]; val t = pts[1]; val r = pts[2]; val b = pts[3]
        val imgW = r - l; val imgH = b - t

        var dx = 0f; var dy = 0f

        if (imgW <= viewW) {
            dx = (viewW - imgW) / 2f - l
        } else {
            if (l > 0) dx = -l
            else if (r < viewW) dx = viewW - r
        }
        if (imgH <= viewH) {
            dy = (viewH - imgH) / 2f - t
        } else {
            if (t > 0) dy = -t
            else if (b < viewH) dy = viewH - b
        }

        if (dx != 0f || dy != 0f) {
            panX += dx
            panY += dy
            workMatrix.postTranslate(dx, dy)
        }
    }

    private fun animateScale() {
        savedScale = scale
        savedPanX = panX
        savedPanY = panY

        if (scale > MIN_SCALE + 0.1f) {
            val startScale = scale
            val startPanX = panX
            val startPanY = panY
            isAnimating = true
            ValueAnimator.ofFloat(0f, 1f).apply {
                interpolator = defaultInterpolator
                addUpdateListener { animator ->
                    val t = animator.animatedFraction
                    scale = startScale + (MIN_SCALE - startScale) * t
                    panX = startPanX + (0f - startPanX) * t
                    panY = startPanY + (0f - startPanY) * t
                    rebuildMatrix()
                }
                addListener(onEnd = {
                    scale = MIN_SCALE
                    panX = 0f
                    panY = 0f
                    isAnimating = false
                    rebuildMatrix()
                    onScaleChanged?.invoke(scale)
                })
                duration = ANIM_DURATION
                start()
            }
        } else {
            val px = doubleTapX
            val py = doubleTapY
            val startPanX = panX
            val startPanY = panY
            val targetScale = MEDIUM_SCALE
            val targetPanX = (viewW / 2f - px) * (1f - targetScale / scale.coerceAtLeast(1.01f))
            val targetPanY = (viewH / 2f - py) * (1f - targetScale / scale.coerceAtLeast(1.01f))
            val startScale = scale
            isAnimating = true
            ValueAnimator.ofFloat(0f, 1f).apply {
                interpolator = defaultInterpolator
                addUpdateListener { animator ->
                    val t = animator.animatedFraction
                    scale = startScale + (targetScale - startScale) * t
                    panX = startPanX + (targetPanX - startPanX) * t
                    panY = startPanY + (targetPanY - startPanY) * t
                    rebuildMatrix()
                }
                addListener(onEnd = {
                    scale = targetScale
                    panX = targetPanX
                    panY = targetPanY
                    isAnimating = false
                    rebuildMatrix()
                    onScaleChanged?.invoke(scale)
                })
                duration = ANIM_DURATION
                start()
            }
        }
    }
}