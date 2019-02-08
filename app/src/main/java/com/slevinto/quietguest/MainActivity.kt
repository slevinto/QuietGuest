package com.slevinto.quietguest

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.Telephony
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.View

abstract class ReadSMS : BroadcastReceiver()

class MainActivity : AppCompatActivity() {

    companion object {
        const val KEY_GATE_NUMBER = "gate_number"
        const val KEY_SMS_TEXT = "sms_text"
    }

    private val prefs = "com.slevinto.quietguest.prefs"
    private val changeUserPreferencesRequest = 1
    private val smsReadRequest = 100
    private val phoneCallRequest = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
        registerReceiver(smsReceiver, filter)
        //Request Run time Permissions for accessing SMS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), smsReadRequest)
        }
        //Request Run time Permissions for phone call
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), phoneCallRequest)
        }
        // get preferences values from preferences file
        val gateNumber = getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_GATE_NUMBER, null)
        val smsText = getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_SMS_TEXT, null)
        // values are set
        if (gateNumber != null && smsText != null) {
            setContentView(R.layout.activity_main)
        }
        else {  // values not set
            val intent = Intent(this, UserPreferencesActivity::class.java)
            startActivityForResult(intent, changeUserPreferencesRequest)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            smsReadRequest -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_SMS)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(arrayOf(Manifest.permission.READ_SMS), smsReadRequest)
                        }
                    }
                }
            }
            phoneCallRequest -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CALL_PHONE)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), phoneCallRequest)
                        }
                    }
                }
            }
        }
    }

    private val smsReceiver = object : ReadSMS() {
        override fun onReceive(context: Context, intent: Intent) {
            // Here you receive SMS content
            val data = intent.extras
            val pdusObj = data!!.get("pdus") as Array<*>
            for (i in pdusObj.indices) {
                val currentMessage = Telephony.Sms.Intents.getMessagesFromIntent(intent)[0].displayMessageBody
                if (currentMessage == getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_SMS_TEXT, "")) {
                    try {
                        val tel = getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_GATE_NUMBER, "")
                        val call = Intent(Intent.ACTION_CALL, Uri.parse("tel:$tel"))
                        startActivity(call)
                    }
                    catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == changeUserPreferencesRequest) {
            if (resultCode == Activity.RESULT_OK) {
                val gateNumber = data?.getStringExtra(KEY_GATE_NUMBER)
                val smsText = data?.getStringExtra(KEY_SMS_TEXT)
                gateNumber?.let {
                    // save the data in Shared Preferences
                    val sharedPref = this.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit()
                    sharedPref.putString(KEY_GATE_NUMBER, gateNumber)
                    sharedPref.apply()
                }
                smsText?.let {
                    // save the data in Shared Preferences
                    val sharedPref = this.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit()
                    sharedPref.putString(KEY_SMS_TEXT, smsText)
                    sharedPref.apply()
                }
            }
        }
        setContentView(R.layout.activity_main)
    }

    fun btnChangeSettingsClicked(@Suppress("UNUSED_PARAMETER")view: View) {
        val intent = Intent(this, UserPreferencesActivity::class.java)
        val gateNumber = getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_GATE_NUMBER, null)
        val smsText = getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_SMS_TEXT, null)
        intent.putExtra(KEY_GATE_NUMBER, gateNumber)
        intent.putExtra(KEY_SMS_TEXT, smsText)
        startActivityForResult(intent, changeUserPreferencesRequest)
    }
}
