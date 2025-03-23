package com.example.innovativeproject

data class UserDetails(
    var Email: String = "",
    var Phoneno: String = "",
    var Profile_pic: String = "",
    var Username: String = ""
)

data class UserMetrics(
    var AvH: Double,
    var AvP: Double,
    var AvA: Double,
    var timestamp: String
)

class User_profile(
    var userDetails: ArrayList<UserDetails> = arrayListOf(),
    var metrics: ArrayList<UserMetrics> = arrayListOf()
)
