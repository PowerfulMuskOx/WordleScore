package org.example.common

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sun.org.apache.xalan.internal.lib.ExsltDatetime.time
import org.example.db.entities.Score
import org.example.db.entities.Users
import org.example.domain.UserDomain
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.util.*

class Util {

    private val logger: Logger = LoggerFactory.getLogger("WordleScore")

    fun setupDb() {
        val properties = loadProperties()
        val connectionString = properties!!["db_connection"]
        val dbDriver = properties["db_driver"]
        Database.connect(connectionString!!, driver = dbDriver!!)

        transaction {
            // Create tables
            create(Users, Score)

            // Create users
            createUsers()
        }
    }

    private fun createUsers() {
        val json = readFile("users.json")
        val gson = Gson()
        val listType = object : TypeToken<List<UserDomain>>() {}.type
        val users: List<UserDomain> = gson.fromJson(json, listType)
        users.forEach { user ->
            val alreadyExists = Users.select { Users.slackId eq user.slackId }.count() > 0
            if (!alreadyExists) {
                Users.insert {
                    it[name] = user.name
                    it[slackId] = user.slackId
                }
            }
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

    fun getCalendarInstance(hourOfDay: Int, dayOfWeek: String?): Calendar? {
        val weeklyCalendar = dayOfWeek != null
        val calendar = Calendar.getInstance()
        if (weeklyCalendar) {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.DAY_OF_WEEK, DayOfWeek.valueOf(dayOfWeek!!).value)

            if (calendar.time.before(Calendar.getInstance().time)) {
                calendar.add(Calendar.DATE, 7)
            }

        } else {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)

            if (calendar.time.before(Calendar.getInstance().time)) {
                calendar.add(Calendar.DATE, 1)
            }
        }

        return calendar
    }

    fun getReadableDateFromCalendar(calendar: Calendar): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }
}