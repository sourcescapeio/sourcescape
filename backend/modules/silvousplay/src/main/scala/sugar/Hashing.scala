package silvousplay

import java.util.Base64
import java.security.SecureRandom

import javax.crypto._
import javax.crypto.spec._
import org.bouncycastle.util.encoders.Hex

object Hashing {
  private val SizeOfPasswordHashInBytes = 16
  private val RandomSource = new SecureRandom()
  private val DefaultNrOfPasswordHashIterations = 2000 // 2014 recommended value: 1000-2000

  private val encoder = Base64.getEncoder()
  private val decoder = Base64.getDecoder()

  def uuid() = java.util.UUID.randomUUID().toString().replace("-", "")

  def encode64(bytes: Array[Byte]) = encoder.encodeToString(bytes)
  def decode64(str: String) = decoder.decode(str)

  def encodeHex(bytes: Array[Byte]) = Hex.toHexString(bytes)

  private val messageDigest = java.security.MessageDigest.getInstance("SHA-256")
  def checksum(bytes: Array[Byte]): String = {
    encode64(messageDigest.digest(bytes))
  }

  def pbkdf2(password: String, salt: Array[Byte]): Array[Byte] = {
    val nrOfIterations = 2000
    val keySpec = new PBEKeySpec(password.toCharArray(), salt, DefaultNrOfPasswordHashIterations, SizeOfPasswordHashInBytes * 8)
    val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")

    keyFactory.generateSecret(keySpec).getEncoded()
  }

  def generateSalt(): Array[Byte] = {
    val keyData = new Array[Byte](SizeOfPasswordHashInBytes)
    RandomSource.nextBytes(keyData)
    keyData
  }
}

trait HashingHelpers {
  val Hashing = silvousplay.Hashing

  val Keyable = silvousplay.Keyable
}
