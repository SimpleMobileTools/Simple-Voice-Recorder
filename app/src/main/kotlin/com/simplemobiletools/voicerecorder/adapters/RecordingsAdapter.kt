package com.simplemobiletools.voicerecorder.adapters

import android.provider.MediaStore
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.formatDate
import com.simplemobiletools.commons.extensions.formatSize
import com.simplemobiletools.commons.extensions.getAdjustedPrimaryColor
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.activities.SimpleActivity
import com.simplemobiletools.voicerecorder.dialogs.RenameRecordingDialog
import com.simplemobiletools.voicerecorder.interfaces.RefreshRecordingsListener
import com.simplemobiletools.voicerecorder.models.Recording
import kotlinx.android.synthetic.main.item_recording.view.*
import java.util.*

class RecordingsAdapter(
    activity: SimpleActivity, var recordings: ArrayList<Recording>, val refreshListener: RefreshRecordingsListener,
    recyclerView: MyRecyclerView, fastScroller: FastScroller, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {
    var currRecordingId = 0

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_recordings

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = isOneItemSelected()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_rename -> renameRecording()
            R.id.cab_select_all -> selectAll()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = recordings.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = recordings.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = recordings.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_recording, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recording = recordings[position]
        holder.bindView(recording, true, true) { itemView, layoutPosition ->
            setupView(itemView, recording)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = recordings.size

    private fun getItemWithKey(key: Int): Recording? = recordings.firstOrNull { it.id == key }

    fun updateItems(newItems: ArrayList<Recording>) {
        if (newItems.hashCode() != recordings.hashCode()) {
            recordings = newItems
            finishActMode()
        }
        fastScroller?.measureRecyclerView()
    }

    private fun renameRecording() {
        val recording = getItemWithKey(selectedKeys.first()) ?: return
        RenameRecordingDialog(activity, recording) {
            finishActMode()
            refreshListener.refreshRecordings()
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().first()
        val items = if (itemsCnt == 1) {
            "\"${firstItem.title}\""
        } else {
            resources.getQuantityString(R.plurals.delete_recordings, itemsCnt, itemsCnt)
        }

        val baseString = R.string.delete_recordings_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteMediaStoreRecordings()
            }
        }
    }

    private fun deleteMediaStoreRecordings() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val oldRecordingIndex = recordings.indexOfFirst { it.id == currRecordingId }
        val recordingsToRemove = recordings.filter { selectedKeys.contains(it.id) } as ArrayList<Recording>
        val positions = getSelectedItemPositions()
        recordingsToRemove.forEach {
            val uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val selection = "${MediaStore.Audio.Media._ID} = ?"
            val selectionArgs = arrayOf(it.id.toString())
            activity.contentResolver.delete(uri, selection, selectionArgs)
        }

        recordings.removeAll(recordingsToRemove)
        activity.runOnUiThread {
            if (recordings.isEmpty()) {
                refreshListener.refreshRecordings()
                finishActMode()
            } else {
                removeSelectedItems(positions)
                if (recordingsToRemove.map { it.id }.contains(currRecordingId)) {
                    val newRecordingIndex = Math.min(oldRecordingIndex, recordings.size - 1)
                    val newRecording = recordings[newRecordingIndex]
                    refreshListener.playRecording(newRecording)
                }
            }
        }
    }

    fun updateCurrentRecording(newId: Int) {
        val oldId = currRecordingId
        currRecordingId = newId
        notifyItemChanged(recordings.indexOfFirst { it.id == oldId })
        notifyItemChanged(recordings.indexOfFirst { it.id == newId })
    }

    private fun getSelectedItems() = recordings.filter { selectedKeys.contains(it.id) } as ArrayList<Recording>

    private fun setupView(view: View, recording: Recording) {
        view.apply {
            recording_frame?.isSelected = selectedKeys.contains(recording.id)

            arrayListOf<TextView>(recording_title, recording_date, recording_duration, recording_size).forEach {
                it.setTextColor(textColor)
            }

            if (recording.id == currRecordingId) {
                recording_title.setTextColor(context.getAdjustedPrimaryColor())
            }

            recording_title.text = recording.title
            recording_date.text = recording.timestamp.formatDate(context)
            recording_duration.text = recording.duration.getFormattedDuration()
            recording_size.text = recording.size.formatSize()
        }
    }
}
