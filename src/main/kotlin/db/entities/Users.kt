package org.example.db.entities

import org.jetbrains.exposed.sql.Table

object Users : Table() {
    val slackId = varchar("slack_id", 100)
    val name = varchar("name", 100)
    override val primaryKey = PrimaryKey(slackId, name)
}