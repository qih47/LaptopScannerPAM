package com.dev.scanlaptop.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class UserData(
    @SerialName("npp")
    val npp: String,

    @SerialName("nama_lengkap")
    val nama_lengkap: String,

    @SerialName("role")
    val role: String,

    @SerialName("password")
    val password: String? = null,
    
    @SerialName("foto_profil")
    val foto_profil: String? = null
)