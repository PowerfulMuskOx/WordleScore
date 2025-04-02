package org.example.slack

import com.slack.api.Slack
import com.slack.api.methods.SlackApiException
import com.slack.api.methods.request.chat.ChatPostMessageRequest.ChatPostMessageRequestBuilder
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest.ConversationsHistoryRequestBuilder
import com.slack.api.methods.request.conversations.ConversationsOpenRequest.ConversationsOpenRequestBuilder
import com.slack.api.methods.request.users.UsersInfoRequest.UsersInfoRequestBuilder
import com.slack.api.model.Message
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*


class SlackService {

    val client = Slack.getInstance().methods()
    private var conversationHistory: Optional<List<Message>> = Optional.empty()
    private val logger: Logger = LoggerFactory.getLogger("SlackService")

    fun fetchHistory(id: String, latest: ZonedDateTime, oldest: ZonedDateTime): List<Message> {
        var messageList = emptyList<Message>()

        try {
            // Call the conversations.history method using the built-in WebClient
            val result = client.conversationsHistory { r: ConversationsHistoryRequestBuilder ->
                r // The token you used to initialize your app
                    .token(System.getenv("SLACK_BOT_TOKEN"))
                    .channel(id)
                    .latest(latest.toEpochSecond().toString())
                    .oldest(oldest.toEpochSecond().toString())
            }
            conversationHistory = Optional.ofNullable(result.messages)
            messageList = conversationHistory.orElse(emptyList())
            // Print results
            logger.info(
                "{} messages found in {}",
                messageList.size,
                id
            )


        } catch (e: IOException) {
            logger.error("error: {}", e.message, e)
        } catch (e: SlackApiException) {
            logger.error("error: {}", e.message, e)
        }

        return messageList
    }

    fun postMessage(id: String, text: String) {
        try {
            client.chatPostMessage { r: ChatPostMessageRequestBuilder ->
                r.token(System.getenv("SLACK_BOT_TOKEN"))
                    .channel(id)
                    .text(text)
            }
        } catch (e: IOException) {
            logger.error("error: {}", e.message, e)
        } catch (e: SlackApiException) {
            logger.error("error: {}", e.message, e)
        }
    }

    fun fetchUsername(slackId: String): String {
        logger.info("Fetching name for slackId: {}", slackId)
        var name = ""
        try {
            val result = client.usersInfo { r: UsersInfoRequestBuilder ->
                r.token(System.getenv("SLACK_BOT_TOKEN"))
                    .user(slackId)
            }
            name = result.user.realName
        } catch (e: IOException) {
            logger.error("error: {}", e.message, e)
        } catch (e: SlackApiException) {
            logger.error("error: {}", e.message, e)
        }
        return name
    }

    fun openConversation(slackId: String): String {
        var channel = ""
        try {
            val result = client.conversationsOpen { r: ConversationsOpenRequestBuilder ->
                r.token(System.getenv("SLACK_BOT_TOKEN"))
                    .users(mutableListOf(slackId))
            }
            channel = result.channel.id
        } catch (e: IOException) {
            logger.error("error: {}", e.message, e)
        } catch (e: SlackApiException) {
            logger.error("error: {}", e.message, e)
        }
        return channel

    }
}