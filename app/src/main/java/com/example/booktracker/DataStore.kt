package com.example.booktracker

object DataStore {
    val books = mutableListOf(
        Book("The Hobbit", "J.R.R. Tolkien", "English", true),
        Book("The Lord of the Rings", "J.R.R. Tolkien", "English", false),
        Book("The Silmarillion", "J.R.R. Tolkien", "English", false),
    )
}