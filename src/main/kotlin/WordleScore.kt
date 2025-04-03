package org.example

import org.example.common.Util
import org.example.db.ScoreService
import org.example.slack.SlackService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.*
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger: Logger = LoggerFactory.getLogger("WordleScore")
private val util = Util()
private val slackService = SlackService()
private val scoreService = ScoreService()

fun main() {

    val properties = util.loadProperties()
    val slackChannel = properties!!["slack_channel"].orEmpty()
    val personalSlackId = properties["personal_slack_id"].orEmpty()
    val hourDailyFetch = properties["hour_daily_fetch"]!!.toInt()
    val hourWeeklyReport = properties["hour_weekly_report"]!!.toInt()
    val dayWeeklyReport = properties["day_weekly_report"]!!

    util.setupDb()

    //Schedule daily read of Slack data
    val dailyCalendar = util.getCalendarInstance(hourDailyFetch)
    val dailyTimer = Timer()
    if (dailyCalendar != null) {
        logger.info("Daily read of slack data occurs at {}", hourDailyFetch)

        dailyTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val oldest = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault())
                val latest =
                    LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.MAX).atZone(ZoneId.systemDefault())
                val messageList = slackService.fetchHistory(slackChannel, latest, oldest)

                if (messageList.isNotEmpty()) {
                    val userMessageMap = scoreService.filterWordleResults(messageList)
                    scoreService.insertScoreData(userMessageMap)
                }
                val personalChannelId = slackService.openConversation(personalSlackId)
                val message = "Daily Wordle results collected!"
                slackService.postMessage(personalChannelId, message)
            }
        }, dailyCalendar.time, 24 * 60 * 60 * 1000)
    }


    //Schedule weekly score aggregation and posting
    val scheduler = Executors.newScheduledThreadPool(1)

    val initialDelay = util.getInitialDelayInSeconds(hourWeeklyReport, dayWeeklyReport)
    val period = TimeUnit.DAYS.toSeconds(7) // Run every week
    logger.info("Weekly Schedule set to: {} at {}", dayWeeklyReport, hourWeeklyReport)

    scheduler.scheduleAtFixedRate(
        {
            //Fetch the last days messages and handle
            val oldest = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            val latest = ZonedDateTime.now(ZoneId.systemDefault())
            val messageList = slackService.fetchHistory(slackChannel, latest, oldest)

            if (messageList.isNotEmpty()) {
                val userMessageMap = scoreService.filterWordleResults(messageList)
                scoreService.insertScoreData(userMessageMap)
            }
            val weeklyReport = scoreService.calculateWeeklyReport()
            slackService.postMessage(slackChannel, weeklyReport)
        },
        initialDelay,
        period,
        TimeUnit.SECONDS
    )
}