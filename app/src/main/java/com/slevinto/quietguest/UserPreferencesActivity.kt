package com.slevinto.quietguest

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_user_preferences.*

class UserPreferencesActivity : AppCompatActivity() {

    private val errorInputEmpty = "מלא את כל השדות בבקשה"
    private val contactsRequest = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_preferences)
        val gateNumber = intent.getStringExtra(MainActivity.KEY_GATE_NUMBER)
        val smsText = intent.getStringExtra(MainActivity.KEY_SMS_TEXT)
        if ( gateNumber != null ) {
            gate_number.setText(gateNumber)
        }
        if ( smsText != null ) {
            sms_text.setText(smsText)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == contactsRequest) {
            if (resultCode == Activity.RESULT_OK) {
                val contactData = data!!.data
                val cursor = contentResolver.query(contactData!!, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val phoneNumber = cursor.getString(column)
                    gate_number.setText(phoneNumber)
                    cursor.close()
                }
            }
        }
    }

    fun btnDoneClicked(@Suppress("UNUSED_PARAMETER")view: View) {
        //If the user has left any fields empty, show Toast message
        val gateNumber = gate_number.text.toString()
        val smsText = sms_text.text.toString()
        if (gateNumber.isEmpty() || smsText.isEmpty()) {
            val toast = Toast.makeText(applicationContext, errorInputEmpty, Toast.LENGTH_LONG)
            toast.setGravity(Gravity.TOP , 0, 0)
            toast.show()
        }
        else {
            val result = Intent()
            val sharedPref = this.getSharedPreferences(MainActivity.prefs, Context.MODE_PRIVATE).edit()
            sharedPref.putString(MainActivity.KEY_GATE_NUMBER, gateNumber)
            sharedPref.putString(MainActivity.KEY_SMS_TEXT, smsText)
            sharedPref.apply()
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }

    fun btnContactsClicked(@Suppress("UNUSED_PARAMETER")view: View) {
        val intent = Intent(Intent.ACTION_PICK)
        intent.setDataAndType(ContactsContract.Contacts.CONTENT_URI, ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)
        startActivityForResult(intent, contactsRequest)
    }
}
