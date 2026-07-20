package com.example.routeplanning.mvp.domain

data class AddressPlace(
    val id: String,
    val label: String,
    val coordinate: Coordinate
) {
    init {
        require(id.isNotBlank()) { "Place id must not be blank" }
        require(label.isNotBlank()) { "Place label must not be blank" }
    }
}
