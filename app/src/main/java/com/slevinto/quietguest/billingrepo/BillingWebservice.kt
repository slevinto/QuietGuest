/**
 * Copyright (C) 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.slevinto.quietguest.billingrepo

import com.android.billingclient.api.Purchase

/**
 * TODO("not implemented"): You must implement this class and all its methods.
 *
 * This class is a placeholder for your HTTP client, and it's left for you to implement.
 * If you don't yet have a favorite HTTP client, searching on Google for "http client for
 * android" should reveal a few.
 */
class BillingWebservice {


    fun getPurchases(): Any {
        return Any()//TODO("not implemented"): You must implement this method to get purchase data
        // from your server to verify data in local db
    }

    fun updateServer(purchases: Set<Purchase>) {
        //TODO("not implemented"): You must implement this method to send purchase data from play
        //  billing to server
    }

    fun onComsumeResponse(purchaseToken: String?, responseCode: Int) {
        //TODO("not implemented"): You must implement this method to tell your secure server what
        // has been consumed
    }

    companion object {
        fun create(): BillingWebservice {
            //TODO("not implemented"): You must implement this method
            return BillingWebservice()
        }
    }
}