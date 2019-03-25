package com.slevinto.quietguest

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import com.google.android.material.navigation.NavigationView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.Toolbar
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity

abstract class ReadSMS : BroadcastReceiver()

class MainActivity : AppCompatActivity() {

    companion object {
        const val KEY_GATE_NUMBER = "gate_number"
        const val KEY_SMS_TEXT = "sms_text"
        const val KEY_NAME = "name"
        const val KEY_SURNAME = "surname"
        const val KEY_CITY = "city"
        const val KEY_PHONE = "phone"
        const val KEY_EMAIL = "email"
        const val KEY_CLIENT = "client"
        const val KEY_BUSINESS = "business"
        const val prefs = "com.slevinto.quietguest.prefs"
    }

    private val changeUserPreferencesRequest = 1
    private val changePrivateInfoRequest = 2
    private val smsReadRequest = 100
    private val phoneCallRequest = 42
    private val accessNetworkRequest = 43
    private val internetRequest = 44
    private lateinit var mDrawerLayout: DrawerLayout

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
        //Request Run time Permissions for send email
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_NETWORK_STATE), accessNetworkRequest)
        }
        //Request Run time Permissions for send email
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(arrayOf(Manifest.permission.INTERNET), internetRequest)
        }

        setContentView(R.layout.activity_main)

        // setup drawer view
        mDrawerLayout = findViewById(R.id.drawer_layout)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionbar: ActionBar? = supportActionBar
        actionbar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu)
        }
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            // set item as selected to persist highlight
            menuItem.isChecked = true
            // close drawer when item is tapped
            when (menuItem.itemId){
                R.id.change_settings -> btnChangeSettingsClicked()
            }
            mDrawerLayout.closeDrawers()
            true
        }
    }

    override fun onStart() {
        super.onStart()

        // get preferences values from preferences file
        val name = getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_NAME, null)
        val gateNumber = getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_GATE_NUMBER, null)
        val smsText = getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_SMS_TEXT, null)

        // values are set
        if (name != null && gateNumber != null && smsText != null) {

        }
        else if (name != null) {  // user preferences values not set
            val intent = Intent(this, UserPreferences::class.java)
            startActivityForResult(intent, changeUserPreferencesRequest)
        } else { // private info values not set
            val intent = Intent(this, PrivateInfoActivity::class.java)
            startActivityForResult(intent, changePrivateInfoRequest)
        }
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
            accessNetworkRequest -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_NETWORK_STATE)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(arrayOf(Manifest.permission.ACCESS_NETWORK_STATE), accessNetworkRequest)
                        }
                    }
                }
            }
            internetRequest -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.INTERNET)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(arrayOf(Manifest.permission.INTERNET), internetRequest)
                        }
                    }
                }
            }
        }
    }

    private val smsReceiver = object : ReadSMS() {
        override fun onReceive(context: Context, intent: Intent) {
            // Here you receive SMS content
            val gameFr = GameFragment()
            if (gameFr.onGateOpen()) {
                val data = intent.extras
                val pdusObj = data!!.get("pdus") as Array<*>
                for (i in pdusObj.indices) {
                    val currentMessage = Telephony.Sms.Intents.getMessagesFromIntent(intent)[0].displayMessageBody
                    if (currentMessage == getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_SMS_TEXT,"")) {
                        try {
                            val tel = getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_GATE_NUMBER, "")
                            val call = Intent(Intent.ACTION_CALL, Uri.parse("tel:$tel"))
                            try {
                                startActivity(call)
                            } catch (e: SecurityException) {
                                e.printStackTrace()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                mDrawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun btnChangeSettingsClicked() {
        val intent = Intent(this, UserPreferences::class.java)
        val gateNumber = getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_GATE_NUMBER, null)
        val smsText = getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY_SMS_TEXT, null)
        intent.putExtra(KEY_GATE_NUMBER, gateNumber)
        intent.putExtra(KEY_SMS_TEXT, smsText)
        startActivityForResult(intent, changeUserPreferencesRequest)
    }
}
