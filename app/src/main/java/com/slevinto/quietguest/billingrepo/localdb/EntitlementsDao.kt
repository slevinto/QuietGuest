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
package com.slevinto.quietguest.billingrepo.localdb

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface EntitlementsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(goldStatus: GoldStatus)

    @Update
    fun update(goldStatus: GoldStatus)

    @Query("SELECT * FROM goldStatus LIMIT 1")
    fun getGoldStatus(): LiveData<GoldStatus>

    @Delete
    fun delete(goldStatus: GoldStatus)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(gateOpensLevel: GateOpensTank)

    @Update
    fun update(gateOpensLevel: GateOpensTank)

    @Query("SELECT * FROM gateOpensTank LIMIT 1")
    fun getGateOpensTank(): LiveData<GateOpensTank>

    @Delete
    fun delete(gateOpensLevel: GateOpensTank)

    @Transaction
    fun insert(vararg entitlements: Entitlement) {
        entitlements.forEach {
            when (it) {
                is GateOpensTank -> insert(it)
                is GoldStatus -> insert(it)
            }
        }
    }

    @Transaction
    fun update(vararg entitlements: Entitlement) {
        entitlements.forEach {
            when (it) {
                is GateOpensTank -> update(it)
                is GoldStatus -> update(it)
            }
        }
    }
}