# Blaze SDK Android

The Blaze SDK is an easy-to-use toolkit that allows you to effortlessly integrate and utilize [Breeze 1 Click Checkout](https://breeze.in) & its services into your android app.

## Android SDK Integration

Follow the below steps to integrate Blaze SDK into your android application:

These steps open one-click checkout (`in.breeze.onecco`). For agentic commerce (`in.breeze.shop`), see [Agentic commerce](#agentic-commerce).

### Step 1: Obtaining the Blaze SDK

1.1. Add the JitPack repository to your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // JitPack hosts the Blaze SDK
    maven { url = uri("https://jitpack.io") }
  }
}
```

1.2. Add the SDK to your app module's `build.gradle.kts`, using the latest version from the [releases page](https://github.com/juspay/blaze-sdk-android/releases):

```kotlin
dependencies {
  implementation("com.github.juspay:blaze-sdk-android:<version>")
}
```

### Step 2: Initialize the SDK

**2.1. Create an instance of the `Blaze` class in your activity.**

```kotlin
val blaze = Blaze()
```

**2.2. Initiate the Blaze instance. Preferably in the `onCreate` method of your activity.**

Call `initiate` once. Further calls are ignored until you call `terminate` (Step 5).

In order to call initiate you need to perform the following steps:

#### 2.2.1: Construct the Initiate Payload

Create a Json with correct parameters to initiate the SDK. This is the data that will be used to initialize the SDK.

```kotlin
// Create a JSONObject for the Initiate data
val initiatePayload = JSONObject()
initiatePayload.put("merchantId", "<MERCHANT_ID>")
initiatePayload.put("environment", "<ENVIRONMENT>")
initiatePayload.put("shopUrl", "<SHOP_URL>")


// Place Initiate Payload into SDK Payload
val initSDKPayload = JSONObject()
initSDKPayload.put("requestId", "<UNIQUE_RANDOM_ID>")
initSDKPayload.put("service", "in.breeze.onecco")
initSDKPayload.put("payload", initiatePayload)

```

Note: Obtain values for `merchantId`, `environment` and `shopUrl` from the Breeze team.

All three are required; if one is missing, the `initiate` answer has `error: true`. `environment` is `beta`, `release`, `smbBeta` or `smbRelease`, and decides which Breeze environment the SDK loads.

#### 2.2.2: Construct the Callback Method

During the user journey the SDK will call the callback method with the result of the SDK operation.
You need to implement this method in order to handle the result of the SDK operation.

The callback receives a JSON object with `requestId`, `service` and `payload`. `payload.eventName` names the event: the first is `initiate`, in answer to your `initiate` call, and the rest depend on the service and the journey.

When something fails, `payload.error` is `true` and `payload.errorDetails` describes the failure for your logs. If the failure has a code, `payload.errorCode` names it; see [Error codes](docs/error-codes.md).

The SDK calls the callback on a background thread. Switch to the main thread before updating your UI, for example with `runOnUiThread`.

```kotlin

fun blazeCallbackHandler (event: JSONObject) {
  val payload = event.optJSONObject("payload") ?: return
  val eventName = payload.optString("eventName")

  when(eventName) {
    // Handle various events according your desired logic
  }
}

```

#### 2.2.3: Call the initiate method on Blaze Instance

Finally, call the initiate method on the Blaze instance with the payload and the callback method.
The first parameter is your activity.

```kotlin
blaze.initiate(this, initSDKPayload, ::blazeCallbackHandler)
```

***Combined Example:***

```kotlin
class MainActivity : ComponentActivity() {

    // Create Blaze Instance
    val blaze = Blaze()

    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)

      // 2.2 Initiate

      // 2.2.1 Create a JSONObject for the Initiate data
      val initiatePayload = JSONObject()
      initiatePayload.put("merchantId", "<MERCHANT_ID>")
      initiatePayload.put("environment", "<ENVIRONMENT>")
      initiatePayload.put("shopUrl", "<SHOP_URL>")

      // Place Initiate Payload into SDK Payload
      val initSDKPayload = JSONObject()
      initSDKPayload.put("requestId", "<UNIQUE_RANDOM_ID>")
      initSDKPayload.put("service", "in.breeze.onecco")
      initSDKPayload.put("payload", initiatePayload)

      // 2.2.3 Initiate Blaze SDK
      blaze.initiate(this, initSDKPayload, ::blazeCallbackHandler)
    }
}

// 2.2.2: Creating a Callback handler
fun blazeCallbackHandler (event: JSONObject) {
  val payload = event.optJSONObject("payload") ?: return
  val eventName = payload.optString("eventName")

  when(eventName) {
    // Handle various events according your desired logic
  }
}

```

### Step 3: Start processing your requests

Once the SDK is initiated, you can start processing your requests using the initialized instance of the SDK.
The SDK will call the callback method with the result of the SDK operation.

#### 3.1: Construct the Process Payload

Create a Json payload with the required parameters to process the request.
The process payload differs based on the request.
`action` is one of `startCheckout`, `startPayment`, `sendOTP`, `verifyOTP`, `listOffers`, `updateCustomer`, `listEMI`, `logout`, `manageProfile`, `manageAddress` or `verifyLogin`. Each action takes its own fields; get them from the Breeze team.

```kotlin

// 3.1 Create SDK Process Payload
// Create a JSONObject for the Process data
val processPayload = JSONObject()
processPayload.put("action", "<ACTION>")
// and more parameters required as per the action

// Place Process Payload into SDK Payload
val processSDKPayload = JSONObject()
processSDKPayload.put("requestId", "<UNIQUE_RANDOM_ID>")
processSDKPayload.put("service", "in.breeze.onecco")
processSDKPayload.put("payload", processPayload)

```

#### 3.2: Call the process method on Blaze Instance

Call the process method on the Blaze instance with the process payload to start the user journey or a headless flow.

```kotlin
blaze.process(processSDKPayload)
```

### Step 4: Handling Back Press

For making the hardware back button work as expected, you need to call the `handleBackPress` method on the Blaze instance.
Register an `OnBackPressedCallback` in your activity's `onCreate` and call it from there.
This method returns `true` when the SDK did not use the back press, and your app should handle it.

```kotlin
onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
  override fun handleOnBackPressed() {
    if (blaze.handleBackPress()) {
      isEnabled = false
      onBackPressedDispatcher.onBackPressed()
      isEnabled = true
    }
  }
})
```

### Step 5: Terminate the SDK

When you are done with the SDK, call `terminate`. It closes the journey and releases the SDK's WebView. Call `initiate` again before you use the SDK next time.

```kotlin
blaze.terminate()
```

### Optional Steps

`Blaze` also has `onActivityResult` and `onRequestPermissionsResult` methods. They are kept for compatibility and do nothing, so you don't need to forward these lifecycle callbacks to the Blaze instance.

### Agentic commerce

Agentic commerce (`in.breeze.shop`) lets the shopper search for products, build a cart and check out, all inside your app. You open it with the same `Blaze` calls as one-click checkout (`initiate`, `process`, `handleBackPress`, `terminate`); only the payloads, and what comes back, differ.

#### Prepare it with `initiate`

```kotlin
val initiatePayload = JSONObject()
initiatePayload.put("action", "initiate")
initiatePayload.put("publisherId", "<PUBLISHER_ID>")
initiatePayload.put("environment", "<ENVIRONMENT>")

val initSDKPayload = JSONObject()
initSDKPayload.put("requestId", "<UNIQUE_RANDOM_ID>")
initSDKPayload.put("service", "in.breeze.shop")
initSDKPayload.put("payload", initiatePayload)

blaze.initiate(this, initSDKPayload, ::blazeCallbackHandler)
```

| Key | Required | Value |
| --- | --- | --- |
| `publisherId` | Yes | Your publisher id, from the Breeze team |
| `environment` | No | `beta` or `release`. Defaults to `release` |

Nothing appears yet: `initiate` prepares everything in the background, so the shopper sees it instantly when you open it. `merchantId` and `shopUrl` aren't needed.

Your callback then gets `eventName: "initiate"` with `status: "success"`. If something is wrong, it gets `error: true` instead, with the reason in `errorDetails`, for example `Missing publisherId in initiate payload`.

#### Open it with `process`

Once the `initiate` answer has arrived, call `process`. It opens full screen.

```kotlin
val processPayload = JSONObject()
processPayload.put("action", "launch")

val processSDKPayload = JSONObject()
processSDKPayload.put("requestId", "<UNIQUE_RANDOM_ID>")
processSDKPayload.put("service", "in.breeze.shop")
processSDKPayload.put("payload", processPayload)

blaze.process(processSDKPayload)
```

Your callback gets `eventName: "eventStream"` with `payload.event` set to `ProcessStarted`.

#### What your callback receives

| `payload.eventName` | Means |
| --- | --- |
| `initiate` | The answer to `initiate`: ready, or an error |
| `eventStream` with `payload.event` `ProcessStarted` | It opened, in answer to `process` |
| `process` with `error: true` | The `process` payload couldn't be read |

#### Back press and terminate

These work as in Steps 4 and 5. Back takes the shopper one screen back, and closes it from the first screen. After `terminate`, call `initiate` again before you open it.

### Error codes

The error codes the SDK sends are listed in [Error codes](docs/error-codes.md).
