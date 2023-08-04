package com.simplemobiletools.voicerecorder.fragments

import android.content.Context
import android.util.AttributeSet
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.voicerecorder.activities.SimpleActivity
import com.simplemobiletools.voicerecorder.adapters.TrashAdapter
import com.simplemobiletools.voicerecorder.databinding.FragmentTrashBinding
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.extensions.getAllRecordings
import com.simplemobiletools.voicerecorder.interfaces.RefreshRecordingsListener
import com.simplemobiletools.voicerecorder.models.Events
import com.simplemobiletools.voicerecorder.models.Recording
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class TrashFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet), RefreshRecordingsListener {
    private var itemsIgnoringSearch = ArrayList<Recording>()
    private var lastSearchQuery = ""
    private var bus: EventBus? = null
    private var prevSavePath = ""
    private lateinit var binding: FragmentTrashBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentTrashBinding.bind(this)
    }

    override fun onResume() {
        setupColors()
        if (prevSavePath.isNotEmpty() && context!!.config.saveRecordingsFolder != prevSavePath) {
            itemsIgnoringSearch = getRecordings()
            setupAdapter(itemsIgnoringSearch)
        } else {
            getRecordingsAdapter()?.updateTextColor(context.getProperTextColor())
        }

        storePrevPath()
    }

    override fun onDestroy() {
        bus?.unregister(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        bus = EventBus.getDefault()
        bus!!.register(this)
        setupColors()
        itemsIgnoringSearch = getRecordings()
        setupAdapter(itemsIgnoringSearch)
        storePrevPath()
    }

    override fun refreshRecordings() {
        itemsIgnoringSearch = getRecordings()
        setupAdapter(itemsIgnoringSearch)
    }

    override fun playRecording(recording: Recording, playOnPrepared: Boolean) {}

    private fun setupAdapter(recordings: ArrayList<Recording>) {
        binding.trashFastscroller.beVisibleIf(recordings.isNotEmpty())
        binding.trashPlaceholder.beVisibleIf(recordings.isEmpty())
        if (recordings.isEmpty()) {
            val stringId = if (lastSearchQuery.isEmpty()) {
                com.simplemobiletools.commons.R.string.recycle_bin_empty
            } else {
                com.simplemobiletools.commons.R.string.no_items_found
            }

            binding.trashPlaceholder.text = context.getString(stringId)
        }

        val adapter = getRecordingsAdapter()
        if (adapter == null) {
            TrashAdapter(context as SimpleActivity, recordings, this, binding.trashList).apply {
                binding.trashList.adapter = this
            }

            if (context.areSystemAnimationsEnabled) {
                binding.trashList.scheduleLayoutAnimation()
            }
        } else {
            adapter.updateItems(recordings)
        }
    }

    private fun getRecordings(): ArrayList<Recording> {
        return context.getAllRecordings(trashed = true).apply {
            sortByDescending { it.timestamp }
        }
    }

    fun onSearchTextChanged(text: String) {
        lastSearchQuery = text
        val filtered = itemsIgnoringSearch.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Recording>
        setupAdapter(filtered)
    }

    private fun getRecordingsAdapter() = binding.trashList.adapter as? TrashAdapter

    private fun storePrevPath() {
        prevSavePath = context!!.config.saveRecordingsFolder
    }

    private fun setupColors() {
        val properPrimaryColor = context.getProperPrimaryColor()
        binding.trashFastscroller.updateColors(properPrimaryColor)
        context.updateTextColors(binding.trashHolder)
    }

    fun finishActMode() = getRecordingsAdapter()?.finishActMode()

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun recordingMovedToRecycleBin(event: Events.RecordingTrashUpdated) {
        refreshRecordings()
    }
}
