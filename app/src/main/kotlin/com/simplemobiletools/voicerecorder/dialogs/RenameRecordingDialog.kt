package com.simplemobiletools.voicerecorder.dialogs

import android.content.ContentValues
import android.provider.MediaStore.Audio.Media
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.voicerecorder.databinding.DialogRenameRecordingBinding
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.helpers.getAudioFileContentUri
import com.simplemobiletools.voicerecorder.models.Events
import com.simplemobiletools.voicerecorder.models.Recording
import org.greenrobot.eventbus.EventBus
import java.io.File
import com.simplemobiletools.commons.R as CommonsR

class RenameRecordingDialog(val activity: BaseSimpleActivity, val recording: Recording, val callback: () -> Unit) {
    init {
        val binding = DialogRenameRecordingBinding.inflate(activity.layoutInflater).apply {
            renameRecordingTitle.setText(recording.title.substringBeforeLast('.'))
        }
        val view = binding.root

        activity.getAlertDialogBuilder()
            .setPositiveButton(CommonsR.string.ok, null)
            .setNegativeButton(CommonsR.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, CommonsR.string.rename) { alertDialog ->
                    alertDialog.showKeyboard(binding.renameRecordingTitle)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = binding.renameRecordingTitle.value
                        if (newTitle.isEmpty()) {
                            activity.toast(CommonsR.string.empty_name)
                            return@setOnClickListener
                        }

                        if (!newTitle.isAValidFilename()) {
                            activity.toast(CommonsR.string.invalid_name)
                            return@setOnClickListener
                        }

                        ensureBackgroundThread {
                            if (isRPlus()) {
                                updateMediaStoreTitle(recording, newTitle)
                            } else {
                                updateLegacyFilename(recording, newTitle)
                            }

                            activity.runOnUiThread {
                                callback()
                                alertDialog.dismiss()
                            }
                        }
                    }
                }
            }
    }

    private fun updateMediaStoreTitle(recording: Recording, newTitle: String) {
        val oldExtension = recording.title.getFilenameExtension()
        val newDisplayName = "${newTitle.removeSuffix(".$oldExtension")}.$oldExtension"

        val values = ContentValues().apply {
            put(Media.TITLE, newTitle.substringAfterLast('.'))
            put(Media.DISPLAY_NAME, newDisplayName)
        }

        // if the old way of renaming fails, try the new SDK 30 one on Android 11+
        try {
            activity.contentResolver.update(getAudioFileContentUri(recording.id.toLong()), values, null, null)
        } catch (e: Exception) {
            try {
                val path = "${activity.config.saveRecordingsFolder}/${recording.title}"
                val newPath = "${path.getParentPath()}/$newDisplayName"
                activity.handleSAFDialogSdk30(path) {
                    val success = activity.renameDocumentSdk30(path, newPath)
                    if (success) {
                        EventBus.getDefault().post(Events.RecordingCompleted())
                    }
                }
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }
        }
    }

    private fun updateLegacyFilename(recording: Recording, newTitle: String) {
        val oldExtension = recording.title.getFilenameExtension()
        val oldPath = recording.path
        val newFilename = "${newTitle.removeSuffix(".$oldExtension")}.$oldExtension"
        val newPath = File(oldPath.getParentPath(), newFilename).absolutePath
        activity.renameFile(oldPath, newPath, false)
    }
}
