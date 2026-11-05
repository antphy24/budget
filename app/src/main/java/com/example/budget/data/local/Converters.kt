package com.example.budget.data.local

import androidx.room.TypeConverter
import com.example.budget.domain.model.AccountType
import com.example.budget.domain.model.AssetType
import com.example.budget.domain.model.EnvelopeType
import com.example.budget.domain.model.TransactionType

class Converters {

    // TransactionType
    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(name: String): TransactionType = TransactionType.valueOf(name)

    // AssetType
    @TypeConverter
    fun fromAssetType(type: AssetType): String = type.name

    @TypeConverter
    fun toAssetType(name: String): AssetType = AssetType.valueOf(name)

    // EnvelopeType
    @TypeConverter
    fun fromEnvelopeType(type: EnvelopeType): String = type.name

    @TypeConverter
    fun toEnvelopeType(name: String): EnvelopeType = EnvelopeType.valueOf(name)

    // AccountType
    @TypeConverter
    fun fromAccountType(type: AccountType): String = type.name

    @TypeConverter
    fun toAccountType(name: String): AccountType = AccountType.valueOf(name)
}
