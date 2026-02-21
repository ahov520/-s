package com.readflow.app.ui.navigation

object Route {
    const val SHELF = "shelf"
    const val READER = "reader"
    const val READER_BOOK_ID = "bookId"

    const val READER_PATH = "$READER/{$READER_BOOK_ID}"

    fun reader(bookId: String): String = "$READER/$bookId"
}
