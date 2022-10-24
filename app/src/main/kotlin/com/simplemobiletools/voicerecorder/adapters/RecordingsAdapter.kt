package com.simplemobiletools.voicerecorder.adapters

import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.view.*
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.net.toUri
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.voicerecorder.BuildConfig
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.activities.SimpleActivity
import com.simplemobiletools.voicerecorder.dialogs.RenameRecordingDialog
import com.simplemobiletools.voicerecorder.helpers.getAudioFileContentUri
import com.simplemobiletools.voicerecorder.interfaces.RefreshRecordingsListener
import com.simplemobiletools.voicerecorder.models.Recording
import kotlinx.android.synthetic.main.item_recording.view.*
import java.io.File

class RecordingsAdapter(
    activity: SimpleActivity,
    var recordings: ArrayList<Recording>,
    val refreshListener: RefreshRecordingsListener,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) :
    MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    var currRecordingId = 0

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_recordings

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = isOneItemSelected()
            findItem(R.id.cab_open_with).isVisible = isOneItemSelected()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_rename -> renameRecording()
            R.id.cab_share -> shareRecordings()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_select_all -> selectAll()
            R.id.cab_open_with -> openRecordingWith()
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
            notifyDataSetChanged()
            finishActMode()
        }
    }

    private fun renameRecording() {
        val recording = getItemWithKey(selectedKeys.first()) ?: return
        RenameRecordingDialog(activity, recording) {
            finishActMode()
            refreshListener.refreshRecordings()
        }
    }

    private fun openRecordingWith() {
        val recording = getItemWithKey(selectedKeys.first()) ?: return
        val path = if (isQPlus()) {
            getAudioFileContentUri(recording.id.toLong()).toString()
        } else {
            recording.path
        }

        activity.openPathIntent(path, false, BuildConfig.APPLICATION_ID, "audio/*")
    }

    private fun shareRecordings() {
        val selectedItems = getSelectedItems()
        val paths = selectedItems.map {
            it.path.ifEmpty {
                getAudioFileContentUri(it.id.toLong()).toString()
            }
        }

        activity.sharePathsIntent(paths, BuildConfig.APPLICATION_ID)
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

        when {
            isRPlus() -> {
                val fileUris = recordingsToRemove.map { recording ->
                    "${Media.EXTERNAL_CONTENT_URI}/${recording.id.toLong()}".toUri()
                }

                activity.deleteSDK30Uris(fileUris) { success ->
                    if (success) {
                        doDeleteAnimation(oldRecordingIndex, recordingsToRemove, positions)
                    }
                }
            }
            isQPlus() -> {
                recordingsToRemove.forEach {
                    val uri = Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val selection = "${Media._ID} = ?"
                    val selectionArgs = arrayOf(it.id.toString())
                    val result = activity.contentResolver.delete(uri, selection, selectionArgs)

                    if (result == 0) {
                        recordingsToRemove.forEach {
                            activity.deleteFile(File(it.path).toFileDirItem(activity))
                        }
                    }
                }
                doDeleteAnimation(oldRecordingIndex, recordingsToRemove, positions)
            }
            else -> {
                recordingsToRemove.forEach {
                    activity.deleteFile(File(it.path).toFileDirItem(activity))
                }
                doDeleteAnimation(oldRecordingIndex, recordingsToRemove, positions)
            }
        }
    }

    private fun doDeleteAnimation(oldRecordingIndex: Int, recordingsToRemove: ArrayList<Recording>, positions: ArrayList<Int>) {
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
                    refreshListener.playRecording(newRecording, false)
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
                recording_title.setTextColor(context.getProperPrimaryColor())
            }

            recording_title.text = recording.title
            recording_date.text = recording.timestamp.formatDate(context)
            recording_duration.text = recording.duration.getFormattedDuration()
            recording_size.text = recording.size.formatSize()

            overflow_menu_icon.drawable.apply {
                mutate()
                setTint(activity.getProperTextColor())
            }

            overflow_menu_icon.setOnClickListener {
                showPopupMenu(overflow_menu_anchor, recording)
            }
        }
    }

    override fun onChange(position: Int) = recordings.getOrNull(position)?.title ?: ""

    private fun showPopupMenu(view: View, recording: Recording) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(getActionMenuId())
            menu.findItem(R.id.cab_select_all).isVisible = false
            setOnMenuItemClickListener { item ->
                val recordingId = recording.id
                when (item.itemId) {
                    R.id.cab_rename -> {
                        executeItemMenuOperation(recordingId) {
                            renameRecording()
                        }
                    }
                    R.id.cab_share -> {
                        executeItemMenuOperation(recordingId) {
                            shareRecordings()
                        }
                    }
                    R.id.cab_open_with -> {
                        executeItemMenuOperation(recordingId) {
                            openRecordingWith()
                        }
                    }
                    R.id.cab_delete -> {
                        executeItemMenuOperation(recordingId, removeAfterCallback = false) {
                            askConfirmDelete()
                        }
                    }
                }

                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(callId: Int, removeAfterCallback: Boolean = true, callback: () -> Unit) {
        selectedKeys.add(callId)
        callback()
        if (removeAfterCallback) {
            selectedKeys.remove(callId)
        }
    }
}
