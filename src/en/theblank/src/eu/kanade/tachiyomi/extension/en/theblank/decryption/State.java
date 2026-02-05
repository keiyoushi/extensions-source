/*
 * Portions of this software are derived from libsodium.
 * Source: https://github.com/jedisct1/libsodium
 * * Copyright (c) 2013-2024 Frank Denis <j at pureftpd dot org>
 */

package eu.kanade.tachiyomi.extension.en.theblank.decryption;

// crypto_secretstream_xchacha20poly1305_state
public class State {
  public byte[] k = new byte[32]; // key (crypto_stream_chacha20_ietf_KEYBYTES)
  public byte[] nonce = new byte[12]; // nonce (crypto_stream_chacha20_ietf_NONCEBYTES)
  public byte[] _pad = new byte[8]; // padding
}
