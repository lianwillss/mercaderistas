package com.rutamercaderistas.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.barteksc.pdfviewer.PDFView
import com.rutamercaderistas.R
import com.rutamercaderistas.ui.components.PdfGridModal
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ClayBackground = Color(0xFFF0EBE3)
private val ClaySurface = Color(0xFFE8E0D5)

private fun Modifier.clayHighlight(shape: Shape = RoundedCornerShape(16.dp)): Modifier = drawBehind {
    drawRoundRect(
        color = Color.White.copy(alpha = 0.45f),
        topLeft = Offset(-size.width * 0.03f, -size.height * 0.03f),
        size = size,
        cornerRadius = CornerRadius(
            when (shape) {
                is RoundedCornerShape -> shape.topStart.toPx(size, this)
                else -> 16.dp.toPx()
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfFile: File?,
    brandName: String?,
    pageStart: Int,
    pageEnd: Int,
    onClose: () -> Unit,
    onPageChanged: (Int) -> Unit = {},
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var uiVisible by remember { mutableStateOf(true) }
    var pdfViewRef by remember { mutableStateOf<PDFView?>(null) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var showGrid by remember { mutableStateOf(false) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    val thumbnailCache = remember {
        object : LinkedHashMap<Int, Bitmap>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean = size > 40
        }
    }

    val pageRange = pageStart..pageEnd.coerceAtLeast(pageStart)
    val pageCount = pageRange.count()
    val view = LocalView.current
    val ctx = LocalContext.current

    if (pdfFile == null || !pdfFile.exists()) {
        errorMessage = if (pdfFile == null) stringResource(R.string.pdf_no_disponible) else stringResource(R.string.pdf_archivo_no_encontrado)
    }

    LaunchedEffect(currentPage) {
        if (!isLoading) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            onPageChanged(currentPage)
        }
    }

    DisposableEffect(pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) return@DisposableEffect onDispose { }
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        pdfRenderer = renderer
        onDispose {
            renderer.close()
            pfd.close()
            pdfRenderer = null
        }
    }

    fun resetChromeTimer() {
        uiVisible = true
    }

    BackHandler(enabled = showGrid) { showGrid = false }
    BackHandler(enabled = !showGrid && pdfFile != null) { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
            when {
                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.PictureAsPdf,
                                contentDescription = stringResource(R.string.pdf_no_disponible),
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = errorMessage ?: "Error",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                pdfFile != null -> {
                    AndroidView(
                        factory = { ctx ->
                            PDFView(ctx, null).apply {
                                fromFile(pdfFile)
                                    .pages(*pageRange.toList().toIntArray())
                                    .defaultPage(0)
                                    .enableSwipe(true)
                                    .swipeHorizontal(true)
                                    .pageSnap(true)
                                    .pageFling(true)
                                    .fitEachPage(true)
                                    .enableDoubletap(true)
                                    .enableAnnotationRendering(false)
                                    .enableAntialiasing(true)
                                    .spacing(4)
                                    .onLoad { totalPages = pageCount; isLoading = false }
                                    .onPageChange { page: Int, _: Int ->
                                        currentPage = page
                                        resetChromeTimer()
                                    }
                                    .onError { isLoading = false; errorMessage = ctx.getString(R.string.pdf_error_carga) }
                                    .load()
                                pdfViewRef = this
                                setMinZoom(1f)
                                setMidZoom(2f)
                                setMaxZoom(4f)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                    ) {
                        AnimatedVisibility(
                            visible = uiVisible,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut(),
                        ) {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text(
                                            text = brandName ?: stringResource(R.string.pdf_manual_marcas),
                                            style = MaterialTheme.typography.titleLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (totalPages > 0) {
                                            Text(
                                                text = stringResource(R.string.pdf_pagina_formato, currentPage + 1, totalPages),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = onClose) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.cerrar),
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = {
                                            if (pdfRenderer != null) {
                                                showGrid = !showGrid
                                                uiVisible = false
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Apps,
                                            contentDescription = stringResource(R.string.pdf_vista_cuadricula_cd),
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = ClaySurface.copy(alpha = 0.95f),
                                    scrolledContainerColor = ClaySurface.copy(alpha = 0.95f),
                                ),
                                modifier = Modifier
                                    .clayHighlight(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onDragEnd = {
                                                if (dragAccumulator > 800f) onClose()
                                                dragAccumulator = 0f
                                            },
                                            onVerticalDrag = { _, d -> dragAccumulator += d },
                                        )
                                    },
                            )
                        }
                    }

                    if (isLoading) {
                        val shimmerTransition = rememberInfiniteTransition()
                        val shimmerAlpha by shimmerTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800),
                                repeatMode = RepeatMode.Reverse,
                            ),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ClayBackground),
                            contentAlignment = Alignment.Center,
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .fillMaxHeight(0.85f)
                                    .clayHighlight(RoundedCornerShape(4.dp)),
                                shape = RoundedCornerShape(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = ClaySurface.copy(alpha = shimmerAlpha),
                                ),
                            ) {
                                Column(modifier = Modifier.padding(28.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.4f)
                                            .height(18.dp)
                                            .background(
                                                MaterialTheme.colorScheme.onSurface
                                                    .copy(alpha = 0.08f),
                                                RoundedCornerShape(3.dp),
                                            ),
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    repeat(6) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(
                                                    when (it) {
                                                        2 -> 0.55f
                                                        4 -> 0.7f
                                                        else -> 1f
                                                    },
                                                )
                                                .height(10.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.onSurface
                                                        .copy(alpha = 0.06f),
                                                    RoundedCornerShape(2.dp),
                                                ),
                                        )
                                        Spacer(
                                            Modifier.height(
                                                if (it < 5) 10.dp else 0.dp,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    AnimatedVisibility(
        visible = showGrid,
        enter = scaleIn(initialScale = 0.9f) + fadeIn(),
        exit = scaleOut(targetScale = 0.9f) + fadeOut(),
    ) {
        PdfGridModal(
            brandName = brandName,
            renderer = pdfRenderer,
            pageRange = pageRange,
            currentPage = currentPage,
            cache = thumbnailCache,
            onPageSelected = { page ->
                pdfViewRef?.jumpTo(page, true)
                currentPage = page
                showGrid = false
                resetChromeTimer()
            },
            onDismiss = { showGrid = false },
        )
    }
}

fun renderThumbnail(renderer: PdfRenderer, pageIndex: Int): Bitmap {
    val page = renderer.openPage(pageIndex)
    val w = 150
    val h = (w * page.height / page.width).coerceAtLeast(100)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    page.close()
    return bmp
}


