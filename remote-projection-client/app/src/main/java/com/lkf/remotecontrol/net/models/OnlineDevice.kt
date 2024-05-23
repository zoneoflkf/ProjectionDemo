package com.lkf.remotecontrol.net.models

import android.os.Parcel
import android.os.Parcelable

data class OnlineDevice(
    val width: Int, val height: Int,
    val deviceName: String?, val versionCode: Int, val versionName: String?
) : Parcelable {
    var deviceId: Long = 0

    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readInt(),
        parcel.readString(),
        parcel.readInt(),
        parcel.readString()
    ) {
        deviceId = parcel.readLong()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(width)
        parcel.writeInt(height)
        parcel.writeString(deviceName)
        parcel.writeInt(versionCode)
        parcel.writeString(versionName)
        parcel.writeLong(deviceId)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<OnlineDevice> {
        override fun createFromParcel(parcel: Parcel): OnlineDevice {
            return OnlineDevice(parcel)
        }

        override fun newArray(size: Int): Array<OnlineDevice?> {
            return arrayOfNulls(size)
        }
    }
}