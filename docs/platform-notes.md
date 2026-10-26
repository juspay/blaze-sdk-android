# Platform notes

What the Blaze Android SDK does inside your app, for reviewing it before you ship. These notes describe the SDK version they ship with.

## What runs inside the SDK

The SDK shows a Breeze web page in an Android `WebView`. It finds the page's address in a configuration file, `https://sdk.breeze.in/core/sdk.json`, which lists an address for each service and environment. It picks the address in this order:

1. **A saved copy of the file**, from an earlier launch: used straight away, and refreshed in the background once per app run.
2. **No saved copy**: the SDK downloads the file first, with 3-second connection and read timeouts.
3. **Still no `https://` address** for your `service` and `environment` (for example, the download failed): the SDK falls back to an address built into it, `https://shop.breeze.in/sdk` for the shop assistant (`in.breeze.shop`), or the one-click checkout page for your `environment` for any other service.

Apart from downloading that file and loading the page, the SDK's own code makes no network requests; everything else is done by the web page.

When the page opens a UPI payment link, the SDK hands it to a UPI app through an Android intent instead of loading it in the web view. A UPI payment link is a URL whose scheme is `upi`, `phonepe`, `tez`, `gpay`, `paytm`, `paytmmp`, `bhim`, `amazonpay`, `mobikwik`, `freecharge` or `credpay`, and which carries a payee address (`pa`), a payee name (`pn`) and an amount (`am`).

## Permissions

The SDK's manifest is merged into your app's manifest and declares two entries:

- `android.permission.INTERNET`, to load the page and download the configuration file.
- A `<queries>` entry for `upi://pay` intents. On Android 11 and later an app can only see the other apps it declares, so this lets the SDK list the UPI apps on the device. Integrations that don't take UPI payments don't use it.

The SDK requests no runtime permissions.

## Methods the page can call

The page talks to the SDK through a JavaScript interface named `Native`, and can call these methods:

| Method | What it does |
| --- | --- |
| `saveToStorage` | Saves a string under a key |
| `getFromStorage` | Reads the string saved under a key |
| `openApp` | Opens a URL in another app, such as a UPI app |
| `findApps` | Lists the installed apps that can open a URL, with each app's package name and name |

Any other method gets the answer `"Method not found"`.

## Data the SDK stores

- **SDK configuration:** the saved copy of `sdk.json`, in the SharedPreferences file `BlazeSdkConfigPref`, under the key `blazeSdkConfig`.
- **Values the page saves:** strings written through `saveToStorage`, in the SharedPreferences file `BlazeSharedPref`, under keys the page chooses. One-click checkout saves a copy of everything it writes to its own web storage this way.
