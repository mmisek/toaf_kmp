package pl.misek.toafmobile.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupElement(
     var id: Int,
     var description: String,
     var author: String,
     var created: String
)

@Serializable
data class BackupElementResponse(
     var code : Int,
     var message : List<BackupElement>
)
