package com.rutamercaderistas.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.rutamercaderistas.fragments.ZoomablePageFragment

class PagePagerAdapter(
    activity: FragmentActivity,
    private val pageCount: Int,
    private val pageStart: Int,
    private val pdfPath: String
) : FragmentStateAdapter(activity) {

    override fun getItemCount() = pageCount

    override fun createFragment(position: Int): Fragment {
        return ZoomablePageFragment.newInstance(pageStart + position, pdfPath)
    }

    override fun getItemId(position: Int): Long =
        (pageStart + position).toLong()

    override fun containsItem(itemId: Long): Boolean =
        itemId in pageStart.toLong() until (pageStart + pageCount).toLong()
}
