/*
 * Portions of this software are derived from libsodium.
 * Source: https://github.com/jedisct1/libsodium
 * * Copyright (c) 2013-2024 Frank Denis <j at pureftpd dot org>
 */

package eu.kanade.tachiyomi.extension.en.theblank.decryption;

public class Poly1305 {

  public static class State {
    private long r0, r1, r2, r3, r4;
    private long h0, h1, h2, h3, h4;
    private long pad0, pad1, pad2, pad3;
    private byte[] buffer = new byte[16];
    private int leftover = 0;
  }

  // crypto_onetimeauth_poly1305_init
  public static void init(State state, byte[] key) {
    // Load r from key[0..15]
    long t0 = load32_le(key, 0);
    long t1 = load32_le(key, 4);
    long t2 = load32_le(key, 8);
    long t3 = load32_le(key, 12);

    // r &= 0xffffffc0ffffffc0ffffffc0fffffff
    state.r0 = t0 & 0x3ffffff;
    state.r1 = ((t0 >>> 26) | (t1 << 6)) & 0x3ffff03;
    state.r2 = ((t1 >>> 20) | (t2 << 12)) & 0x3ffc0ff;
    state.r3 = ((t2 >>> 14) | (t3 << 18)) & 0x3f03fff;
    state.r4 = (t3 >>> 8) & 0x00fffff;

    // h = 0
    state.h0 = 0;
    state.h1 = 0;
    state.h2 = 0;
    state.h3 = 0;
    state.h4 = 0;

    // Load pad from key[16..31]
    state.pad0 = load32_le(key, 16);
    state.pad1 = load32_le(key, 20);
    state.pad2 = load32_le(key, 24);
    state.pad3 = load32_le(key, 28);

    state.leftover = 0;
  }

  // crypto_onetimeauth_poly1305_update
  public static void update(State state, byte[] m, int mlen) {
    update(state, m, 0, mlen);
  }

  public static void update(State state, byte[] m, int offset, int mlen) {
    int pos = offset;
    int remaining = mlen;

    // Handle leftover from previous update
    if (state.leftover > 0) {
      int want = 16 - state.leftover;
      if (want > remaining) {
        want = remaining;
      }
      System.arraycopy(m, pos, state.buffer, state.leftover, want);
      remaining -= want;
      pos += want;
      state.leftover += want;

      if (state.leftover < 16) {
        return;
      }

      blocks(state, state.buffer, 0, 16);
      state.leftover = 0;
    }

    // Process full 16-byte blocks
    if (remaining >= 16) {
      int want = remaining & ~15;
      blocks(state, m, pos, want);
      pos += want;
      remaining -= want;
    }

    // Store leftover
    if (remaining > 0) {
      System.arraycopy(m, pos, state.buffer, 0, remaining);
      state.leftover = remaining;
    }
  }

  // crypto_onetimeauth_poly1305_final
  public static void finalizeMAC(State state, byte[] mac) {
    if (state.leftover > 0) {
      state.buffer[state.leftover] = 1;
      for (int i = state.leftover + 1; i < 16; i++) {
        state.buffer[i] = 0;
      }
      blocksPartial(state, state.buffer, 0, 16);
    }

    // Fully carry h
    long h0 = state.h0;
    long h1 = state.h1;
    long h2 = state.h2;
    long h3 = state.h3;
    long h4 = state.h4;

    long c;
    c = h1 >>> 26;
    h1 &= 0x3ffffff;
    h2 += c;

    c = h2 >>> 26;
    h2 &= 0x3ffffff;
    h3 += c;

    c = h3 >>> 26;
    h3 &= 0x3ffffff;
    h4 += c;

    c = h4 >>> 26;
    h4 &= 0x3ffffff;
    h0 += c * 5;

    c = h0 >>> 26;
    h0 &= 0x3ffffff;
    h1 += c;

    // Compute h + -p
    long g0 = h0 + 5;
    c = g0 >>> 26;
    g0 &= 0x3ffffff;
    long g1 = h1 + c;
    c = g1 >>> 26;
    g1 &= 0x3ffffff;
    long g2 = h2 + c;
    c = g2 >>> 26;
    g2 &= 0x3ffffff;
    long g3 = h3 + c;
    c = g3 >>> 26;
    g3 &= 0x3ffffff;
    long g4 = h4 + c - (1L << 26);

    // Select h if h < p, or h + -p if h >= p
    long mask = (g4 >>> 63) - 1;
    g0 &= mask;
    g1 &= mask;
    g2 &= mask;
    g3 &= mask;
    g4 &= mask;
    mask = ~mask;
    h0 = (h0 & mask) | g0;
    h1 = (h1 & mask) | g1;
    h2 = (h2 & mask) | g2;
    h3 = (h3 & mask) | g3;
    h4 = (h4 & mask) | g4;

    // h = h % (2^128)
    h0 = ((h0) | (h1 << 26)) & 0xffffffffL;
    h1 = ((h1 >>> 6) | (h2 << 20)) & 0xffffffffL;
    h2 = ((h2 >>> 12) | (h3 << 14)) & 0xffffffffL;
    h3 = ((h3 >>> 18) | (h4 << 8)) & 0xffffffffL;

    // mac = (h + pad) % (2^128)
    long f;
    f = h0 + state.pad0;
    h0 = f & 0xffffffffL;
    f = h1 + state.pad1 + (f >>> 32);
    h1 = f & 0xffffffffL;
    f = h2 + state.pad2 + (f >>> 32);
    h2 = f & 0xffffffffL;
    f = h3 + state.pad3 + (f >>> 32);
    h3 = f & 0xffffffffL;

    store32_le(mac, 0, (int) h0);
    store32_le(mac, 4, (int) h1);
    store32_le(mac, 8, (int) h2);
    store32_le(mac, 12, (int) h3);
  }

  private static void blocks(State state, byte[] m, int offset, int bytes) {
    long hibit = 1L << 24;

    long r0 = state.r0;
    long r1 = state.r1;
    long r2 = state.r2;
    long r3 = state.r3;
    long r4 = state.r4;

    long h0 = state.h0;
    long h1 = state.h1;
    long h2 = state.h2;
    long h3 = state.h3;
    long h4 = state.h4;

    long s1 = r1 * 5;
    long s2 = r2 * 5;
    long s3 = r3 * 5;
    long s4 = r4 * 5;

    int pos = offset;
    while (bytes >= 16) {
      // h += m[i]
      long t0 = load32_le(m, pos + 0);
      long t1 = load32_le(m, pos + 4);
      long t2 = load32_le(m, pos + 8);
      long t3 = load32_le(m, pos + 12);

      h0 += t0 & 0x3ffffff;
      h1 += ((t0 >>> 26) | (t1 << 6)) & 0x3ffffff;
      h2 += ((t1 >>> 20) | (t2 << 12)) & 0x3ffffff;
      h3 += ((t2 >>> 14) | (t3 << 18)) & 0x3ffffff;
      h4 += (t3 >>> 8) | hibit;

      // h *= r
      long d0 = h0 * r0 + h1 * s4 + h2 * s3 + h3 * s2 + h4 * s1;
      long d1 = h0 * r1 + h1 * r0 + h2 * s4 + h3 * s3 + h4 * s2;
      long d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * s4 + h4 * s3;
      long d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0 + h4 * s4;
      long d4 = h0 * r4 + h1 * r3 + h2 * r2 + h3 * r1 + h4 * r0;

      // (partial) h %= p
      long c;
      c = d0 >>> 26;
      h0 = d0 & 0x3ffffff;
      d1 += c;
      c = d1 >>> 26;
      h1 = d1 & 0x3ffffff;
      d2 += c;
      c = d2 >>> 26;
      h2 = d2 & 0x3ffffff;
      d3 += c;
      c = d3 >>> 26;
      h3 = d3 & 0x3ffffff;
      d4 += c;
      c = d4 >>> 26;
      h4 = d4 & 0x3ffffff;
      h0 += c * 5;
      c = h0 >>> 26;
      h0 &= 0x3ffffff;
      h1 += c;

      pos += 16;
      bytes -= 16;
    }

    state.h0 = h0;
    state.h1 = h1;
    state.h2 = h2;
    state.h3 = h3;
    state.h4 = h4;
  }

  private static void blocksPartial(State state, byte[] m, int offset, int bytes) {
    long r0 = state.r0;
    long r1 = state.r1;
    long r2 = state.r2;
    long r3 = state.r3;
    long r4 = state.r4;

    long h0 = state.h0;
    long h1 = state.h1;
    long h2 = state.h2;
    long h3 = state.h3;
    long h4 = state.h4;

    long s1 = r1 * 5;
    long s2 = r2 * 5;
    long s3 = r3 * 5;
    long s4 = r4 * 5;

    long t0 = load32_le(m, offset + 0);
    long t1 = load32_le(m, offset + 4);
    long t2 = load32_le(m, offset + 8);
    long t3 = load32_le(m, offset + 12);

    h0 += t0 & 0x3ffffff;
    h1 += ((t0 >>> 26) | (t1 << 6)) & 0x3ffffff;
    h2 += ((t1 >>> 20) | (t2 << 12)) & 0x3ffffff;
    h3 += ((t2 >>> 14) | (t3 << 18)) & 0x3ffffff;
    h4 += (t3 >>> 8);

    // h *= r
    long d0 = h0 * r0 + h1 * s4 + h2 * s3 + h3 * s2 + h4 * s1;
    long d1 = h0 * r1 + h1 * r0 + h2 * s4 + h3 * s3 + h4 * s2;
    long d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * s4 + h4 * s3;
    long d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0 + h4 * s4;
    long d4 = h0 * r4 + h1 * r3 + h2 * r2 + h3 * r1 + h4 * r0;

    // (partial) h %= p
    long c;
    c = d0 >>> 26;
    h0 = d0 & 0x3ffffff;
    d1 += c;
    c = d1 >>> 26;
    h1 = d1 & 0x3ffffff;
    d2 += c;
    c = d2 >>> 26;
    h2 = d2 & 0x3ffffff;
    d3 += c;
    c = d3 >>> 26;
    h3 = d3 & 0x3ffffff;
    d4 += c;
    c = d4 >>> 26;
    h4 = d4 & 0x3ffffff;
    h0 += c * 5;
    c = h0 >>> 26;
    h0 &= 0x3ffffff;
    h1 += c;

    state.h0 = h0;
    state.h1 = h1;
    state.h2 = h2;
    state.h3 = h3;
    state.h4 = h4;
  }

  private static long load32_le(byte[] src, int offset) {
    return (src[offset] & 0xFFL)
        | ((src[offset + 1] & 0xFFL) << 8)
        | ((src[offset + 2] & 0xFFL) << 16)
        | ((src[offset + 3] & 0xFFL) << 24);
  }

  private static void store32_le(byte[] dst, int offset, int w) {
    dst[offset] = (byte) (w & 0xFF);
    dst[offset + 1] = (byte) ((w >>> 8) & 0xFF);
    dst[offset + 2] = (byte) ((w >>> 16) & 0xFF);
    dst[offset + 3] = (byte) ((w >>> 24) & 0xFF);
  }
}
