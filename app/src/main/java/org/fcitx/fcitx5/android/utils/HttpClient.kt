package org.fcitx.fcitx5.android.utils

import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object HttpClient {
    val gson = Gson()

    val client by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
    }

    suspend inline fun <reified T> get(
        url: String, headers: Map<String, String> = emptyMap()
    ): T = withContext(Dispatchers.IO) {

        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) ->
                addHeader(k, v)
            }
        }.get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body.string()
            gson.fromJson(body, T::class.java)
        }
    }

    suspend inline fun <reified T> post(
        url: String, body: Any, headers: Map<String, String> = emptyMap()
    ): T = withContext(Dispatchers.IO) {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(
            "application/json".toMediaTypeOrNull()
        )
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) ->
                addHeader(k, v)
            }
        }.post(requestBody).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val responseBody = response.body.string()
            gson.fromJson(responseBody, T::class.java)
        }
    }

    suspend fun download(
        url: String,
        targetFile: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) ->
                addHeader(k, v)
            }
        }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body
            val total = body.contentLength()
            body.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) {
                            break
                        }
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress?.invoke(downloaded, total)
                    }
                    output.flush()
                }
            }
            targetFile
        }
    }
}