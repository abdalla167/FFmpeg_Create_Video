package com.example.ffmpeg_create_video


import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build

import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.FFmpeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.Integer.min
import kotlin.math.ceil


class CreatVedio
{

    suspend fun convertToVideo2(
        context: Context,
        bitmapList: List<Bitmap?>,
        effectedBitmaps: List<Bitmap?>,
        nameFile: String,
        musicFilePath: String,
        durationInSeconds:Int,
        executed: (Boolean, String?) -> Unit
    )= runBlocking {
        val outputDirCap = File(context.cacheDir, "temp_cap")
        val outputDirEff = File(context.cacheDir, "temp_effect")

        outputDirCap.mkdirs()
        outputDirEff.mkdirs()

        val outputFilePath = File(context.cacheDir, "output.mp4").absolutePath
        val file = File(outputFilePath)
        file.delete()

        Log.d("TAG", "convertToVideo: $outputFilePath")


        val frameRate = 20 // Number of frames per second

        val totalFrames = 30 * frameRate
        val framesPerImage = totalFrames / bitmapList.size


        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(getAudioFilePath(context,musicFilePath))
        val audioDuration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
        mediaMetadataRetriever.release()

        val duration = audioDuration?.div(1000)?.toInt() ?: 0 // Duration of the audio file in seconds

        // Adjust the duration to match the desired durationInSeconds for the video
        val adjustedDuration = min(duration, durationInSeconds)

        // Trim the audio file using FFmpeg

        val trimmedAudioFilePath = File(context.cacheDir, "trimmed_audio.mp3").absolutePath


        runBlocking {
            launch(Dispatchers.Default) {
                generateFrames(bitmapList, outputDirCap, totalFrames, framesPerImage)
            }

            launch(Dispatchers.Default) {
                generateFrames(effectedBitmaps, outputDirEff, totalFrames, framesPerImage)
            }


        }
        val trimCommand = arrayOf(
            "-i", getAudioFilePath(context,musicFilePath),
            "-ss", "0",
            "-t", adjustedDuration.toString(),
            "-c", "copy",
            "-y", trimmedAudioFilePath
        )



        val trimResult = FFmpeg.execute(trimCommand)
        if (trimResult == RETURN_CODE_SUCCESS) {


            var command = arrayOf(

                "-r", frameRate.toString(),
                "-i", "${outputDirCap.absolutePath}/frame%d.png",
                "-i", "${outputDirEff.absolutePath}/frame%d.png",
                "-i",trimmedAudioFilePath,
                "-filter_complex", "[0:v][1:v]overlay",
              //  "-vf", "select='eq(n,$lastFrameIndex)':n=1, setpts=N/FRAME_RATE/TB+$adjustedDuration/TB",
                "-c:a", "libmp3lame",
                "-y",
                outputFilePath

            )


            Log.d("TAG", "convertToVideo: $outputFilePath")


            try {
                val returnCode = FFmpeg.execute(command)
                Config.enableLogCallback { message -> Log.d("FFmpeg", message.toString()) }
                if (returnCode == RETURN_CODE_SUCCESS) {
                    Log.d("TAG", "Command execution completed successfully.")
                    outputDirCap.deleteRecursively()
                    outputDirEff.deleteRecursively()
                    executed(true, saveVideoToStorage(context, outputFilePath).toString())


                } else if (returnCode == RETURN_CODE_CANCEL) {
                    Log.d("TAG", "Command execution cancelled by user.")
                    outputDirCap.deleteRecursively()
                    outputDirEff.deleteRecursively()
                    executed(false, " execution cancelled by user")
                } else {
                    Log.d("TAG", "Command execution failed with returnCode=$returnCode.")
                    outputDirCap.deleteRecursively()
                    outputDirEff.deleteRecursively()
                    executed(false, "Command execution failed with returnCode=$returnCode.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        }


    private fun saveVideoToStorage(context: Context, videoFilePath: String): Uri? {
        val videoFile = File(videoFilePath)
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)


        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, videoFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
            val contentResolver = context.contentResolver
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let { destinationUri ->
                val outputStream = contentResolver.openOutputStream(destinationUri)
                outputStream?.use { output ->
                    val inputStream = FileInputStream(videoFile)
                    inputStream.use { input ->
                        input.copyTo(output)
                    }
                }

                // Notify the MediaScanner about the new video file
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destinationUri.path),
                    null,
                    null
                )

                // Return the Uri of the saved video file
                destinationUri
            }
        }
        else {
            val destFile = File(storageDir, videoFile.name)
            videoFile.copyTo(destFile, overwrite = true)

            // Notify the MediaScanner about the new video file
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                null,
                null
            )

            // Return the Uri of the saved video file
            Uri.fromFile(destFile)
        }
    }

    private fun getAudioFilePath(context: Context, uri: String): String? {
        var filePath: String? = null
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val cursor = context.contentResolver.query(Uri.parse(uri), projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                filePath = it.getString(columnIndex)
            }
        }
        return filePath
    }

    private fun generateFrames(bitmapList: List<Bitmap?>, outputDir: File, totalFrames: Int, framesPerImage: Int) {
        var frameCount = 0
        var bitmapIndex = 0

        while (frameCount < totalFrames) {
            val bitmap = bitmapList[bitmapIndex % bitmapList.size]
            val outputFile = File(outputDir, "frame$frameCount.png")

            try {
                val outputStream = BufferedOutputStream(outputFile.outputStream())
                val byteArrayOutputStream = ByteArrayOutputStream()

                bitmap?.compress(Bitmap.CompressFormat.PNG, 80, byteArrayOutputStream)
                outputStream.write(byteArrayOutputStream.toByteArray())

                outputStream.flush()
                outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            frameCount++
            if (frameCount % framesPerImage == 0) {
                bitmapIndex++
            }
        }
        Log.d("TAG", "generateFrames: ")
    }



}


