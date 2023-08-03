package com.simplemobiletools.voicerecorder.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.beGoneIf
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.voicerecorder.databinding.DialogDeleteConfirmationBinding
import com.simplemobiletools.commons.R as CommonsR

class DeleteConfirmationDialog(
    private val activity: Activity,
    private val message: String,
    private val showSkipRecycleBinOption: Boolean,
    private val callback: (skipRecycleBin: Boolean) -> Unit
) {

    private var dialog: AlertDialog? = null
    val binding = DialogDeleteConfirmationBinding.inflate(activity.layoutInflater)
    val view = binding.root

    init {
        binding.deleteRememberTitle.text = message
        binding.skipTheRecycleBinCheckbox.beGoneIf(!showSkipRecycleBinOption)
        activity.getAlertDialogBuilder()
            .setPositiveButton(CommonsR.string.yes) { _, _ -> dialogConfirmed() }
            .setNegativeButton(CommonsR.string.no, null)
            .apply {
                activity.setupDialogStuff(view, this) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun dialogConfirmed() {
        dialog?.dismiss()
        callback(binding.skipTheRecycleBinCheckbox.isChecked)
    }
}
