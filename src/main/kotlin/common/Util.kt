package org.example.common

import org.example.db.entities.Score
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.util.*

class Util {

    private val logger: Logger = LoggerFactory.getLogger("Util")

    fun setupDb() {
        val properties = loadProperties()
        val connectionString = properties!!["db_connection"]
        val dbDriver = properties["db_driver"]
        Database.connect(connectionString!!, driver = dbDriver!!)

        transaction {
            // Create tables
            create(Score)
        }
    }

    fun loadProperties(): Map<String, String>? {
        val content = readFile("config.properties")
        if (content != null) {
            return content.lines() // Split the string into lines
                .filter { it.isNotBlank() && !it.startsWith("#") } // Ignore blank lines and comments
                .associate {
                    val (key, value) = it.split("=") // Split each line into key-value pairs
                    key.trim() to value.trim()
                }
        }
        return null
    }

    private fun readFile(filePath: String): String? {
        val classLoader = Thread.currentThread().contextClassLoader
        val resource = classLoader.getResourceAsStream(filePath)
        if (resource != null) {
            resource.use { inputStream ->
                return inputStream.bufferedReader().use { it.readText() }
            }
        } else {
            logger.info("File: {} not found", filePath)
        }
        return null
    }

    fun getCalendarInstance(hourOfDay: Int): Calendar? {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        if (calendar.time.before(Calendar.getInstance().time)) {
            calendar.add(Calendar.DATE, 1)
        }

        return calendar
    }

    fun getInitialDelayInSeconds(hourOfDay: Int, dayOfWeek: String): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, getCalendarDayFromString(dayOfWeek))
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        if (now.after(target)) {
            target.add(Calendar.WEEK_OF_YEAR, 1) // Move to next week
        }

        return (target.timeInMillis - now.timeInMillis) / 1000
    }

    companion object {
        private fun getCalendarDayFromString(dayOfWeek: String) =
            when (dayOfWeek) {
                "SUNDAY" -> 1
                "MONDAY" -> 2
                "TUESDAY" -> 3
                "WEDNESDAY" -> 4
                "THURSDAY" -> 5
                "FRIDAY" -> 6
                "SATURDAY" -> 7
                else -> throw IllegalArgumentException("Invalid day of week: $dayOfWeek")
            }
    }
}