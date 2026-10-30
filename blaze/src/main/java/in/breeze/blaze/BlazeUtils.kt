package `in`.breeze.blaze

import android.net.Uri
import `in`.breeze.blaze.security.NavigationPolicy
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

fun dropsExternalLinks(payload: JSONObject): Boolean {
    return payload.optJSONObject("payload")?.optBoolean("dropExternalLinks") == true
}

fun getContentOrigin(payload: JSONObject): String? {
    val contentOrigin = payload.optJSONObject("payload")?.optString("contentOrigin") ?: return null
    return NavigationPolicy.originOf(contentOrigin)
}

fun getBaseUrl(payload: JSONObject): String {
    if (getService(payload) == BlazeConstants.SHOP_SERVICE) {
        return (getContentOrigin(payload) ?: "") + BlazeConstants.SHOP_FRAME_PATH
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
