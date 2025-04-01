package org.example.db

import com.slack.api.model.Message
import org.example.db.entities.Score
import org.example.domain.ModelDomain
import org.example.domain.Weekdays
import org.example.slack.SlackService
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.*

class ScoreService {
    private val logger: Logger = LoggerFactory.getLogger("WordleScore")
    private val slackService = SlackService()

    fun filterWordleResults(messageList: List<Message>): MutableList<ModelDomain> {
        val filteredMessageList: MutableList<ModelDomain> = mutableListOf()
        messageList.forEach { message ->
            val messageText = message.text

            //Filter messages that contains Wordle score
            val regex = """Wordle \d+(\D+)\d+ (?:\d+|X)/\d+(?=\*| |:|\n|$)""".toRegex()
            val wordleString = regex.find(messageText)?.value

            if (wordleString != null) {
                var score = wordleString.substringBefore("/").last()
                if(score == 'X') {
                    score = '7'
                }
                val modelDomain = ModelDomain(id = message.user, score = score.digitToInt(), timeStamp = message.ts)
                filteredMessageList.add(modelDomain)
            }
        }

        logger.info("Number of Wordle messages: {}", filteredMessageList.size)

        return filteredMessageList
    }

    fun insertScoreData(messageList: List<ModelDomain>) {
        messageList.forEach { message ->
            val date = convertUnixToLocalDateTime(message.timeStamp)
            val weekOfYear = getWeek(date)
            val dayOfWeek = date.dayOfWeek
            val currentYear = date.year

            val userName = message.id

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

    fun calculateWeeklyReport(): String {
        val currentWeek = getWeek(LocalDateTime.now())
        val currentYear = LocalDateTime.now().year

        var allScoresForCurrentWeek = emptyList<ResultRow>()

        transaction {
            allScoresForCurrentWeek = Score.select { (Score.week eq currentWeek) and (Score.year eq currentYear) }.toList()
        }

        val aggregatedMap = allScoresForCurrentWeek.groupBy { slackService.fetchUsername(it[Score.name]) }
            .mapValues { (_, values) ->
                val totalScore = values.sumOf { it[Score.score] }
                val adjustment = (5 - values.size) * 7
                totalScore + adjustment
            }.toList().sortedBy { (_, score) -> score }.toMap()

        return createSlackNumberedList(currentWeek.toString(), aggregatedMap)
    }

    private fun createSlackNumberedList(week: String, data: Map<String, Int>): String {
        val title = "Wordle Result Week $week"
        val numbers = data.entries.withIndex().joinToString("\n") { (index, entry) ->
            "${index + 1}. *${entry.key}*: ${entry.value}"
        }

        return """
*$title*
$numbers
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