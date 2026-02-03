/*
 * Portions of this software are derived from libsodium.
 * Source: https://github.com/jedisct1/libsodium
 * * Copyright (c) 2013-2024 Frank Denis <j at pureftpd dot org>
 */

package eu.kanade.tachiyomi.extension.en.theblank.decryption;

public class Core {
  public static void HCaCha20(byte[] out, byte[] in, byte[] k, byte[] c) {
    int i;
    int x0, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15;

    if (c == null) {
      x0 = 0x61707865;
      x1 = 0x3320646e;
      x2 = 0x79622d32;
      x3 = 0x6b206574;
    } else {
      x0 = load32_le(c, 0);
      x1 = load32_le(c, 4);
      x2 = load32_le(c, 8);
      x3 = load32_le(c, 12);
    }

    x4 = load32_le(k, 0);
    x5 = load32_le(k, 4);
    x6 = load32_le(k, 8);
    x7 = load32_le(k, 12);
    x8 = load32_le(k, 16);
    x9 = load32_le(k, 20);
    x10 = load32_le(k, 24);
    x11 = load32_le(k, 28);
    x12 = load32_le(in, 0);
    x13 = load32_le(in, 4);
    x14 = load32_le(in, 8);
    x15 = load32_le(in, 12);

    for (i = 0; i < 10; i++) {
      // Column rounds
      int[] result;
      result = quarterround(x0, x4, x8, x12);
      x0 = result[0];
      x4 = result[1];
      x8 = result[2];
      x12 = result[3];

      result = quarterround(x1, x5, x9, x13);
      x1 = result[0];
      x5 = result[1];
      x9 = result[2];
      x13 = result[3];

      result = quarterround(x2, x6, x10, x14);
      x2 = result[0];
      x6 = result[1];
      x10 = result[2];
      x14 = result[3];

      result = quarterround(x3, x7, x11, x15);
      x3 = result[0];
      x7 = result[1];
      x11 = result[2];
      x15 = result[3];

      // Diagonal rounds
      result = quarterround(x0, x5, x10, x15);
      x0 = result[0];
      x5 = result[1];
      x10 = result[2];
      x15 = result[3];

      result = quarterround(x1, x6, x11, x12);
      x1 = result[0];
      x6 = result[1];
      x11 = result[2];
      x12 = result[3];

      result = quarterround(x2, x7, x8, x13);
      x2 = result[0];
      x7 = result[1];
      x8 = result[2];
      x13 = result[3];

      result = quarterround(x3, x4, x9, x14);
      x3 = result[0];
      x4 = result[1];
      x9 = result[2];
      x14 = result[3];
    }

    store32_le(out, 0, x0);
    store32_le(out, 4, x1);
    store32_le(out, 8, x2);
    store32_le(out, 12, x3);
    store32_le(out, 16, x12);
    store32_le(out, 20, x13);
    store32_le(out, 24, x14);
    store32_le(out, 28, x15);
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

  private static int[] quarterround(int a, int b, int c, int d) {
    a += b;
    d = rotl32(d ^ a, 16);
    c += d;
    b = rotl32(b ^ c, 12);
    a += b;
    d = rotl32(d ^ a, 8);
    c += d;
    b = rotl32(b ^ c, 7);
    return new int[] {a, b, c, d};
  }
}
