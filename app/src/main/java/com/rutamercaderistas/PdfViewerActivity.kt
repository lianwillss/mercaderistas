package com.rutamercaderistas

import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.rutamercaderistas.adapters.PagePagerAdapter
import com.rutamercaderistas.models.PageBitmapCache
import com.rutamercaderistas.models.PdfRendererCache
import kotlin.math.abs

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var tvPageInfoBadge: TextView
    private lateinit var tvBrandTitle: TextView
    private lateinit var ivSwipeArrow: ImageView
    private lateinit var layoutPageIndicator: LinearLayout
    private lateinit var viewPager: ViewPager2
    private var pageStart = 0
    private var pageEnd = 0
    private var totalPages = 0
    private var brandName: String = ""
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    companion object {
        private const val SAVED_PAGE = "current_page"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pdfPath = intent.getStringExtra("pdf_path")
        if (pdfPath == null) { finish(); return }

        PdfRendererCache.init(pdfPath)
        val pdfTotal = PdfRendererCache.getPageCount()
        if (pdfTotal == 0) { finish(); return }

        setContentView(R.layout.activity_pdf_viewer)

        tvPageInfoBadge = findViewById(R.id.tvPageInfoBadge)
        tvBrandTitle = findViewById(R.id.tvPageInfo)
        ivSwipeArrow = findViewById(R.id.ivSwipeArrow)
        layoutPageIndicator = findViewById(R.id.layoutPageIndicator)
        viewPager = findViewById(R.id.viewPager)
        findViewById<TextView>(R.id.btnCerrar).setOnClickListener { finish() }
        pageStart = intent.getIntExtra("page_start", 1)
        pageEnd = intent.getIntExtra("page_end", pageStart)
        brandName = intent.getStringExtra("brand_name") ?: ""

        PageBitmapCache.init(this)

        if (pageEnd > pdfTotal) pageEnd = pdfTotal
        totalPages = pageEnd - pageStart + 1
        if (totalPages <= 0) {
            android.widget.Toast.makeText(this, "Marca no encontrada", android.widget.Toast.LENGTH_SHORT).show()
            finish(); return
        }

        viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        viewPager.offscreenPageLimit = 1
        viewPager.adapter = PagePagerAdapter(this, totalPages, pageStart, pdfPath)

        viewPager.setPageTransformer { page: View, position: Float ->
            page.alpha = 1f - abs(position).coerceIn(0f, 1f)
            page.translationY = position * page.height * 0.08f
        }

        tvBrandTitle.text = brandName

        if (totalPages > 1) {
            val pulse = AlphaAnimation(0.4f, 1f).apply {
                duration = 700
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
                interpolator = LinearInterpolator()
            }
            ivSwipeArrow.startAnimation(pulse)
        } else {
            ivSwipeArrow.visibility = View.INVISIBLE
        }

        val startPage = savedInstanceState?.getInt(SAVED_PAGE, 1) ?: 1

        pageChangeCallback?.let { viewPager.unregisterOnPageChangeCallback(it) }
        pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tvPageInfoBadge.text = "${position + 1} de $totalPages"
            }
        }
        viewPager.registerOnPageChangeCallback(pageChangeCallback!!)

        if (startPage > 1 && startPage <= totalPages) {
            viewPager.post { viewPager.setCurrentItem(startPage - 1, false) }
        }

        tvPageInfoBadge.text = "1 de $totalPages"
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SAVED_PAGE, viewPager.currentItem + 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        PdfRendererCache.close()
    }
}
