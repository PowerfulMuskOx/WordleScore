package org.example.db

import com.slack.api.model.Message
import org.example.db.entities.Score
import org.example.db.entities.Users
import org.example.domain.ModelDomain
import org.example.domain.Weekdays
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.*

class ScoreService {
    private val logger: Logger = LoggerFactory.getLogger("WordleScore")

    fun filterWordleResults(messageList: List<Message>): MutableList<ModelDomain> {
        val filteredMessageList: MutableList<ModelDomain> = mutableListOf()
        messageList.forEach { message ->
            val messageText = message.text

            //Filter messages that contains Wordle score
            val regex = """Wordle \d+ \d+ \d+/\d+(?=\*| |:|\n|$)""".toRegex()
            val regexMobile = """Wordle \d+(?:,\d{3})* \d+/\d+(?=\*| |:|\n|$)""".toRegex()
            var wordleString = regex.find(messageText)?.value
            if (wordleString == null) {
                wordleString = regexMobile.find(messageText)?.value
            }
            if (wordleString != null) {
                val score = wordleString.substringBefore("/").last()
                val modelDomain = ModelDomain(id = message.user, score = score.digitToInt(), timeStamp = message.ts)
                filteredMessageList.add(modelDomain)
            }
        }

        return filteredMessageList
    }

    fun insertScoreData(messageList: List<ModelDomain>) {
        messageList.forEach { message ->
            val date = convertUnixToLocalDateTime(message.timeStamp)
            val weekOfYear = getWeek(date)
            val dayOfWeek = date.dayOfWeek
            val currentYear = date.year

            val userName = fetchName(message.id)

            transaction {
                val exists =
                    Score.select {
                        (Score.name eq userName) and
                                (Score.week eq weekOfYear) and
                                (Score.day eq dayOfWeek.name) and
                                (Score.year eq currentYear)
                    }.count() > 0
                if (!exists && isWeekDay(dayOfWeek.name)) {
                    logger.info("Inserting Score data for user {}", userName)
                    Score.insert {
                        it[name] = userName
                        it[week] = weekOfYear
                        it[score] = message.score
                        it[day] = dayOfWeek.name
                        it[year] = currentYear
                    }
                } else {
                    logger.info("No data inserted for user {} for day {}", userName, dayOfWeek.name)
                }
            }
        }
    }

    private fun fetchName(slackId: String): String {
        var userName = ""
        transaction {
            userName = Users.select { Users.slackId eq slackId }.single()[Users.name]
        }
        return userName
    }

    fun calculateWeeklyReport(): String {
        val currentWeek = getWeek(LocalDateTime.now())
        val currentYear = LocalDateTime.now().year

        var allScoresForCurrentWeek = emptyList<ResultRow>()

        transaction {
            allScoresForCurrentWeek = Score.select { (Score.week eq currentWeek) and (Score.year eq currentYear) }.toList()
        }

        val aggregatedMap = allScoresForCurrentWeek.groupBy { it[Score.name] }
            .mapValues { (_, values) -> values.sumOf { it[Score.score] } }.entries.sortedBy { it.value }
            .associate { it.key to it.value }

        return createSlackBulletedList(currentWeek.toString(), aggregatedMap)
    }

    private fun createSlackBulletedList(week: String, data: Map<String, Int>): String {
        val title = "Wordle Result Week $week"
        val bullets = data.entries.joinToString("\n") { (key, value) ->
            "- *$key*: $value"
        }

        return """
*$title*
$bullets
    """.trimIndent()
    }

    private fun convertUnixToLocalDateTime(unixString: String): LocalDateTime {
        val parts = unixString.split(".")
        val seconds = parts[0].toLong()
        val nanos = parts[1].padEnd(9, '0').substring(0, 9).toInt()

        val instant = Instant.ofEpochSecond(seconds, nanos.toLong())
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    }

    companion object {
        fun isWeekDay(day: String): Boolean = enumValues<Weekdays>().any { it.name.equals(day, ignoreCase = true) }
        fun getWeek(date: LocalDateTime) = date.get(WeekFields.of(Locale.getDefault()).weekOfYear())
    }
}