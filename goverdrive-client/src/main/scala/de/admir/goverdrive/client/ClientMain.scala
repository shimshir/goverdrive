package de.admir.goverdrive.client

import com.typesafe.scalalogging.Logger
import de.admir.goverdrive.client.db.SqLite
import de.admir.goverdrive.core.model.FileMapping

object ClientMain extends App {
    val logger = Logger[this.type]

    SqLite.insertFileMappingSync(FileMapping("qwerty1", "/local/path/x", "/remote/path/x"))
}
