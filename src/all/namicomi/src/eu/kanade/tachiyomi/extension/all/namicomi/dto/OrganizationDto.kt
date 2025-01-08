package eu.kanade.tachiyomi.extension.all.namicomi.dto

import eu.kanade.tachiyomi.extension.all.namicomi.NamicomiConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(NamicomiConstants.organization)
data class OrganizationDto(override val attributes: OrganizationAttributesDto? = null) : EntityDto()

@Serializable
data class OrganizationAttributesDto(val name: String) : AttributesDto()
