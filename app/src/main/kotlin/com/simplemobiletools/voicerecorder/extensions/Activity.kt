package com.simplemobiletools.voicerecorder.extensions

import android.content.ContentValues
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import androidx.core.net.toUri
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.deleteFile
import com.simplemobiletools.commons.extensions.getParentPath
import com.simplemobiletools.commons.extensions.toFileDirItem
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.voicerecorder.models.Recording
import java.io.File

fun BaseSimpleActivity.deleteRecordings(recordingsToRemove: Collection<Recording>, callback: (success: Boolean) -> Unit) {
    when {
        isRPlus() -> {
            val fileUris = recordingsToRemove.map { recording ->
                "${Media.EXTERNAL_CONTENT_URI}/${recording.id.toLong()}".toUri()
            }

            deleteSDK30Uris(fileUris, callback)
        }

        isQPlus() -> {
            recordingsToRemove.forEach {
                val uri = Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${Media._ID} = ?"
                val selectionArgs = arrayOf(it.id.toString())
                val result = contentResolver.delete(uri, selection, selectionArgs)

                if (result == 0) {
                    val fileDirItem = File(it.path).toFileDirItem(this)
                    deleteFile(fileDirItem)
                }
            }
            callback(true)
        }

        else -> {
            recordingsToRemove.forEach {
                val fileDirItem = File(it.path).toFileDirItem(this)
                deleteFile(fileDirItem)
            }
            callback(true)
        }
    }
}

fun BaseSimpleActivity.restoreRecordings(recordingsToRestore: Collection<Recording>, callback: (success: Boolean) -> Unit) {
    when {
        isRPlus() -> {
            val fileUris = recordingsToRestore.map { recording ->
                "${Media.EXTERNAL_CONTENT_URI}/${recording.id.toLong()}".toUri()
            }

            trashSDK30Uris(fileUris, false, callback)
        }

        isQPlus() -> {
            var wait = false
            recordingsToRestore.forEach {
                val uri = Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${Media._ID} = ?"
                val selectionArgs = arrayOf(it.id.toString())
                val values = ContentValues().apply {
                    put(Media.IS_TRASHED, 0)
                }
                val result = contentResolver.update(uri, values, selection, selectionArgs)

                if (result == 0) {
                    wait = true
                    copyMoveFilesTo(
                        fileDirItems = arrayListOf(File(it.path).toFileDirItem(this)),
                        source = it.path.getParentPath(),
                        destination = config.saveRecordingsFolder,
                        isCopyOperation = false,
                        copyPhotoVideoOnly = false,
                        copyHidden = false
                    ) {
                        callback(true)
                    }
                }
            }
            if (!wait) {
                callback(true)
            }
        }

        else -> {
            copyMoveFilesTo(
                fileDirItems = recordingsToRestore.map { File(it.path).toFileDirItem(this) }.toMutableList() as ArrayList<FileDirItem>,
                source = recordingsToRestore.first().path.getParentPath(),
                destination = config.saveRecordingsFolder,
                isCopyOperation = false,
                copyPhotoVideoOnly = false,
                copyHidden = false
            ) {
                callback(true)
            }
        }
    }
}

fun BaseSimpleActivity.moveRecordingsToRecycleBin(recordingsToMove: Collection<Recording>, callback: (success: Boolean) -> Unit) {
    when {
        isRPlus() -> {
            val fileUris = recordingsToMove.map { recording ->
                "${Media.EXTERNAL_CONTENT_URI}/${recording.id.toLong()}".toUri()
            }

            trashSDK30Uris(fileUris, true, callback)
        }

        isQPlus() -> {
            var wait = false
            recordingsToMove.forEach {
                val uri = Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${Media._ID} = ?"
                val selectionArgs = arrayOf(it.id.toString())
                val values = ContentValues().apply {
                    put(Media.IS_TRASHED, 1)
                }
                val result = contentResolver.update(uri, values, selection, selectionArgs)

                if (result == 0) {
                    wait = true
                    copyMoveFilesTo(
                        fileDirItems = arrayListOf(File(it.path).toFileDirItem(this)),
                        source = it.path.getParentPath(),
                        destination = getOrCreateTrashFolder(),
                        isCopyOperation = false,
                        copyPhotoVideoOnly = false,
                        copyHidden = false
                    ) {
                        callback(true)
                    }
                }
            }
            if (!wait) {
                callback(true)
            }
        }

        else -> {
            copyMoveFilesTo(
                fileDirItems = recordingsToMove.map { File(it.path).toFileDirItem(this) }.toMutableList() as ArrayList<FileDirItem>,
                source = recordingsToMove.first().path.getParentPath(),
                destination = getOrCreateTrashFolder(),
                isCopyOperation = false,
                copyPhotoVideoOnly = false,
                copyHidden = false
            ) {
                callback(true)
            }
        }
    }
}

fun BaseSimpleActivity.checkRecycleBinItems() {
    if (config.useRecycleBin && config.lastRecycleBinCheck < System.currentTimeMillis() - DAY_SECONDS * 1000) {
        config.lastRecycleBinCheck = System.currentTimeMillis()
        ensureBackgroundThread {
            try {
                deleteRecordings(getLegacyRecordings(trashed = true).filter { it.timestamp < System.currentTimeMillis() - MONTH_SECONDS * 1000L }) {}
            } catch (e: Exception) {
            }
        }
    }
}

fun BaseSimpleActivity.emptyTheRecycleBin() {
    deleteRecordings(getAllRecordings(trashed = true)) {}
}
