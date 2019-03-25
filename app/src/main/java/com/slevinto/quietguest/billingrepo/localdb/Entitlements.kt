package com.slevinto.quietguest.billingrepo.localdb

import androidx.room.Entity
import androidx.room.PrimaryKey

private const val FULL_TANK = 1
private const val EMPTY_TANK = 0
const val gateOpensPurchase = 10

abstract class Entitlement {
    @PrimaryKey
    var id: Int = 1

    abstract fun mayPurchase(): Boolean
}

@Entity(tableName = "goldStatus")
data class GoldStatus(val entitled: Boolean) : Entitlement() {
    override fun mayPurchase(): Boolean = !entitled
}

@Entity(tableName = "gateOpensTank")
class GateOpensTank(private var level: Int) : Entitlement() {
    fun getLevel() = level
    override fun mayPurchase(): Boolean = level < FULL_TANK
    fun needGateOpens(): Boolean = level <= EMPTY_TANK
    fun decrement(by: Int = 1) {
        level -= by
    }
}