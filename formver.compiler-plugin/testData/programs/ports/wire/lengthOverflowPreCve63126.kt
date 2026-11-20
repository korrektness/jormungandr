// FULL_JDK
// REPLACE_STDLIB_EXTENSIONS
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
import java.net.ProtocolException

internal const val STATE_LENGTH_DELIMITED = 2
internal const val STATE_TAG = 6 // Note: not a field encoding.

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.<!VIPER_TEXT!>and<!>(other: Int): Int = toInt() and other

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.<!VIPER_TEXT!>shl<!>(other: Int): Int = toInt() shl other

internal enum class FieldEncoding { VARINT, FIXED64, LENGTH_DELIMITED, FIXED32 }

internal <!VIPER_TEXT!>class ByteArrayProtoReader32<!> @NeverVerify constructor(
  private val source: ByteArray,

  /** The current position in [source], starting at 0 and increasing monotonically. */
  private var pos: Int = 0,

  /** The absolute position of the end of the current message. */
  private var limit: Int = source.size,
) {

  /** How to interpret the next read call. */
  private var state = STATE_LENGTH_DELIMITED

  /** The most recently read tag. Used to make packed values look like regular values. */
  private var tag = -1

  /** Limit once we complete the current length-delimited value. */
  private var pushedLimit: Int = -1

  /** The encoding of the next value to be read. */
  private var nextFieldEncoding: FieldEncoding? = null

  @Law @Pure @Unique fun framing(): Boolean = 0 <= pos && pos <= limit && limit <= source.size

  @Unique @Borrowed fun <!VIPER_TEXT!>nextLengthDelimited<!>(): Int {
    check(state == STATE_TAG || state == STATE_LENGTH_DELIMITED) {
      "Unexpected call to nextDelimited()"
    }
    return internalNextLengthDelimited()
  }

  <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>@Unique @Borrowed private fun <!VIPER_TEXT!>internalNextLengthDelimited<!>(): Int {
    nextFieldEncoding = FieldEncoding.LENGTH_DELIMITED
    state = STATE_LENGTH_DELIMITED
    val length = internalReadVarint32()
    if (length < 0) throw ProtocolException("Negative length: $length. Reader position: $pos. Last read tag: $tag.")
    if (pushedLimit != -1) throw IllegalStateException()
    // Push the current limit, and set a new limit to the length of this value.
    pushedLimit = limit
    limit = <!INTEGER_OVERFLOW!>pos + length<!>
    if (limit > pushedLimit) throw EOFException()
    return length
  }<!>

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

  @Unique @Borrowed private fun <!VIPER_TEXT!>skip<!>(byteCount: Int) {
    preconditions { 0 <= byteCount }
    val newPos = <!INTEGER_OVERFLOW!>pos + byteCount<!>
    if (newPos > limit) throw EOFException()
    pos = newPos
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>readUtf8<!>(byteCount: Int): String {
    preconditions { 0 <= byteCount }
    val newPos = <!INTEGER_OVERFLOW!>pos + byteCount<!>
    if (newPos > limit) throw EOFException()
    val result = <!OPAQUE_CALL!>source.decodeToString(startIndex = pos, endIndex = newPos)<!>
    pos = newPos
    return result
  }

  @Unique @Borrowed private fun <!VIPER_TEXT!>readByte<!>(): Byte {
    if (pos == limit) throw EOFException()
    return source[pos++]
  }
}
