package org.fcitx.fcitx5.android.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

sealed class Command {

    abstract fun execute(context: Context, rootDir: File)

    data class ToastCommand(val message: String, val duration: Int = Toast.LENGTH_LONG) :
        Command() {
        override fun execute(context: Context, rootDir: File) {
            Toast.makeText(context, message, duration).show()
        }
    }

    data class DeleteDirCommand(
        val dirName: String
    ) : Command() {
        override fun execute(
            context: Context,
            rootDir: File
        ) {
            val dir = File(rootDir, dirName)
            if (!dir.exists()) {
                return
            }
            dir.deleteRecursively()
        }
    }

    data class ExtractCommand(
        val zipFileName: String, val outputDirName: String
    ) : Command() {
        override fun execute(context: Context, rootDir: File) {
            val zipFile = File(rootDir, zipFileName)
            if (!zipFile.exists()) {
                Toast.makeText(context, "zip not found", Toast.LENGTH_SHORT).show()
                return
            }
            val outputDir = File(rootDir.parentFile?.parentFile, outputDirName)
            outputDir.mkdirs()
            ZipInputStream(FileInputStream(zipFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(outputDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use {
                            zip.copyTo(it)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    data class InstallApkCommand(val fileName: String) : Command() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun execute(context: Context, rootDir: File) {
            val file = File(rootDir, fileName)
            if (!file.exists()) {
                Toast.makeText(context, "apk not found", Toast.LENGTH_SHORT).show()
                return
            }
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                ).apply {
                    data = android.net.Uri.parse(
                        "package:${context.packageName}"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "请允许安装未知应用", Toast.LENGTH_LONG).show()
                return
            }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    uri, "application/vnd.android.package-archive"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }
    }

    companion object {
        private val gson = Gson()
        fun parse(commandFile: File): List<Command> {
            val json = commandFile.readText()
            val lines = gson.fromJson(json, Array<String>::class.java)
            return lines.mapNotNull { parseLine(it) }
        }

        private fun parseLine(line: String): Command? {
            val content = line.trim()
            return when {
                content.startsWith("toast ") -> {
                    ToastCommand(content.removePrefix("toast ").trim())
                }
                content.startsWith("installapk ") -> {
                    InstallApkCommand(content.removePrefix("installapk ").trim())
                }
                content.startsWith("extract ") -> {
                    val args = content.removePrefix("extract ").split(" ")
                    if (args.size < 2) {
                        return null
                    }
                    ExtractCommand(zipFileName = args[0], outputDirName = args[1])
                }
                content.startsWith("delete ") -> {
                    DeleteDirCommand(content.removePrefix("delete ").trim())
                }
                else -> null
            }
        }
    }
}