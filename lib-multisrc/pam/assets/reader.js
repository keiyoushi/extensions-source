// The reader v2 handshake, run on the site's own code: attestation, manifest, page key.
(async (input) => {
    const done = (result) => window[input.bridge].post(JSON.stringify(result));
    const decode = (b64) => Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    const encode = (bytes) => btoa(String.fromCharCode(...bytes));

    try {
        const meta = document.createElement("meta");
        meta.name = "csrf-token";
        meta.content = input.csrfToken;
        document.head.appendChild(meta);

        // The site's attestation, which reports this browser's fingerprint and mints the chapter token.
        const shared = await import(input.sharedUrl);
        const attest = Object.values(shared).find((it) => typeof it === "function" && String(it).includes('"/api/v1/t"'));
        if (!attest) throw new Error("Reader attestation not found");

        // The reader draws its ECDH key from getRandomValues; hand it ours so the token is bound to our key.
        const privateKey = decode(input.privateKey);
        const getRandomValues = crypto.getRandomValues.bind(crypto);
        let keyTaken = false;
        crypto.getRandomValues = (array) => {
            if (!keyTaken && array instanceof Uint8Array && array.length === privateKey.length) {
                keyTaken = true;
                array.set(privateKey);
                return array;
            }
            return getRandomValues(array);
        };
        // The key the attestation actually sent; any other would get the manifest request refused.
        const fetch = window.fetch;
        let attestedKey = null;
        window.fetch = (resource, options) => {
            if (String(resource) === "/api/v1/t") attestedKey = JSON.parse(options.body).pk;
            return fetch(resource, options);
        };

        let attestation = input.attestation;
        let token = null;
        try {
            for (let attempt = 0; attempt < 3 && !token; attempt++) {
                const result = await attest(attestation, input.serverPubkey);
                if (!result.supported) throw new Error("Attestation refused: device not supported");
                token = result.token;
                if (token) break;

                // Only the challenge handed back by a partial reload gets a token.
                const reload = await fetch(input.chapterUrl, { headers: input.reloadHeaders, credentials: "same-origin" });
                const props = (await reload.json()).props;
                token = props.chapter_token ?? null;
                attestation = props.attestation;
                if (!token && !attestation) throw new Error("Attestation refused");
            }
        } finally {
            crypto.getRandomValues = getRandomValues;
            window.fetch = fetch;
        }
        if (!token) throw new Error("Attestation refused");
        if (attestedKey !== input.clientPubkey) throw new Error("Unsupported reader build");

        const signer = await (await import(input.glueUrl)).default();
        const call = (name, ...args) => {
            const fn = signer[input.exports[name]];
            if (typeof fn !== "function") throw new Error(`Reader export ${name} missing`);
            if (!fn(...args)) throw new Error(`Reader ${name} failed`);
        };
        const put = (bytes) => {
            const ptr = signer._malloc(bytes.length);
            signer.HEAPU8.set(bytes, ptr);
            return ptr;
        };
        const text = new TextEncoder();

        const ts = Math.floor(Date.now() / 1000);
        const nonce = Array.from(getRandomValues(new Uint8Array(16)), (b) => b.toString(16).padStart(2, "0")).join("");
        const t = text.encode(token);
        const u = text.encode(input.uid);
        const n = text.encode(nonce);
        const signature = signer._malloc(64);
        call("signManifest", put(t), t.length, input.manifestVersion, put(u), u.length, ts, put(n), n.length, signature);

        const response = await fetch("/api/v1/m", {
            method: "POST",
            headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": input.csrfToken, "X-Client-Pubkey": input.clientPubkey },
            credentials: "same-origin",
            body: JSON.stringify({
                v: input.manifestVersion,
                c: input.uid,
                t: token,
                ts,
                n: nonce,
                s: new TextDecoder().decode(signer.HEAPU8.slice(signature, signature + 64)),
            }),
        });
        if (!response.ok) throw new Error(`Manifest rejected: HTTP ${response.status} ${(await response.text()).slice(0, 100)}`);
        const manifest = await response.json();

        // The manifest base is /p/<chapter uid>/<key version>/...; the hint unmasks into the page key.
        const [, chapterUid, keyVersion] = manifest.base.split("/").filter(Boolean);
        const serverPubkey = decode(input.serverPubkey);
        call("ecdhInit", put(privateKey), privateKey.length, put(serverPubkey), serverPubkey.length, signer._malloc(32));
        privateKey.fill(0);
        const c = text.encode(chapterUid);
        const hint = decode(manifest.hint);
        const contentKey = signer._malloc(32);
        call("kdfRot", put(c), c.length, Number(keyVersion), put(hint), hint.length, contentKey);

        done({ token, manifest, contentKey: encode(signer.HEAPU8.slice(contentKey, contentKey + 32)) });
    } catch (e) {
        done({ error: String(e?.message ?? e) });
    }
})
