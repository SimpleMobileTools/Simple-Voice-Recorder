package com.simplemobiletools.voicerecorder.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout

abstract class MyViewPagerFragment(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    abstract fun onResume()

    abstract fun onDestroy()
}
