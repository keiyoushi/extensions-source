# Implementing a Pam source

Every Pam site ships its own build of the reader's WASM signer. The signer holds a 32-byte
secret and a handful of ordering choices that are shuffled per site, and the server checks all
of them. A wrong value is never reported as an error: `/api/v1/t` keeps answering `refresh`
and the extension fails with `Attestation refused`.

A subclass therefore has to provide six members:

| Member | What it is | Recovered from |
|---|---|---|
| `readerSecret` | 32 bytes mixed into every HMAC and the page-key KDF | `f_q` in the decompiled wasm |
| `kdfDomain` | 8-char prefix of the KDF info string | memory after `kdfRot` |
| `signedPayload(payload)` | `readerSecret + payload` or `payload + readerSecret` | trying both against `signAttestation` |
| `manifestPayload(uid, version, ts, nonce)` | field order of the manifest HMAC input | memory after `signManifest` |
| `contentKeyMaterial(sharedSecret, info)` | order of `readerSecret`, ECDH secret and info in each KDF round | brute force against `kdfRot` |
| `contentKeyRounds` | how many times the KDF digest is folded | same brute force |

Reference values for the three known sites live in their extension classes.

## 1. Get the wasm

1. Open any chapter page and note the single `<script src="/build/assets/<hash>.js">`.
2. In that bundle, find `"./pages/serie-chapter-reader.tsx":()=>...import("./<reader>.js")`.
3. In the reader chunk, list its `import("./<x>.js")` calls. One of those chunks contains the
   string `AGFzbQ` - the wasm binary, base64-encoded inline. Decode it:

```sh
python3 -c '
import re, base64, sys
js = open(sys.argv[1]).read()
open("site.wasm", "wb").write(base64.b64decode(re.search(r"\"(AGFzbQ[A-Za-z0-9+/=]+)\"", js).group(1)))
' <chunk>.js
```

The sites sit behind Cloudflare. Fetch with a `cf_clearance` cookie solved by FlareSolverr, over
the same IP family the solver used.

## 2. Export layout

Exports are single letters and have been identical across every build so far. The reader
resolves them through an obfuscated name map, but the letters themselves are stable:

| Letter | Function | Signature |
|---|---|---|
| `c` | ctors | `()` - call once after instantiating |
| `i` / `e` | malloc / free | |
| `l` | signAttestation | `(challengePtr, len, msgPtr, len, out64)` - writes 64 hex chars |
| `k` | signManifest | `(tokenPtr, len, version:i32, uidPtr, len, ts:f64, noncePtr, len, out64)` |
| `f` | ecdhInit | `(privPtr, 32, serverPubPtr, 32, outPub32)` - stores the shared secret internally |
| `j` | kdfRot | `(uidPtr, len, keyVersion, hintPtr, 32, out32)` - needs a prior `ecdhInit` |

The module has one import, `a.a`, which can be stubbed with `() => 0`. If the letters ever
change, `wasm-objdump -x -j Export site.wasm` and the reader chunk's `Ge={...}` map plus the glue's
`_<name>=A.<letter>` assignments give the new mapping.

## 3. Secret

`readerSecret` is never left in memory: the signer rebuilds it on the stack for every call and
wipes it afterwards. Read it statically instead. `wasm-decompile site.wasm -o site.dcmp`, then
look at `f_q`: 32 lines of the form

```
a.<field> = d_...[448 + i]:ubyte ^ d_...[480 + i]:ubyte;
```

Field names map to byte positions (`a`..`z` = 0..25, `aa`..`fa` = 26..31) and the data
segment is the one initialised at offset 1184. The script below does this.

## 4. Everything else, by running the signer

The script instantiates the wasm in Node, computes the secret from the decompiled `f_q`, then
calls each export with known inputs and works out the remaining knobs by comparing against
reimplementations. Run `node recover.mjs site` with `site.wasm` and `site.dcmp` in the
working directory.

```js
import fs from 'node:fs';
import crypto from 'node:crypto';

const site = process.argv[2];
const { instance } = await WebAssembly.instantiate(fs.readFileSync(site + '.wasm'), { a: { a: () => 0 } });
const X = instance.exports;
X.c();
const mem = () => new Uint8Array(X.b.buffer);
const put = (buf) => { const p = X.i(buf.length); mem().set(buf, p); return p; };
const enc = (s) => Buffer.from(s, 'utf8');
const hmac = (key, data) => crypto.createHmac('sha256', key).update(data).digest('hex');
const sha = (parts) => { const h = crypto.createHash('sha256'); for (const p of parts) h.update(p); return h.digest(); };
const printableRun = (m, from, to) => {
  let s = from, e = to;
  while (s > 0 && m[s - 1] >= 0x20 && m[s - 1] < 0x7f) s--;
  while (e < m.length && m[e] >= 0x20 && m[e] < 0x7f) e++;
  return m.subarray(s, e).toString();
};

// readerSecret: XOR pairs from the data segment, permuted by f_q's field assignments.
const fq = fs.readFileSync(site + '.dcmp', 'utf8').match(/^function f_q\([\s\S]*?^}/m)[0];
const fieldIdx = (f) => f.length === 1 ? f.charCodeAt(0) - 97 : 26 + (f.charCodeAt(0) - 97);
const data = mem().slice(1184, 1184 + 512);
const secret = Buffer.alloc(32);
for (const m of fq.matchAll(/a\.(\w+) =\s*d_\w+\[(\d+)\]:ubyte \^\s*d_\w+\[(\d+)\]:ubyte;/g)) {
  secret[fieldIdx(m[1])] = data[+m[2]] ^ data[+m[3]];
}
console.log('readerSecret', secret.toString('hex'));

// signedPayload: HMAC(challenge, secret||msg) or HMAC(challenge, msg||secret).
const challenge = crypto.randomBytes(32).toString('hex');
const msg = '{"webdriver":false}\0clientpub==';
const out = X.i(64);
X.l(put(enc(challenge)), challenge.length, put(enc(msg)), msg.length, out);
const sig = Buffer.from(mem().slice(out, out + 64)).toString();
if (hmac(challenge, Buffer.concat([secret, enc(msg)])) === sig) console.log('signedPayload  readerSecret + payload');
else if (hmac(challenge, Buffer.concat([enc(msg), secret])) === sig) console.log('signedPayload  payload + readerSecret');
else console.log('signedPayload  NO MATCH - secret or model wrong');

// manifestPayload: the formatted string is left in memory; find it via the nonce.
const uid = 'UIDUIDUIDUID', token = 'tok' + crypto.randomBytes(8).toString('hex');
const ts = 1789390284, nonce = crypto.randomBytes(16).toString('hex');
const out2 = X.i(64);
X.k(put(enc(token)), token.length, 2, put(enc(uid)), uid.length, ts, put(enc(nonce)), nonce.length, out2);
const sig2 = Buffer.from(mem().slice(out2, out2 + 64)).toString();
let m = Buffer.from(mem());
for (let i = m.indexOf(nonce); i !== -1; i = m.indexOf(nonce, i + 1)) {
  const p = printableRun(m, i, i + nonce.length);
  const ok = hmac(token, Buffer.concat([secret, enc(p)])) === sig2 || hmac(token, Buffer.concat([enc(p), secret])) === sig2;
  if (ok) console.log('manifestPayload', JSON.stringify(p.replace(uid, '$uid').replace(String(ts), '$ts').replace(nonce, '$nonce').replace(/\b2\b/, '$version')));
}

// kdfDomain, contentKeyMaterial, contentKeyRounds: brute force against kdfRot.
const priv = crypto.randomBytes(32);
const server = crypto.generateKeyPairSync('x25519');
X.f(put(priv), 32, put(server.publicKey.export({ type: 'spki', format: 'der' }).subarray(-32)), 32, X.i(32));
const privKey = crypto.createPrivateKey({ key: Buffer.concat([Buffer.from('302e020100300506032b656e04220420', 'hex'), priv]), format: 'der', type: 'pkcs8' });
const shared = crypto.diffieHellman({ privateKey: privKey, publicKey: server.publicKey });
const keyVersion = 1, hint = crypto.randomBytes(32), out3 = X.i(32);
X.j(put(enc(uid)), uid.length, keyVersion, put(hint), 32, out3);
const ck = Buffer.from(mem().slice(out3, out3 + 32));
m = Buffer.from(mem());
const needle = '|' + uid + '|' + keyVersion;
const infos = new Set();
for (let i = m.indexOf(needle); i !== -1; i = m.indexOf(needle, i + 1)) infos.add(printableRun(m, i, i + needle.length));
const perms = (a) => a.length <= 1 ? [a] : a.flatMap((x, i) => perms([...a.slice(0, i), ...a.slice(i + 1)]).map((p) => [x, ...p]));
const parts = { readerSecret: secret, sharedSecret: shared };
search: for (const info of infos) for (const order of perms(['readerSecret', 'sharedSecret', 'info'])) for (let rounds = 1; rounds <= 8; rounds++) {
  let folded = Buffer.alloc(0);
  for (let r = 0; r < rounds; r++) folded = sha([folded, ...order.map((n) => n === 'info' ? enc(info) : parts[n])]);
  if (Buffer.from(folded.map((b, i) => b ^ hint[i])).equals(ck)) {
    console.log('kdfDomain', JSON.stringify(info.split('|')[0]));
    console.log('contentKeyMaterial', `listOf(${order.join(', ')})`);
    console.log('contentKeyRounds', rounds);
    break search;
  }
}
```

Sanity check: run it against a site whose values are already in the repo before trusting it on
a new one. If the manifest or KDF step prints nothing, the signer's argument order or fold
model changed and the decompiled `k` / `j` functions need a fresh look.

## 5. Wire it up

Paste the printed values into the extension class as overrides, bump the version, and probe the
chapter and image ops. A wrong secret shows up immediately as `Attestation refused`; a wrong
KDF knob shows up as a garbage `.ece` payload that fails GCM authentication.
