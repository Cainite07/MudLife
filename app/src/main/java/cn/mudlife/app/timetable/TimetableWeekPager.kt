package cn.mudlife.app.timetable

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

internal class TimetableWeekPager(
    context: Context,
    private val createGrid: () -> WeeklyTimetableView,
    private val bindGrid: (WeeklyTimetableView, Int) -> Unit,
    private val selected: (Int) -> Unit
) {
    val view = ViewPager2(context)
    private val list = view.getChildAt(0) as RecyclerView
    private var weekCount = 0
    private var currentWeek = 0
    private var scrollPosition = 0
    private var bottomInset = 0
    private var refreshing = false

    private class Holder(val grid: WeeklyTimetableView) : RecyclerView.ViewHolder(grid)

    private val pages = object : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = weekCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(createGrid().apply {
            // The horizontal RecyclerView observes touch from DOWN. The grid's ScrollView
            // takes control itself when it detects a vertical drag.
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val grid = holder.grid
            grid.setBottomInset(bottomInset)
            bindGrid(grid, position + 1)
            grid.scrollToPosition(scrollPosition)
            setPageAccessibility(grid, position + 1)
        }
    }

    fun setBottomInset(pixels: Int) {
        if (pixels == bottomInset) return
        bottomInset = pixels
        for (index in 0 until list.childCount) {
            (list.getChildAt(index) as WeeklyTimetableView).setBottomInset(pixels)
        }
    }

    init {
        list.itemAnimator = null
        list.setItemViewCacheSize(0)
        view.adapter = pages
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                // Compose installs its host delegate during attach. Keep its virtual parent
                // and traversal links when exposing the visible native subtree.
                v.doOnPreDraw {
                    val host = v.parent as? ViewGroup ?: return@doOnPreDraw
                    val original = ViewCompat.getAccessibilityDelegate(host) ?: return@doOnPreDraw
                    if (original !is GridHostAccessibility) {
                        ViewCompat.setAccessibilityDelegate(host, GridHostAccessibility(v, original))
                    }
                }
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshing = false
            if (weekCount > 0 && view.offscreenPageLimit == ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT) {
                // Draw the selected week first, then prepare one neighbor on each side.
                view.postOnAnimation { view.offscreenPageLimit = 1 }
            }
        }
        view.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    refreshing = false
                    alignScrollPositions()
                }
            }

            override fun onPageSelected(position: Int) {
                val week = position + 1
                if (refreshing || week !in 1..weekCount || week == currentWeek) return
                // Accessibility actions may change pages without a touch or a call to select().
                alignScrollPositions()
                currentWeek = week
                for (index in 0 until list.childCount) {
                    val grid = list.getChildAt(index) as WeeklyTimetableView
                    setPageAccessibility(grid, list.getChildAdapterPosition(grid) + 1)
                }
                selected(week)
            }
        })
    }

    fun refresh(weeks: Int, week: Int) {
        alignScrollPositions()
        val previousCount = weekCount
        weekCount = weeks.coerceAtLeast(0)
        currentWeek = if (weekCount == 0) 0 else week.coerceIn(1, weekCount)
        refreshing = true
        if (weekCount > previousCount) pages.notifyItemRangeInserted(previousCount, weekCount - previousCount)
        if (weekCount < previousCount) pages.notifyItemRangeRemoved(weekCount, previousCount - weekCount)
        val retained = minOf(previousCount, weekCount)
        if (retained > 0) pages.notifyItemRangeChanged(0, retained)
        if (currentWeek > 0) view.setCurrentItem(currentWeek - 1, false)
    }

    fun select(week: Int, smooth: Boolean) {
        if (weekCount == 0) return
        val position = week.coerceIn(1, weekCount) - 1
        if (position == view.currentItem) return
        refreshing = false
        alignScrollPositions()
        view.setCurrentItem(position, smooth && ValueAnimator.areAnimatorsEnabled())
    }

    private fun alignScrollPositions() {
        if (currentWeek == 0) return
        val current = list.findViewHolderForAdapterPosition(currentWeek - 1) as? Holder
        if (current != null) scrollPosition = current.grid.scrollPosition
        for (index in 0 until list.childCount) {
            val grid = list.getChildAt(index) as WeeklyTimetableView
            if (grid !== current?.grid && grid.scrollPosition != scrollPosition) {
                grid.scrollToPosition(scrollPosition)
            }
        }
    }

    private fun setPageAccessibility(grid: WeeklyTimetableView, week: Int) {
        grid.importantForAccessibility = if (week == currentWeek) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private class GridHostAccessibility(
        private val pager: View,
        private val original: AccessibilityDelegateCompat
    ) : AccessibilityDelegateCompat() {
        private val visibleArea = Rect()

        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            original.onInitializeAccessibilityNodeInfo(host, info)
            // Compose 1.6 marks the AndroidView host invisible while accessibility is active.
            // Readers that skip invisible nodes then miss all of the current week's cells.
            info.isVisibleToUser = pager.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS &&
                pager.isShown && pager.windowVisibility == View.VISIBLE && pager.getGlobalVisibleRect(visibleArea)
        }
    }
}
