package com.slevinto.quietguest

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_private_info.*
import com.creativityapps.gmailbackgroundlibrary.BackgroundMail

class PrivateInfoActivity : AppCompatActivity() {

    private val errorInputEmpty = "מלא את כל השדות בבקשה"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_private_info)

        businessLabel.visibility = View.GONE
        business_spinner.visibility = View.GONE

        clients_spinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedItem = parent?.getItemAtPosition(position).toString()
                if (selectedItem == getString(R.string.array_client_business_label)) {
                    businessLabel.visibility = View.VISIBLE
                    business_spinner.visibility = View.VISIBLE
                }
            }
        }
    }

    fun btnDoneClicked(@Suppress("UNUSED_PARAMETER")view: View) {
        //If the user has left any fields empty, show Toast message
        val name = name.text.toString()
        val surname = surname.text.toString()
        val city = city.text.toString()
        val phone = phone.text.toString()
        val email = email.text.toString()
        val client = clients_spinner.selectedItem.toString()
        val business = business_spinner.selectedItem.toString()

        if (name.isEmpty() || surname.isEmpty() || city.isEmpty() || phone.isEmpty() || email.isEmpty() ||
            client == getString(R.string.array_client_kind) ||
            (client == getString(R.string.array_client_business_label) && business == getString(R.string.array_business_kind))) {
            val toast = Toast.makeText(applicationContext, errorInputEmpty, Toast.LENGTH_LONG)
            toast.setGravity(Gravity.TOP , 0, 0)
            toast.show()
        }
        else {
            BackgroundMail.newBuilder(this)
                .withUsername("quietguestinfo@gmail.com")
                .withPassword("pusich333")
                //.withMailto("slevinto@gmail.com")
                .withMailto("benelrom@gmail.com")
                .withType(BackgroundMail.TYPE_PLAIN)
                .withSubject("QuietGuest לקוח חדש")
                .withBody("${getString(R.string.name_label)}: $name\n" +
                          "${getString(R.string.surname_label)}: $surname\n" +
                          "${getString(R.string.city_label)}: $city\n" +
                          "${getString(R.string.phone_label)}: $phone\n" +
                          "${getString(R.string.email_label)}: $email\n" +
                          "${getString(R.string.client_label)}: $client\n" +
                          "${getString(R.string.business_label)}: $business")
                .withOnSuccessCallback {
                    val toast = Toast.makeText(applicationContext, errorInputEmpty, Toast.LENGTH_LONG)
                    toast.setGravity(Gravity.TOP , 0, 0)
                    toast.show()
                }
                .withOnFailCallback {
                    val toast = Toast.makeText(applicationContext, errorInputEmpty, Toast.LENGTH_LONG)
                    toast.setGravity(Gravity.TOP , 0, 0)
                    toast.show()
                }
                .send()
            val result = Intent()
            val sharedPref = this.getSharedPreferences(MainActivity.prefs, Context.MODE_PRIVATE).edit()
            sharedPref.putString(MainActivity.KEY_NAME, name)
            sharedPref.putString(MainActivity.KEY_SURNAME, surname)
            sharedPref.putString(MainActivity.KEY_CITY, city)
            sharedPref.putString(MainActivity.KEY_PHONE, phone)
            sharedPref.putString(MainActivity.KEY_EMAIL, email)
            sharedPref.putString(MainActivity.KEY_CLIENT, client)
            sharedPref.putString(MainActivity.KEY_BUSINESS, business)
            sharedPref.apply()

            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}
