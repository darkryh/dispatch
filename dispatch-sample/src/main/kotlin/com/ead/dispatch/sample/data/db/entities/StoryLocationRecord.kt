package com.ead.dispatch.sample.data.db.entities

data class StoryLocationRecord(
    /** Stable identifier for the location record. */
    val id: String,
    /** Parent story id that owns this location. */
    val storyId: String,
    /** Profile details grouped for readability. */
    val profile: LocationProfile,
    /** Tags for filtering and retrieval (normalized list). */
    val tags: List<String> = emptyList(),
    /** Creation time for audits and sorting. */
    val createdAt: Long,
) {
    data class LocationProfile(
        /** Human-readable location name. */
        val name: String,
        /** Optional description for worldbuilding. */
        val description: String? = null,
    )
}
