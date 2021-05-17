package com.upm.gabrau.walkmate.models

import android.os.Parcel
import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

class User(
    @DocumentId var id: String? = "",
    @PropertyName("name") var name: String? = ""
) : Parcelable {
    constructor(parcel: Parcel) : this (
        parcel.readString(),
        parcel.readString()
    )

    fun toMap(): Map<String, Any?> {
        return hashMapOf(
            "name" to name
        )
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
    }

    override fun describeContents(): Int { return 0 }

    companion object CREATOR : Parcelable.Creator<User> {
        override fun createFromParcel(parcel: Parcel): User { return User(parcel) }
        override fun newArray(size: Int): Array<User?> { return arrayOfNulls(size) }
    }
}