package com.rutamercaderistas.adapters

import com.rutamercaderistas.models.LocalDelDia

sealed class ListItem {
    data class Header(val comuna: String, var isExpanded: Boolean = true) : ListItem()
    data class StoreItem(val local: LocalDelDia) : ListItem()
}
