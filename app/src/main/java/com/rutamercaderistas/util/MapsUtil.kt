package com.rutamercaderistas.util

import android.content.Context
import android.content.Intent
import android.net.Uri

fun openMaps(context: Context, address: String, transportMode: String = "transit") {
    val encoded = Uri.encode(address)
    val mapsUrl = "https://www.google.com/maps/dir/?api=1&destination=$encoded&travelmode=$transportMode"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).apply {
        setPackage("com.google.android.apps.maps")
    }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)))
    }
}
