// FULL_JDK
// This class is derived from the CodedInputByteBuffer class in Google's "Nano" Protocol Buffer
// implementation. The original copyright notice, list of conditions, and disclaimer for those
// classes is as follows:

// Protocol Buffers - Google's data interchange format
// Copyright 2013 Google Inc.  All rights reserved.
// http://code.google.com/p/protobuf/
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
// * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
// * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
import org.jetbrains.kotlin.formver.plugin.*
import java.io.EOFException
import java.io.IOException
import java.net.ProtocolException

internal const val DEFAULT_RECURSION_LIMIT = 100
internal const val FIELD_ENCODING_MASK = 0x7
internal const val TAG_FIELD_ENCODING_BITS = 3
internal const val STATE_VARINT = 0
internal const val STATE_FIXED64 = 1
internal const val STATE_LENGTH_DELIMITED = 2
internal const val STATE_START_GROUP = 3
internal const val STATE_END_GROUP = 4
internal const val STATE_FIXED32 = 5
internal const val STATE_TAG = 6 // Note: not a field encoding.
internal const val STATE_PACKED_TAG = 7 // Note: not a field encoding.

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.<!VIPER_TEXT!>and<!>(other: Int): Int = toInt() and other

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.<!VIPER_TEXT!>and<!>(other: Long): Long = toLong() and other

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.<!VIPER_TEXT!>shl<!>(other: Int): Int = toInt() shl other

internal enum class FieldEncoding { VARINT, FIXED64, LENGTH_DELIMITED, FIXED32 }

internal <!VIPER_TEXT!>class ByteString<!> {
  companion <!VIPER_TEXT!>object<!> {
    val EMPTY: ByteString = ByteString()
  }
}

internal <!VIPER_TEXT!>class ByteArrayProtoReader32<!>(
  private val source: ByteArray,

  /** The current position in [source], starting at 0 and increasing monotonically. */
  private var pos: Int = 0,

  /** The absolute position of the end of the current message. */
  private var limit: Int = source.size,

  /** The maximum number of levels of message and group nesting to allow. */
  private val recursionLimit: Int = DEFAULT_RECURSION_LIMIT,
) {
  init {
    if (!(pos in 0..source.size)) {
      throw IllegalArgumentException("pos=$pos must be between 0 and source size ${source.size}")
    }
    if (!(limit in pos..source.size)) {
      throw IllegalArgumentException("limit=$limit must be between pos=$pos and source size ${source.size}")
    }
    if (!(recursionLimit >= 0)) throw IllegalArgumentException("recursionLimit=$recursionLimit must not be negative")
  }

  /** The current number of levels of message nesting. */
  private var recursionDepth = 0

  /** How to interpret the next read call. */
  private var state = STATE_LENGTH_DELIMITED

  /** The most recently read tag. Used to make packed values look like regular values. */
  private var tag = -1

  /** Limit once we complete the current length-delimited value. */
  private var pushedLimit: Int = -1

  /** The encoding of the next value to be read. */
  private var nextFieldEncoding: FieldEncoding? = null

  @Law @Pure @Unique fun framing(): Boolean =
    0 <= pos && pos <= source.size && limit <= source.size && (limit == -1 || pos <= limit) &&
      (pushedLimit == -1 || (pos <= limit && limit <= pushedLimit && pushedLimit <= source.size))

  @Unique @Borrowed fun <!VIPER_TEXT!>beginMessage<!>(): Int {
    if (state != STATE_LENGTH_DELIMITED) {
      throw ProtocolException("Expected LENGTH_DELIMITED but was $state. Reader position: $pos. Last read tag: $tag.")
    }
    if (<!INTEGER_OVERFLOW!>++<!>recursionDepth > recursionLimit) {
      throw IOException("Wire recursion limit exceeded")
    }
    // Give the pushed limit to the caller to hold. The value is returned in endMessage() where we
    // resume using it as our limit.
    val token = pushedLimit
    pushedLimit = -1
    state = STATE_TAG
    return token
  }

  @Unique @Borrowed fun <!VIPER_TEXT!>endMessageAndGetUnknownFields<!>(token: Int): ByteString {
    preconditions { token == -1 || (pos <= token && token <= source.size) }
    if (!(state == STATE_TAG)) throw IllegalStateException("Unexpected call to endMessage()")
    if (!(<!INTEGER_OVERFLOW!>--<!>recursionDepth >= 0 && pushedLimit == -1)) throw IllegalStateException("No corresponding call to beginMessage()")
    if (pos != limit && recursionDepth != 0) {
      throw IOException("Expected to end at $limit but was $pos")
    }
    limit = token
    return ByteString.EMPTY
  }

  @Unique @Borrowed fun <!VIPER_TEXT!>nextLengthDelimited<!>(): Int {
    if (!(state == STATE_TAG || state == STATE_LENGTH_DELIMITED)) {
      throw IllegalStateException("Unexpected call to nextDelimited()")
    }
    return internalNextLengthDelimited()
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>internalNextLengthDelimited<!>(): Int {
    nextFieldEncoding = FieldEncoding.LENGTH_DELIMITED
    state = STATE_LENGTH_DELIMITED
    val length = internalReadVarint32()
    requireNonNegativeLength(length)
    if (pushedLimit != -1) throw IllegalStateException()
    val newLimit = checkedLimit(length)
    // Push the current limit, and set a new limit to the length of this value.
    pushedLimit = limit
    limit = newLimit
    return length
  }

  @Unique @Borrowed fun <!VIPER_TEXT!>nextTag<!>(): Int {
    if (state == STATE_PACKED_TAG) {
      state = STATE_LENGTH_DELIMITED
      return tag
    } else if (state != STATE_TAG) {
      throw IllegalStateException("Unexpected call to nextTag()")
    }

    loop@ while (pos < limit) {
      val tagAndFieldEncoding = internalReadVarint32()
      if (tagAndFieldEncoding == 0) throw ProtocolException("Unexpected tag 0. Reader position: $pos. Last read tag: $tag.")

      tag = tagAndFieldEncoding ushr TAG_FIELD_ENCODING_BITS
      when (val groupOrFieldEncoding = tagAndFieldEncoding and FIELD_ENCODING_MASK) {
        STATE_START_GROUP -> {
          skipGroup(tag)
          continue@loop
        }

        STATE_END_GROUP -> throw ProtocolException("Unexpected end group. Reader position: $pos. Last read tag: $tag.")

        STATE_LENGTH_DELIMITED -> {
          internalNextLengthDelimited()
          return tag
        }

        STATE_VARINT -> {
          nextFieldEncoding = FieldEncoding.VARINT
          state = STATE_VARINT
          return tag
        }

        STATE_FIXED64 -> {
          nextFieldEncoding = FieldEncoding.FIXED64
          state = STATE_FIXED64
          return tag
        }

        STATE_FIXED32 -> {
          nextFieldEncoding = FieldEncoding.FIXED32
          state = STATE_FIXED32
          return tag
        }

        else -> throw ProtocolException("Unexpected field encoding: $groupOrFieldEncoding. Reader position: $pos. Last read tag: $tag.")
      }
    }
    return -1
  }

  @Unique @Borrowed fun <!VIPER_TEXT!>peekFieldEncoding<!>(): FieldEncoding? = nextFieldEncoding

  @Unique @Borrowed fun <!VIPER_TEXT!>skip<!>() {
    when (state) {
      STATE_LENGTH_DELIMITED -> {
        val byteCount = beforeLengthDelimitedScalar()
        skip(byteCount)
      }
      STATE_VARINT -> readVarint64()
      STATE_FIXED64 -> readFixed64()
      STATE_FIXED32 -> readFixed32()
      else -> throw IllegalStateException("Unexpected call to skip()")
    }
  }

  /** Skips a section of the input delimited by START_GROUP/END_GROUP type markers. */
  @Unique @Borrowed private fun <!VIPER_TEXT!>skipGroup<!>(expectedEndTag: Int) {
    while (pos < limit) {
      val tagAndFieldEncoding = internalReadVarint32()
      if (tagAndFieldEncoding == 0) throw ProtocolException("Unexpected tag 0. Reader position: $pos. Last read tag: $tag.")
      val tag = tagAndFieldEncoding ushr TAG_FIELD_ENCODING_BITS
      when (val groupOrFieldEncoding = tagAndFieldEncoding and FIELD_ENCODING_MASK) {
        STATE_START_GROUP -> {
          recursionDepth<!INTEGER_OVERFLOW!>++<!>
            if (recursionDepth > recursionLimit) {
              throw IOException("Wire recursion limit exceeded")
            }
            // Nested group.
            skipGroup(tag)
            recursionDepth<!INTEGER_OVERFLOW!>--<!>
        }
        STATE_END_GROUP -> {
          if (tag == expectedEndTag) return // Success!
          throw ProtocolException("Unexpected end group. Reader position: $pos. Last read tag: $tag.")
        }
        STATE_LENGTH_DELIMITED -> {
          val length = internalReadVarint32()
          requireNonNegativeLength(length, tag)
          skip(length)
        }
        STATE_VARINT -> {
          state = STATE_VARINT
          readVarint64()
        }
        STATE_FIXED64 -> {
          state = STATE_FIXED64
          readFixed64()
        }
        STATE_FIXED32 -> {
          state = STATE_FIXED32
          readFixed32()
        }
        else -> throw ProtocolException("Unexpected field encoding: $groupOrFieldEncoding. Reader position: $pos. Last read tag: $tag.")
      }
    }
    throw EOFException()
  }

  @Unique @Borrowed fun <!VIPER_TEXT!>beforePossiblyPackedScalar<!>(): Boolean {
    return when (state) {
      STATE_LENGTH_DELIMITED -> {
        if (pos < limit) {
          // It's packed and there's a value.
          true
        } else {
          // It's packed and there aren't any values.
          limit = pushedLimit
          pushedLimit = -1
          state = STATE_TAG
          false
        }
      }
      STATE_VARINT,
      STATE_FIXED64,
      STATE_FIXED32,
      -> true // Not packed.

      else -> throw ProtocolException("unexpected state: $state. Reader position: $pos. Last read tag: $tag.")
    }
  }

  @Unique @Borrowed fun <!VIPER_TEXT!>readString<!>(): String {
    val byteCount = beforeLengthDelimitedScalar()
    return readUtf8(byteCount)
  }

  @Unique @Borrowed fun <!VIPER_TEXT!>readVarint32<!>(): Int {
    if (state != STATE_VARINT && state != STATE_LENGTH_DELIMITED) {
      throw ProtocolException("Expected VARINT or LENGTH_DELIMITED but was $state. Reader position: $pos. Last read tag: $tag.")
    }
    val result = internalReadVarint32()
    afterPackableScalar(STATE_VARINT)
    return result
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>internalReadVarint32<!>(): Int {
    var tmp = readByte()
    if (tmp >= 0) {
      return tmp.toInt()
    }
    var result = tmp and 0x7f
    tmp = readByte()
    if (tmp >= 0) {
      result = result or (tmp shl 7)
    } else {
      result = result or (tmp and 0x7f shl 7)
      tmp = readByte()
      if (tmp >= 0) {
        result = result or (tmp shl 14)
      } else {
        result = result or (tmp and 0x7f shl 14)
        tmp = readByte()
        if (tmp >= 0) {
          result = result or (tmp shl 21)
        } else {
          result = result or (tmp and 0x7f shl 21)
          tmp = readByte()
          result = result or (tmp shl 28)
          if (tmp < 0) {
            // Discard upper 32 bits.
            for (i in 0..4) {
              if (readByte() >= 0) {
                return result
              }
            }
            throw ProtocolException("Malformed VARINT. Reader position: $pos. Last read tag: $tag.")
          }
        }
      }
    }
    return result
  }

  @NeverVerify @Unique @Borrowed fun <!VIPER_TEXT!>readVarint64<!>(): Long {
    if (state != STATE_VARINT && state != STATE_LENGTH_DELIMITED) {
      throw ProtocolException("Expected VARINT or LENGTH_DELIMITED but was $state. Reader position: $pos. Last read tag: $tag.")
    }
    var shift = 0
    var result: Long = 0
    while (shift < 64) {
      val b = readByte()
      result = result or ((b and 0x7F).toLong() shl shift)
      if (b and 0x80 == 0) {
        afterPackableScalar(STATE_VARINT)
        return result
      }
      shift += 7
    }
    throw ProtocolException("WireInput encountered a malformed varint. Reader position: $pos. Last read tag: $tag.")
  }

  @Unique @Borrowed fun <!VIPER_TEXT!>readFixed32<!>(): Int {
    if (state != STATE_FIXED32 && state != STATE_LENGTH_DELIMITED) {
      throw ProtocolException("Expected FIXED32 or LENGTH_DELIMITED but was $state. Reader position: $pos. Last read tag: $tag.")
    }
    val result = readIntLe()
    afterPackableScalar(STATE_FIXED32)
    return result
  }

  @Unique @Borrowed fun <!VIPER_TEXT!>readFixed64<!>(): Long {
    if (state != STATE_FIXED64 && state != STATE_LENGTH_DELIMITED) {
      throw ProtocolException("Expected FIXED64 or LENGTH_DELIMITED but was $state. Reader position: $pos. Last read tag: $tag.")
    }
    val result = readLongLe()
    afterPackableScalar(STATE_FIXED64)
    return result
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>afterPackableScalar<!>(fieldEncoding: Int) {
    if (state == fieldEncoding) {
      state = STATE_TAG
    } else {
      when {
        pos > limit -> throw IOException("Expected to end at $limit but was $pos")
        pos == limit -> {
          // We've completed a sequence of packed values. Pop the limit.
          limit = pushedLimit
          pushedLimit = -1
          state = STATE_TAG
        }
        else -> state = STATE_PACKED_TAG
      }
    }
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>beforeLengthDelimitedScalar<!>(): Int {
    if (state != STATE_LENGTH_DELIMITED) {
      throw ProtocolException("Expected LENGTH_DELIMITED but was $state. Reader position: $pos. Last read tag: $tag.")
    }
    val byteCount = remainingInLimit()
    state = STATE_TAG
    // We've completed a length-delimited scalar. Pop the limit.
    if (pushedLimit != -1) limit = pushedLimit
    pushedLimit = -1
    return byteCount
  }

  @Unique @Borrowed fun <!VIPER_TEXT!>nextFieldMinLengthInBytes<!>(): Int {
    return when (nextFieldEncoding) {
      FieldEncoding.LENGTH_DELIMITED -> remainingInLimit()
      FieldEncoding.FIXED32 -> 4
      FieldEncoding.FIXED64 -> 8
      FieldEncoding.VARINT -> 1
      null -> throw IllegalStateException("nextFieldEncoding is not set")
    }
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>skip<!>(byteCount: Int) {
    pos = checkedLimit(byteCount)
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>readUtf8<!>(byteCount: Int): String {
    val newPos = checkedLimit(byteCount)
    val result = <!OPAQUE_CALL!>source.decodeToString(startIndex = pos, endIndex = newPos)<!>
    pos = newPos
    return result
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>readByte<!>(): Byte {
    checkedLimit(1)
    return source[pos++]
  }

  @NeverVerify @Unique @Borrowed private fun <!VIPER_TEXT!>readIntLe<!>(): Int {
    checkedLimit(4)

    val result = (
      (source[pos++] and 0xff)
        or (source[pos++] and 0xff shl 8)
        or (source[pos++] and 0xff shl 16)
        or (source[pos++] and 0xff shl 24)
      )

    return result
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>readLongLe<!>(): Long {
    checkedLimit(8)

    val result = (
      (source[pos++].toLong() and 0xffL)
        or (source[pos++].toLong() and 0xffL shl 8)
        or (source[pos++].toLong() and 0xffL shl 16)
        or (source[pos++].toLong() and 0xffL shl 24)
        or (source[pos++].toLong() and 0xffL shl 32)
        or (source[pos++].toLong() and 0xffL shl 40)
        or (source[pos++].toLong() and 0xffL shl 48)
        or (source[pos++].toLong() and 0xffL shl 56)
      )

    return result
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>requireNonNegativeLength<!>(length: Int, lastReadTag: Int = tag) {
    postconditions<Unit> {
      length >= 0
      pos == old(pos) && limit == old(limit) && pushedLimit == old(pushedLimit)
    }
    if (length < 0) {
      throw ProtocolException("Negative length: $length. Reader position: $pos. Last read tag: $lastReadTag.")
    }
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>checkedLimit<!>(byteCount: Int): Int {
    postconditions<Int> { r ->
      0 <= byteCount && pos <= limit && byteCount <= limit - pos && r == pos + byteCount
      pos == old(pos) && limit == old(limit) && pushedLimit == old(pushedLimit)
    }
    if (byteCount < 0 || byteCount > remainingInLimit()) throw EOFException()
    return pos + byteCount
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>remainingInLimit<!>(): Int {
    postconditions<Int> { r ->
      pos <= limit && r == limit - pos
      pos == old(pos) && limit == old(limit) && pushedLimit == old(pushedLimit)
    }
    if (pos > limit) throw EOFException()
    return limit - pos
  }
}
