package com.example.heychat.data

object SampleData {
    val countryCodes = listOf(
        CountryCode("+1", "United States", "🇺🇸"),
        CountryCode("+91", "India", "🇮🇳"),
        CountryCode("+44", "United Kingdom", "🇬🇧"),
        CountryCode("+1", "Canada", "🇨🇦"),
        CountryCode("+61", "Australia", "🇦🇺"),
        CountryCode("+49", "Germany", "🇩🇪"),
        CountryCode("+33", "France", "🇫🇷"),
        CountryCode("+81", "Japan", "🇯🇵"),
        CountryCode("+86", "China", "🇨🇳"),
        CountryCode("+55", "Brazil", "🇧🇷")
    )

    val currentUser = User(
        id = "me",
        name = "Alex Morgan",
        phoneNumber = "+1 (555) 019-2831",
        status = "Hey there! I am using Hey Chat 💬",
        avatarUrl = null,
        isOnline = true
    )

    val users = listOf(
        User("1", "Sarah Connor", "+1 555-0101", "Coding Kotlin & Jetpack Compose 🚀", null, true, "Online"),
        User("2", "David Miller", "+1 555-0102", "At work 💼", null, false, "Today at 10:45 AM"),
        User("3", "Emma Watson", "+1 555-0103", "Living life to the fullest ✨", null, true, "Online"),
        User("4", "James Wilson", "+1 555-0104", "Battery about to die 🔋", null, false, "Yesterday"),
        User("5", "Sophia Martinez", "+1 555-0105", "Available", null, false, "Today at 8:12 AM"),
        User("6", "Liam Johnson", "+1 555-0106", "In a meeting 🔇", null, true, "Online"),
        User("7", "Olivia Brown", "+1 555-0107", "Gym time 🏋️‍♀️", null, false, "2 hours ago"),
        User("8", "Noah Garcia", "+1 555-0108", "Hey Chat user", null, false, "Yesterday")
    )

    val sampleChats = listOf(
        Chat(
            id = "chat_1",
            contact = users[0],
            lastMessage = Message("m1", "1", "Hey Alex! Are we meeting for coffee today?", "10:42 AM", false, MessageStatus.READ),
            unreadCount = 2,
            isPinned = true
        ),
        Chat(
            id = "chat_2",
            contact = users[1],
            lastMessage = Message("m2", "me", "Sounds great, I'll send the Jetpack Compose PR shortly!", "10:15 AM", true, MessageStatus.READ),
            unreadCount = 0,
            isPinned = true
        ),
        Chat(
            id = "chat_3",
            contact = users[2],
            lastMessage = Message("m3", "3", "Did you check out the new Material Design 3 guidelines?", "Yesterday", false, MessageStatus.READ),
            unreadCount = 5
        ),
        Chat(
            id = "chat_4",
            contact = users[3],
            lastMessage = Message("m4", "me", "Call me when you are free.", "Yesterday", true, MessageStatus.DELIVERED),
            unreadCount = 0
        ),
        Chat(
            id = "chat_5",
            contact = users[4],
            lastMessage = Message("m5", "5", "Thanks for helping with the Kotlin build setup!", "Monday", false, MessageStatus.READ),
            unreadCount = 0
        ),
        Chat(
            id = "chat_6",
            contact = users[5],
            lastMessage = Message("m6", "6", "Let's catch up later tonight.", "Sunday", false, MessageStatus.READ),
            unreadCount = 1
        )
    )

    val sampleMessages = listOf(
        Message("1", "1", "Hey Alex! How's the new Hey Chat app coming along?", "10:30 AM", false, MessageStatus.READ),
        Message("2", "me", "Hey Sarah! It's going awesome! Building the full Jetpack Compose UI with modern M3 components.", "10:32 AM", true, MessageStatus.READ),
        Message("3", "1", "That sounds super clean! Are you supporting light and dark theme?", "10:35 AM", false, MessageStatus.READ),
        Message("4", "me", "Yes! Both Light and Dark theme with dynamic RGB mix color palette and custom rounded bubble shapes.", "10:38 AM", true, MessageStatus.READ),
        Message("5", "1", "Hey Alex! Are we meeting for coffee today?", "10:42 AM", false, MessageStatus.READ)
    )

    val sampleStatuses = listOf(
        StatusStory("s1", users[0], "12 minutes ago", false),
        StatusStory("s2", users[2], "45 minutes ago", false),
        StatusStory("s3", users[5], "2 hours ago", true),
        StatusStory("s4", users[6], "Yesterday", true)
    )

    val sampleCalls = listOf(
        CallRecord("c1", users[0], "Today, 10:15 AM", isVideo = true, isIncoming = true, isMissed = false),
        CallRecord("c2", users[1], "Yesterday, 6:30 PM", isVideo = false, isIncoming = false, isMissed = false),
        CallRecord("c3", users[3], "October 24, 2:15 PM", isVideo = true, isIncoming = true, isMissed = true)
    )
}
