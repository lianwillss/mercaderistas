package com.rutamercaderistas.models

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache

object PageBitmapCache {
    private const val MAX_CACHE_FRACTION = 0.10f
    private var cache: LruCache<Int, Bitmap>? = null

    fun init(context: Context) {
        if (cache != null) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryClass = am.memoryClass
        val maxBytes = (memoryClass * 1024 * 1024 * MAX_CACHE_FRACTION).toInt()
        cache = object : LruCache<Int, Bitmap>(maxBytes.coerceIn(3 * 1024 * 1024, 20 * 1024 * 1024)) {
            override fun sizeOf(key: Int, bitmap: Bitmap): Int = bitmap.byteCount
            override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
                if (evicted) oldValue.recycle()
            }
        }
    }

    fun put(pageNum: Int, bitmap: Bitmap) = cache?.put(pageNum, bitmap)
    fun get(pageNum: Int): Bitmap? = cache?.get(pageNum)
    fun remove(pageNum: Int) = cache?.remove(pageNum)
    fun clear() { cache?.evictAll(); cache = null }
}
