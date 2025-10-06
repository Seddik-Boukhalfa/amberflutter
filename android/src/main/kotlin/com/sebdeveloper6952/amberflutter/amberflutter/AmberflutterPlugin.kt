package com.sebdeveloper6952.amberflutter.amberflutter

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sebdeveloper6952.amberflutter.amberflutter.models.*
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.util.concurrent.atomic.AtomicInteger

/** AmberflutterPlugin */
class AmberflutterPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var _channel : MethodChannel
  private lateinit var _context : Context
  private var _activity: Activity? = null
  
  // Use a map to track multiple pending requests
  private val _pendingResults = mutableMapOf<Int, MethodChannel.Result>()
  private val _requestCodeGenerator = AtomicInteger(1000)

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    _channel = MethodChannel(flutterPluginBinding.binaryMessenger, "com.sebdeveloper6952.amberflutter")
    _channel.setMethodCallHandler(this)
    _context = flutterPluginBinding.applicationContext
  }

  fun isPackageInstalled(context: Context, target: String): Boolean {
    return context.packageManager.getInstalledApplications(0).find { info -> info.packageName == target } != null
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    if (call.method == nostrsignerUri) {
      val wrappedResult = MethodResultWrapper(result)

      var paramsMap: HashMap<*, *>? = null
      if (call.arguments != null && call.arguments is HashMap<*, *>) {
        paramsMap = call.arguments as HashMap<*, *>
      }

      if (paramsMap == null) {
        Log.d("onMethodCall", "paramsMap is null")
        wrappedResult.error("INVALID_ARGUMENTS", "Parameters map is null", null)
        return
      }

      val requestType = paramsMap[intentExtraKeyType] as? String ?: ""
      val currentUser = paramsMap[intentExtraKeyCurrentUser] as? String ?: ""
      val pubKey = paramsMap[intentExtraKeyPubKey] as? String ?: ""
      val id = paramsMap[intentExtraKeyId] as? String ?: ""
      val uriData = paramsMap[intentExtraKeyUriData] as? String ?: ""
      val permissions = paramsMap[intentExtraKeyPermissions] as? String ?: ""

      // Try ContentResolver first
      val data = getDataFromContentResolver(
        requestType.uppercase(),
        arrayOf(uriData, pubKey, currentUser),
        _context.contentResolver,
      )
      if (!data.isNullOrEmpty()) {
        Log.d("onMethodCall", "content resolver got data")
        wrappedResult.success(data)
        return
      }

      // Generate unique request code for this call
      val requestCode = _requestCodeGenerator.incrementAndGet()
      
      // Store the result callback for this request
      _pendingResults[requestCode] = wrappedResult

      val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(
          "$nostrsignerUri:$uriData"
        )
      )

      intent.putExtra(intentExtraKeyType, requestType)
      intent.putExtra(intentExtraKeyCurrentUser, currentUser)
      intent.putExtra(intentExtraKeyPubKey, pubKey)
      intent.putExtra(intentExtraKeyId, id)
      intent.putExtra(intentExtraKeyPermissions, permissions)
      
      // Add the request code to the intent so we can track it
      intent.putExtra("REQUEST_CODE", requestCode)

      try {
        _activity?.startActivityForResult(intent, requestCode)
      } catch (e: Exception) {
        Log.e("onMethodCall", "Failed to start activity", e)
        _pendingResults.remove(requestCode)
        wrappedResult.error("ACTIVITY_ERROR", "Failed to start external app: ${e.message}", null)
      }
      
    } else if (call.method == "isAppInstalled") {
        var paramsMap: HashMap<*, *>? = null
        if (call.arguments != null && call.arguments is HashMap<*, *>) {
            paramsMap = call.arguments as HashMap<*, *>
        }
        if (paramsMap == null) {
            result.error("INVALID_ARGUMENTS", "Parameters map is null", null)
            return
        }
        val packageName: String? = paramsMap["packageName"] as? String
        if (packageName == null) {
            result.error("INVALID_ARGUMENTS", "Package name is required", null)
            return
        }
        val isInstalled: Boolean = isPackageInstalled(_context, packageName)
        result.success(isInstalled)
    } else if (call.method == "getPlatformVersion") {
    result.success("Android " + android.os.Build.VERSION.RELEASE)
    } else {
      result.notImplemented()
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?): Boolean {
    // Find the pending result for this request code
    val pendingResult = _pendingResults.remove(requestCode)
    
    if (pendingResult != null) {
      if (resultCode == Activity.RESULT_OK && intent != null) {
        val dataMap: HashMap<String, String?> = HashMap()
        
        if (intent.hasExtra(intentExtraKeySignature)) {
          val signature = intent.getStringExtra(intentExtraKeySignature)
          dataMap[intentExtraKeySignature] = signature
        }
        if (intent.hasExtra(intentExtraKeyId)) {
          val id = intent.getStringExtra(intentExtraKeyId)
          dataMap[intentExtraKeyId] = id
        }
        if (intent.hasExtra(intentExtraKeyEvent)) {
          val event = intent.getStringExtra(intentExtraKeyEvent)
          dataMap[intentExtraKeyEvent] = event
        }

        pendingResult.success(dataMap)
      } else {
        // Handle cancellation or error
        pendingResult.success(null)
      }
      return true
    }

    return false
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    _channel.setMethodCallHandler(null)
    // Clean up any pending results
    _pendingResults.clear()
  }

  override fun onDetachedFromActivity() {
    _activity = null
    // Clean up pending results when activity is detached
    for (result in _pendingResults.values) {
      result.error("ACTIVITY_DETACHED", "Activity was detached before request completed", null)
    }
    _pendingResults.clear()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    _activity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    _activity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    _activity = null
  }

  /*
    Code taken from: https://github.com/0xchat-app/nostr-dart/blob/main/android/src/main/kotlin/com/oxchat/nostrcore/ChatcorePlugin.kt
   */
  private fun getDataFromContentResolver(
    type: String,
    uriData: Array<out String>,
    resolver: ContentResolver,
  ): HashMap<String, String?>? {
    try {
      resolver.query(
        Uri.parse("content://${amberPackageName}.$type"),
        uriData,
        null,
        null,
        null
      ).use { cursor ->
        if (cursor == null) {
          Log.d("getDataFromResolver", "resolver query is NULL")
          return null
        }
        if (cursor.moveToFirst()) {
          val dataMap: HashMap<String, String?> = HashMap()
          val index = cursor.getColumnIndex("signature")
          if (index >= 0) {
            val signature = cursor.getString(index)
            dataMap["signature"] = signature
          } else {
            Log.d("getDataFromResolver", "column 'signature' not found")
          }
          
          val indexJson = cursor.getColumnIndex("event")
          if (indexJson >= 0) {
            val eventJson = cursor.getString(indexJson)
            dataMap["event"] = eventJson
          } else {
            Log.d("getDataFromResolver", "column 'event' not found")
          }

          return dataMap
        }
      }
    } catch (e: Exception) {
      Log.d("contentResolver", e.message ?: "unknown error")
      return null
    }
    return null
  }
}

private class MethodResultWrapper internal constructor(result: MethodChannel.Result) :
  MethodChannel.Result {
  private val methodResult: MethodChannel.Result
  private val handler: Handler
  @Volatile
  private var hasReplied = false

  init {
    methodResult = result
    handler = Handler(Looper.getMainLooper())
  }

  @Synchronized
  override fun success(result: Any?) {
    if (!hasReplied) {
      hasReplied = true
      handler.post { methodResult.success(result) }
    }
  }

  @Synchronized
  override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
    if (!hasReplied) {
      hasReplied = true
      handler.post { methodResult.error(errorCode, errorMessage, errorDetails) }
    }
  }

  @Synchronized
  override fun notImplemented() {
    if (!hasReplied) {
      hasReplied = true
      handler.post { methodResult.notImplemented() }
    }
  }
}