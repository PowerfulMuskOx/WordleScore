package org.example.db.entities

import org.jetbrains.exposed.sql.Table

object Score : Table() {
    val name = varchar("name", 100)
    val week = integer("week_of_year")
    val day = varchar("day_of_week", 10)
    val score = integer("score")
    override val primaryKey = PrimaryKey(name, week, day)
}