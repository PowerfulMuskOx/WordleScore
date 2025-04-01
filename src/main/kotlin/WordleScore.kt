package org.example

import org.example.common.Util
import org.example.db.ScoreService
import org.example.slack.SlackService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val logger: Logger = LoggerFactory.getLogger("WordleScore")
private val util = Util()
private val slackService = SlackService()
private val scoreService = ScoreService()

fun main() {

    val properties = util.loadProperties()
    val slackChannel = properties!!["slack_channel"].orEmpty()
    val hourDailyFetch = properties["hour_daily_fetch"]!!.toInt()
    val hourWeeklyReport = properties["hour_weekly_report"]!!.toInt()
    val dayWeeklyReport = properties["day_weekly_report"]

    util.setupDb()

    //Schedule daily read of Slack data
    val dailyCalendar = util.getCalendarInstance(hourDailyFetch, null)
    val dailyTimer = Timer()
    if (dailyCalendar != null) {
        logger.info("Daily Schedule set to: {}", util.getReadableDateFromCalendar(dailyCalendar))

        dailyTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val messageList = slackService.fetchHistory(slackChannel)

                if (messageList.isNotEmpty()) {
                    val userMessageMap = scoreService.filterWordleResults(messageList)
                    scoreService.insertScoreData(userMessageMap)
                }
            }
        }, dailyCalendar.time, 24 * 60 * 60 * 1000)
    }


    //Schedule weekly score aggregation and posting
    val weeklyCalendar = util.getCalendarInstance(hourWeeklyReport, dayWeeklyReport)
    val weeklyTimer = Timer()
    if (weeklyCalendar != null) {
        logger.info("Weekly Schedule set to: {}", util.getReadableDateFromCalendar(weeklyCalendar))

        weeklyTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val weeklyReport = scoreService.calculateWeeklyReport()
                slackService.postWeeklyReport(slackChannel, weeklyReport)
            }
        }, weeklyCalendar.time, 7 * 24 * 60 * 60 * 1000)

    }

    val messageList = slackService.fetchHistory(slackChannel)

    if (messageList.isNotEmpty()) {
        val userMessageMap = scoreService.filterWordleResults(messageList)
        scoreService.insertScoreData(userMessageMap)
    }

    val weeklyReport = scoreService.calculateWeeklyReport()
    slackService.postWeeklyReport(slackChannel, weeklyReport)
}