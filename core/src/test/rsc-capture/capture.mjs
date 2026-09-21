// Captures raw React Flight (RSC) wire output for models exercising the
// special value markers the Kotlin parser handles:
//   $D (Date), $n (BigInt), $Q (Map), $W (Set), $Infinity / $-Infinity / $NaN / $-0,
//   $undefined, $@ (Promise), $L (lazy chunk), path references ($<id>:<path>),
//   and binary "T" chunks for large strings.
//
// Run:  cd core/src/test/rsc-capture && npm install && npm run capture
// (the "react-server" export condition is mandatory for RSC; see package.json script)
// Output: ../resources/reactflight/*.txt  (consumed by NextJsTest)
//
// The capture runs with NODE_ENV=production, which is what real sites serve. The
// development build additionally emits debug rows carrying absolute component-stack
// file paths, which would bake this machine's paths into the fixtures.

import { renderToPipeableStream } from "react-server-dom-webpack/server.node";
import { createElement } from "react";
import { mkdirSync, writeFileSync } from "node:fs";
import { Writable } from "node:stream";
import { join } from "node:path";

// Yields to the event loop so a Server Component cannot finish synchronously,
// which is what forces React to outline its subtree behind a "$L" reference.
const tick = () => new Promise((resolve) => setTimeout(resolve, 10));

async function DeferredTitle() {
  await tick();
  return "Deferred Title";
}

async function DeferredCount() {
  await tick();
  return "42 chapters";
}

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
  // A Promise in the model is outlined into its own row; the parent gets "$@<id>"
  // and the row is emitted once the promise settles. Next.js does this for values
  // passed to the client as promises (async params/searchParams, PPR holes).
  promises: {
    mangaTitle: "Async Manga",
    chapters: Promise.resolve([
      { number: 2, title: "Second" },
      { number: 1, title: "First" },
    ]),
  },
  // An async Server Component cannot complete synchronously, so React flushes the
  // parent element immediately with "$L<id>" in the children slot and emits the
  // resolved subtree in its own row later. This is the App Router streaming path.
  lazyrefs: createElement(
    "section",
    { mangaTitle: "Lazy Manga" },
    createElement(DeferredTitle),
    createElement(DeferredCount),
  ),
  // A shared object reference appearing twice: React outlines the first occurrence and
  // emits the second as a path reference ($<id>:preview:0) into the model where it was
  // first written. Mirrors RimuScans, whose full chapter list references its 3 newest
  // chapters (already written by the preview component) by path.
  pathrefs: (() => {
    const newest = { number: 3, title: "Newest" };
    return {
      preview: [newest, { number: 2, title: "Middle" }],
      full: {
        mangaTitle: "My Manga",
        chapters: [newest, { number: 1, title: "Oldest" }],
      },
    };
  })(),
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
