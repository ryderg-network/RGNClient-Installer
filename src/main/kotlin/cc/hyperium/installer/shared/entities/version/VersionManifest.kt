/*
 * Copyright © 2020 by Sk1er LLC
 *
 * All rights reserved.
 *
 * Sk1er LLC
 * 444 S Fulton Ave
 * Mount Vernon, NY
 * sk1er.club
 */

package cc.hyperium.installer.shared.entities.version

import com.google.gson.annotations.SerializedName

data class VersionManifest(
    val latest: Version,
    @SerializedName("latest_beta")
    val latestBeta: Version,
    val versions: List<Version>
)