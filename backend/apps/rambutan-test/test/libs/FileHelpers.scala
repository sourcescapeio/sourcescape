package test

import org.apache.commons.io.FileUtils
import java.io.File
import scala.jdk.CollectionConverters._

object FileHelpers {

  def directory(url: String) = {
    val fileListing = FileUtils.listFiles(new File(url), null, true)

    fileListing.asScala.map { fileObj =>
      val fileUrl = fileObj.toString()
      fileUrl.replaceFirst(s"${url}/", "") -> file(fileUrl)
    }.toList
  }

  def file(url: String) = scala.io.Source.fromFile(url).getLines().mkString("\n")

}
