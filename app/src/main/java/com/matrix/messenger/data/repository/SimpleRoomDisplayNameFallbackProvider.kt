package com.matrix.messenger.data.repository

import org.matrix.android.sdk.api.provider.RoomDisplayNameFallbackProvider

class SimpleRoomDisplayNameFallbackProvider : RoomDisplayNameFallbackProvider {
    override fun excludedUserIds(roomId: String): List<String> = emptyList()
    override fun getNameForRoomInvite(): String = "Invite"
    override fun getNameForEmptyRoom(isDirect: Boolean, leftMemberNames: List<String>): String = "Empty room"
    override fun getNameFor1member(name: String): String = name
    override fun getNameFor2members(name1: String, name2: String): String = "$name1 and $name2"
    override fun getNameFor3members(name1: String, name2: String, name3: String): String = "$name1, $name2 and $name3"
    override fun getNameFor4members(name1: String, name2: String, name3: String, name4: String): String = "$name1, $name2, $name3 and $name4"
    override fun getNameFor4membersAndMore(name1: String, name2: String, name3: String, remainingCount: Int): String = "$name1, $name2, $name3 and $remainingCount others"
}
