package com.simplemobiletools.voicerecorder.adapters

import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.activities.SimpleActivity
import com.simplemobiletools.voicerecorder.fragments.MyViewPagerFragment

class ViewPagerAdapter(private val activity: SimpleActivity) : PagerAdapter() {
    private val mFragments = SparseArray<MyViewPagerFragment>()

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout = R.layout.fragment_recorder
        val view = activity.layoutInflater.inflate(layout, container, false)
        container.addView(view)

        mFragments.put(position, view as MyViewPagerFragment)

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        container.removeView(item as View)
    }

    override fun getCount() = 1

    override fun isViewFromObject(view: View, item: Any) = view == item

    fun onResume() {
        mFragments.get(0)?.onResume()
    }

    fun onDestroy() {
        mFragments.get(0)?.onDestroy()
    }
}
