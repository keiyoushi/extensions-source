/*
 * Portions of this software are derived from libsodium.
 * Source: https://github.com/jedisct1/libsodium
 * * Copyright (c) 2013-2024 Frank Denis <j at pureftpd dot org>
 */

package eu.kanade.tachiyomi.extension.en.theblank.decryption;

public class ChaCha20 {
  private static final int ROUNDS = 20;

  private static void chachaBlock(int[] output, int[] input) {
    int x0 = input[0];
    int x1 = input[1];
    int x2 = input[2];
    int x3 = input[3];
    int x4 = input[4];
    int x5 = input[5];
    int x6 = input[6];
    int x7 = input[7];
    int x8 = input[8];
    int x9 = input[9];
    int x10 = input[10];
    int x11 = input[11];
    int x12 = input[12];
    int x13 = input[13];
    int x14 = input[14];
    int x15 = input[15];

    for (int i = 0; i < ROUNDS; i += 2) {
      x0 += x4;
      x12 = rotl32(x12 ^ x0, 16);
      x8 += x12;
      x4 = rotl32(x4 ^ x8, 12);
      x0 += x4;
      x12 = rotl32(x12 ^ x0, 8);
      x8 += x12;
      x4 = rotl32(x4 ^ x8, 7);

      x1 += x5;
      x13 = rotl32(x13 ^ x1, 16);
      x9 += x13;
      x5 = rotl32(x5 ^ x9, 12);
      x1 += x5;
      x13 = rotl32(x13 ^ x1, 8);
      x9 += x13;
      x5 = rotl32(x5 ^ x9, 7);

      x2 += x6;
      x14 = rotl32(x14 ^ x2, 16);
      x10 += x14;
      x6 = rotl32(x6 ^ x10, 12);
      x2 += x6;
      x14 = rotl32(x14 ^ x2, 8);
      x10 += x14;
      x6 = rotl32(x6 ^ x10, 7);

      x3 += x7;
      x15 = rotl32(x15 ^ x3, 16);
      x11 += x15;
      x7 = rotl32(x7 ^ x11, 12);
      x3 += x7;
      x15 = rotl32(x15 ^ x3, 8);
      x11 += x15;
      x7 = rotl32(x7 ^ x11, 7);

      x0 += x5;
      x15 = rotl32(x15 ^ x0, 16);
      x10 += x15;
      x5 = rotl32(x5 ^ x10, 12);
      x0 += x5;
      x15 = rotl32(x15 ^ x0, 8);
      x10 += x15;
      x5 = rotl32(x5 ^ x10, 7);

      x1 += x6;
      x12 = rotl32(x12 ^ x1, 16);
      x11 += x12;
      x6 = rotl32(x6 ^ x11, 12);
      x1 += x6;
      x12 = rotl32(x12 ^ x1, 8);
      x11 += x12;
      x6 = rotl32(x6 ^ x11, 7);

      x2 += x7;
      x13 = rotl32(x13 ^ x2, 16);
      x8 += x13;
      x7 = rotl32(x7 ^ x8, 12);
      x2 += x7;
      x13 = rotl32(x13 ^ x2, 8);
      x8 += x13;
      x7 = rotl32(x7 ^ x8, 7);

      x3 += x4;
      x14 = rotl32(x14 ^ x3, 16);
      x9 += x14;
      x4 = rotl32(x4 ^ x9, 12);
      x3 += x4;
      x14 = rotl32(x14 ^ x3, 8);
      x9 += x14;
      x4 = rotl32(x4 ^ x9, 7);
    }

    output[0] = x0 + input[0];
    output[1] = x1 + input[1];
    output[2] = x2 + input[2];
    output[3] = x3 + input[3];
    output[4] = x4 + input[4];
    output[5] = x5 + input[5];
    output[6] = x6 + input[6];
    output[7] = x7 + input[7];
    output[8] = x8 + input[8];
    output[9] = x9 + input[9];
    output[10] = x10 + input[10];
    output[11] = x11 + input[11];
    output[12] = x12 + input[12];
    output[13] = x13 + input[13];
    output[14] = x14 + input[14];
    output[15] = x15 + input[15];
  }

  // crypto_stream_chacha20_ietf
  public static void streamIETF(byte[] c, int clen, byte[] nonce, byte[] key) {
    int[] input = new int[16];
    int[] output = new int[16];
    byte[] blockBytes = new byte[64];

    // Constants: "expand 32-byte k"
    input[0] = 0x61707865;
    input[1] = 0x3320646e;
    input[2] = 0x79622d32;
    input[3] = 0x6b206574;

    // Key
    input[4] = load32_le(key, 0);
    input[5] = load32_le(key, 4);
    input[6] = load32_le(key, 8);
    input[7] = load32_le(key, 12);
    input[8] = load32_le(key, 16);
    input[9] = load32_le(key, 20);
    input[10] = load32_le(key, 24);
    input[11] = load32_le(key, 28);

    // Counter (starts at 0)
    input[12] = 0;

    // Nonce (12 bytes for IETF variant)
    input[13] = load32_le(nonce, 0);
    input[14] = load32_le(nonce, 4);
    input[15] = load32_le(nonce, 8);

    int pos = 0;
    while (pos < clen) {
      chachaBlock(output, input);

      // Convert output to bytes
      for (int i = 0; i < 16; i++) {
        store32_le(blockBytes, i * 4, output[i]);
      }

      int remaining = clen - pos;
      int toCopy = Math.min(remaining, 64);
      System.arraycopy(blockBytes, 0, c, pos, toCopy);

      pos += 64;
      input[12]++; // Increment counter
    }
  }

  // crypto_stream_chacha20_ietf_xor_ic
  public static void streamIETFXorIC(
      byte[] c, byte[] m, int mlen, byte[] nonce, int ic, byte[] key) {
    int[] input = new int[16];
    int[] output = new int[16];
    byte[] blockBytes = new byte[64];

    // Constants: "expand 32-byte k"
    input[0] = 0x61707865;
    input[1] = 0x3320646e;
    input[2] = 0x79622d32;
    input[3] = 0x6b206574;

    // Key
    input[4] = load32_le(key, 0);
    input[5] = load32_le(key, 4);
    input[6] = load32_le(key, 8);
    input[7] = load32_le(key, 12);
    input[8] = load32_le(key, 16);
    input[9] = load32_le(key, 20);
    input[10] = load32_le(key, 24);
    input[11] = load32_le(key, 28);

    // Counter (starts at ic)
    input[12] = ic;

    // Nonce (12 bytes for IETF variant)
    input[13] = load32_le(nonce, 0);
    input[14] = load32_le(nonce, 4);
    input[15] = load32_le(nonce, 8);

    int pos = 0;
    while (pos < mlen) {
      chachaBlock(output, input);

      // Convert output to bytes
      for (int i = 0; i < 16; i++) {
        store32_le(blockBytes, i * 4, output[i]);
      }

      int remaining = mlen - pos;
      int toProcess = Math.min(remaining, 64);
      for (int i = 0; i < toProcess; i++) {
        c[pos + i] = (byte) (m[pos + i] ^ blockBytes[i]);
      }

      pos += 64;
      input[12]++; // Increment counter
    }
  }

  private static int load32_le(byte[] src, int offset) {
    return (src[offset] & 0xFF)
        | ((src[offset + 1] & 0xFF) << 8)
        | ((src[offset + 2] & 0xFF) << 16)
        | ((src[offset + 3] & 0xFF) << 24);
  }

  private static void store32_le(byte[] dst, int offset, int w) {
    dst[offset] = (byte) (w & 0xFF);
    dst[offset + 1] = (byte) ((w >>> 8) & 0xFF);
    dst[offset + 2] = (byte) ((w >>> 16) & 0xFF);
    dst[offset + 3] = (byte) ((w >>> 24) & 0xFF);
  }

  private static int rotl32(int x, int n) {
    return (x << n) | (x >>> (32 - n));
  }
}
