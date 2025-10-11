/**
  Copyright © 2019 W3C and Jeff Carpenter <jeffcarp@chromium.org>

  atob and btoa source code is released under the 3-Clause BSD license.
*/
 function atob(data) {
     if (arguments.length === 0) {
         throw new TypeError("1 argument required, but only 0 present.");
     }

     const keystr = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

     function atobLookup(chr) {
         const index = keystr.indexOf(chr);
         // Throw exception if character is not in the lookup string; should not be hit in tests
         return index < 0 ? undefined : index;
     }

     data = `${data}`;
     data = data.replace(/[ \t\n\f\r]/g, "");
     if (data.length % 4 === 0) {
         data = data.replace(/==?$/, "");
     }
     if (data.length % 4 === 1 || /[^+/0-9A-Za-z]/.test(data)) {
         return null;
     }
     let output = "";
     let buffer = 0;
     let accumulatedBits = 0;
     for (let i = 0; i < data.length; i++) {
         buffer <<= 6;
         buffer |= atobLookup(data[i]);
         accumulatedBits += 6;
         if (accumulatedBits === 24) {
         output += String.fromCharCode((buffer & 0xff0000) >> 16);
         output += String.fromCharCode((buffer & 0xff00) >> 8);
         output += String.fromCharCode(buffer & 0xff);
         buffer = accumulatedBits = 0;
         }
     }
     if (accumulatedBits === 12) {
         buffer >>= 4;
         output += String.fromCharCode(buffer);
     } else if (accumulatedBits === 18) {
         buffer >>= 2;
         output += String.fromCharCode((buffer & 0xff00) >> 8);
         output += String.fromCharCode(buffer & 0xff);
     }
     return output;
 }

 function btoa(s) {
     if (arguments.length === 0) {
       throw new TypeError("1 argument required, but only 0 present.");
     }

     const keystr = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

     function btoaLookup(index) {
         if (index >= 0 && index < 64) {
         return keystr[index];
         }

         return undefined;
     }

     let i;
     s = `${s}`;
     for (i = 0; i < s.length; i++) {
       if (s.charCodeAt(i) > 255) {
         return null;
       }
     }
     let out = "";
     for (i = 0; i < s.length; i += 3) {
       const groupsOfSix = [undefined, undefined, undefined, undefined];
       groupsOfSix[0] = s.charCodeAt(i) >> 2;
       groupsOfSix[1] = (s.charCodeAt(i) & 0x03) << 4;
       if (s.length > i + 1) {
         groupsOfSix[1] |= s.charCodeAt(i + 1) >> 4;
         groupsOfSix[2] = (s.charCodeAt(i + 1) & 0x0f) << 2;
       }
       if (s.length > i + 2) {
         groupsOfSix[2] |= s.charCodeAt(i + 2) >> 6;
         groupsOfSix[3] = s.charCodeAt(i + 2) & 0x3f;
       }
       for (let j = 0; j < groupsOfSix.length; j++) {
         if (typeof groupsOfSix[j] === "undefined") {
           out += "=";
         } else {
           out += btoaLookup(groupsOfSix[j]);
         }
       }
     }
     return out;
 }

// provided by: @Trung0246 on Github

/**
 * Readable refactor of crc_vrf() with identical output to the original.
 * - Uses byte arrays throughout.
 * - Consolidates repeated transform logic.
 * - Adds clear naming and comments.
 *
 * Example check (must be true):
 * console.log(
 *   crc_vrf("67890@ The quick brown fox jumps over the lazy dog @12345")
 *   === "ZBYeRCjYBk0tkZnKW4kTuWBYw-81e-csvu6v17UY4zchviixt67VJ_tjpFEsOXB-a8X4ZFpDoDbPq8ms-7IyN95vmLVdP5vWSoTAl4ZbIBE8xijci8emrkdEYmArOPMUq5KAc3KEabUzHkNwjBtwvs0fQR7nDpI"
 * );
 */

// Node/browser-friendly base64 helpers
const atob_ = typeof atob === "function"
	? atob
	: (b64) => Buffer.from(b64, "base64").toString("binary");

const btoa_ = typeof btoa === "function"
	? btoa
	: (bin) => Buffer.from(bin, "binary").toString("base64");

// Byte helpers
const toBytes = (str) => Array.from(str, (c) => c.charCodeAt(0) & 0xff);
const fromBytes = (bytes) => bytes.map((b) => String.fromCharCode(b & 0xff)).join("");

// RC4 over byte arrays (key is a binary string)
function rc4Bytes(key, input) {
	const s = Array.from({ length: 256 }, (_, i) => i);
	let j = 0;

	// KSA
	for (let i = 0; i < 256; i++) {
		j = (j + s[i] + key.charCodeAt(i % key.length)) & 0xff;
		[s[i], s[j]] = [s[j], s[i]];
	}

	// PRGA
	const out = new Array(input.length);
	let i = 0;
	j = 0;
	for (let y = 0; y < input.length; y++) {
		i = (i + 1) & 0xff;
		j = (j + s[i]) & 0xff;
		[s[i], s[j]] = [s[j], s[i]];
		const k = s[(s[i] + s[j]) & 0xff];
		out[y] = (input[y] ^ k) & 0xff;
	}
	return out;
}

// One generic “step” to remove repeated boilerplate.
function transform(input, initSeedBytes, prefixKeyString, prefixLen, schedule) {
	const out = [];
	for (let i = 0; i < input.length; i++) {
		if (i < prefixLen) out.push(prefixKeyString.charCodeAt(i) & 0xff);

		out.push(schedule[i % 10]((input[i] ^ initSeedBytes[i % 32]) & 0xff) & 0xff);
	}
	return out;
}

// 8-bit ops
const add8 = (n) => (c) => (c + n) & 0xff;
const sub8 = (n) => (c) => (c - n + 256) & 0xff;
const xor8 = (n) => (c) => (c ^ n) & 0xff;
const rotl8 = (n) => (c) => ((c << n) | (c >>> (8 - n))) & 0xff;

// Schedules for each step (10 ops each, indexed by i % 10)
const scheduleC = [
	sub8(48), sub8(19), xor8(241), sub8(19), add8(223),
	sub8(19), sub8(170), sub8(19), sub8(48), xor8(8),
];

const scheduleY = [
	rotl8(4), add8(223), rotl8(4), xor8(163), sub8(48),
	add8(82), add8(223), sub8(48), xor8(83), rotl8(4),
];

const scheduleB = [
	sub8(19), add8(82), sub8(48), sub8(170), rotl8(4),
	sub8(48), sub8(170), xor8(8), add8(82), xor8(163),
];

const scheduleJ = [
	add8(223), rotl8(4), add8(223), xor8(83), sub8(19),
	add8(223), sub8(170), add8(223), sub8(170), xor8(83),
];

const scheduleE = [
	add8(82), xor8(83), xor8(163), add8(82), sub8(170),
	xor8(8), xor8(241), add8(82), add8(176), rotl8(4),
];

function base64UrlEncodeBytes(bytes) {
	const std = btoa_(fromBytes(bytes));
	return std.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function bytesFromBase64(b64) {
	return toBytes(atob_(b64));
}

// Constants — grouped logically and left as-is (base64) for clarity.
const CONST = {
	rc4Keys: {
		l: "u8cBwTi1CM4XE3BkwG5Ble3AxWgnhKiXD9Cr279yNW0=",
		g: "t00NOJ/Fl3wZtez1xU6/YvcWDoXzjrDHJLL2r/IWgcY=",
		B: "S7I+968ZY4Fo3sLVNH/ExCNq7gjuOHjSRgSqh6SsPJc=",
		m: "7D4Q8i8dApRj6UWxXbIBEa1UqvjI+8W0UvPH9talJK8=",
		F: "0JsmfWZA1kwZeWLk5gfV5g41lwLL72wHbam5ZPfnOVE=",
	},
	seeds32: {
		A: "pGjzSCtS4izckNAOhrY5unJnO2E1VbrU+tXRYG24vTo=",
		V: "dFcKX9Qpu7mt/AD6mb1QF4w+KqHTKmdiqp7penubAKI=",
		N: "owp1QIY/kBiRWrRn9TLN2CdZsLeejzHhfJwdiQMjg3w=",
		P: "H1XbRvXOvZAhyyPaO68vgIUgdAHn68Y6mrwkpIpEue8=",
		k: "2Nmobf/mpQ7+Dxq1/olPSDj3xV8PZkPbKaucJvVckL0=",
	},
	prefixKeys: {
		O: "Rowe+rg/0g==",
		v: "8cULcnOMJVY8AA==",
		L: "n2+Og2Gth8Hh",
		p: "aRpvzH+yoA==",
		W: "ZB4oBi0=",
	},
};

function crc_vrf(input) {
	// Stage 0: normalize to URI-encoded bytes
	let bytes = toBytes(encodeURIComponent(input));

	// RC4 1
	bytes = rc4Bytes(atob_(CONST.rc4Keys.l), bytes);

	// Step C1
	bytes = transform(
		bytes,
		bytesFromBase64(CONST.seeds32.A),
		atob_(CONST.prefixKeys.O),
		7,
		scheduleC
	);

	// RC4 2
	bytes = rc4Bytes(atob_(CONST.rc4Keys.g), bytes);

	// Step Y
	bytes = transform(
		bytes,
		bytesFromBase64(CONST.seeds32.V),
		atob_(CONST.prefixKeys.v),
		10,
		scheduleY
	);

	// RC4 3
	bytes = rc4Bytes(atob_(CONST.rc4Keys.B), bytes);

	// Step B
	bytes = transform(
		bytes,
		bytesFromBase64(CONST.seeds32.N),
		atob_(CONST.prefixKeys.L),
		9,
		scheduleB
	);

	// RC4 4
	bytes = rc4Bytes(atob_(CONST.rc4Keys.m), bytes);

	// Step J
	bytes = transform(
		bytes,
		bytesFromBase64(CONST.seeds32.P),
		atob_(CONST.prefixKeys.p),
		7,
		scheduleJ
	);

	// RC4 5
	bytes = rc4Bytes(atob_(CONST.rc4Keys.F), bytes);

	// Step E
	bytes = transform(
		bytes,
		bytesFromBase64(CONST.seeds32.k),
		atob_(CONST.prefixKeys.W),
		5,
		scheduleE
	);

	// Base64URL
	return base64UrlEncodeBytes(bytes);
}
