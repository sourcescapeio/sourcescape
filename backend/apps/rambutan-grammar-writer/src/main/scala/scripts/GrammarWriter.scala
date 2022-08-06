package scripts

import models.query.grammar.Grammar
import play.api.libs.json._
import java.nio.file.{ Paths, Files }
import org.apache.commons.io.FileUtils
import java.nio.charset.StandardCharsets

object GrammarWriter {

  def main(args: Array[String]): Unit = {

    args.headOption match {
      case Some(dir) => {
        val payload = Grammar.generateGrammarPayload()
        val filePath = Paths.get(System.getProperty("user.dir")).resolve(dir)
        println(filePath)

        if (Files.exists(filePath) && Files.isDirectory(filePath)) {
          println(s"${filePath} already exists and is a directory")
          System.exit(1)
        }

        FileUtils.write(filePath.toFile(), Json.stringify(payload), "UTF-8", false)
        println(s"Wrote new grammar to ${filePath}")
      }
      case None => {
        println("PLEASE SET A FILE TO WRITE TO")
        System.exit(1)
      }
    }
  }
}
