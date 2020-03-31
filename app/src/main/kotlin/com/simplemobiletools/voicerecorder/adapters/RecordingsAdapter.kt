package com.simplemobiletools.voicerecorder.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.formatDate
import com.simplemobiletools.commons.extensions.formatSize
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.activities.SimpleActivity
import com.simplemobiletools.voicerecorder.models.Recording
import kotlinx.android.synthetic.main.item_recording.view.*
import java.util.*

class RecordingsAdapter(activity: SimpleActivity, var recordings: ArrayList<Recording>, recyclerView: MyRecyclerView, fastScroller: FastScroller,
    itemClick: (Any) -> Unit) : MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_recordings

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = recordings.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = recordings.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = recordings.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_recording, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = recordings[position]
        holder.bindView(group, true, true) { itemView, layoutPosition ->
            setupView(itemView, group)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = recordings.size

    private fun setupView(view: View, recording: Recording) {
        view.apply {
            recording_frame?.isSelected = selectedKeys.contains(recording.id)
            recording_title.text = recording.title

            recording_date.text = recording.timestamp.formatDate(context)
            recording_duration.text = recording.duration.getFormattedDuration()
            recording_size.text = recording.size.formatSize()

            arrayListOf<TextView>(recording_title, recording_date, recording_duration, recording_size).forEach {
                it.setTextColor(textColor)
            }
        }
    }
}
