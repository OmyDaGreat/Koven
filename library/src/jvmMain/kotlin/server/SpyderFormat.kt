package xyz.malefic.spyder.server

import org.http4k.format.ConfigurableKotlinxSerialization
import xyz.malefic.spyder.SpyderJson

object SpyderFormat : ConfigurableKotlinxSerialization(SpyderJson.configuration)
