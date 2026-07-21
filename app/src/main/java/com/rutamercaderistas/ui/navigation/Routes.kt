package com.rutamercaderistas.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object MainRoute

@Serializable
data class AllLocalesRoute(val brand: String = "")

@Serializable
data object PromotionsRoute

@Serializable
data object ManualRoute

@Serializable
data class PdfViewerRoute(val page: Int = 0)
