// Captures raw React Flight (RSC) wire output for models exercising the
// special value markers the Kotlin parser handles:
//   $D (Date), $n (BigInt), $Q (Map), $W (Set), $Infinity / $-Infinity / $NaN / $-0,
//   $undefined, and binary "T" chunks for large strings.
//
// Run:  cd core/src/test/rsc-capture && npm install && npm run capture
// (the "react-server" export condition is mandatory for RSC; see package.json script)
// Output: ../resources/reactflight/*.txt  (consumed by NextJsTest)

import { renderToPipeableStream } from "react-server-dom-webpack/server.node";
import { mkdirSync, writeFileSync } from "node:fs";
import { Writable } from "node:stream";
import { join } from "node:path";

// No client components -> empty bundler map is fine.
const bundlerConfig = {};

const models = {
  markers: {
    name: "hello",
    big: 123456789012345678901234567890n,
    date: new Date("2024-01-02T03:04:05.000Z"),
    inf: Infinity,
    negInf: -Infinity,
    nan: NaN,
    negZero: -0,
    plain: 3.14,
    missing: undefined,
  },
  largetext: {
    title: "big",
    // >= 1024 chars -> React outlines it as a binary "T" chunk (referenced by $<id>).
    body: "Lorem ipsum dolor sit amet. ".repeat(60),
    unicode: "héllo wörld 你好 🚀 ".repeat(80),
  },
  collections: {
    items: new Map([
      ["a", 1],
      ["b", 2],
    ]),
    tags: new Set([10, 20, 30]),
    nested: new Map([["list", new Set(["x", "y"])]]),
  },
};

function renderToString(model) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    const sink = new Writable({
      write(chunk, _enc, cb) {
        chunks.push(Buffer.from(chunk));
        cb();
      },
    });
    sink.on("finish", () => resolve(Buffer.concat(chunks).toString("utf8")));
    sink.on("error", reject);
    const { pipe } = renderToPipeableStream(model, bundlerConfig);
    pipe(sink);
  });
}

const outDir = join(import.meta.dirname, "../resources/reactflight");
mkdirSync(outDir, { recursive: true });

for (const [name, model] of Object.entries(models)) {
  const body = await renderToString(model);
  const file = join(outDir, `${name}.txt`);
  writeFileSync(file, body);
  console.log(`--- ${name} -> ${file} ---`);
}
