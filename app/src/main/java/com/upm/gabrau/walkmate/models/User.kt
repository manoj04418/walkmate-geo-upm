package com.upm.gabrau.walkmate.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

class User(
    @DocumentId var id: String? = "",
    @PropertyName("name") var name: String? = "",
    @PropertyName("keywords") var keywords: ArrayList<String>? = arrayListOf()
) {
    fun toMap(): Map<String, Any?> {
        return hashMapOf(
            "name" to name,
            "keywords" to keywords
        )
    }
}