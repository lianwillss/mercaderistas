package com.rutamercaderistas.fragments

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rutamercaderistas.R
import com.rutamercaderistas.models.PageBitmapCache
import com.rutamercaderistas.models.PdfRendererCache
import com.rutamercaderistas.views.ZoomableImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZoomablePageFragment : Fragment() {

    companion object {
        private const val ARG_PAGE = "page_num"
        private const val ARG_PDF_PATH = "pdf_path"
        private const val MAX_RENDER_PX = 1200

        fun newInstance(pageNum: Int, pdfPath: String): ZoomablePageFragment {
            return ZoomablePageFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_PAGE, pageNum)
                    putString(ARG_PDF_PATH, pdfPath)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_page_viewer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val zoomView = view.findViewById<ZoomableImageView>(R.id.zoomImageView)
        val pageNum = arguments?.getInt(ARG_PAGE) ?: return

        loadPage(zoomView, pageNum)
    }

    private fun loadPage(zoomView: ZoomableImageView, pageNum: Int) {
        val cached = PageBitmapCache.get(pageNum)
        if (cached != null && !cached.isRecycled) {
            zoomView.loadPage(cached)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bitmap = withContext<Bitmap?>(Dispatchers.IO) {
                    renderPage(pageNum)
                }
                if (bitmap != null && isAdded) {
                    PageBitmapCache.put(pageNum, bitmap)
                    zoomView.loadPage(bitmap)
                } else if (bitmap != null) {
                    bitmap.recycle()
                }
            } catch (_: Exception) {
                if (isAdded) {
                    android.widget.Toast.makeText(
                        context, "Error al cargar página $pageNum", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun renderPage(pageNum: Int): Bitmap? {
        return PdfRendererCache.renderBitmap(pageNum, MAX_RENDER_PX)
    }
}
