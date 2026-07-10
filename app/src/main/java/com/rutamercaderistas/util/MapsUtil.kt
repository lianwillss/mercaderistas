package com.rutamercaderistas.util

import android.content.Context
import android.content.Intent
import android.net.Uri

fun openMaps(context: Context, address: String) {
    val encoded = Uri.encode(address)
    val mode = context.getSharedPreferences("mercaderistas_prefs", Context.MODE_PRIVATE)
        .getString("transport_mode", "transit") ?: "transit"
    val mapsUrl = "https://www.google.com/maps/dir/?api=1&destination=$encoded&travelmode=$mode"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).apply {
        setPackage("com.google.android.apps.maps")
    }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)))
    }
}
