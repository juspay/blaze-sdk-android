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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal class RemoteFrame(val frameUrl: String?, val allowedOrigins: List<String>?)

internal object BlazeConfig {

  private class ConfigFile(
    val url: String,
    val prefsKey: String,
    val isValid: (JSONObject) -> Boolean
  )

  private val executor = Executors.newSingleThreadExecutor()
  private val mainHandler = Handler(Looper.getMainLooper())
  private val cachedConfigs = ConcurrentHashMap<String, JSONObject>()
  private val fetchedUrls: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

  private val hasServices: (JSONObject) -> Boolean = { it.optJSONObject("services") != null }

  private val configFile = ConfigFile(
    BlazeConstants.SDK_CONFIG_URL,
    BlazeConstants.SDK_CONFIG_KEY,
    hasServices
  )

  fun resolveFrame(
    context: Context,
    service: String,
    environment: String,
    callback: (remote: RemoteFrame?) -> Unit
  ) {
    load(context.applicationContext, configFile) { config ->
      callback(config?.let {
        RemoteFrame(frameUrlFor(it, service, environment), originsFor(it, service, environment))
      })
    }
  }

  private fun originsFor(config: JSONObject, service: String, environment: String): List<String>? {
    val origins = config.optJSONObject("services")
      ?.optJSONObject(service)
      ?.optJSONObject("allowedOrigins")
      ?.optJSONArray(environment) ?: return null
    return (0 until origins.length()).map { origins.optString(it) }
  }

  private fun load(context: Context, file: ConfigFile, callback: (JSONObject?) -> Unit) {
    val inMemoryConfig = cachedConfigs[file.url]
    if (inMemoryConfig != null) {
      callback(inMemoryConfig)
      refresh(context, file)
      return
    }

    executor.execute {
      val persistedConfig = loadPersistedConfig(context, file)
      if (persistedConfig != null) {
        cachedConfigs[file.url] = persistedConfig
      }
      val config = persistedConfig ?: fetchAndStore(context, file)
      mainHandler.post { callback(config) }
      refresh(context, file)
    }
  }

  private fun refresh(context: Context, file: ConfigFile) {
    if (file.url in fetchedUrls) {
      return
    }
    executor.execute { fetchAndStore(context, file) }
  }

  private fun fetchAndStore(context: Context, file: ConfigFile): JSONObject? {
    if (!fetchedUrls.add(file.url)) {
      return cachedConfigs[file.url]
    }

    try {
      val config = fetchConfig(file)
      if (config != null) {
        prefs(context).edit { putString(file.prefsKey, config.toString()) }
        cachedConfigs[file.url] = config
        return config
      }
    } catch (e: Exception) {
      Log.e("BlazeSDK: config: ", e.message.toString())
    }
    fetchedUrls.remove(file.url)
    return cachedConfigs[file.url]
  }

  private fun loadPersistedConfig(context: Context, file: ConfigFile): JSONObject? {
    try {
      val config = prefs(context).getString(file.prefsKey, null) ?: return null
      val configJson = safeParseJson(config)
      return if (file.isValid(configJson)) configJson else null
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

  private fun fetchConfig(file: ConfigFile): JSONObject? {
    var connection: HttpURLConnection? = null
    try {
      connection = (URL(file.url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = BlazeConstants.SDK_CONFIG_TIMEOUT_MS
        readTimeout = BlazeConstants.SDK_CONFIG_TIMEOUT_MS
        instanceFollowRedirects = false
        setRequestProperty("Accept", "application/json")
      }
      if (connection.responseCode !in 200..299) {
        Log.e("BlazeSDK: config: ", "Unexpected response ${connection.responseCode}")
        return null
      }
      if (!isJson(connection.contentType)) {
        Log.e("BlazeSDK: config: ", "Unexpected content type ${connection.contentType}")
        return null
      }
      val body = connection.inputStream.bufferedReader().use { it.readText() }
      val config = JSONObject(body)
      return if (file.isValid(config)) config else null
    } catch (e: Exception) {
      Log.e("BlazeSDK: config: ", e.message.toString())
      return null
    } finally {
      connection?.disconnect()
    }
  }

  private fun isJson(contentType: String?): Boolean {
    return contentType?.trim()?.startsWith("application/json", ignoreCase = true) == true
  }
}
