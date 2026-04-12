package com.example.thread_number_app.ui.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

fun uriToFile(context: Context, uri: Uri): File {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Tidak bisa membuka URI: $uri")

    val file = File(
        context.cacheDir,
        "image_${System.currentTimeMillis()}.jpg"
    )

    FileOutputStream(file).use { output ->
        inputStream.copyTo(output)
    }

    inputStream.close()
    return file
}
