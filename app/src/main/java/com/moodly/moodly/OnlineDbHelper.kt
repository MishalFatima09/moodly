import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.moodly.moodly.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException


data class ApiResponse(
    val status: Int,
    val data: DataResponse?,
    val error: ErrorResponse?
)
data class DataResponse(
    // From executeQuery
    val type: String?,
    val rows: List<Map<String, Any>>?,
    val affectedRows: Int?,

    // From uploadImage
    val url: String?
)

data class ErrorResponse(
    val message: String
)

data class QueryRequest(
    val query: String,
    val params: List<Any>
)

data class UploadImageRequest(
    val image_base64: String
)

object OnlineDbHelper {

    private val SQL_API_URL = AppConfig.SQL_API_URL
    private val UPLOAD_API_URL = AppConfig.UPLOAD_API_URL

    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun executeQuery(
        query: String,
        params: List<Any> = emptyList(),
        onResult: (ApiResponse?, Exception?) -> Unit
    ) {
        val requestModel = QueryRequest(query, params)
        val requestJson = gson.toJson(requestModel)
        val requestBody = requestJson.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(SQL_API_URL)
            .post(requestBody)
            .build()

        //enqueue for asynchronous call
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    onResult(null, e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful || responseBody == null) {
                        throw IOException("Server error: ${response.code}")
                    }

                    val apiResponse = gson.fromJson(responseBody, ApiResponse::class.java)
                    mainHandler.post {
                        onResult(apiResponse, null)
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        onResult(null, e)
                    }
                }
            }
        })
    }

    fun uploadImage(
        base64Image: String,
        compressionThresholdInBytes: Long = 500_000, // 500KB default
        compressionQuality: Int = 80,
        onResult: (String?, Exception?) -> Unit
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                var fileBytes = Base64.decode(base64Image, Base64.DEFAULT)
                //Compress file
                if (fileBytes.size > compressionThresholdInBytes) {
                    Log.d("OnlineDbHelper", "Compressing image...")
                    val bitmap = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.size)
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, compressionQuality, outputStream)
                    fileBytes = outputStream.toByteArray()
                    Log.d("OnlineDbHelper", "Compressed to ${fileBytes.size} bytes.")
                }
                val finalBase64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

                //send data
                val requestModel = UploadImageRequest(finalBase64Data)
                val requestJson = gson.toJson(requestModel)
                val requestBody = requestJson.toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(UPLOAD_API_URL)
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        mainHandler.post { onResult(null, e) }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val responseBody = response.body?.string()
                            if (!response.isSuccessful || responseBody == null) {
                                throw IOException("Server error: ${response.code}")
                            }
                            val apiResponse = gson.fromJson(responseBody, ApiResponse::class.java)
                            if (apiResponse.status == 1 && apiResponse.data?.url != null) {
                                // Success
                                mainHandler.post { onResult(apiResponse.data.url, null) }
                            } else {
                                val message = apiResponse.error?.message ?: "Unknown API error"
                                throw IOException(message)
                            }
                        } catch (e: Exception) {
                            mainHandler.post { onResult(null, e) }
                        }
                    }
                })

            } catch (e: Exception) {
                Log.e("OnlineDbHelper", "Image processing failed: ${e.message}", e)
                mainHandler.post { onResult(null, e) }
            }
        }
    }
    fun executeQueryFireAndForget(query: String, params: List<Any> = emptyList()) {
        // Launch in the global scope, not tied to any Activity lifecycle
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val requestModel = QueryRequest(query, params)
                val requestJson = gson.toJson(requestModel)
                val requestBody = requestJson.toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(SQL_API_URL)
                    .post(requestBody)
                    .build()

                // Use .execute() for a SYNCHRONOUS call on this background thread.
                // This blocks the coroutine (not the main thread) until it's done.
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val apiResponse = gson.fromJson(responseBody, ApiResponse::class.java)
                    if (apiResponse.status == 1) {
                        Log.d("OnlineDbHelper", "Fire-and-forget success.")
                    } else {
                        Log.e(
                            "OnlineDbHelper",
                            "Fire-and-forget API error: ${apiResponse.error?.message}"
                        )
                    }
                } else {
                    Log.e("OnlineDbHelper", "Fire-and-forget server error: ${response.code}")
                }
                response.close() // Always close the response

            } catch (e: Exception) {
                Log.e("OnlineDbHelper", "Fire-and-forget failure: ${e.message}")
            }
        }
    }

    suspend fun executeSyncQuery(query: String, params: List<Any>): ApiResponse = withContext(Dispatchers.IO) {
        val requestModel = QueryRequest(query, params)
        val requestJson = gson.toJson(requestModel)
        val requestBody = requestJson.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(SQL_API_URL)
            .post(requestBody)
            .build()

        // Use .execute() for synchronous call
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful || responseBody == null) {
                throw IOException("Server error during sync query: ${response.code}")
            }
            return@withContext gson.fromJson(responseBody, ApiResponse::class.java)
        }
    }

    suspend fun uploadImageSync(base64Image: String): String? = withContext(Dispatchers.IO) {
        var fileBytes = Base64.decode(base64Image, Base64.DEFAULT)

        // NOTE: Compression logic is omitted here for brevity, but you should add it
        // from your existing uploadImage function to be complete.

        val finalBase64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

        val requestModel = UploadImageRequest(finalBase64Data)
        val requestJson = gson.toJson(requestModel)
        val requestBody = requestJson.toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(UPLOAD_API_URL)
            .post(requestBody)
            .build()

        // Use .execute() for synchronous call
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful || responseBody == null) {
                throw IOException("Server error during image sync upload: ${response.code}")
            }
            val apiResponse = gson.fromJson(responseBody, ApiResponse::class.java)

            if (apiResponse.status == 1 && apiResponse.data?.url != null) {
                return@withContext apiResponse.data.url
            } else {
                val message = apiResponse.error?.message ?: "Unknown API error during upload"
                throw IOException(message)
            }
        }
    }
}