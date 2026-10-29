package `in`.breeze.blaze

import android.net.Uri
import org.json.JSONObject

fun safeParseJson(jsonString: String): JSONObject {
    return try {
        JSONObject(jsonString)
    } catch (e: Exception) {
        JSONObject()
    }
}

fun getEnvironment(payload: JSONObject): String {
    val environment = payload.optJSONObject("payload")?.optString("environment")
    return if (environment.isNullOrBlank()) "release" else environment
}

fun getService(payload: JSONObject): String {
    return payload.optString("service")
}

fun getBaseUrl(payload: JSONObject): String {
    if (getService(payload) == "in.breeze.shop") {
        return "https://shop.breeze.in/sdk"
    }

    val environment = getEnvironment(payload)
    return if (environment == "smbBeta") {
        "https://app.beta.v2.breezesdk.store"
    } else if (environment == "smbRelease") {
        "https://app.v2.breezesdk.store"
    } else if (environment == "beta") {
        "https://app.beta.v2.breeze.in"
    } else {
        "https://app.v2.breeze.in"
    }
}
internal fun originOf(url: String?): String? {
    if (url.isNullOrBlank()) {
        return null
    }
    val uri = Uri.parse(url)
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (scheme != "https" || host.isEmpty()) {
        return null
    }
    val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    return "$scheme://$host$port"
}

fun isUPIIntentUri(uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase() ?: return false
    val queryParams = uri.queryParameterNames.associateBy(
        keySelector = { name -> name.lowercase() },
        valueTransform = { name -> uri.getQueryParameter(name).orEmpty() })

    val hasPayeeAddress = queryParams["pa"]?.isNotBlank() == true
    val hasPayeeName = queryParams["pn"]?.isNotBlank() == true
    val hasAmount = queryParams["am"]?.isNotBlank() == true
    val isKnownScheme = scheme in BlazeConstants.UPI_SCHEMES

    return hasPayeeAddress && hasPayeeName && hasAmount && isKnownScheme
}
