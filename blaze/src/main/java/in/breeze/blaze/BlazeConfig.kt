package `in`.breeze.blaze

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal object BlazeConfig {

  private val executor = Executors.newSingleThreadExecutor()
  private val mainHandler = Handler(Looper.getMainLooper())

  @Volatile
  private var cachedConfig: JSONObject? = null

  @Volatile
  private var hasFetched: Boolean = false

  fun resolveFrameUrl(
    context: Context,
    service: String,
    environment: String,
    callback: (frameUrl: String?) -> Unit
  ) {
    val appContext = context.applicationContext

    val inMemoryConfig = cachedConfig
    if (inMemoryConfig != null) {
      callback(frameUrlFor(inMemoryConfig, service, environment))
      refresh(appContext)
      return
    }

    executor.execute {
      val persistedConfig = loadPersistedConfig(appContext)
      if (persistedConfig != null) {
        cachedConfig = persistedConfig
      }
      val config = persistedConfig ?: fetchAndStore(appContext)
      mainHandler.post { callback(config?.let { frameUrlFor(it, service, environment) }) }
      refresh(appContext)
    }
  }

  private fun refresh(context: Context) {
    if (hasFetched) {
      return
    }
    executor.execute { fetchAndStore(context) }
  }

  private fun fetchAndStore(context: Context): JSONObject? {
    if (hasFetched) {
      return cachedConfig
    }
    hasFetched = true

    try {
      val config = fetchConfig()
      if (config != null) {
        persistConfig(prefs(context), config)
        cachedConfig = config
        return config
      }
    } catch (e: Exception) {
      Log.e("BlazeSDK: config: ", e.message.toString())
    }
    return cachedConfig
  }

  private fun loadPersistedConfig(context: Context): JSONObject? {
    try {
      return readPersistedConfig(prefs(context))
    } catch (e: Exception) {
      Log.e("BlazeSDK: config: ", e.message.toString())
      return null
    }
  }

  private fun prefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(BlazeConstants.SDK_CONFIG_PREF_NAME, Context.MODE_PRIVATE)
  }

  private fun frameUrlFor(config: JSONObject, service: String, environment: String): String? {
    if (service.isBlank() || environment.isBlank()) {
      return null
    }

    val frameUrls = config.optJSONObject("services")
      ?.optJSONObject(service)
      ?.optJSONObject("frameUrls") ?: return null

    if (frameUrls.isNull(environment)) {
      return null
    }

    val frameUrl = frameUrls.optString(environment)
    if (!frameUrl.startsWith("https://", ignoreCase = true)) {
      return null
    }
    return frameUrl
  }

  private fun fetchConfig(): JSONObject? {
    var connection: HttpURLConnection? = null
    try {
      connection = (URL(BlazeConstants.SDK_CONFIG_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = BlazeConstants.SDK_CONFIG_TIMEOUT_MS
        readTimeout = BlazeConstants.SDK_CONFIG_TIMEOUT_MS
        setRequestProperty("Accept", "application/json")
      }
      if (connection.responseCode !in 200..299) {
        Log.e("BlazeSDK: config: ", "Unexpected response ${connection.responseCode}")
        return null
      }
      val body = connection.inputStream.bufferedReader().use { it.readText() }
      val config = JSONObject(body)
      return if (config.optJSONObject("services") == null) null else config
    } catch (e: Exception) {
      Log.e("BlazeSDK: config: ", e.message.toString())
      return null
    } finally {
      connection?.disconnect()
    }
  }

  private fun readPersistedConfig(prefs: SharedPreferences): JSONObject? {
    val config = prefs.getString(BlazeConstants.SDK_CONFIG_KEY, null) ?: return null
    val configJson = safeParseJson(config)
    return if (configJson.optJSONObject("services") == null) null else configJson
  }

  private fun persistConfig(prefs: SharedPreferences, config: JSONObject) {
    prefs.edit {
      putString(BlazeConstants.SDK_CONFIG_KEY, config.toString())
    }
  }
}
