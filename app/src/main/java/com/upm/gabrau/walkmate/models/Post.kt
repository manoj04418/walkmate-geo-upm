package com.upm.gabrau.walkmate.models

import android.os.Parcel
import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName
import java.util.*

class Post(
    @DocumentId var id: String? = "",
    @PropertyName("name") var name: String? = "",
    @PropertyName("geopoint") var geoPoint: GeoPoint? = GeoPoint(0.0, 0.0),
    @PropertyName("created") var created: Date? = Calendar.getInstance().time,
): Parcelable {
    constructor(parcel: Parcel) : this (
        parcel.readString(),
        parcel.readString(),
        parcel.readGeoPoint(),
        parcel.readDate()
    )

    fun toMap(): Map<String, Any?> {
        return hashMapOf(
            "name" to name,
            "geopoint" to geoPoint,
            "created" to created
        )
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeGeoPoint(geoPoint)
        parcel.writeDate(created)
    }

    override fun describeContents(): Int { return 0 }

    companion object CREATOR : Parcelable.Creator<Post> {
        private fun Parcel.writeDate(date: Date?) { writeLong(date?.time ?: -1) }
        private fun Parcel.readDate(): Date? {
            val long = readLong()
            return if (long != 1L) Date(long) else null
        }

        private fun Parcel.writeGeoPoint(point: GeoPoint?) {
            writeDouble(point?.latitude ?: -0.000000001)
            writeDouble(point?.longitude ?: -0.000000001)
        }
        private fun Parcel.readGeoPoint(): GeoPoint? {
            val latitude = readDouble()
            val longitude = readDouble()
            return if (latitude != -0.000000001 && longitude != -0.000000001) GeoPoint(latitude, longitude) else null
        }

        override fun createFromParcel(parcel: Parcel): Post { return Post(parcel) }
        override fun newArray(size: Int): Array<Post?> { return arrayOfNulls(size) }
    }
}