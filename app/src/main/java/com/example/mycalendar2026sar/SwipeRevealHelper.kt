package com.example.mycalendar2026sar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class SwipeTouchViews(
    val foreground: View,
    val actionsContainer: View
)

/**
 * Helper to manage smooth right-to-left swipe-to-reveal Edit and Delete action buttons on transaction rows.
 * Also handles hiding buttons and restoring row appearance when tapping a swiped row.
 */
class SwipeRevealHelper {

    private var currentlyOpenForeground: View? = null
    private var currentlyOpenActions: View? = null

    fun closeCurrentlyOpen() {
        val openView = currentlyOpenForeground
        val openActions = currentlyOpenActions
        if (openView != null) {
            ObjectAnimator.ofFloat(openView, "translationX", 0f).apply {
                duration = 180
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        openActions?.visibility = View.GONE
                    }
                })
                start()
            }
            currentlyOpenForeground = null
            currentlyOpenActions = null
        }
    }

    fun attachItemTouchHelper(
        recyclerView: RecyclerView,
        getViews: (RecyclerView.ViewHolder) -> SwipeTouchViews?
    ) {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getSwipeThreshold(vh: RecyclerView.ViewHolder): Float = 1.0f

            override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue * 10f

            override fun onChildDraw(
                c: Canvas,
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val views = getViews(vh)
                if (views != null) {
                    val foreground = views.foreground
                    val actions = views.actionsContainer

                    actions.visibility = View.VISIBLE
                    val actionsWidth = actions.width.toFloat().let { if (it <= 0f) 280f else it }

                    if (isCurrentlyActive) {
                        if (currentlyOpenForeground != null && currentlyOpenForeground != foreground) {
                            closeCurrentlyOpen()
                        }
                        val translationX = dX.coerceIn(-actionsWidth, 0f)
                        foreground.translationX = translationX
                    } else {
                        val currentTransX = foreground.translationX
                        if (currentTransX < -actionsWidth / 3f) {
                            ObjectAnimator.ofFloat(foreground, "translationX", -actionsWidth).apply {
                                duration = 150
                                start()
                            }
                            currentlyOpenForeground = foreground
                            currentlyOpenActions = actions
                        } else if (currentTransX != 0f) {
                            ObjectAnimator.ofFloat(foreground, "translationX", 0f).apply {
                                duration = 150
                                addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        actions.visibility = View.GONE
                                    }
                                })
                                start()
                            }
                            if (currentlyOpenForeground == foreground) {
                                currentlyOpenForeground = null
                                currentlyOpenActions = null
                            }
                        }
                    }
                } else {
                    super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
                }
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
        }

        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachSwipe(
        foreground: View,
        actionsContainer: View,
        btnEdit: View,
        btnDelete: View,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        onContentClick: (() -> Unit)? = null
    ) {
        // Reset translation and visibility on bind
        foreground.translationX = 0f
        actionsContainer.visibility = View.GONE

        btnEdit.setOnClickListener {
            closeCurrentlyOpen()
            onEdit()
        }

        btnDelete.setOnClickListener {
            closeCurrentlyOpen()
            onDelete()
        }

        val touchSlop = ViewConfiguration.get(foreground.context).scaledTouchSlop
        var startX = 0f
        var startY = 0f
        var isSwiping = false
        var initialTranslationX = 0f

        foreground.setOnClickListener {
            if (foreground.translationX < 0f || currentlyOpenForeground == foreground) {
                closeCurrentlyOpen()
            } else {
                onContentClick?.invoke()
            }
        }

        foreground.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initialTranslationX = foreground.translationX
                    isSwiping = false

                    if (currentlyOpenForeground != null && currentlyOpenForeground != foreground) {
                        closeCurrentlyOpen()
                    }
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY

                    if (!isSwiping) {
                        if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                            isSwiping = true
                            foreground.parent?.requestDisallowInterceptTouchEvent(true)
                            actionsContainer.visibility = View.VISIBLE
                        }
                    }

                    if (isSwiping) {
                        val actionsWidth = actionsContainer.width.toFloat().let {
                            if (it <= 0f) 280f else it
                        }
                        val newTranslationX = (initialTranslationX + dx).coerceIn(-actionsWidth, 0f)
                        foreground.translationX = newTranslationX
                        return@setOnTouchListener true
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val actionsWidth = actionsContainer.width.toFloat().let {
                        if (it <= 0f) 280f else it
                    }

                    if (isSwiping) {
                        val currentTransX = foreground.translationX

                        if (currentTransX < -actionsWidth / 3f) {
                            // Snap open
                            ObjectAnimator.ofFloat(foreground, "translationX", -actionsWidth).apply {
                                duration = 150
                                start()
                            }
                            currentlyOpenForeground = foreground
                            currentlyOpenActions = actionsContainer
                        } else {
                            // Snap closed
                            ObjectAnimator.ofFloat(foreground, "translationX", 0f).apply {
                                duration = 150
                                addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        actionsContainer.visibility = View.GONE
                                    }
                                })
                                start()
                            }
                            if (currentlyOpenForeground == foreground) {
                                currentlyOpenForeground = null
                                currentlyOpenActions = null
                            }
                        }
                        return@setOnTouchListener true
                    } else {
                        if (foreground.translationX < 0f || currentlyOpenForeground == foreground) {
                            closeCurrentlyOpen()
                            return@setOnTouchListener true
                        }
                    }
                    false
                }

                else -> false
            }
        }
    }
}
