"use strict";
(self.webpackChunk_N_E = self.webpackChunk_N_E || []).push([
  [1648],
  {
    98739: function (e, t, r) {
      r.d(t, {
        g: function () {
          return n;
        },
      });
      var i = r(11527),
        s = r(48705);
      let n = (e) => {
        let { label: t, size: r = "M" } = e;
        return "S" === r
          ? (0, i.jsxs)("div", {
              className:
                "inline-flex h-16 flex-row items-center  rounded-24 bg-comic-reward px-4",
              children: [
                (0, i.jsx)(s.J, { name: s.u.COIN_S }),
                (0, i.jsx)("div", {
                  className: "ml-4 text-xxs font-bold text-white",
                  children: t,
                }),
              ],
            })
          : (0, i.jsxs)("div", {
              className:
                "inline-flex flex-row items-center rounded-24 bg-comic-reward px-8 py-4",
              children: [
                (0, i.jsx)(s.J, { name: s.u.COIN_S }),
                (0, i.jsx)("div", {
                  className: "ml-4 font-bold text-white typography-12",
                  children: t,
                }),
              ],
            });
      };
    },
    8789: function (e, t, r) {
      r.d(t, {
        n: function () {
          return d;
        },
      });
      var i = r(11527),
        s = r(50959),
        n = r(44230),
        a = r(9917),
        l = r(48736),
        o = r(26454);
      let d = (e) => {
          let { eventBanners: t, fa: r } = e;
          (0, s.useEffect)(() => {
            t.length > 0 && (0, o.vk)({ id: t[0].url });
          }, [t]);
          let n = (0, s.useCallback)(() => {
              t.length > 0 &&
                ((0, o.yw)({ id: t[0].url }),
                r &&
                  (0, o.V4)({
                    ...r,
                    click_name: "open_event_banner",
                    item_id: t[0].url,
                  }));
            }, [t, r]),
            a = (0, l.F)({
              impression_name: "".concat(
                null == r ? void 0 : r.area_name,
                "_campaign_banner"
              ),
              item_id: t.length > 0 ? t[0].id.toString() : void 0,
              area_id: null == r ? void 0 : r.area_id,
            });
          return 0 === t.length
            ? null
            : (0, i.jsx)(c, {
                eventBanner: t[0],
                onClick: n,
                inViewHandler: a,
              });
        },
        c = (e) => {
          let { eventBanner: t, onClick: r, inViewHandler: s } = e;
          return (0, i.jsxs)(n.df, {
            onChange: s,
            triggerOnce: !0,
            threshold: 0.5,
            as: "a",
            href: t.url,
            target: "_blank",
            rel: "noopener noreferrer",
            className: "block w-full screen4:mx-auto screen4:w-[912px]",
            style: { fontSize: 0 },
            onClick: r,
            children: [
              (0, i.jsx)("div", {
                className: "hidden screen4:block",
                children: (0, i.jsx)(a.E, {
                  src: t.images.pc.url,
                  width: 912,
                  height: 130,
                }),
              }),
              (0, i.jsx)("div", {
                className: "screen4:hidden",
                children: (0, i.jsx)(a.E, {
                  src: t.images.main.url,
                  width: 375,
                  height: 130,
                }),
              }),
            ],
          });
        };
    },
    61511: function (e, t, r) {
      r.d(t, {
        O: function () {
          return n;
        },
      });
      var i = r(11527),
        s = r(3585);
      let n = () =>
        (0, i.jsxs)("div", {
          className:
            "mx-auto w-full rounded-8 py-64 text-center screen4:my-16 screen4:bg-surface2",
          children: [
            (0, i.jsx)("div", {
              children: (0, i.jsx)(s.C, { naiyoVariation: 0 }),
            }),
            (0, i.jsxs)("div", {
              className: "mt-16 text-text2 typography-14",
              children: [
                "現在メンテナンス中につき、この機能はご利用いただけません。",
                (0, i.jsx)("br", {}),
                "メンテナンス終了までしばらくお待ちください。",
              ],
            }),
          ],
        });
    },
    20616: function (e, t, r) {
      r.d(t, {
        f: function () {
          return o;
        },
      });
      var i = r(11527),
        s = r(48705),
        n = r(3898),
        a = r(12678),
        l = r(79141);
      let o = (e) => {
        let { adBook: t } = e;
        return (0, i.jsxs)("div", {
          className:
            "mb-8 flex gap-8 rounded-8 border-0 border-solid border-border screen4:border screen4:p-8",
          children: [
            (0, i.jsx)("div", {
              className: "h-[146px]",
              children: (0, i.jsx)(n.E, {
                src: t.imageUrl,
                width: 110,
                height: 146,
                alt: "".concat(t.title, " 書影"),
              }),
            }),
            (0, i.jsxs)("div", {
              className:
                "border-0 border-b border-solid border-border screen4:border-0",
              children: [
                (0, i.jsx)("div", {
                  className: "line-clamp-2 text-text2 typography-14",
                  children: t.title,
                }),
                (0, i.jsx)("div", {
                  className: "mt-8 w-88",
                  children: (0, i.jsx)("a", {
                    href: t.amazonUrl,
                    target: "_blank",
                    rel: "noreferrer noopener",
                    children: (0, i.jsxs)(l.Y, {
                      type: a.L.Primary,
                      children: [
                        (0, i.jsx)("span", {
                          className: "mr-4",
                          children: "購入する",
                        }),
                        (0, i.jsx)(s.J, { name: s.u.EXTERNAL }),
                      ],
                    }),
                  }),
                }),
              ],
            }),
          ],
        });
      };
    },
    90551: function (e, t, r) {
      r.d(t, {
        T: function () {
          return s;
        },
      });
      var i = r(11527);
      let s = (e) => {
        let { size: t = "S" } = e;
        return "S" === t
          ? (0, i.jsx)("div", {
              className:
                "inline-flex h-16 items-center rounded-24 bg-gradient-to-r from-brand-primary-50 to-comic-plmr px-4",
              children: (0, i.jsx)("div", {
                className: "text-xxs font-bold text-white",
                children: "プラミル",
              }),
            })
          : (0, i.jsx)("div", {
              className:
                "inline-flex rounded-24 bg-gradient-to-r from-brand-primary-50 to-comic-plmr px-8 py-4",
              children: (0, i.jsx)("div", {
                className: "font-bold text-white typography-12",
                children: "プラミル",
              }),
            });
      };
    },
    18651: function (e, t, r) {
      r.d(t, {
        FT: function () {
          return g;
        },
        jN: function () {
          return m;
        },
        tA: function () {
          return y;
        },
      });
      var i = r(21262),
        s = r(11527),
        n = r(5341),
        a = r(48705),
        l = r(52339);
      function o() {
        let e = (0, i._)(["typography-20"]);
        return (
          (o = function () {
            return e;
          }),
          e
        );
      }
      function d() {
        let e = (0, i._)(["typography-16"]);
        return (
          (d = function () {
            return e;
          }),
          e
        );
      }
      function c() {
        let e = (0, i._)(["typography-14"]);
        return (
          (c = function () {
            return e;
          }),
          e
        );
      }
      function u() {
        let e = (0, i._)(["typography-12"]);
        return (
          (u = function () {
            return e;
          }),
          e
        );
      }
      function h() {
        let e = (0, i._)(["text-text2"]);
        return (
          (h = function () {
            return e;
          }),
          e
        );
      }
      function p() {
        let e = (0, i._)(["text-white"]);
        return (
          (p = function () {
            return e;
          }),
          e
        );
      }
      function x() {
        let e = (0, i._)(["text-text1"]);
        return (
          (x = function () {
            return e;
          }),
          e
        );
      }
      let m = { XL: "XL", L: "L", M: "M", S: "S" },
        g = { JPY: "JPY", COIN: "COIN" },
        v = (e) => {
          switch (e) {
            case m.XL:
              return (0, n.Z)(o());
            case m.L:
              return (0, n.Z)(d());
            case m.M:
              return (0, n.Z)(c());
            case m.S:
              return (0, n.Z)(u());
          }
        },
        f = (e) => {
          switch (e) {
            case "gray":
              return (0, n.Z)(h());
            case "white":
              return (0, n.Z)(p());
            default:
              return (0, n.Z)(x());
          }
        },
        _ = (e) => {
          switch (e) {
            case m.XL:
            case m.L:
            case m.M:
              return a.u.JPY_M;
            case m.S:
              return a.u.JPY_S;
          }
        },
        w = (e) => {
          switch (e) {
            case m.XL:
            case m.L:
            case m.M:
              return a.u.COIN_M;
            case m.S:
              return a.u.COIN_S;
          }
        },
        y = (e) => {
          let { value: t, size: r = m.S, color: i, type: o = g.JPY } = e;
          return (0, s.jsxs)("div", {
            className: "flex items-center",
            children: [
              (0, s.jsx)("div", {
                className: "mr-4 flex items-center",
                children:
                  o === g.JPY
                    ? (0, s.jsx)(a.J, { name: _(r) })
                    : (0, s.jsx)(a.J, { name: w(r) }),
              }),
              (0, s.jsx)("div", {
                className: (0, n.Z)("font-bold", v(r), f(i)),
                children: (0, l._y)(t),
              }),
            ],
          });
        };
    },
    79141: function (e, t, r) {
      r.d(t, {
        Y: function () {
          return h;
        },
      });
      var i = r(21262),
        s = r(11527),
        n = r(5341),
        a = r(12678),
        l = r(68184);
      function o() {
        let e = (0, i._)([
          "bg-brand-primary-50 hover:bg-brand-primary-50-hover text-white",
        ]);
        return (
          (o = function () {
            return e;
          }),
          e
        );
      }
      function d() {
        let e = (0, i._)(["bg-surface3 hover:bg-surface3-hover text-text2"]);
        return (
          (d = function () {
            return e;
          }),
          e
        );
      }
      function c() {
        let e = (0, i._)([
          "bg-background2 dark:bg-surface3-disabled cursor-not-allowed text-surface4 dark:text-text4",
        ]);
        return (
          (c = function () {
            return e;
          }),
          e
        );
      }
      let u = (e) => {
          switch (e) {
            case a.L.Primary:
              return (0, n.Z)(o());
            case a.L.Default:
              return (0, n.Z)(d());
            case a.L.Disabled:
              return (0, n.Z)(c());
          }
        },
        h = (e) => {
          let { children: t, onClick: r, type: i = a.L.Default, ...o } = e;
          return (0, s.jsx)("div", {
            className: (0, n.Z)(l.IV, u(i)),
            style: { transition: "all 0.2s" },
            onClick: r,
            ...o,
            children: t,
          });
        };
    },
    45488: function (e, t, r) {
      r.d(t, {
        u: function () {
          return l;
        },
      });
      var i = r(11527),
        s = r(44264),
        n = r.n(s),
        a = r(68184);
      let l = (e) => {
        let { path: t, htmlMetaData: r, canonical: s, isViewer: l = !1 } = e;
        return (0, i.jsxs)(n(), {
          children: [
            (0, i.jsx)("title", { children: r.title }),
            (0, i.jsx)("meta", {
              name: "viewport",
              content:
                "width=device-width, initial-scale=1.0, viewport-fit=cover",
            }),
            (0, i.jsx)("meta", { name: "description", content: r.description }),
            (0, i.jsx)("meta", { property: "og:type", content: "website" }),
            (0, i.jsx)("meta", { property: "og:title", content: r.title }),
            (0, i.jsx)("meta", {
              property: "og:description",
              content: r.description,
            }),
            (0, i.jsx)("meta", {
              property: "og:url",
              content: "".concat(a._n).concat(t),
            }),
            (0, i.jsx)("meta", { property: "og:image", content: r.imageUrl }),
            (0, i.jsx)("meta", {
              name: "twitter:card",
              content: r.twitterCard,
            }),
            (0, i.jsx)("meta", {
              name: "twitter:site",
              content: "@pixivcomic",
            }),
            (0, i.jsx)("meta", {
              name: "twitter:description",
              content: r.description,
            }),
            (0, i.jsx)("meta", { name: "twitter:image", content: r.imageUrl }),
            (0, i.jsx)("meta", {
              name: "theme-color",
              content: l ? "#000" : "#ffc400",
            }),
            (0, i.jsx)("link", {
              rel: "manifest",
              href: "/static/manifest1.json",
            }),
            s && (0, i.jsx)("link", { rel: "canonical", href: s }),
          ],
        });
      };
    },
    84315: function (e, t, r) {
      r.d(t, {
        A: function () {
          return l;
        },
      });
      var i = r(11527),
        s = r(90163),
        n = r.n(s),
        a = r(79910);
      let l = (e) => {
        let { children: t } = e;
        return (0, i.jsxs)("div", {
          className: "jsx-68335456ff889d6c",
          children: [
            (0, i.jsx)(n(), {
              id: "68335456ff889d6c",
              children: "body{background-color:#181818;margin:0;padding:0}",
            }),
            (0, i.jsx)(a.Y, { children: t }),
          ],
        });
      };
    },
    71885: function (e, t, r) {
      r.d(t, {
        o: function () {
          return tq;
        },
      });
      var i = r(11527),
        s = r(90163),
        n = r.n(s),
        a = r(71250),
        l = r(50959),
        o = r(93709),
        d = r(79710),
        c = r(41212),
        u = r(24817),
        h = r(36699),
        p = r(48705),
        x = r(48147),
        m = r(93363),
        g = r(82434),
        v = r(39999),
        f = r(35659),
        _ = r(37618),
        w = r(26454);
      let y = (e) => {
          let { episode: t } = e,
            { width: r } = (0, v.Ux)(),
            { currentPage: s, dispatch: n, isSpreadView: a } = (0, f.Y)(),
            o = (0, l.useRef)(a),
            d = (0, l.useRef)(r),
            [c, u, h] = (0, _.L)(!0),
            p = (0, l.useMemo)(
              () =>
                t
                  ? a
                    ? Math.ceil((t.pages.length - 1) / 2)
                    : t.pages.length - 1
                  : 0,
              [t, a]
            ),
            x = (0, l.useMemo)(() => (s >= p ? p : s), [s, p]),
            m = (0, l.useCallback)(
              (e) => {
                n({ type: "seekFinished", payload: e });
              },
              [n]
            );
          return ((0, l.useEffect)(() => {
            o.current !== a &&
              (h(),
              setTimeout(() => {
                (o.current = a), u();
              }, 1));
          }, [a, h, u]),
          (0, l.useEffect)(() => {
            d.current !== r &&
              (h(),
              setTimeout(() => {
                (d.current = r), u();
              }, 1));
          }, [r, h, u]),
          t && c)
            ? (0, i.jsx)(j, {
                currentPage: x,
                pageLength: p,
                dragEnd: m,
                width: r,
                episode: t,
              })
            : null;
        },
        j = (e) => {
          let {
              currentPage: t,
              pageLength: r,
              dragEnd: s,
              width: n,
              episode: a,
            } = e,
            o = (0, l.useMemo)(() => n - 100, [n]),
            d = (0, l.useMemo)(() => {
              let e = o / r;
              return [...Array(r + 1)].map((t, i) => e * (r - i));
            }, [r, o]),
            c = (0, m.c)(d[t]),
            p = (0, g.H)(c, (e) => e - 12),
            x = (0, g.H)(p, (e) => o - e),
            [v, f] = (0, l.useState)(t),
            [y, j, b] = (0, _.L)(!1),
            k = (0, l.useCallback)(() => {
              j(),
                (0, w.V4)({
                  click_name: "seek_start",
                  area_name: "viewer",
                  area_id: a.id.toString(),
                  item_index: t,
                });
            }, [j, a, t]),
            N = (0, l.useCallback)(
              (e, t) => {
                let r = d.reduce(
                  (e, r, i) => {
                    let s = Math.abs(r - (t.point.x - 50));
                    return e.diff > s ? { diff: s, candidate: i } : e;
                  },
                  { diff: 1 / 0, candidate: 0 }
                );
                f(r.candidate);
              },
              [d]
            ),
            S = (0, l.useCallback)(() => {
              s(v),
                c.set(d[v]),
                b(),
                (0, w.V4)({
                  click_name: "seek_end",
                  area_name: "viewer",
                  area_id: a.id.toString(),
                  item_index: t,
                  area_index: v.toString(),
                });
            }, [b, v, s, d, t, a]);
          return (0, i.jsx)("div", {
            className: "relative bg-[#333]",
            children: (0, i.jsxs)("div", {
              className: "mx-auto mb-[-20px] pt-[12px]",
              style: { width: o },
              children: [
                (0, i.jsxs)("div", {
                  className: "relative",
                  style: { width: o },
                  children: [
                    (0, i.jsx)("div", { className: "h-[2px] w-full bg-text4" }),
                    (0, i.jsx)(h.E.div, {
                      className:
                        "relative top-[-2px] h-[2px] bg-brand-primary-50",
                      style: { width: x, left: p },
                    }),
                  ],
                }),
                (0, i.jsx)(u.M, {
                  children: (0, i.jsx)(h.E.div, {
                    className: "absolute top-[-58px]",
                    style: { x: p },
                  }),
                }),
                (0, i.jsxs)(h.E.div, {
                  drag: "x",
                  className: "relative",
                  style: { x: p, y: -11 },
                  onDrag: N,
                  onDragStart: k,
                  onDragEnd: S,
                  dragConstraints: { right: o - 12, left: -12 },
                  dragElastic: 0,
                  dragMomentum: !1,
                  children: [
                    (0, i.jsx)(u.M, {
                      children:
                        y &&
                        (0, i.jsxs)(h.E.div, {
                          className:
                            "absolute rounded-[50px] bg-black bg-opacity-50 px-16 py-8 text-white",
                          style: { transform: "translate(-44%,-140%)" },
                          initial: { opacity: 0 },
                          animate: { opacity: 1 },
                          children: [(y ? v : t) + 1, "\xa0/\xa0", r + 1],
                        }),
                    }),
                    (0, i.jsx)("div", {
                      className: "rounded-oval bg-brand-primary-50",
                      style: { width: 16, height: 16 },
                    }),
                  ],
                }),
              ],
            }),
          });
        };
      var b = r(14373),
        k = r(89166),
        N = r(68446),
        S = r(68562),
        E = r(52339);
      let C = (e) => {
          var t;
          let { episode: r } = e;
          return (0, i.jsxs)("div", {
            className: "cursor-pointer",
            "aria-label": "next-episode",
            children: [
              (0, i.jsx)("div", {
                className: "flex items-center justify-center",
                children: (0, i.jsx)(p.J, { name: p.u.PREV }),
              }),
              (0, i.jsxs)("div", {
                className: "flex",
                children: [
                  (null == r
                    ? void 0
                    : null === (t = r.nextEpisode) || void 0 === t
                    ? void 0
                    : t.state) === "purchasable" &&
                    (0, i.jsx)("div", {
                      className: "mt-[-2px] h-[20px]",
                      children: (0, i.jsx)(p.J, { name: p.u.COIN_M }),
                    }),
                  (0, i.jsx)("div", {
                    className: "mt-4 text-center text-white typography-12",
                    children: "次の話",
                  }),
                ],
              }),
            ],
          });
        },
        I = (e) => {
          let { episode: t } = e;
          return (null == t ? void 0 : t.nextEpisode)
            ? (0, i.jsx)("div", {
                className: "flex",
                children: (0, i.jsx)(R, { episode: t }),
              })
            : (0, i.jsx)("div", {
                className: "opacity-50",
                children: (0, i.jsx)(C, { episode: t }),
              });
        },
        R = (e) => {
          var t;
          let { episode: r } = e,
            s = (0, k.C)(),
            [n] = (0, S.h)({
              episode: null == r ? void 0 : r.nextEpisode,
              fa: {
                click_name: "order_episode",
                area_name: "viewer_bottom_next",
                area_id: r.id.toString(),
                item_id:
                  null === (t = r.nextEpisode) || void 0 === t
                    ? void 0
                    : t.id.toString(),
              },
              showLoadingWhenOpen: !0,
            }),
            { dispatch: a } = (0, N.vR)(),
            o = (0, l.useCallback)(() => {
              var e;
              a({
                type: "needLogin",
                payload: {
                  fa: {
                    area_name: "viewer_bottom_next",
                    area_id: r.id.toString(),
                    item_id:
                      null === (e = r.nextEpisode) || void 0 === e
                        ? void 0
                        : e.id.toString(),
                  },
                },
              });
            }, [r]);
          if (!r.nextEpisode) return null;
          if ((0, E.lk)(r.nextEpisode.viewerPath))
            return (0, i.jsx)("a", {
              href: r.nextEpisode.viewerPath,
              children: (0, i.jsx)(C, { episode: r }),
            });
          switch (r.nextEpisode.state) {
            case "readable":
              return (0, i.jsx)(x.r, {
                href: r.nextEpisode.viewerPath,
                fa: {
                  click_name: s ? "open_novel_episode" : "open_episode",
                  area_name: s
                    ? "novel_viewer_bottom_next"
                    : "viewer_bottom_next",
                  area_id: r.id.toString(),
                  item_id: r.nextEpisode.id.toString(),
                },
                children: (0, i.jsx)(C, { episode: r }),
              });
            case "purchasable":
              return (0, i.jsx)("div", {
                onClick: n,
                children: (0, i.jsx)(C, { episode: r }),
              });
            case "login_required":
              return (0, i.jsx)("div", {
                onClick: o,
                children: (0, i.jsx)(C, { episode: r }),
              });
            default:
              return null;
          }
        },
        P = (e) => {
          var t;
          let { episode: r } = e;
          return (0, i.jsxs)("div", {
            className: "cursor-pointer",
            "aria-label": "prev-episode",
            children: [
              (0, i.jsx)("div", {
                className: "flex items-center justify-center",
                children: (0, i.jsx)(p.J, { name: p.u.NEXT }),
              }),
              (0, i.jsxs)("div", {
                className: "flex",
                children: [
                  (null == r
                    ? void 0
                    : null === (t = r.prevEpisode) || void 0 === t
                    ? void 0
                    : t.salesType) === "sell" &&
                    !r.prevEpisode.isPurchased &&
                    (0, i.jsx)("div", {
                      className: "mt-[-2px] h-[20px]",
                      children: (0, i.jsx)(p.J, { name: p.u.COIN_M }),
                    }),
                  (0, i.jsx)("div", {
                    className: "mt-4 text-center text-white typography-12",
                    children: "前の話",
                  }),
                ],
              }),
            ],
          });
        },
        L = (e) => {
          let { episode: t } = e;
          return (null == t ? void 0 : t.prevEpisode)
            ? (0, i.jsx)("div", {
                className: "flex",
                children: (0, i.jsx)(T, { episode: t }),
              })
            : (0, i.jsx)("div", {
                className: "opacity-50",
                children: (0, i.jsx)(P, { episode: t }),
              });
        },
        T = (e) => {
          var t;
          let { episode: r } = e,
            s = (0, k.C)(),
            [n] = (0, S.h)({
              episode: null == r ? void 0 : r.prevEpisode,
              fa: {
                click_name: "order_episode",
                area_name: "viewer_bottom_previous",
                area_id: null == r ? void 0 : r.id.toString(),
                item_id:
                  null == r
                    ? void 0
                    : null === (t = r.prevEpisode) || void 0 === t
                    ? void 0
                    : t.id.toString(),
              },
              showLoadingWhenOpen: !0,
            }),
            { dispatch: a } = (0, N.vR)(),
            o = (0, l.useCallback)(() => {
              var e;
              a({
                type: "needLogin",
                payload: {
                  fa: {
                    area_name: "viewer_bottom_next",
                    area_id: r.id.toString(),
                    item_id:
                      null === (e = r.nextEpisode) || void 0 === e
                        ? void 0
                        : e.id.toString(),
                  },
                },
              });
            }, [r]);
          if (!r.prevEpisode) return null;
          if ((0, E.lk)(r.prevEpisode.viewerPath))
            return (0, i.jsx)("a", {
              href: r.prevEpisode.viewerPath,
              children: (0, i.jsx)(P, { episode: r }),
            });
          switch (r.prevEpisode.state) {
            case "readable":
              return (0, i.jsx)(x.r, {
                href: r.prevEpisode.viewerPath,
                fa: {
                  click_name: s ? "open_novel_episode" : "open_episode",
                  area_name: s
                    ? "novel_viewer_bottom_previous"
                    : "viewer_bottom_previous",
                  area_id: r.id.toString(),
                  item_id: r.prevEpisode.id.toString(),
                },
                children: (0, i.jsx)(P, { episode: r }),
              });
            case "purchasable":
              return (0, i.jsx)("div", {
                onClick: n,
                children: (0, i.jsx)(P, { episode: r }),
              });
            case "login_required":
              return (0, i.jsx)("div", {
                onClick: o,
                children: (0, i.jsx)(P, { episode: r }),
              });
            default:
              return null;
          }
        },
        M = (e) => {
          let { episode: t } = e,
            r = (0, k.C)(),
            {
              isHorizontalReading: s,
              isShowNavigation: n,
              isZooming: a,
              dispatch: o,
            } = (0, f.Y)(),
            d = (0, l.useCallback)(() => {
              o({
                type: "toggleIsHorizontalReading",
                payload: (e) => {
                  e
                    ? ((0, w.t8)({ key: "dimension10", value: b.C.HORIZON }),
                      (0, w.V4)({
                        click_name: "change_direction",
                        area_name: r ? "novel_viewer_bottom" : "viewer_bottom",
                        area_id: t.id.toString(),
                        item_id: "horizontal",
                      }))
                    : ((0, w.t8)({ key: "dimension10", value: b.C.VERTICAL }),
                      (0, w.V4)({
                        click_name: "change_direction",
                        area_name: r ? "novel_viewer_bottom" : "viewer_bottom",
                        area_id: t.id.toString(),
                        item_id: "vertical",
                      }));
                },
              }),
                o({ type: "showReadDirection" });
            }, [o, t, r]),
            c = (0, l.useCallback)(() => {
              o({
                type: "toggleZoom",
                payload: (e) => {
                  (0, w.V4)({
                    click_name: "change_zoom",
                    area_name: r ? "novel_viewer_bottom" : "viewer_bottom",
                    area_id: t.id.toString(),
                    item_id: e ? "zoom_out" : "zoom_in",
                  });
                },
              });
            }, [o, t, r]);
          return (0, i.jsx)(u.M, {
            children: n
              ? (0, i.jsx)(h.E.div, {
                  initial: { y: "100%", opacity: 0 },
                  animate: { y: "0%", opacity: 1 },
                  exit: { y: "100%", opacity: 0 },
                  transition: { type: "tween" },
                  children: (0, i.jsx)(V, {
                    isHorizontalReading: s,
                    toggleReadDirection: d,
                    episode: t,
                    toggleZoom: c,
                    isZooming: a,
                    isNovel: r,
                  }),
                })
              : null,
          });
        },
        V = (e) =>
          (0, i.jsxs)("div", {
            className: "relative z-[1]",
            children: [
              e.isHorizontalReading && (0, i.jsx)(y, { episode: e.episode }),
              (0, i.jsx)(H, { ...e }),
            ],
          }),
        H = (e) => {
          let {
            episode: t,
            isHorizontalReading: r = !1,
            toggleReadDirection: s,
            toggleZoom: n,
            isZooming: a = !1,
            isNovel: l,
          } = e;
          return (0, i.jsx)("div", {
            className: "bg-[#333]",
            children: (0, i.jsxs)("div", {
              className:
                "mx-auto flex max-w-screen-screen4 justify-between p-16",
              children: [
                !(null == t ? void 0 : t.isTateyomi) &&
                  (0, i.jsxs)("div", {
                    className: "flex",
                    children: [
                      (0, i.jsxs)("div", {
                        className: "w-64 cursor-pointer",
                        onClick: n,
                        children: [
                          (0, i.jsx)("div", {
                            className: "flex items-center justify-center",
                            children: (0, i.jsx)(p.J, {
                              name: a ? p.u.ZOOMOUT : p.u.ZOOMIN,
                            }),
                          }),
                          (0, i.jsx)("div", {
                            className:
                              "mt-4 text-center text-white typography-12",
                            children: a ? "ズーム解除" : "ズーム",
                          }),
                        ],
                      }),
                      (0, i.jsxs)("div", {
                        className: "w-64 cursor-pointer",
                        onClick: s,
                        children: [
                          (0, i.jsx)("div", {
                            className: "flex items-center justify-center",
                            children: (0, i.jsx)(p.J, {
                              name: r ? p.u.READ_VERTICAL : p.u.READ_HORIZONTAL,
                            }),
                          }),
                          (0, i.jsx)("div", {
                            className:
                              "mt-4 text-center text-white typography-12",
                            children: r ? "縦読み" : "横読み",
                          }),
                        ],
                      }),
                    ],
                  }),
                (0, i.jsx)(x.r, {
                  href: ""
                    .concat(l ? "/novel" : "", "/works/")
                    .concat(t.workId),
                  fa: {
                    click_name: l
                      ? "open_novel_official_work"
                      : "open_official_work",
                    area_name: l ? "novel_viewer_bottom" : "viewer_bottom",
                    area_id: t.id.toString(),
                    item_id: t.workId.toString(),
                  },
                  children: (0, i.jsxs)("div", {
                    children: [
                      (0, i.jsx)("div", {
                        className: "flex items-center justify-center",
                        children: (0, i.jsx)(p.J, {
                          name: p.u.VIEW_LIST_WHITE,
                        }),
                      }),
                      (0, i.jsx)("div", {
                        className: "mt-4 text-white typography-12",
                        children: "作品詳細",
                      }),
                    ],
                  }),
                }),
                (0, i.jsxs)("div", {
                  className: "flex",
                  children: [
                    (0, i.jsx)("div", {
                      className: "flex w-64 justify-center",
                      children: (0, i.jsx)(I, { episode: t }),
                    }),
                    (0, i.jsx)("div", {
                      className: "flex w-64 justify-center",
                      children: (0, i.jsx)(L, { episode: t }),
                    }),
                  ],
                }),
              ],
            }),
          });
        };
      var z = r(10766);
      let A = (e) => {
        let { isHorizontalReading: t = !1, isSpreadView: r = !1, ...s } = e;
        return (0, i.jsx)("div", {
          className: "relative bg-background1 pt-[141.5%] before:block",
          style: {
            width: r ? "50vw" : "100vw",
            top: (0, E.u)(t, "50%"),
            transform: (0, E.u)(t, "translateY(-50%)"),
            marginBottom: (0, E.u)(!t, "1rem"),
            ...s.style,
          },
          ...s,
          children: (0, i.jsx)(z.W, {
            className: "absolute h-[114px] w-[114px]",
            style: { left: "calc(50% - 57px)", top: "calc(50% - 57px)" },
          }),
        });
      };
      var Z = r(73117),
        O = r(79910),
        U = r(73294),
        Y = r(76762),
        D = r(21262),
        F = r(67510),
        J = r(27554);
      function q() {
        let e = (0, D._)(["translateY(", "%)"]);
        return (
          (q = function () {
            return e;
          }),
          e
        );
      }
      function B() {
        let e = (0, D._)(["rgba(0, 0, 0, ", ")"]);
        return (
          (B = function () {
            return e;
          }),
          e
        );
      }
      let W = (e) => {
        let [t, r] = (0, l.useState)(!1),
          i = (0, l.useCallback)(() => {
            r(!0);
          }, []),
          s = (0, l.useCallback)(() => {
            r(!1);
          }, []),
          n = (0, F.q)(100, { bounce: 0 }),
          a = (0, F.q)(0),
          o = (0, J.Y)(q(), n),
          d = (0, J.Y)(B(), a);
        return (
          (0, l.useEffect)(() => {
            t ? (n.set(0), a.set(0.4)) : (n.set(100), a.set(0));
          }, [t]),
          (0, l.useEffect)(() => {
            n.on("change", (t) => {
              100 === t && e();
            });
          }, []),
          { show: i, hide: s, transform: o, backgroundColor: d }
        );
      };
      var X = r(68184);
      let K = (e) => {
          let { episode: t } = e,
            { isOpen: r, position: s, close: n } = (0, O.A)(),
            { show: a, hide: o, transform: d, backgroundColor: c } = W(n);
          return ((0, l.useEffect)(() => {
            r && a();
          }, [r]),
          (null == t ? void 0 : t.shareContent) &&
            r &&
            (s === U.r.AfterReading || s === U.r.NavBar))
            ? (0, i.jsxs)(h.E.div, {
                className: "screen4:hidden",
                style: {
                  position: "fixed",
                  top: 0,
                  left: 0,
                  width: "100vw",
                  height: "100vh",
                  backgroundColor: c,
                  zIndex: X.V3 + 1,
                },
                children: [
                  (0, i.jsx)("div", {
                    className: "absolute left-0 top-0 h-full w-full",
                    onClick: o,
                  }),
                  (0, i.jsx)(h.E.div, {
                    style: {
                      position: "absolute",
                      width: "100%",
                      left: "0",
                      bottom: "0",
                      transform: d,
                    },
                    children: (0, i.jsx)(Q, {
                      content: t.shareContent,
                      closeFn: o,
                    }),
                  }),
                ],
              })
            : null;
        },
        Q = (e) => {
          let { content: t, closeFn: r } = e;
          return (0, i.jsxs)("div", {
            className: "bg-surface1",
            children: [
              (0, i.jsxs)("div", {
                className: "relative py-16 text-center",
                children: [
                  (0, i.jsx)("span", {
                    className: "text-base text-text1",
                    children: "シェアする",
                  }),
                  (0, i.jsx)("div", {
                    className: "absolute right-0 top-0 w-[46px] p-16",
                    onClick: r,
                    children: (0, i.jsx)(p.J, { name: p.u.CLOSE_BLACK }),
                  }),
                ],
              }),
              (0, i.jsxs)("div", {
                className: "pb-[120px]",
                children: [
                  (0, i.jsx)(Y.T, { type: "twitter", content: t }),
                  (0, i.jsx)(Y.T, { type: "facebook", content: t }),
                  (0, i.jsx)(Y.T, { type: "line", content: t }),
                ],
              }),
            ],
          });
        },
        G = (e) => {
          let { episode: t } = e,
            r = (0, k.C)(),
            { open: s, isOpen: n, position: o, close: d } = (0, O.A)(),
            { isShowNavigation: c } = (0, f.Y)(),
            p = (0, l.useCallback)(() => {
              (0, w.V4)({
                click_name: "share_episode",
                item_id: null == t ? void 0 : t.id.toString(),
                area_name: r ? "novel_viewer_top" : "viewer_top",
              }),
                s(U.r.NavBar);
            }, [s, t, r]),
            x = (0, a.useRouter)(),
            m = (0, l.useCallback)(() => {
              (0, w.V4)({
                click_name: "close_episode",
                item_id: null == t ? void 0 : t.id.toString(),
                area_name: r ? "novel_viewer_top" : "viewer_top",
              }),
                !r && t
                  ? x.push("/works/".concat(t.workId))
                  : r && t && x.push("/novel/works/".concat(t.workId));
            }, [t, x, r]);
          return (0, i.jsx)(u.M, {
            children: c
              ? (0, i.jsx)(h.E.div, {
                  initial: { y: "-100%", opacity: 0 },
                  animate: { y: "0%", opacity: 1 },
                  exit: { y: "-100%", opacity: 0 },
                  transition: { type: "tween" },
                  children: (0, i.jsx)($, {
                    isOpenShareMenu: n && o === U.r.NavBar,
                    openShareMenu: p,
                    closeShareMenu: d,
                    close: m,
                    shareContent: null == t ? void 0 : t.shareContent,
                    episode: t,
                  }),
                })
              : null,
          });
        },
        $ = (e) => {
          let {
            isOpenShareMenu: t = !1,
            openShareMenu: r,
            closeShareMenu: s,
            close: n,
            shareContent: a,
            episode: l,
          } = e;
          return (0, i.jsxs)(i.Fragment, {
            children: [
              (0, i.jsx)("div", {
                className:
                  "relative box-border bg-[#333] p-[12px] screen2:h-[64px] screen2:py-[20px]",
                children: (0, i.jsxs)("div", {
                  className: "mx-auto grid max-w-screen-screen4 gap-[20px]",
                  style: { gridTemplateColumns: "24px 1fr 24px" },
                  children: [
                    (0, i.jsx)("div", {
                      className: "flex cursor-pointer items-center",
                      children: (0, i.jsx)(p.J, {
                        name: p.u.CLOSE,
                        onClick: n,
                      }),
                    }),
                    (0, i.jsx)("div", {
                      className:
                        "line-clamp-1 pt-4 text-center font-bold text-text5 typography-16",
                      children: null == l ? void 0 : l.workTitle,
                    }),
                    (0, i.jsx)("div", {
                      className: "flex cursor-pointer items-center",
                      children: (0, i.jsx)(p.J, {
                        name: p.u.SHARE,
                        onClick: r,
                      }),
                    }),
                    (0, i.jsx)("div", {
                      className:
                        "absolute left-0 top-0 h-screen w-screen bg-transparent ".concat(
                          t ? "hidden screen4:block" : "hidden"
                        ),
                      onClick: s,
                      style: { display: t ? void 0 : "none" },
                    }),
                    a &&
                      (0, i.jsx)("div", {
                        className: "absolute right-[4px] top-[52px] ".concat(
                          t ? "hidden screen4:block" : "hidden"
                        ),
                        children: (0, i.jsx)(Z.G, { content: a }),
                      }),
                  ],
                }),
              }),
              (0, i.jsx)(K, { episode: l }),
            ],
          });
        },
        ee = () =>
          (0, i.jsx)("canvas", {
            className: "hidden",
            id: "__comic__web_decrypt_canvas",
          });
      var et = r(44230),
        er = r(17956),
        ei = r(17201),
        es = r(8789),
        en = r(5341),
        ea = r(12678);
      let el = (e) => {
        let { isFree: t = !0 } = e;
        return (0, i.jsxs)(ea.z, {
          type: ea.L.PrimaryImportant,
          children: [
            !t && (0, i.jsx)(p.J, { name: p.u.COIN_M }),
            "次のエピソード",
          ],
        });
      };
      var eo = r(57870),
        ed = r(9448),
        ec = r(65420),
        eu = r(1432),
        eh = r(29871);
      let ep = (e) => {
          let t = (0, a.useRouter)(),
            r = (0, eu.NL)(),
            i = (0, l.useCallback)(
              (i) => {
                r.setQueryData(
                  (0, eh._)({
                    path: "/api/app/episodes/{episodeId}/read_v4",
                    operationId: "getEpisodesIdReadV4",
                    variables: {
                      pathParams: { episodeId: e.id.toString() },
                      queryParams: { access_token: t.query.access_token },
                      headers: {},
                    },
                  }),
                  { readingEpisode: { ...e, isWorkFollowing: i } }
                );
              },
              [r, t, e]
            );
          return i;
        },
        ex = (e) => {
          let t = (0, a.useRouter)(),
            r = (0, eu.NL)(),
            i = (0, l.useCallback)(() => {
              r.setQueryData(
                (0, eh._)({
                  path: "/api/app/episodes/{episodeId}/read_v4",
                  operationId: "getEpisodesIdReadV4",
                  variables: {
                    pathParams: { episodeId: e.id.toString() },
                    queryParams: { access_token: t.query.access_token },
                    headers: {},
                  },
                }),
                { readingEpisode: { ...e, isLiked: !0 } }
              );
            }, [r, t, e]);
          return i;
        },
        em = (e) => {
          let t = (0, a.useRouter)(),
            r = (0, eu.NL)(),
            i = (0, l.useCallback)(() => {
              r.setQueryData(
                (0, eh._)({
                  path: "/api/app/novel/episodes/{storyId}/read_v4",
                  operationId: "getNovelEpisodesIdReadV4",
                  variables: {
                    pathParams: { storyId: e.id.toString() },
                    queryParams: { access_token: t.query.access_token },
                    headers: {},
                  },
                }),
                { readingEpisode: { ...e, isLiked: !0 } }
              );
            }, [r, t, e]);
          return i;
        },
        eg = (e) => {
          let t = (0, a.useRouter)(),
            r = (0, eu.NL)(),
            i = (0, l.useCallback)(
              (i) => {
                r.setQueryData(
                  (0, eh._)({
                    path: "/api/app/novel/episodes/{storyId}/read_v4",
                    operationId: "getNovelEpisodesIdReadV4",
                    variables: {
                      pathParams: { storyId: e.id.toString() },
                      queryParams: { access_token: t.query.access_token },
                      headers: {},
                    },
                  }),
                  { readingEpisode: { ...e, isWorkFollowing: i } }
                );
              },
              [r, t, e]
            );
          return i;
        };
      var ev = r(12783),
        ef = r(30833),
        e_ = r(21737);
      let ew = { Round: "Round", RoundedRectangle: "RoundedRectangle" },
        ey = (e) => {
          let t = (0, k.C)();
          return t ? (0, i.jsx)(eb, { ...e }) : (0, i.jsx)(ej, { ...e });
        },
        ej = (e) => {
          let { episode: t, type: r } = e,
            { status: s } = (0, ed.SE)(),
            { mutate: n, isLoading: a } = (0, er.td$)(),
            { mutate: o, isLoading: d } = (0, er.I64)(),
            c = (0, ev.O)(),
            u = ep(t),
            h = (0, e_.z)(t.workId),
            { dispatch: p } = (0, N.vR)(),
            x = (0, l.useCallback)(() => {
              if (
                ((0, w.V4)({
                  click_name: "follow_episode",
                  item_id: t.id.toString(),
                  area_name: "viewer_finish",
                }),
                s !== ec.J.LoggedIn)
              ) {
                p({
                  type: "needLogin",
                  payload: {
                    fa: {
                      area_name: "viewer_finish_follow",
                      item_id: t.id.toString(),
                    },
                  },
                });
                return;
              }
              (null == t ? void 0 : t.isWorkFollowing) &&
                !d &&
                o(
                  { pathParams: { workId: t.workId.toString() } },
                  {
                    onSuccess: () => {
                      u(!1),
                        h(!1),
                        c(),
                        (0, w.V4)({
                          click_name: "remove_official_work",
                          item_id: t.workId.toString(),
                          area_name: "episode_viewer_finished_to_read",
                        }),
                        (0, w.fv)({
                          type: "remove_official_work",
                          item_id: t.workId.toString(),
                          button_type: "episode_viewer_finished_to_read",
                        });
                    },
                    onError: () => {
                      alert("フォローの解除に失敗しました");
                    },
                  }
                ),
                (null == t ? void 0 : t.isWorkFollowing) ||
                  a ||
                  n(
                    { pathParams: { workIds: t.workId.toString() } },
                    {
                      onSuccess: () => {
                        u(!0),
                          h(!0),
                          c(),
                          (0, w.V4)({
                            click_name: "add_official_work",
                            item_id: t.workId.toString(),
                            area_name: "episode_viewer_finished_to_read",
                          }),
                          (0, w.ZN)({
                            type: "add_official_work",
                            item_id: t.workId.toString(),
                            button_type: "episode_viewer_finished_to_read",
                          });
                      },
                      onError: () => {
                        alert("フォローに失敗しました");
                      },
                    }
                  );
            }, [t]);
          return t
            ? (0, i.jsx)(ek, {
                onClick: x,
                isFollowing: t.isWorkFollowing,
                type: r,
              })
            : null;
        },
        eb = (e) => {
          let { episode: t, type: r } = e,
            { status: s } = (0, ed.SE)(),
            { mutate: n, isLoading: a } = (0, er.YV1)(),
            { mutate: o, isLoading: d } = (0, er.qTl)(),
            c = (0, ev.Z)(),
            u = eg(t),
            h = (0, ef.f)(t.workId),
            { dispatch: p } = (0, N.vR)(),
            x = (0, l.useCallback)(() => {
              if (
                ((0, w.V4)({
                  click_name: "follow_episode",
                  item_id: t.id.toString(),
                  area_name: "novel_viewer_finish",
                }),
                s !== ec.J.LoggedIn)
              ) {
                p({
                  type: "needLogin",
                  payload: {
                    fa: {
                      area_name: "novel_viewer_finish_follow",
                      item_id: t.id.toString(),
                    },
                  },
                });
                return;
              }
              (null == t ? void 0 : t.isWorkFollowing) &&
                !d &&
                o(
                  { pathParams: { workId: t.workId.toString() } },
                  {
                    onSuccess: () => {
                      u(!1),
                        h(!1),
                        c(),
                        (0, w.V4)({
                          click_name: "remove_novel_official_work",
                          item_id: t.workId.toString(),
                          area_name: "novel_episode_viewer_finished_to_read",
                        }),
                        (0, w.fv)({
                          type: "remove_novel_official_work",
                          item_id: t.workId.toString(),
                          button_type: "episode_viewer_finished_to_read",
                        });
                    },
                    onError: () => {
                      alert("フォローの解除に失敗しました");
                    },
                  }
                ),
                (null == t ? void 0 : t.isWorkFollowing) ||
                  a ||
                  n(
                    { pathParams: { workId: t.workId.toString() } },
                    {
                      onSuccess: () => {
                        u(!0),
                          h(!0),
                          c(),
                          (0, w.V4)({
                            click_name: "add_novel_official_work",
                            item_id: t.workId.toString(),
                            area_name: "novel_episode_viewer_finished_to_read",
                          }),
                          (0, w.ZN)({
                            type: "add_novel_official_work",
                            item_id: t.workId.toString(),
                            button_type: "episode_viewer_finished_to_read",
                          });
                      },
                      onError: () => {
                        alert("フォローに失敗しました");
                      },
                    }
                  );
            }, [t]);
          return t
            ? (0, i.jsx)(ek, {
                onClick: x,
                isFollowing: t.isWorkFollowing,
                type: r,
              })
            : null;
        },
        ek = (e) => {
          let { isFollowing: t, onClick: r, type: s = ew.Round } = e;
          switch (s) {
            case ew.Round:
              return (0, i.jsx)("div", {
                className: (0, en.Z)(
                  t ? "border-brand-primary-50" : "border-surface1-hover",
                  t ? "bg-brand-primary-00" : "bg-background1 dark:bg-surface3",
                  "box-border flex h-[80px] w-[80px] cursor-pointer flex-col justify-center rounded-[40px] border border-solid text-center"
                ),
                onClick: r,
                children: (0, i.jsxs)("div", {
                  children: [
                    (0, i.jsx)("div", {
                      style: { lineHeight: 0 },
                      children: (0, i.jsx)(p.J, {
                        name: t ? p.u.CHECK : p.u.ADD,
                      }),
                    }),
                    (0, i.jsx)("div", {
                      className: "font-bold text-text2 typography-12",
                      children: t ? "フォロー中" : "フォロー",
                    }),
                  ],
                }),
              });
            case ew.RoundedRectangle:
              return (0, i.jsx)(ea.z, {
                type: t ? ea.L.Default : ea.L.Primary,
                onClick: r,
                children: t
                  ? "フォロー中"
                  : (0, i.jsxs)(i.Fragment, {
                      children: [
                        (0, i.jsx)(p.J, { name: p.u.ADD_INLINE }),
                        "\xa0作品をフォローする",
                      ],
                    }),
              });
            default:
              return null;
          }
        },
        eN = (e) => {
          let { episode: t, ...r } = e,
            s = (0, l.useCallback)(() => {
              (0, w.V4)({
                click_name: "share_episode",
                area_name: "viewer_finish",
                item_id: t.id.toString(),
              });
            }, [t]);
          return (0, i.jsx)(eS, { episode: t, onClick: s, ...r });
        },
        eS = (e) => {
          let { episode: t, onClick: r } = e;
          return (0, i.jsxs)("div", {
            children: [
              (0, i.jsx)("div", {
                className: "pb-8 text-center text-text2 typography-12",
                children: "このエピソードが最新です",
              }),
              t.isWorkFollowing && t.shareContent
                ? (0, i.jsxs)("a", {
                    className: (0, en.Z)(X.jE, "bg-[#1DA1F2] text-text5"),
                    target: "_blank",
                    rel: "noopener noreferrer",
                    href: (0, eo.MM)("twitter", t.shareContent),
                    onClick: r,
                    children: [
                      (0, i.jsx)(p.J, { name: p.u.TWITTER_WHITE }),
                      "\xa0Twitterで感想をつぶやく",
                    ],
                  })
                : (0, i.jsx)(ey, { episode: t, type: ew.RoundedRectangle }),
            ],
          });
        },
        eE = (e) => {
          var t;
          let [r] = (0, S.h)({
              episode: e.episode.nextEpisode,
              fa: {
                click_name: "order_episode",
                area_name: "viewer_finish",
                area_id: e.episode.id.toString(),
                item_id:
                  null === (t = e.episode.nextEpisode) || void 0 === t
                    ? void 0
                    : t.id.toString(),
              },
              showLoadingWhenOpen: !0,
            }),
            { dispatch: s } = (0, N.vR)(),
            n = (0, l.useCallback)(() => {
              var t;
              s({
                type: "needLogin",
                payload: {
                  fa: {
                    area_name: "viewer_after_read",
                    area_id: e.episode.id.toString(),
                    item_id:
                      null === (t = e.episode.nextEpisode) || void 0 === t
                        ? void 0
                        : t.id.toString(),
                  },
                },
              });
            }, [e]);
          return (0, i.jsx)(eC, {
            ...e,
            openPurchaseModal: r,
            openLoginModal: n,
          });
        },
        eC = (e) => {
          let { nextFreeEpisode: t, episode: r } = e;
          return (0, i.jsxs)("div", {
            className: "mx-16 flex flex-col gap-16",
            children: [
              (0, i.jsx)(eI, { ...e }),
              t &&
                (0, i.jsx)(x.r, {
                  href: t.viewerPath,
                  fa: {
                    click_name: "open_episode",
                    area_id: r.id.toString(),
                    item_id: t.id.toString(),
                    area_name: "viewer_finish_next_free_episode",
                  },
                  children: (0, i.jsx)(ea.z, {
                    children: "無料公開中の次の話",
                  }),
                }),
            ],
          });
        },
        eI = (e) => {
          let {
            nextEpisode: t,
            openPurchaseModal: r,
            openLoginModal: s,
            episode: n,
            nextFreeEpisode: a,
          } = e;
          if (null == t ? void 0 : t.episode) {
            if ((0, E.lk)(t.episode.viewerPath))
              return (0, i.jsx)("a", {
                href: t.episode.viewerPath,
                style: { textDecoration: "none" },
                children: (0, i.jsx)(el, {
                  isFree: "free" === t.episode.salesType,
                }),
              });
            switch (t.state) {
              case "readable":
                var l;
                return (0, i.jsx)(x.r, {
                  href: t.episode.viewerPath,
                  anchorProps: { className: "gtm-after-reading-section-next" },
                  fa: {
                    click_name: "open_episode",
                    area_id: n.id.toString(),
                    item_id:
                      null === (l = n.nextEpisode) || void 0 === l
                        ? void 0
                        : l.id.toString(),
                    area_name: "viewer_finish_next",
                  },
                  children: (0, i.jsx)(el, {}),
                });
              case "purchasable":
                return (0, i.jsxs)("div", {
                  onClick: r,
                  children: [
                    (0, i.jsx)(el, { isFree: !1 }),
                    (0, i.jsx)("div", {
                      className: (0, en.Z)(
                        "text-center text-text3 typography-12",
                        t.message && "mt-8"
                      ),
                      children: t.message,
                    }),
                  ],
                });
              case "not_publishing":
                return (0, i.jsxs)("div", {
                  children: [
                    (0, i.jsx)("div", {
                      className: (0, en.Z)(
                        X.jE,
                        "cursor-not-allowed bg-comic-important text-white opacity-30"
                      ),
                      children: "次のエピソード",
                    }),
                    (0, i.jsx)("div", {
                      className: "mt-4 text-center text-text3 typography-12",
                      children: t.message,
                    }),
                  ],
                });
              case "login_required":
                return (0, i.jsxs)("div", {
                  onClick: s,
                  children: [
                    (0, i.jsx)(el, {
                      isFree:
                        "sell" !== t.episode.salesType &&
                        "sell_before_free" !== t.episode.salesType,
                    }),
                    (0, i.jsx)("div", {
                      className: (0, en.Z)(
                        "text-center text-text3 typography-12",
                        t.message && "mt-8"
                      ),
                      children: t.message,
                    }),
                  ],
                });
            }
          }
          return t || a ? null : (0, i.jsx)(eN, { episode: n });
        };
      var eR = r(23282);
      let eP = (e) => {
          let { workId: t, episodeId: r } = e,
            s = (0, k.C)(),
            n = (0, eR.Z)({
              area_id: null == r ? void 0 : r.toString(),
              area_name: s ? "novel_viewer_finish" : "viewer_finish",
              item_id: null == t ? void 0 : t.toString(),
            });
          return (0, i.jsx)(eL, { onClick: n });
        },
        eL = (e) => {
          let { onClick: t } = e;
          return (0, i.jsx)("div", {
            className:
              "box-border flex h-[80px] w-[80px] cursor-pointer flex-col justify-center rounded-[40px] border border-solid border-surface1-hover bg-background1 text-center dark:bg-surface3",
            onClick: t,
            "aria-label": "feedback-button",
            children: (0, i.jsxs)("div", {
              children: [
                (0, i.jsx)("div", {
                  style: { lineHeight: 0 },
                  children: (0, i.jsx)(p.J, {
                    name: p.u.MESSAGE,
                    applyDarkMode: !0,
                  }),
                }),
                (0, i.jsx)("span", {
                  className: "font-bold text-text2 typography-12",
                  children: "ファンレター",
                }),
              ],
            }),
          });
        };
      var eT = r(3898),
        eM = r(94698),
        eV = r(93608),
        eH = r(92278);
      let ez = (e) => {
          let { work: t, className: r, fa: s, kind: n } = e;
          return (0, i.jsx)(x.r, {
            href:
              "comic" === n
                ? "/works/".concat(t.id)
                : "/novel/works/".concat(t.id),
            fa: s,
            anchorProps: { className: r },
            children: (0, i.jsxs)("div", {
              children: [
                (0, i.jsx)("div", {
                  className: "relative",
                  children: (0, i.jsx)("div", {
                    className:
                      "aspect-h-35 aspect-w-26 relative w-full border border-solid border-border",
                    children: (0, i.jsx)(eT.E, {
                      src: t.mainImageUrl,
                      alt: "".concat(t.title, " 書影"),
                      fill: !0,
                      sizes: "(max-width: 744px) 33vw, 25vw",
                      className: "object-contain",
                    }),
                  }),
                }),
                (0, i.jsx)("div", {
                  className:
                    "line-clamp-1 h-[1.5em] pt-4 font-bold text-text1 typography-12",
                  children: t.title,
                }),
                (0, i.jsx)("div", {
                  className: "pt-[2px]",
                  children: (0, i.jsx)(eH.t, {
                    isNewWork: t.isNewWork,
                    lastStoryReadStartAt: t.lastStoryReadStartAt,
                    fill: !0,
                    fallbackComponent:
                      "comic" === n
                        ? (0, i.jsx)(eV._, { count: t.storiesCount })
                        : (0, i.jsx)(eM.O, { category: t.category }),
                  }),
                }),
              ],
            }),
          });
        },
        eA = (e) => {
          let { works: t, episodeId: r } = e,
            s = (0, k.C)();
          return t.length > 0
            ? (0, i.jsxs)(i.Fragment, {
                children: [
                  (0, i.jsx)("div", {
                    className: "font-bold text-text2 typography-16",
                    children: "この作品を読んでる人にオススメ！",
                  }),
                  (0, i.jsx)("div", {
                    className:
                      "mt-8 grid grid-cols-3 gap-16 screen2:grid-cols-4",
                    children: t
                      .slice(0, 9)
                      .map((e, t) =>
                        (0, i.jsx)(
                          ez,
                          {
                            work: e,
                            className: "gtm-recommend-work screen2:last:hidden",
                            fa: {
                              click_name: "open_official_work",
                              area_id: null == r ? void 0 : r.toString(),
                              item_id: e.id.toString(),
                              area_name: "viewer_finish",
                              item_index: t,
                            },
                            kind: s ? "novel" : "comic",
                          },
                          e.id
                        )
                      ),
                  }),
                ],
              })
            : null;
        };
      var eZ = r(41989),
        eO = r(61511),
        eU = r(76915),
        eY = r(20616),
        eD = r(98739),
        eF = r(90551),
        eJ = r(18651),
        eq = r(39975);
      let eB = (e) => {
          let { variant: t, ...r } = e;
          return (0, i.jsx)(x.r, {
            href: "/store/variants/".concat(t.sku),
            ...r,
            children: (0, i.jsxs)("div", {
              children: [
                (0, i.jsxs)("div", {
                  className: "relative",
                  children: [
                    (0, i.jsx)("div", {
                      className:
                        "aspect-h-35 aspect-w-26 relative w-full border border-solid border-border",
                      children: (0, i.jsx)(eT.E, {
                        src: t.coverUrl,
                        alt: "".concat(t.title, " 書影"),
                        fill: !0,
                        sizes: "(max-width: 744px) 33vw, 25vw",
                        className: "object-contain",
                      }),
                    }),
                    t.specialContents.length > 0 &&
                      (0, i.jsx)("div", {
                        className: (0, en.Z)(
                          "absolute left-4",
                          t.bonusLabel ? "bottom-24" : "bottom-4"
                        ),
                        children: (0, i.jsx)(eF.T, { size: "S" }),
                      }),
                    t.bonusLabel &&
                      (0, i.jsx)("div", {
                        className: "absolute bottom-4 left-4",
                        children: (0, i.jsx)(eD.g, {
                          label: t.bonusLabel,
                          size: "S",
                        }),
                      }),
                  ],
                }),
                (0, i.jsx)("div", {
                  className:
                    "mt-4 line-clamp-1 font-bold text-text1 typography-12",
                  children: t.title,
                }),
                t.isPublished
                  ? (0, i.jsx)(eJ.tA, {
                      value: t.price.amount,
                      type: eJ.FT.COIN,
                    })
                  : (0, i.jsxs)("div", {
                      className: "text-text2 typography-12",
                      children: [(0, eq.Sb)(t.permitStartOn), "\xa0配信予定"],
                    }),
              ],
            }),
          });
        },
        eW = (e) => {
          let { episode: t } = e,
            r = (0, k.C)();
          return r && t.workId
            ? (0, i.jsx)(eQ, { workId: t.workId, episodeId: t.id })
            : !r && t.workProductKey
            ? (0, i.jsx)(eX, {
                workProductKey: t.workProductKey,
                episodeId: t.id,
              })
            : null;
        },
        eX = (e) => {
          let { workProductKey: t, ...r } = e,
            { data: s, error: n } = (0, er.MQ3)({
              pathParams: { key: t },
              queryParams: {
                order: "desc",
                view_type: "episode_finished_to_read",
              },
            }),
            a = (0, l.useMemo)(
              () => n instanceof Response && 503 === n.status,
              [n]
            ),
            o = (0, l.useMemo)(
              () =>
                s ? s.pages.reduce((e, t) => [...e, ...t.variants], []) : [],
              [s]
            );
          return s
            ? (0, i.jsx)(eK, {
                productKey: t,
                variants: o,
                isStoreMaintenance: a,
                ...r,
              })
            : (0, i.jsx)(eZ.g, {});
        },
        eK = (e) => {
          let {
            productKey: t,
            variants: r,
            isStoreMaintenance: s,
            episodeId: n,
          } = e;
          return r.length > 0 || s
            ? (0, i.jsxs)(i.Fragment, {
                children: [
                  (0, i.jsxs)("div", {
                    className: "flex items-center justify-between",
                    children: [
                      (0, i.jsx)("div", {
                        className: "font-bold text-text2 typography-16",
                        children: "この作品の単行本",
                      }),
                      !s &&
                        (0, i.jsx)("div", {
                          className: "gtm-variant-more",
                          children: (0, i.jsx)(x.r, {
                            href: "/store/products/".concat(t),
                            fa: {
                              click_name: "open_product",
                              area_id: null == n ? void 0 : n.toString(),
                              item_id: t,
                              area_name: "viewer_finish",
                            },
                            children: (0, i.jsx)(eU.T, {}),
                          }),
                        }),
                    ],
                  }),
                  s
                    ? (0, i.jsx)(eO.O, {})
                    : (0, i.jsx)("div", {
                        className:
                          "mt-8 grid grid-cols-3 gap-16 screen2:grid-cols-4",
                        children: r.map((e, t) =>
                          (0, i.jsx)(
                            eB,
                            {
                              variant: e,
                              fa: {
                                click_name: "open_variant",
                                area_id: null == n ? void 0 : n.toString(),
                                item_id: e.sku,
                                area_name: "viewer_finish",
                                item_index: t,
                              },
                              anchorProps: {
                                className:
                                  "gtm-variant [&:nth-child(4)]:hidden screen2:[&:nth-child(4)]:block",
                              },
                            },
                            e.sku
                          )
                        ),
                      }),
                ],
              })
            : null;
        },
        eQ = (e) => {
          let { workId: t, episodeId: r } = e,
            { data: s } = (0, er.n$H)({ pathParams: { workId: t } }),
            n = (0, l.useMemo)(() => (s ? s.pages[0].adBooks : []), [s]);
          return n.length > 0
            ? (0, i.jsx)(eG, { adBooks: n, episodeId: r, workId: t })
            : null;
        },
        eG = (e) => {
          let { adBooks: t, workId: r, episodeId: s } = e;
          return (0, i.jsxs)("div", {
            children: [
              (0, i.jsxs)("div", {
                className: "flex items-center justify-between",
                children: [
                  (0, i.jsx)("div", {
                    className: "font-bold text-text2 typography-16",
                    children: "この作品の単行本",
                  }),
                  (0, i.jsx)("div", {
                    children: (0, i.jsx)(x.r, {
                      href: "/novel/works/".concat(r),
                      fa: {
                        click_name: "open_novel_work",
                        area_id: s.toString(),
                        area_name: "viewer_finish",
                      },
                      children: (0, i.jsx)(eU.T, {}),
                    }),
                  }),
                ],
              }),
              (0, i.jsx)("div", {
                className: "mt-8",
                children: t.map((e) => (0, i.jsx)(eY.f, { adBook: e }, e.id)),
              }),
            ],
          });
        };
      var e$ = r(24563),
        e0 = r(89706);
      let e1 = (e) => {
          let { episode: t } = e,
            r = (0, k.C)();
          return r
            ? (0, i.jsx)(e2, { episode: t })
            : (0, i.jsx)(e4, { episode: t });
        },
        e4 = (e) => {
          let { episode: t } = e,
            { status: r } = (0, ed.SE)(),
            { mutate: s, isLoading: n } = (0, er.VIv)(),
            a = ex(t),
            { dispatch: o } = (0, N.vR)(),
            d = (0, l.useCallback)(() => {
              if (
                ((0, w.V4)({
                  click_name: "like_episode",
                  item_id: t.id.toString(),
                  area_name: "viewer_finish",
                }),
                r === ec.J.NotLoggedIn)
              ) {
                o({
                  type: "needLogin",
                  payload: {
                    fa: {
                      area_name: "viewer_finish_like",
                      item_id: t.id.toString(),
                    },
                  },
                });
                return;
              }
              t.isLiked ||
                n ||
                s(
                  { pathParams: { storyId: t.id.toString() } },
                  { onSuccess: a, onError: () => alert("いいねに失敗しました") }
                );
            }, [t, n, s, r, a, o]),
            c = (0, e0.S)(t.workLikeCount);
          return (0, i.jsx)(e5, { count: c, isActive: t.isLiked, onClick: d });
        },
        e2 = (e) => {
          let { episode: t } = e,
            { status: r } = (0, ed.SE)(),
            { mutate: s, isLoading: n } = (0, er.Ryl)(),
            a = em(t),
            { dispatch: o } = (0, N.vR)(),
            d = (0, l.useCallback)(() => {
              if (
                ((0, w.V4)({
                  click_name: "like_episode",
                  item_id: t.id.toString(),
                  area_name: "novel_viewer_finish",
                }),
                r === ec.J.NotLoggedIn)
              ) {
                o({
                  type: "needLogin",
                  payload: {
                    fa: {
                      area_name: "novel_viewer_finish_like",
                      item_id: t.id.toString(),
                    },
                  },
                });
                return;
              }
              t.isLiked ||
                n ||
                s(
                  { pathParams: { storyId: t.id.toString() } },
                  { onSuccess: a, onError: () => alert("いいねに失敗しました") }
                );
            }, [t, n, s, r, a, o]),
            c = (0, e0.S)(t.workLikeCount);
          return (0, i.jsx)(e5, { count: c, isActive: t.isLiked, onClick: d });
        },
        e5 = (e) => {
          let { count: t, isActive: r = !1, onClick: s } = e;
          return (0, i.jsxs)(i.Fragment, {
            children: [
              (0, i.jsx)("div", {
                className: (0, en.Z)(
                  r ? "border-brand-primary-50" : "border-surface1-hover",
                  r ? "bg-brand-primary-00" : "bg-background1 dark:bg-surface3",
                  "box-border flex h-[80px] w-[80px] cursor-pointer flex-col justify-center rounded-[40px] border border-solid text-center"
                ),
                onClick: s,
                children: (0, i.jsxs)("div", {
                  children: [
                    (0, i.jsx)("div", {
                      style: { lineHeight: 0 },
                      children: (0, i.jsx)(p.J, {
                        name: r ? p.u.LIKE_ACTIVE : p.u.LIKE,
                      }),
                    }),
                    (0, i.jsx)("span", {
                      className: "font-bold text-text2 typography-12",
                      children: "いいね",
                    }),
                  ],
                }),
              }),
              (0, i.jsx)("div", {
                className:
                  "pt-4 text-center font-bold text-brand-primary-50 typography-12",
                children: t,
              }),
            ],
          });
        };
      var e8 = r(9917),
        e6 = r(98804);
      let e3 = (e) => {
          let { publishingSiteBanner: t } = e,
            { screenSize: r } = (0, v.Ux)();
          return (0, i.jsx)("a", {
            href: t.url,
            target: "_blank",
            rel: "noopener noreferrer",
            children:
              r === e6._G.Large
                ? (0, i.jsx)("div", {
                    className: "mx-auto max-w-[600px]",
                    children: (0, i.jsx)(e8.E, {
                      src: t.images.pc.url,
                      width: t.images.pc.width,
                      height: t.images.pc.height,
                    }),
                  })
                : (0, i.jsx)("div", {
                    className: "mx-auto max-w-[600px]",
                    children: (0, i.jsx)(e8.E, {
                      src: t.images.main.url,
                      width: t.images.main.width,
                      height: t.images.main.height,
                    }),
                  }),
          });
        },
        e7 = (e) => {
          let { episode: t } = e,
            r = (0, k.C)();
          return r
            ? (0, i.jsx)(te, { episode: t })
            : (0, i.jsx)(e9, { episode: t });
        },
        e9 = (e) => {
          let { episode: t } = e,
            r = (0, a.useRouter)(),
            { data: s } = (0, er.qn4)({
              pathParams: { episodeId: t.id.toString() },
              queryParams: {
                ...("string" == typeof r.query.access_token
                  ? { access_token: r.query.access_token }
                  : {}),
              },
            });
          return ((0, l.useEffect)(() => {
            (0, e$.aj)(t.id), (0, e$.WF)(t.workId);
          }, [t]),
          s)
            ? (0, i.jsx)(tt, { episode: t, finishedToRead: s })
            : null;
        },
        te = (e) => {
          let { episode: t } = e,
            r = (0, a.useRouter)(),
            { data: s } = (0, er.eiQ)({
              pathParams: { storyId: t.id.toString() },
              queryParams: {
                ...("string" == typeof r.query.access_token
                  ? { access_token: r.query.access_token }
                  : {}),
              },
            });
          return ((0, l.useEffect)(() => {
            (0, e$.KM)(t.id), (0, e$.gK)(t.workId);
          }, [t]),
          s)
            ? (0, i.jsx)(tt, { episode: t, finishedToRead: s })
            : null;
        },
        tt = (e) => {
          let {
            episode: t,
            finishedToRead: {
              recommendedOfficialWorks: r,
              eventBanners: s,
              nextFreeEpisode: n,
              nextEpisode: a,
            },
          } = e;
          return (0, i.jsxs)("div", {
            className: "bg-background1 pb-[360px] pt-64",
            children: [
              (0, i.jsx)("div", {
                className: "hidden text-center screen4:block",
                children: (0, i.jsx)(es.n, {
                  eventBanners: s,
                  fa: { area_name: "viewer_finish", area_id: t.id.toString() },
                }),
              }),
              (0, i.jsxs)("div", {
                className: "mx-auto max-w-[600px]",
                children: [
                  (0, i.jsx)("div", {
                    className: "w-full text-center screen4:hidden",
                    children: (0, i.jsx)(es.n, {
                      eventBanners: s,
                      fa: {
                        area_name: "viewer_finish",
                        area_id: t.id.toString(),
                      },
                    }),
                  }),
                  (0, i.jsxs)("div", {
                    className: "flex justify-center py-16",
                    children: [
                      (0, i.jsx)("div", {
                        className: "mx-[12px]",
                        children: (0, i.jsx)(ey, { episode: t }),
                      }),
                      (0, i.jsx)("div", {
                        className: "mx-[12px]",
                        children: (0, i.jsx)(e1, { episode: t }),
                      }),
                      (0, i.jsx)("div", {
                        className: "mx-[12px]",
                        children: (0, i.jsx)(eP, {
                          workId: t.workId,
                          episodeId: t.id,
                        }),
                      }),
                    ],
                  }),
                  (0, i.jsx)("div", {
                    className: "mb-24",
                    children: (0, i.jsx)(eE, {
                      episode: t,
                      nextFreeEpisode: n,
                      nextEpisode: a,
                    }),
                  }),
                ],
              }),
              t.publishingSiteBanner &&
                (0, i.jsx)(e3, {
                  publishingSiteBanner: t.publishingSiteBanner,
                }),
              (0, i.jsxs)("div", {
                className: "bg-surface3 p-16",
                children: [
                  (0, i.jsxs)("div", {
                    className: "mx-auto max-w-[600px]",
                    children: [
                      (0, i.jsx)(eA, { works: r, episodeId: t.id }),
                      (0, i.jsx)("div", {
                        className: "my-24",
                        children: (0, i.jsx)(eW, { episode: t }),
                      }),
                    ],
                  }),
                  (0, i.jsx)(ei.bf, {}),
                ],
              }),
            ],
          });
        },
        tr = (e) => {
          let { onClick: t, inverse: r = !1 } = e;
          return (0, i.jsx)("div", {
            className:
              "flex h-[48px] w-[48px] items-center justify-center rounded-24 bg-black text-center opacity-60",
            style: { transform: r ? "rotate(180deg)" : void 0 },
            onClick: t,
            children: (0, i.jsx)(p.J, { name: p.u.PREV1 }),
          });
        },
        ti = () => {
          let [e, t] = (0, l.useState)(null);
          return (
            (0, l.useEffect)(() => {
              let e = new URL(location.href),
                r = e.searchParams.get("access_token");
              r && t(r);
            }, []),
            e
          );
        };
      var ts = r(88170);
      let tn = (e) => {
        let t = (0, eu.NL)(),
          r = (0, l.useCallback)(() => {
            t.removeQueries(
              (0, eh._)({
                path: "/api/app/works/{workId}/episodes/v2",
                operationId: "getWorksIdEpisodesV2",
                variables: { pathParams: { workId: e.toString() } },
              })
            );
          }, [t, e]);
        return r;
      };
      var ta = r(79063);
      let tl = () => {
        let {
            isShowNavigation: e,
            currentPage: t,
            pageLength: r,
            dispatch: i,
          } = (0, f.Y)(),
          s = (0, l.useCallback)(() => {
            if (t === r) return i({ type: "showNavigation" });
            e ? i({ type: "hideNavigation" }) : i({ type: "showNavigation" });
          }, [e, t, r, i]);
        return s;
      };
      var to = r(62177);
      let td = (e) => {
          let t = (0, k.C)(),
            r = (0, to.U)(() => {
              let r = t ? "novel_official_story" : "official_story";
              window.gtag("event", "finish_to_read", {
                item_id: e.id.toString(),
                type: r,
              }),
                window.krt &&
                  window.krt("send", "finish_to_read", {
                    id: e.id.toString(),
                    type: r,
                    work_id: e.workId.toString(),
                  });
            });
          return r;
        },
        tc = () => {
          let { currentPage: e, pageLength: t } = (0, f.Y)(),
            { isShowAd: r } = (0, ed.SE)(),
            i = (0, l.useMemo)(
              () => e === t || (!!r && e + 1 === t),
              [e, t, r]
            );
          return i;
        },
        tu = () => {
          let { isShowAd: e } = (0, ed.SE)(),
            { currentPage: t, pageLength: r } = (0, f.Y)(),
            i = (0, l.useMemo)(() => !!e && t + 1 === r, [e, t, r]);
          return i;
        },
        th = (e, t) => {
          let { dispatch: r, isShowNavigation: i } = (0, f.Y)(),
            { isTouchDevice: s, width: n } = (0, v.Ux)(),
            a = (0, l.useCallback)(() => {
              r({
                type: t,
                payload: (t) => {
                  e.current &&
                    (s
                      ? e.current.scrollTo({
                          left: -(t * n),
                          behavior: "smooth",
                        })
                      : e.current.scrollTo({ left: -(t * n) }));
                },
              }),
                i && r({ type: "hideNavigation" });
            }, [r, i, s, t, n]);
          return a;
        },
        tp = () => {
          let { width: e } = (0, v.Ux)(),
            t = (0, l.useMemo)(() => e / 3, [e]);
          return t;
        },
        tx = (e) => !!e.nextEpisode && "readable" === e.nextEpisode.state,
        tm = (e) => !!e.prevEpisode && "readable" === e.prevEpisode.state,
        tg = (e) => {
          let { episode: t, pages: r } = e,
            s = tl(),
            n = tn(t.workId),
            o = (0, a.useRouter)(),
            {
              currentPage: d,
              dispatch: c,
              isNeedHorizontalRestore: x,
              isNeedLastRestore: m,
              isForceUnmount: g,
              pageLength: y,
            } = (0, f.Y)(),
            { width: j, height: b } = (0, v.Ux)(),
            N = (0, ts.I)({ episode: t }),
            S = tc(),
            C = tu(),
            I = tp(),
            R = (0, ta.x)({ episode: t }),
            P = (0, k.C)(),
            L = ti(),
            T = (0, l.useRef)(null),
            [M, V, H] = (0, _.L)(!1),
            z = (0, l.useRef)(0),
            [Z, O] = (0, l.useState)(0),
            [U, Y, D] = (0, _.L)(!1),
            F = (0, l.useRef)(!1),
            [J, q, B] = (0, _.L)(!1),
            W = (0, l.useRef)(!1),
            [X, K, Q] = (0, _.L)(!1),
            G = th(T, "incPage"),
            $ = th(T, "decPage"),
            ee = td(t),
            es = (0, l.useCallback)(
              (e) => {
                y !== d &&
                  (e.clientX < I ? G() : e.clientX > j - I ? $() : s());
              },
              [$, d, G, y, I, s, j]
            ),
            en = (0, l.useCallback)(
              (e) => (t) => {
                t && c({ type: "setPage", payload: e });
              },
              [c]
            ),
            { mutate: ea } = (0, er.XCN)(),
            el = (0, l.useCallback)(
              (e) => (r) => {
                r &&
                  ("only_once" === t.salesType ||
                    "sell_if_read" === t.salesType) &&
                  e === t.pages.length - 1 &&
                  ea(
                    {
                      pathParams: { episodeId: t.id.toString() },
                      ...(L ? { queryParams: { access_token: L } } : {}),
                    },
                    { onSettled: n }
                  );
              },
              [t, ea, L, n]
            ),
            eo = (0, l.useCallback)(
              (e) => {
                (F.current || 0 === d) && (z.current = e.touches[0].clientX),
                  0 === d && tm(t)
                    ? (q(), (W.current = !0))
                    : (B(), (W.current = !1)),
                  d === y && tx(t)
                    ? (Y(), (F.current = !0))
                    : (D(), (F.current = !1));
              },
              [t, d, y]
            ),
            ed = (0, l.useCallback)(
              (e) => {
                if (F.current) {
                  let t = (e.touches[0].clientX - z.current) / 100;
                  O(t > 1 ? 1 : t);
                }
                if (0 === d) {
                  let t = (z.current - e.touches[0].clientX) / 100;
                  O(t > 1 ? 1 : t);
                }
              },
              [d]
            ),
            ec = (0, l.useCallback)(() => {
              window.scrollY > 0 && window.scrollTo({ top: 0 }),
                F.current &&
                  O(
                    (e) => (
                      1 === e &&
                        t.nextEpisode &&
                        tx(t) &&
                        ((0, w.V4)({
                          click_name: P ? "open_novel_episode" : "open_episode",
                          area_name: P
                            ? "novel_viewer_finish_pull"
                            : "viewer_finish_pull",
                          area_id: t.id.toString(),
                          item_id: t.nextEpisode.id.toString(),
                        }),
                        Q(),
                        o.push(t.nextEpisode.viewerPath)),
                      0
                    )
                  ),
                W.current &&
                  (O(
                    (e) => (
                      1 === e &&
                        t.prevEpisode &&
                        tm(t) &&
                        ((0, w.V4)({
                          click_name: P ? "open_novel_episode" : "open_episode",
                          area_name: P
                            ? "novel_viewer_first_page"
                            : "viewer_first_page",
                          area_id: t.id.toString(),
                          item_id: t.prevEpisode.id.toString(),
                        }),
                        Q(),
                        c({ type: "showLastPage" }),
                        o.push(t.prevEpisode.viewerPath)),
                      0
                    )
                  ),
                  (W.current = !1));
            }, [o, t, P]),
            eu = (0, l.useCallback)(() => {
              if (!x) {
                if (T.current) {
                  if (m) {
                    let e = document.getElementById(
                      "page-".concat(r.length - 1)
                    );
                    e
                      ? (c({ type: "setPage", payload: r.length - 1 }),
                        T.current.scrollTo({ left: -((r.length - 1) * j) }),
                        c({ type: "lastPageRestored" }))
                      : setTimeout(eu, 10);
                  } else c({ type: "setPage", payload: 0 });
                } else setTimeout(eu, 10);
              }
            }, [r, c, j, x, m]),
            eh = (0, l.useCallback)(() => {
              let e = document.getElementById("page-".concat(d));
              e
                ? T.current &&
                  (T.current.scrollTo({ left: -(d * j) }),
                  c({ type: "horizontalRestored" }))
                : setTimeout(eh, 10);
            }, [d, c, j]);
          return (
            (0, l.useEffect)(() => {
              y > 0 && y - 6 < d && V();
            }, [d, y, V]),
            (0, l.useEffect)(() => {
              H(), D(), B(), (F.current = !1), (W.current = !1), K();
            }, [t.id]),
            (0, l.useEffect)(() => {
              X && eu();
            }, [X]),
            (0, l.useEffect)(() => {
              x && eh();
            }, [x, eh]),
            (0, l.useEffect)(() => {
              g && !m
                ? (Q(),
                  setTimeout(() => {
                    K(), c({ type: "forceUnmounted" });
                  }, 1))
                : c({ type: "forceUnmounted" });
            }, [g, m]),
            X
              ? (0, i.jsxs)(i.Fragment, {
                  children: [
                    (0, i.jsxs)(u.M, {
                      children: [
                        (0, i.jsxs)("div", {
                          className: "flex overflow-x-auto",
                          style: {
                            scrollSnapType: "x mandatory",
                            WebkitOverflowScrolling: "touch",
                            direction: "rtl",
                            height: R ? b - 100 : b,
                          },
                          dir: "rtl",
                          onClick: es,
                          ref: T,
                          onTouchStart: eo,
                          onTouchMove: ed,
                          onTouchEnd: ec,
                          children: [
                            r.map((e, t) => {
                              var r;
                              return (0, i.jsx)(
                                et.df,
                                {
                                  threshold: 0.8,
                                  triggerOnce: !0,
                                  onChange: el(t),
                                  children: (0, i.jsx)(et.df, {
                                    onChange: en(t),
                                    threshold: 0.8,
                                    id: "page-".concat(t),
                                    className:
                                      "h-full w-screen shrink-0 grow-0 bg-contain bg-center bg-no-repeat",
                                    style: {
                                      scrollSnapAlign: "start",
                                      direction: "rtl",
                                      scrollSnapStop: "always",
                                      ...(0, E.Nx)(
                                        null !== (r = e.localUrl) &&
                                          void 0 !== r
                                          ? r
                                          : void 0
                                      ),
                                    },
                                    dir: "rtl",
                                    children:
                                      !e.localUrl &&
                                      (0, i.jsx)(A, {
                                        isHorizontalReading: !0,
                                      }),
                                  }),
                                },
                                e.url
                              );
                            }),
                            N &&
                              r.length > 0 &&
                              (0, i.jsx)(et.df, {
                                className:
                                  "relative h-full w-screen shrink-0 grow-0 overflow-hidden",
                                dir: "rtl",
                                style: {
                                  direction: "rtl",
                                  scrollSnapAlign: "start",
                                  scrollSnapStop: "always",
                                },
                                onChange: en(y - 1),
                                threshold: 0.8,
                                children: (0, i.jsx)("div", {
                                  className:
                                    "absolute left-1/2 top-1/2 w-full bg-background2 px-0 py-[calc(70.75%-125px)]",
                                  style: { transform: "translate(-50%,-50%)" },
                                  children: (0, i.jsx)(ei.tx, {}),
                                }),
                              }),
                            r.length > 0 &&
                              (0, i.jsx)(et.df, {
                                className: "h-full w-screen shrink-0 grow-0",
                                style: {
                                  direction: "rtl",
                                  scrollSnapAlign: "start",
                                  scrollSnapStop: "always",
                                },
                                onChange: en(y),
                                threshold: 0.8,
                                children:
                                  M &&
                                  (0, i.jsxs)("div", {
                                    dir: "ltr",
                                    className: "h-full overflow-y-scroll",
                                    children: [
                                      (0, i.jsx)(et.df, {
                                        threshold: 1,
                                        width: 1,
                                        height: 1,
                                        triggerOnce: !0,
                                        onChange: ee,
                                      }),
                                      (0, i.jsx)(e7, { episode: t }),
                                    ],
                                  }),
                              }),
                          ],
                        }),
                        S &&
                          (0, i.jsx)(
                            h.E.div,
                            {
                              className: "fixed right-8 top-[calc(50vh-24px)]",
                              initial: { opacity: 0 },
                              animate: { opacity: 1 },
                              exit: { opacity: 0 },
                              children: (0, i.jsx)(tr, { onClick: $ }),
                            },
                            "prevButton"
                          ),
                        C &&
                          (0, i.jsx)(
                            h.E.div,
                            {
                              className: "fixed left-8 top-[calc(50vh-24px)]",
                              initial: { opacity: 0 },
                              animate: { opacity: 1 },
                              exit: { opacity: 0 },
                              children: (0, i.jsx)(tr, {
                                inverse: !0,
                                onClick: G,
                              }),
                            },
                            "nextButton"
                          ),
                      ],
                    }),
                    U &&
                      (0, i.jsxs)("div", {
                        className:
                          "fixed top-[calc(50vh-32px)] flex h-64 w-64 flex-col justify-center rounded-oval bg-[#181818] bg-opacity-60",
                        style: { left: 24, opacity: Z },
                        children: [
                          (0, i.jsx)("div", {
                            className: "flex justify-center",
                            children: (0, i.jsx)("div", {
                              className: "h-24 w-24",
                              style: { transform: "rotate(180deg)" },
                              children: (0, i.jsx)(p.J, { name: p.u.PREV1 }),
                            }),
                          }),
                          (0, i.jsx)("div", {
                            className:
                              "w-full text-center font-bold text-white typography-14",
                            children: "次へ",
                          }),
                        ],
                      }),
                    J &&
                      (0, i.jsxs)("div", {
                        className:
                          "fixed top-[calc(50vh-32px)] flex h-64 w-64 flex-col justify-center rounded-oval bg-[#181818] bg-opacity-60",
                        style: { right: 24, opacity: Z },
                        children: [
                          (0, i.jsx)("div", {
                            className: "flex justify-center",
                            children: (0, i.jsx)("div", {
                              className: "h-24 w-24",
                              children: (0, i.jsx)(p.J, { name: p.u.PREV1 }),
                            }),
                          }),
                          (0, i.jsx)("div", {
                            className:
                              "w-full text-center font-bold text-white typography-14",
                            children: "前へ",
                          }),
                        ],
                      }),
                  ],
                })
              : null
          );
        };
      var tv = r(85885);
      let tf = (e) => {
          let { episode: t, pages: r } = e,
            s = (0, a.useRouter)(),
            {
              currentPage: n,
              pageLength: o,
              dispatch: d,
              isNeedHorizontalRestore: c,
              isNeedLastRestore: u,
              isForceUnmount: h,
            } = (0, f.Y)(),
            { width: p } = (0, v.Ux)(),
            x = (0, ts.I)({ episode: t }),
            m = tc(),
            g = tu(),
            y = tp(),
            j = (0, k.C)(),
            b = ti(),
            N = (0, l.useRef)(null),
            [S, C, I] = (0, _.L)(!1),
            R = tl(),
            P = td(t),
            L = th(N, "incPage"),
            T = th(N, "decPage"),
            M = tn(t.workId),
            V = (0, l.useMemo)(() => {
              if (!t) return [];
              let e = r;
              return (
                1 === t.pages.length ||
                  (e.length > 0 &&
                    ("left" === t.twoPageLayout &&
                      (e = [
                        {
                          url: "HP",
                          localUrl: "HP",
                          height: 0,
                          width: 0,
                          gridsize: 32,
                        },
                        ...e,
                      ]),
                    e.length % 2 &&
                      (e = [
                        ...e,
                        {
                          url: "TP",
                          localUrl: "TP",
                          height: 0,
                          width: 0,
                          gridsize: 32,
                        },
                      ]))),
                e
              );
            }, [r, t]),
            [H, z] = (0, l.useMemo)(
              () => (t ? [t.pages[0].width, t.pages[0].height] : [0, 0]),
              [t]
            ),
            Z = (0, l.useCallback)(
              (e) => {
                o !== n &&
                  (e.clientX < y ? L() : e.clientX > p - y ? T() : R());
              },
              [T, n, L, o, y, R, p]
            ),
            O = (0, l.useCallback)(
              (e) => {
                if ((e.preventDefault(), n === o && tx(t) && t.nextEpisode))
                  return (
                    (0, w.V4)({
                      click_name: j ? "open_novel_episode" : "open_episode",
                      area_name: j
                        ? "novel_viewer_finish_pull"
                        : "viewer_finish_pull",
                      area_id: t.id.toString(),
                      item_id: t.nextEpisode.id.toString(),
                    }),
                    I(),
                    s.push(t.nextEpisode.viewerPath)
                  );
                L();
              },
              [n, t, o, s, L, j]
            ),
            U = (0, l.useCallback)(
              (e) => {
                if ((e.preventDefault(), 0 === n && tm(t) && t.prevEpisode))
                  return (
                    (0, w.V4)({
                      click_name: j ? "open_novel_episode" : "open_episode",
                      area_name: j
                        ? "novel_viewer_first_page"
                        : "viewer_first_page",
                      area_id: t.id.toString(),
                      item_id: t.prevEpisode.id.toString(),
                    }),
                    I(),
                    d({ type: "showLastPage" }),
                    s.push(t.prevEpisode.viewerPath)
                  );
                T();
              },
              [n, t, s, T, j]
            ),
            { mutate: Y } = (0, er.XCN)(),
            D = (0, l.useCallback)(
              (e) => (r) => {
                r &&
                  ("only_once" === t.salesType ||
                    "sell_if_read" === t.salesType) &&
                  e === V.length - 1 &&
                  Y(
                    {
                      pathParams: { episodeId: t.id.toString() },
                      ...(b ? { queryParams: { access_token: b } } : {}),
                    },
                    { onSettled: M }
                  );
              },
              [t, Y, V, b, M]
            ),
            F = (0, l.useCallback)(() => {
              if (!c) {
                if (N.current) {
                  if (u) {
                    let e = document.getElementById(
                      "page-".concat(r.length - 1)
                    );
                    e
                      ? (d({
                          type: "setPage",
                          payload: Math.round((r.length - 1) / 2),
                        }),
                        N.current.scrollTo({
                          left: -(Math.round((r.length - 1) / 2) * p),
                        }),
                        d({ type: "lastPageRestored" }))
                      : setTimeout(F, 10);
                  } else
                    d({ type: "setPage", payload: 0 }),
                      N.current.scrollTo({ left: 0 });
                } else setTimeout(F, 10);
              }
            }, [r, d, p, c, u]),
            J = (0, l.useCallback)(() => {
              let e = document.getElementById("page-".concat(n));
              e
                ? N.current &&
                  (N.current.scrollTo({ left: -(n * p) }),
                  d({ type: "horizontalRestored" }))
                : setTimeout(J, 10);
            }, [n, d, p]);
          return (
            (0, l.useEffect)(() => {
              c && J();
            }, [c, J]),
            (0, l.useEffect)(() => {
              C();
            }, [t]),
            (0, l.useEffect)(() => {
              S && F();
            }, [S]),
            (0, l.useEffect)(() => {
              h && !u
                ? (I(),
                  setTimeout(() => {
                    C(), d({ type: "forceUnmounted" });
                  }, 1))
                : d({ type: "forceUnmounted" });
            }, [h, u]),
            (0, tv.Z)("h", O, void 0, [O]),
            (0, tv.Z)("l", U, void 0, [U]),
            (0, tv.Z)("ArrowLeft", O, void 0, [O]),
            (0, tv.Z)("ArrowDown", O, void 0, [O]),
            (0, tv.Z)("ArrowRight", U, void 0, [U]),
            (0, tv.Z)("ArrowUp", U, void 0, [U]),
            S
              ? (0, i.jsxs)("div", {
                  children: [
                    (0, i.jsxs)("div", {
                      className: "flex h-screen overflow-hidden",
                      style: {
                        scrollSnapType: "x mandatory",
                        WebkitOverflowScrolling: "touch",
                        direction: "rtl",
                      },
                      dir: "rtl",
                      ref: N,
                      onClick: Z,
                      children: [
                        V.map((e, t) => {
                          var r, s;
                          return "HP" === e.url || "TP" === e.url
                            ? (0, i.jsx)(
                                et.df,
                                {
                                  className:
                                    "h-full w-[50vw] shrink-0 grow-0 bg-contain bg-no-repeat",
                                  style: {
                                    scrollSnapAlign: "start",
                                    direction: "rtl",
                                    scrollSnapStop: t % 2 ? "normal" : "always",
                                    backgroundPosition:
                                      t % 2 ? "right" : "left",
                                    ...(0, E.Nx)((0, E.t$)(H, z)),
                                  },
                                  id: "page-".concat(t),
                                  triggerOnce: !0,
                                  onChange: D(t),
                                  threshold: 0.8,
                                },
                                e.url
                              )
                            : 1 === V.length
                            ? (0, i.jsx)(
                                et.df,
                                {
                                  className:
                                    "h-full w-screen shrink-0 grow-0 bg-contain bg-center bg-no-repeat",
                                  style: {
                                    scrollSnapAlign: "start",
                                    direction: "rtl",
                                    scrollSnapStop: "always",
                                    width: "100vw",
                                    ...(0, E.Nx)(
                                      null !== (r = e.localUrl) && void 0 !== r
                                        ? r
                                        : void 0
                                    ),
                                  },
                                  triggerOnce: !0,
                                  onChange: D(t),
                                  threshold: 0.8,
                                  children:
                                    !e.localUrl &&
                                    (0, i.jsx)(A, {
                                      isHorizontalReading: !0,
                                      isSpreadView: !0,
                                    }),
                                },
                                e.url
                              )
                            : (0, i.jsx)(
                                et.df,
                                {
                                  className: (0, en.Z)(
                                    "h-full w-[50vw] shrink-0 grow-0 bg-contain bg-no-repeat",
                                    t % 2 ? "bg-right" : "bg-left"
                                  ),
                                  style: {
                                    scrollSnapAlign: "start",
                                    direction: "rtl",
                                    scrollSnapStop: t % 2 ? "always" : "normal",
                                    ...(0, E.Nx)(
                                      null !== (s = e.localUrl) && void 0 !== s
                                        ? s
                                        : void 0
                                    ),
                                  },
                                  id: "page-".concat(t),
                                  triggerOnce: !0,
                                  onChange: D(t),
                                  threshold: 0.8,
                                  children:
                                    !e.localUrl &&
                                    (0, i.jsx)(A, {
                                      isHorizontalReading: !0,
                                      isSpreadView: !0,
                                    }),
                                },
                                e.url
                              );
                        }),
                        x &&
                          r.length > 0 &&
                          (0, i.jsx)("div", {
                            className:
                              "relative h-full w-screen shrink-0 grow-0 overflow-hidden",
                            style: {
                              scrollSnapAlign: "start",
                              direction: "rtl",
                              scrollSnapStop: "always",
                            },
                            children: (0, i.jsx)("div", {
                              className:
                                "absolute left-1/2 top-1/2 w-full bg-background1 px-0 py-[calc(70.75%-125px)]",
                              style: { transform: "translate(-50%,-50%)" },
                              children: (0, i.jsx)(ei.VV, {}),
                            }),
                          }),
                        r.length > 0 &&
                          (0, i.jsx)("div", {
                            className:
                              "h-full w-screen shrink-0 grow-0 pt-[48px] screen2:pt-64",
                            style: {
                              scrollSnapAlign: "start",
                              direction: "rtl",
                              scrollSnapStop: "always",
                            },
                            children: (0, i.jsxs)("div", {
                              dir: "ltr",
                              className: "h-full overflow-y-scroll",
                              children: [
                                (0, i.jsx)(et.df, {
                                  threshold: 1,
                                  width: 1,
                                  height: 1,
                                  triggerOnce: !0,
                                  onChange: P,
                                }),
                                (0, i.jsx)(e7, { episode: t }),
                              ],
                            }),
                          }),
                      ],
                    }),
                    m &&
                      (0, i.jsx)("div", {
                        className:
                          "fixed right-8 top-[calc(50vh-24px)] cursor-pointer",
                        onClick: T,
                        children: (0, i.jsx)(tr, {}),
                      }),
                    g &&
                      (0, i.jsx)("div", {
                        className:
                          "fixed left-8 top-[calc(50vh-24px)] cursor-pointer",
                        onClick: L,
                        children: (0, i.jsx)(tr, { inverse: !0 }),
                      }),
                  ],
                })
              : null
          );
        },
        t_ = (e) => {
          let { isSpreadView: t } = (0, f.Y)();
          return t ? (0, i.jsx)(tf, { ...e }) : (0, i.jsx)(tg, { ...e });
        },
        tw = (e) => {
          let { episode: t } = e,
            r = (0, k.C)();
          return r
            ? (0, i.jsx)(tj, { episode: t })
            : (0, i.jsx)(ty, { episode: t });
        },
        ty = (e) => {
          let { episode: t } = e,
            r = (0, a.useRouter)(),
            { data: s } = (0, er.qn4)({
              pathParams: { episodeId: t.id.toString() },
              queryParams: {
                ...("string" == typeof r.query.access_token
                  ? { access_token: r.query.access_token }
                  : {}),
              },
            });
          return ((0, l.useEffect)(() => {
            (0, e$.aj)(t.id), (0, e$.WF)(t.workId);
          }, [t]),
          s)
            ? (0, i.jsx)(tb, { finishedToRead: s, episode: t })
            : null;
        },
        tj = (e) => {
          let { episode: t } = e,
            r = (0, a.useRouter)(),
            { data: s } = (0, er.eiQ)({
              pathParams: { storyId: t.id.toString() },
              queryParams: {
                ...("string" == typeof r.query.access_token
                  ? { access_token: r.query.access_token }
                  : {}),
              },
            });
          return ((0, l.useEffect)(() => {
            (0, e$.KM)(t.id), (0, e$.gK)(t.workId);
          }, [t]),
          s || s)
            ? (0, i.jsx)(tb, { finishedToRead: s, episode: t })
            : null;
        },
        tb = (e) => {
          let {
            episode: t,
            finishedToRead: {
              eventBanners: r,
              nextFreeEpisode: s,
              nextEpisode: n,
              recommendedOfficialWorks: a,
            },
          } = e;
          return (0, i.jsxs)("div", {
            className: "bg-background1 pb-[120px]",
            children: [
              (0, i.jsx)("div", {
                className: "mt-16 hidden text-center screen4:block",
                children: (0, i.jsx)(es.n, {
                  eventBanners: r,
                  fa: { area_name: "viewer_finish", area_id: t.id.toString() },
                }),
              }),
              (0, i.jsxs)("div", {
                className: "mx-auto max-w-[600px]",
                children: [
                  (0, i.jsx)("div", {
                    className: "w-full text-center screen4:hidden",
                    children: (0, i.jsx)(es.n, {
                      eventBanners: r,
                      fa: {
                        area_name: "viewer_finish",
                        area_id: t.id.toString(),
                      },
                    }),
                  }),
                  (0, i.jsxs)("div", {
                    className: "flex justify-center py-16",
                    children: [
                      (0, i.jsx)("div", {
                        className: "mx-[12px]",
                        children: (0, i.jsx)(ey, { episode: t }),
                      }),
                      (0, i.jsx)("div", {
                        className: "mx-[12px]",
                        children: (0, i.jsx)(e1, { episode: t }),
                      }),
                      (0, i.jsx)("div", {
                        className: "mx-[12px]",
                        children: (0, i.jsx)(eP, {
                          workId: t.workId,
                          episodeId: t.id,
                        }),
                      }),
                    ],
                  }),
                  (0, i.jsx)("div", {
                    className: "mb-24",
                    children: (0, i.jsx)(eE, {
                      episode: t,
                      nextFreeEpisode: s,
                      nextEpisode: n,
                    }),
                  }),
                ],
              }),
              t.publishingSiteBanner &&
                (0, i.jsx)(e3, {
                  publishingSiteBanner: t.publishingSiteBanner,
                }),
              !t.nextEpisode &&
                (0, i.jsxs)("div", {
                  className: "bg-surface3 p-16",
                  children: [
                    (0, i.jsxs)("div", {
                      className: "mx-auto max-w-[600px]",
                      children: [
                        (0, i.jsx)(eA, { works: a, episodeId: t.id }),
                        (0, i.jsx)("div", {
                          className: "my-24",
                          children: (0, i.jsx)(eW, { episode: t }),
                        }),
                      ],
                    }),
                    (0, i.jsx)(ei.bf, {}),
                  ],
                }),
            ],
          });
        },
        tk = (e) => {
          let { episode: t, pages: r } = e,
            s = (0, a.useRouter)(),
            { width: n, isTouchDevice: o, screenSize: d } = (0, v.Ux)(),
            c = (0, ts.I)({ episode: t }),
            {
              isZooming: u,
              dispatch: h,
              currentPage: x,
              isNeedVerticalRestore: m,
              isNeedLastRestore: g,
              isForceUnmount: y,
              pageLength: j,
            } = (0, f.Y)(),
            b = (0, k.C)(),
            N = ti(),
            S = tl(),
            C = td(t),
            I = tn(t.workId),
            R = (0, l.useRef)(0),
            P = (0, l.useRef)(null),
            [L, T] = (0, l.useState)(0),
            [M, V, H] = (0, _.L)(!1),
            z = (0, l.useRef)(!1),
            [Z, O, U] = (0, _.L)(!1),
            Y = (0, l.useRef)(!1),
            [D, F, J] = (0, _.L)(!1),
            [q, B, W] = (0, _.L)(!1),
            X = (0, l.useMemo)(
              () =>
                r.map((e) => {
                  let t = e.width / n,
                    r = e.height / t;
                  return {
                    ...e,
                    renderingHeight: e.width < n ? e.height : Math.ceil(r),
                  };
                }),
              [r, n]
            ),
            K = (0, l.useMemo)(
              () => (o && d != e6._G.Large ? "sp" : "pc"),
              [o, d]
            ),
            Q = (0, l.useCallback)(
              (e) => {
                var r;
                let i =
                  null === (r = P.current) || void 0 === r
                    ? void 0
                    : r.getBoundingClientRect().top;
                (z.current || 0 === i) && (R.current = e.touches[0].clientY),
                  0 === i && t.prevEpisode
                    ? "readable" === t.prevEpisode.state &&
                      (O(), (Y.current = !0))
                    : (U(), (Y.current = !1));
              },
              [t]
            ),
            G = (0, l.useCallback)((e) => {
              var t;
              let r =
                null === (t = P.current) || void 0 === t
                  ? void 0
                  : t.getBoundingClientRect().top;
              if (z.current) {
                let t = (R.current - e.touches[0].clientY) / 100;
                T(t > 1 ? 1 : t);
              }
              if (0 === r) {
                let t = (e.touches[0].clientY - R.current) / 100;
                T(t > 1 ? 1 : t), 0 === r && t > 0 && e.preventDefault();
              }
            }, []),
            $ = (0, l.useCallback)(() => {
              z.current &&
                T((e) => {
                  if (1 === e) {
                    var r;
                    (null === (r = t.nextEpisode) || void 0 === r
                      ? void 0
                      : r.state) === "readable" &&
                      ((0, w.V4)({
                        click_name: b ? "open_novel_episode" : "open_episode",
                        area_name: b
                          ? "novel_viewer_vertical_finish_pull"
                          : "viewer_vertical_finish_pull",
                        area_id: t.id.toString(),
                        item_id: t.nextEpisode.id.toString(),
                      }),
                      W(),
                      s.push(t.nextEpisode.viewerPath));
                  }
                  return 0;
                }),
                Y.current &&
                  (T((e) => {
                    if (1 === e) {
                      var r;
                      (null === (r = t.prevEpisode) || void 0 === r
                        ? void 0
                        : r.state) === "readable" &&
                        ((0, w.V4)({
                          click_name: b ? "open_novel_episode" : "open_episode",
                          area_name: b
                            ? "novel_viewer_vertical_first_pull"
                            : "viewer_vertical_first_pull",
                          area_id: t.id.toString(),
                          item_id: t.prevEpisode.id.toString(),
                        }),
                        h({ type: "showLastPage" }),
                        W(),
                        s.push(t.prevEpisode.viewerPath));
                    }
                    return 0;
                  }),
                  (Y.current = !1));
            }, [s, t, b]),
            ee = (0, l.useCallback)(
              (e) => {
                e && t.nextEpisode
                  ? "readable" === t.nextEpisode.state &&
                    (V(), (z.current = !0))
                  : (H(), (z.current = !1));
              },
              [t]
            ),
            es = (0, l.useCallback)(() => {
              if (
                (window.scrollTo({ top: window.scrollY + window.innerHeight }),
                z.current)
              ) {
                var e;
                (null === (e = t.nextEpisode) || void 0 === e
                  ? void 0
                  : e.state) === "readable" &&
                  ((0, w.V4)({
                    click_name: b ? "open_novel_episode" : "open_episode",
                    area_name: b
                      ? "novel_viewer_vertical_finish_pull"
                      : "viewer_vertical_finish_pull",
                    area_id: t.id.toString(),
                    item_id: t.nextEpisode.id.toString(),
                  }),
                  W(),
                  s.push(t.nextEpisode.viewerPath));
              }
            }, [t, s, b]),
            ea = (0, l.useCallback)(() => {
              if (window.scrollY === window.innerHeight)
                return window.scrollTo({
                  top: window.scrollY - window.innerHeight + 1,
                });
              window.scrollTo({ top: window.scrollY - window.innerHeight }),
                0 === window.scrollY &&
                  t.prevEpisode &&
                  ((0, w.V4)({
                    click_name: b ? "open_novel_episode" : "open_episode",
                    area_name: b
                      ? "novel_viewer_vertical_first_pull"
                      : "viewer_vertical_first_pull",
                    area_id: t.id.toString(),
                    item_id: t.prevEpisode.id.toString(),
                  }),
                  W(),
                  h({ type: "showLastPage" }),
                  s.push(t.prevEpisode.viewerPath));
            }, [t, s, b]),
            el = (0, l.useCallback)(() => {
              window.addEventListener("touchstart", Q, { passive: !1 }),
                window.addEventListener("touchmove", G, { passive: !1 }),
                window.addEventListener("touchend", $, { passive: !1 });
            }, [$]),
            eo = (0, l.useCallback)(() => {
              window.removeEventListener("touchstart", Q),
                window.removeEventListener("touchmove", G),
                window.removeEventListener("touchend", $);
            }, [$]),
            ed = (0, l.useCallback)(
              (e) => (t) => {
                t && h({ type: "setPage", payload: e });
              },
              [h]
            ),
            { mutate: ec } = (0, er.XCN)(),
            eu = (0, l.useCallback)(
              (e) => (r) => {
                r &&
                  ("only_once" === t.salesType ||
                    "sell_if_read" === t.salesType) &&
                  e === t.pages.length - 1 &&
                  ec(
                    {
                      pathParams: { episodeId: t.id.toString() },
                      ...(N ? { queryParams: { access_token: N } } : {}),
                    },
                    { onSettled: I }
                  );
              },
              [t, ec, N, I]
            ),
            eh = (0, l.useCallback)(() => {
              let e = document.getElementById("page-".concat(x));
              e
                ? (e.scrollIntoView(), h({ type: "verticalRestored" }))
                : setTimeout(eh, 10);
            }, [x]),
            ep = (0, l.useCallback)(() => {
              let e = document.getElementById("page-".concat(r.length - 1));
              e
                ? (e.scrollIntoView(), h({ type: "lastPageRestored" }))
                : setTimeout(ep, 10);
            }, [r]);
          return (
            (0, l.useEffect)(() => (u ? eo() : el(), eo), [el, eo, u]),
            (0, l.useEffect)(() => {
              J(), H(), (z.current = !1), (Y.current = !1), B();
            }, [t.id]),
            (0, l.useEffect)(() => {
              j > 0 && j - 6 < x && F();
            }, [x, j, F]),
            (0, l.useEffect)(() => {
              m && eh();
            }, [m, eh]),
            (0, l.useEffect)(() => {
              g && ep();
            }, [g, ep]),
            (0, tv.Z)("j", es, void 0, [es]),
            (0, tv.Z)("k", ea, void 0, [ea]),
            (0, l.useEffect)(() => {
              y && !g
                ? (W(),
                  setTimeout(() => {
                    B(), h({ type: "forceUnmounted" });
                  }, 1))
                : h({ type: "forceUnmounted" });
            }, [y, g]),
            q
              ? (0, i.jsxs)("div", {
                  onClick: S,
                  ref: P,
                  children: [
                    X.map((e, r) =>
                      e.localUrl
                        ? (0, i.jsx)(
                            et.df,
                            {
                              triggerOnce: !0,
                              threshold: 0.6,
                              onChange: eu(r),
                              children: (0, i.jsx)(et.df, {
                                className: (0, en.Z)(
                                  "relative mx-auto w-full bg-cover bg-no-repeat",
                                  !t.isTateyomi && "mb-[1rem]"
                                ),
                                style: {
                                  height: e.renderingHeight,
                                  maxWidth: e.width,
                                  ...(0, E.Nx)(e.localUrl),
                                },
                                onChange: ed(r),
                                threshold: 0.6,
                                id: "page-".concat(r),
                              }),
                            },
                            e.url
                          )
                        : (0, i.jsx)(A, {}, "".concat(e.url, "-loading"))
                    ),
                    c &&
                      j > 0 &&
                      (0, i.jsx)("div", {
                        className: "adPage bg-background1",
                        children:
                          "sp" === K
                            ? (0, i.jsx)(ei.tx, {})
                            : (0, i.jsx)(ei.VV, {}),
                      }),
                    D &&
                      (0, i.jsxs)("div", {
                        children: [
                          (0, i.jsx)(et.df, {
                            threshold: 1,
                            width: 1,
                            height: 1,
                            triggerOnce: !0,
                            onChange: C,
                          }),
                          (0, i.jsx)(tw, { episode: t }),
                          (0, i.jsx)(et.df, {
                            threshold: 1,
                            width: 1,
                            height: 1,
                            onChange: ee,
                          }),
                        ],
                      }),
                    M &&
                      o &&
                      (0, i.jsxs)("div", {
                        className:
                          "fixed left-1/2 flex h-64 w-64 flex-col justify-center rounded-oval bg-[#181818] bg-opacity-60",
                        style: {
                          top: "75%",
                          transform: "translate(-50%, -50%)",
                          opacity: L,
                        },
                        children: [
                          (0, i.jsx)("div", {
                            className: "flex justify-center",
                            children: (0, i.jsx)("div", {
                              className: "h-24 w-24",
                              style: { transform: "rotate(90deg)" },
                              children: (0, i.jsx)(p.J, { name: p.u.PREV1 }),
                            }),
                          }),
                          (0, i.jsx)("div", {
                            className:
                              "w-full text-center font-bold text-white typography-14",
                            children: "次へ",
                          }),
                        ],
                      }),
                    Z &&
                      (0, i.jsxs)("div", {
                        className:
                          "fixed left-1/2 flex h-64 w-64 flex-col justify-center rounded-oval bg-[#181818] bg-opacity-60",
                        style: {
                          top: "25%",
                          transform: "translate(-50%, -50%)",
                          opacity: L,
                        },
                        children: [
                          (0, i.jsx)("div", {
                            className: "flex justify-center",
                            children: (0, i.jsx)("div", {
                              className: "h-24 w-24",
                              style: { transform: "rotate(-90deg)" },
                              children: (0, i.jsx)(p.J, { name: p.u.PREV1 }),
                            }),
                          }),
                          (0, i.jsx)("div", {
                            className:
                              "w-full text-center font-bold text-white typography-14",
                            children: "前へ",
                          }),
                        ],
                      }),
                  ],
                })
              : null
          );
        },
        tN = (e) => {
          let {
            isHorizontalReading: t,
            isSpreadView: r,
            displayWidth: i,
            width: s,
            pages: n,
          } = e;
          return t && r && n.length > 1 ? (s - 2 * i) / 2 : (s - i) / 2;
        },
        tS = (e) => {
          var t, r, s;
          let { episode: n, pages: a } = e,
            {
              currentPage: o,
              isHorizontalReading: d,
              isSpreadView: c,
            } = (0, f.Y)(),
            { width: u } = (0, v.Ux)(),
            p = (0, l.useMemo)(() => {
              if (!n) return [];
              if (!c || !d) return [{ ...a[o] }];
              {
                let e = 2 * o,
                  t = a[0].width,
                  r = a[0].height,
                  i = a.map((e) => e);
                return 1 === a.length
                  ? [{ ...a[0] }]
                  : ("left" === n.twoPageLayout &&
                      (i = [
                        {
                          url: "HP",
                          localUrl: "HP",
                          height: r,
                          width: t,
                          gridsize: 32,
                        },
                        ...i,
                      ]),
                    i.length % 2 &&
                      (i = [
                        ...i,
                        {
                          url: "TP",
                          localUrl: "TP",
                          height: r,
                          width: t,
                          gridsize: 32,
                        },
                      ]),
                    i.slice(e, e + 2));
              }
            }, [o, n, c, d, a]),
            x = (0, l.useMemo)(() => {
              let e = 2 * p[0].width;
              if (e > 2 * u) {
                let e = p[0].height / p[0].width;
                return { width: 2 * u, height: 2 * u * e };
              }
              return { width: e, height: 2 * p[0].height };
            }, [p, u]),
            g = (0, m.c)(
              tN({
                isHorizontalReading: d,
                isSpreadView: c,
                displayWidth: x.width,
                width: u,
                pages: p,
              })
            ),
            _ = (0, m.c)(-x.height / 4),
            w = (0, l.useCallback)(
              (e, t) => {
                g.set(g.get() + t.delta.x), _.set(_.get() + t.delta.y);
              },
              [g, _]
            ),
            y = (0, l.useCallback)(
              (e) => {
                g.set(g.get() - e.deltaX), _.set(_.get() - e.deltaY);
              },
              [g, _]
            );
          return (0, i.jsxs)("div", {
            className: "fixed left-0 top-0 h-screen w-full overflow-hidden",
            onWheel: y,
            children: [
              (0, i.jsx)(h.E.div, {
                onPan: w,
                className:
                  "absolute left-0 top-0 h-screen w-screen bg-[#181818]",
                style: { touchAction: "none" },
              }),
              (0, i.jsxs)(h.E.div, {
                drag: !0,
                style: { x: g, y: _ },
                dragMomentum: !1,
                children: [
                  1 === p.length &&
                    p[0] &&
                    (0, i.jsx)("div", {
                      className:
                        "cursor-[grab] bg-contain bg-center bg-no-repeat",
                      style: {
                        ...x,
                        ...(0, E.Nx)(
                          null !== (t = p[0].localUrl) && void 0 !== t
                            ? t
                            : void 0
                        ),
                      },
                    }),
                  2 === p.length &&
                    p[0] &&
                    p[1] &&
                    (0, i.jsxs)("div", {
                      className: "flex cursor-[grab] flex-row-reverse",
                      style: { width: 2 * x.width, height: x.height },
                      children: [
                        (0, i.jsx)("div", {
                          style: x,
                          children: (0, i.jsx)("div", {
                            className:
                              "h-full w-full bg-contain bg-right bg-no-repeat",
                            style: {
                              ...(0, E.Nx)(
                                "HP" === p[0].url
                                  ? (0, E.t$)(p[0].width, p[0].height)
                                  : null !== (r = p[0].localUrl) && void 0 !== r
                                  ? r
                                  : void 0
                              ),
                            },
                          }),
                        }),
                        (0, i.jsx)("div", {
                          style: x,
                          children: (0, i.jsx)("div", {
                            className:
                              "h-full w-full bg-contain bg-left bg-no-repeat",
                            style: {
                              ...(0, E.Nx)(
                                "TP" === p[1].url
                                  ? (0, E.t$)(p[1].width, p[1].height)
                                  : null !== (s = p[1].localUrl) && void 0 !== s
                                  ? s
                                  : void 0
                              ),
                            },
                          }),
                        }),
                      ],
                    }),
                ],
              }),
            ],
          });
        };
      var tE = r(62637);
      function tC(e, t) {
        return (((e << (t %= 32)) >>> 0) | (e >>> (32 - t))) >>> 0;
      }
      class tI {
        next() {
          let e = (9 * tC((5 * this.s[1]) >>> 0, 7)) >>> 0,
            t = (this.s[1] << 9) >>> 0;
          return (
            (this.s[2] = (this.s[2] ^ this.s[0]) >>> 0),
            (this.s[3] = (this.s[3] ^ this.s[1]) >>> 0),
            (this.s[1] = (this.s[1] ^ this.s[2]) >>> 0),
            (this.s[0] = (this.s[0] ^ this.s[3]) >>> 0),
            (this.s[2] = (this.s[2] ^ t) >>> 0),
            (this.s[3] = tC(this.s[3], 11)),
            e
          );
        }
        constructor(e) {
          if (4 !== e.length)
            throw Error(
              "seed.length !== 4 (seed.length: ".concat(e.length, ")")
            );
          (this.s = new Uint32Array(e)),
            0 === this.s[0] &&
              0 === this.s[1] &&
              0 === this.s[2] &&
              0 === this.s[3] &&
              (this.s[0] = 1);
        }
      }
      let tR = globalThis.crypto;
      async function tP(e, t, r, i, s, n, a, l, o) {
        if (t <= 0 || r <= 0 || i <= 0 || s <= 0 || n <= 0)
          throw Error(
            "bytesPerElement <= 0 || width <= 0 || height <= 0 || blockSizeH <= 0 || blockSizeV <= 0 (bytesPerElement: "
              .concat(t, ", width: ")
              .concat(r, ", height: ")
              .concat(i, ", blockSizeH: ")
              .concat(s, ", blockSizeV: ")
              .concat(n, ")")
          );
        if (
          !Number.isSafeInteger(t) ||
          !Number.isSafeInteger(r) ||
          !Number.isSafeInteger(i) ||
          !Number.isSafeInteger(s) ||
          !Number.isSafeInteger(n)
        )
          throw Error(
            "!Number.isSafeInteger(bytesPerElement) || !Number.isSafeInteger(width) || !Number.isSafeInteger(height) || !Number.isSafeInteger(blockSizeH) || !Number.isSafeInteger(blockSizeV) (bytesPerElement: "
              .concat(t, ", width: ")
              .concat(r, ", height: ")
              .concat(i, ", blockSizeH: ")
              .concat(s, ", blockSizeV: ")
              .concat(n, ")")
          );
        if (e.length !== r * i * t)
          throw Error(
            "data.length !== width * height * bytesPerElement (data.length: "
              .concat(e.length, ", width: ")
              .concat(r, ", height: ")
              .concat(i, ", bytesPerElement: ")
              .concat(t, ")")
          );
        let d = Math.ceil(i / n),
          c = Math.floor(r / s),
          u = Array(d)
            .fill(null)
            .map(() => Array.from(Array(c).keys()));
        {
          let e = new TextEncoder().encode(a + l),
            t = await tR.subtle.digest("SHA-256", e),
            r = new Uint32Array(t, 0, 4),
            i = new tI(r);
          for (let e = 0; e < 100; e++) i.next();
          for (let e = 0; e < d; e++) {
            let t = u[e];
            for (let e = c - 1; e >= 1; e--) {
              let r = i.next() % (e + 1),
                s = t[e];
              (t[e] = t[r]), (t[r] = s);
            }
          }
        }
        if (o)
          for (let e = 0; e < d; e++) {
            let t = u[e],
              r = t.map((e, r) => t.indexOf(r));
            if (r.some((e) => e < 0))
              throw Error("Failed to reverse shuffle table");
            u[e] = r;
          }
        let h = new Uint8ClampedArray(e.length);
        for (let a = 0; a < i; a++) {
          let i = Math.floor(a / n),
            l = u[i];
          for (let i = 0; i < c; i++) {
            let n = l[i],
              o = i * s,
              d = (a * r + o) * t,
              c = n * s,
              u = (a * r + c) * t,
              p = s * t;
            for (let t = 0; t < p; t++) h[d + t] = e[u + t];
          }
          {
            let i = c * s,
              n = (a * r + i) * t,
              l = (a * r + r) * t;
            for (let t = n; t < l; t++) h[t] = e[t];
          }
        }
        return h;
      }
      let tL = new Map(),
        tT = new tE.c(6),
        tM = new tE.c(1),
        tV = [],
        tH = [],
        tz = (e) =>
          fetch(
            e.url,
            e.key
              ? {
                  headers: {
                    "X-Cobalt-Thumber-Parameter-GridShuffle-Key": e.key,
                  },
                }
              : void 0
          ),
        tA = (e, t, r) =>
          new Promise(async (i, s) => {
            let n = document.getElementById("__comic__web_decrypt_canvas");
            if (n) {
              let s = n.getContext("2d", { willReadFrequently: !0 });
              (n.width = t.width), (n.height = t.height);
              let a = new Image(t.width, t.height);
              (a.onload = async () => {
                if (s) {
                  s.drawImage(a, 0, 0);
                  let r = s.getImageData(0, 0, t.width, t.height),
                    l = await tP(
                      r.data,
                      4,
                      t.width,
                      t.height,
                      t.gridsize,
                      t.gridsize,
                      "4wXCKprMMoxnyJ3PocJFs4CYbfnbazNe",
                      t.key,
                      !0
                    ),
                    o = new ImageData(l, t.width, t.height);

                    console.log("image deshuffled");

                  s.putImageData(o, 0, 0),
                    n.toBlob((r) => {
                      if (r) {
                        let s = URL.createObjectURL(r),
                          n = tL.get(t.url);
                        n &&
                          (tL.set(t.url, { ...n, localUrl: s }),
                          (tL = new Map(tL))),
                          i(e());
                      }
                    });
                }
              }),
                (a.src = r);
            }
          }),
        tZ = (e, t, r) =>
          tT
            .wait("fetch-".concat(t.url), r)
            .then(() => tz(t))
            .then((e) => e.blob())
            .then((e) => URL.createObjectURL(e))
            .then((i) => {
              if (t.key)
                tH.push(
                  tM
                    .wait("decrypt-".concat(t.url), r)
                    .then(() => tA(e, t, i))
                    .finally(() => tM.end("decrypt-".concat(t.url)))
                );
              else {
                let r = tL.get(t.url);
                r &&
                  (tL.set(t.url, { ...r, localUrl: i }),
                  (tL = new Map(tL)),
                  e());
              }
            })
            .finally(() => tT.end("fetch-".concat(t.url))),
        tO = (e) => (t) => () => (
          e.pages.forEach((e, r) => {
            tL.has(e.url) ||
              (tL.set(e.url, { ...e, localUrl: void 0 }),
              (tL = new Map(tL)),
              t(),
              tV.push(tZ(t, e, r)));
          }),
          () => {}
        ),
        tU = (e) => {
          let t = (0, l.useSyncExternalStore)(tO(e), () => tL),
            r = (0, l.useMemo)(
              () =>
                e.pages.reduce((e, r) => {
                  let i = t.get(r.url);
                  return i ? [...e, i] : e;
                }, []),
              [e, t]
            );
          return r;
        },
        tY = (e) => {
          let { episode: t } = e,
            { isHorizontalReading: r, isZooming: s } = (0, f.Y)(),
            n = tU(t);
          return 0 === t.pages.length
            ? null
            : (0, i.jsxs)("div", {
                className: "h-full",
                children: [
                  (0, i.jsx)(ee, {}),
                  r
                    ? (0, i.jsx)(t_, { episode: t, pages: n })
                    : (0, i.jsx)(tk, { episode: t, pages: n }),
                  s && (0, i.jsx)(tS, { episode: t, pages: n }),
                ],
              });
        },
        tD = { HORIZONTAL: "horizontal", VERTICAL: "vertical" },
        tF = () => {
          let e = (0, F.q)(1),
            { isHorizontalReading: t, dispatch: r } = (0, f.Y)();
          return (
            (0, l.useEffect)(() => {
              setTimeout(() => {
                e.set(0);
              }, 2e3),
                e.onChange((e) => {
                  0 === e && r({ type: "hideReadDirection" });
                });
            }, [r]),
            (0, i.jsx)("div", {
              className: "fixed left-1/2 top-1/2",
              style: { transform: "translate(-50%, -50%)" },
              children: (0, i.jsx)(h.E.div, {
                style: { opacity: e },
                children: (0, i.jsx)(tJ, {
                  direction: t ? tD.HORIZONTAL : tD.VERTICAL,
                }),
              }),
            })
          );
        },
        tJ = (e) => {
          let { direction: t = tD.HORIZONTAL } = e,
            r =
              t === tD.HORIZONTAL
                ? p.u.READ_HORIZONTAL_LARGE
                : p.u.READ_VERTICAL_LARGE,
            s = t === tD.HORIZONTAL ? "横読み" : "縦読み";
          return (0, i.jsxs)("div", {
            className:
              "w-[104px] rounded-8 bg-comic-overlay pt-[17px] text-center",
            style: { height: "calc(104px - 17px)" },
            children: [
              (0, i.jsx)(p.J, { name: r }),
              (0, i.jsx)("div", {
                children: (0, i.jsx)("span", {
                  className: "font-bold text-white typography-14",
                  children: s,
                }),
              }),
            ],
          });
        },
        tq = (e) => {
          let { episode: t } = e,
            r = (0, a.useRouter)(),
            {
              isShowReadDirection: s,
              dispatch: u,
              currentPage: h,
              isShowNavigation: p,
              pageLength: x,
              isSeeking: m,
            } = (0, f.Y)(),
            g = (0, ta.x)({ episode: t }),
            v = (0, l.useRef)(-1);
          (0, l.useEffect)(() => {
            t &&
              (-1 === v.current || v.current !== t.id) &&
              (u({ type: "setEpisode", payload: t }),
              u({ type: "hideNavigation" }),
              u({ type: "setPage", payload: 0 }),
              v.current > 0 && u({ type: "forceUnmount" }),
              (v.current = t.id));
          }, [t]);
          let _ = (0, l.useRef)(0);
          return ((0, l.useEffect)(() => {
            if (x > 0 && x === h && !p) return u({ type: "showNavigation" });
            h !== _.current &&
              p &&
              (m ? u({ type: "seekTransed" }) : u({ type: "hideNavigation" })),
              (_.current = h);
          }, [h, x, p, m, u]),
          (0, l.useEffect)(() => {
            t &&
              !r.query.access_token &&
              ("sell" !== t.salesType ||
                t.isPurchased ||
                (location.href = "/works/".concat(t.workId)));
          }, [t, r]),
          t)
            ? (0, i.jsxs)("div", {
                className: "jsx-5dbbb63ad2a08547",
                children: [
                  (0, i.jsx)(n(), {
                    id: "5dbbb63ad2a08547",
                    children:
                      "html{height:-webkit-fill-available}body{min-height:100vh;height:-webkit-fill-available}",
                  }),
                  (0, i.jsxs)("div", {
                    className: "jsx-5dbbb63ad2a08547",
                    children: [
                      (0, i.jsx)("div", {
                        style: { zIndex: X.V3 },
                        className:
                          "jsx-5dbbb63ad2a08547 fixed left-0 top-0 w-screen",
                        children: (0, i.jsx)(G, { episode: t }),
                      }),
                      (0, i.jsx)(tY, { episode: t }),
                      (0, i.jsx)("div", {
                        style: {
                          bottom: g
                            ? "calc(env(safe-area-inset-bottom) + 100px)"
                            : "env(safe-area-inset-bottom)",
                        },
                        className:
                          "jsx-5dbbb63ad2a08547 " +
                          "fixed left-0 w-screen ".concat(
                            g ? "bottom-[100px]" : "bottom-0"
                          ),
                        children: (0, i.jsx)(M, { episode: t }),
                      }),
                    ],
                  }),
                  g &&
                    (0, i.jsx)("div", {
                      style: { bottom: "env(safe-area-inset-bottom)" },
                      className: "jsx-5dbbb63ad2a08547 fixed bottom-0",
                      children: (0, i.jsx)(c.D, { area_name: "viewer" }),
                    }),
                  t && (0, i.jsx)(d.r, { workId: t.workId.toString() }),
                  (0, i.jsx)(o.l, {}),
                  s && (0, i.jsx)(tF, {}),
                ],
              })
            : (0, i.jsx)(A, {});
        };
    },
    35659: function (e, t, r) {
      r.d(t, {
        Y: function () {
          return h;
        },
        z: function () {
          return u;
        },
      });
      var i = r(47224),
        s = r(50959),
        n = r(68184),
        a = r(26454),
        l = r(24563),
        o = r(14373);
      let d = {
          currentPage: 0,
          pageLength: 0,
          isShowNavigation: !1,
          isShowReadDirection: !1,
          isHorizontalReading: !1,
          isSpreadView: !1,
          isNeedVerticalRestore: !1,
          isNeedHorizontalRestore: !1,
          isZooming: !1,
          episode: null,
          isNeedLastRestore: !1,
          isForceUnmount: !1,
          isSeeking: !1,
        },
        c = (e, t) => {
          switch (t.type) {
            case "incPage": {
              let { currentPage: r, pageLength: i } = e,
                s = r + 1,
                n = s > i ? r : s;
              return t.payload && t.payload(n), { ...e, currentPage: n };
            }
            case "decPage": {
              let { currentPage: r } = e,
                i = r - 1,
                s = i >= 0 ? i : r;
              return t.payload && t.payload(s), { ...e, currentPage: s };
            }
            case "setPage":
              return { ...e, currentPage: t.payload };
            case "showNavigation":
              return { ...e, isShowNavigation: !0 };
            case "hideNavigation":
              return { ...e, isShowNavigation: !1 };
            case "showReadDirection":
              return { ...e, isShowReadDirection: !0 };
            case "hideReadDirection":
              return { ...e, isShowReadDirection: !1 };
            case "toggleIsHorizontalReading": {
              let {
                  isHorizontalReading: r,
                  currentPage: i,
                  isSpreadView: s,
                  isNeedHorizontalRestore: a,
                  isNeedVerticalRestore: o,
                } = e,
                d = !r,
                c = i,
                u = a,
                h = o;
              return (
                s && (c = d ? Math.ceil(i / 2) : 2 * i),
                l.ly.setItem(n.dR.isHorizontalReading, d ? "true" : "false"),
                d ? (u = !0) : (h = !0),
                t.payload && t.payload(d),
                {
                  ...e,
                  isHorizontalReading: d,
                  currentPage: c,
                  isNeedHorizontalRestore: u,
                  isNeedVerticalRestore: h,
                }
              );
            }
            case "verticalRestored":
              return { ...e, isNeedVerticalRestore: !1 };
            case "horizontalRestored":
              return { ...e, isNeedHorizontalRestore: !1 };
            case "toggleZoom": {
              let {
                currentPage: r,
                isHorizontalReading: i,
                isSpreadView: s,
                episode: n,
                isZooming: a,
              } = e;
              if (!n || r < 0 || r >= n.pages.length) return e;
              if (i && s) {
                let t = n.pages.length;
                if (
                  ("left" === n.twoPageLayout && (t += 1),
                  t % 2 && (t += 1),
                  2 * r >= t)
                )
                  return e;
              }
              return (
                a
                  ? (document.body.style.overflow = "initial")
                  : (document.body.style.overflow = "hidden"),
                t.payload && t.payload(a),
                { ...e, isZooming: !a }
              );
            }
            case "setEpisode":
              return { ...e, episode: t.payload };
            case "init": {
              let t = l.ly.getItem(n.dR.isHorizontalReading),
                r = "true" === t;
              (0, a.t8)({
                key: "dimension10",
                value: r ? o.C.HORIZON : o.C.VERTICAL,
              }),
                null === t && window.innerWidth > 750 && (r = !0);
              let i = !1;
              return (
                (i = window.innerWidth > 750),
                {
                  ...e,
                  isHorizontalReading: r,
                  isShowReadDirection: !0,
                  isSpreadView: i,
                }
              );
            }
            case "setPageLength":
              return { ...e, pageLength: t.payload };
            case "setIsSpreadView":
              return { ...e, isSpreadView: t.payload };
            case "showLastPage":
              return { ...e, isNeedLastRestore: !0 };
            case "lastPageRestored":
              return { ...e, isNeedLastRestore: !1 };
            case "forceUnmount":
              return { ...e, isForceUnmount: !0 };
            case "forceUnmounted":
              return { ...e, isForceUnmount: !1 };
            case "seekFinished":
              return {
                ...e,
                isNeedHorizontalRestore: !0,
                currentPage: t.payload,
                isSeeking: !0,
              };
            case "seekTransed":
              return { ...e, isSeeking: !1 };
            case "widthExpanded": {
              let t =
                e.isHorizontalReading && !e.isSpreadView
                  ? Math.ceil(e.currentPage / 2)
                  : e.currentPage;
              return {
                ...e,
                isSpreadView: !0,
                currentPage: t,
                isNeedHorizontalRestore: e.isHorizontalReading,
              };
            }
            case "widthShrinked": {
              var r;
              let t =
                e.isHorizontalReading && e.isSpreadView
                  ? 2 * e.currentPage
                  : e.currentPage;
              return {
                ...e,
                isSpreadView: !1,
                currentPage:
                  (null === (r = e.episode) || void 0 === r
                    ? void 0
                    : r.twoPageLayout) === "left"
                    ? t - 1
                    : t,
                isNeedHorizontalRestore: e.isHorizontalReading,
              };
            }
          }
        },
        [u, h] = (0, i.Z)(() => {
          let [
              {
                currentPage: e,
                pageLength: t,
                isShowNavigation: r,
                isShowReadDirection: i,
                isHorizontalReading: n,
                episode: a,
                isZooming: l,
                isNeedVerticalRestore: o,
                isNeedHorizontalRestore: u,
                isSpreadView: h,
                isNeedLastRestore: p,
                isForceUnmount: x,
                isSeeking: m,
              },
              g,
            ] = (0, s.useReducer)(c, d),
            v = (0, s.useMemo)(() => !!a && n && !a.isTateyomi, [n, a]);
          return {
            currentPage: e,
            pageLength: t,
            isShowNavigation: r,
            isShowReadDirection: i,
            isHorizontalReading: v,
            isZooming: l,
            isNeedVerticalRestore: o,
            isNeedHorizontalRestore: u,
            isSpreadView: h,
            isNeedLastRestore: p,
            isForceUnmount: x,
            isSeeking: m,
            dispatch: g,
          };
        });
    },
    14373: function (e, t, r) {
      r.d(t, {
        C: function () {
          return i;
        },
      });
      let i = { HORIZON: "horizon", VERTICAL: "vertical" };
    },
    42530: function (e, t, r) {
      r.d(t, {
        Y: function () {
          return n;
        },
      });
      var i = r(50959),
        s = r(39999);
      let n = () => {
        let {
          isInited: e,
          setWindowSize: t,
          dispatch: r,
          detectScreenSize: n,
        } = (0, s.Ux)();
        (0, i.useEffect)(() => {
          e ||
            (t(),
            ("ontouchstart" in window || navigator.maxTouchPoints > 0) &&
              r({ type: "setTouchDevice" }),
            n(),
            window.addEventListener("resize", () => {
              t(), n();
            }),
            window.addEventListener(
              "mouseover",
              () => {
                r({ type: "pointed" });
              },
              { once: !0 }
            ),
            window.addEventListener(
              "touchstart",
              () => {
                r({ type: "touched" });
              },
              { once: !0 }
            ),
            r({ type: "inited" }));
        }, [e]);
      };
    },
    34753: function (e, t, r) {
      r.d(t, {
        v: function () {
          return n;
        },
      });
      var i = r(35659),
        s = r(88170);
      let n = (e) => {
        let { isHorizontalReading: t, isSpreadView: r } = (0, i.Y)(),
          n = (0, s.I)({ episode: e });
        if (!e) return 0;
        if (t && r) {
          let t = Math.ceil(
            ("left" === e.twoPageLayout ? e.pages.length + 1 : e.pages.length) /
              2
          );
          return n ? t + 1 : t;
        }
        console.log("pages number: " + e.pages.length);
        return n ? e.pages.length + 1 : e.pages.length;
      };
    },
    99611: function (e, t, r) {
      r.d(t, {
        a: function () {
          return d;
        },
      });
      var i = r(50959),
        s = r(39999),
        n = r(9448),
        a = r(65420),
        l = r(37618),
        o = r(26454);
      let d = function () {
        let e =
            arguments.length > 0 && void 0 !== arguments[0] ? arguments[0] : [],
          [t, r, d] = (0, l.L)(!1),
          { dispatch: c, isInited: u } = (0, s.Ux)(),
          { status: h, user: p } = (0, n.SE)();
        (0, i.useEffect)(() => {
          r(), c({ type: "pageTrans" });
        }, e),
          (0, i.useEffect)(() => {
            if (u && h !== a.J.Unknown && t) {
              if (p) {
                let e = p.id.toString();
                (0, o.t8)({ key: "userId", value: e }),
                  (0, o.t8)({ key: "dimension8", value: e });
              }
              (0, o.LV)(location.pathname, p),
                window.krt && window.krt("view"),
                d();
            }
          }, [u, h, t, p]);
      };
    },
    89706: function (e, t, r) {
      r.d(t, {
        S: function () {
          return n;
        },
      });
      var i = r(50959),
        s = r(52339);
      let n = (e) => {
        let t = (0, i.useMemo)(() => {
          switch (!0) {
            case e < 1e4:
              return (0, s._y)(e);
            case e >= 1e4 && e < 1e5:
              return "".concat((e / 1e4).toFixed(1).replace(/\.0$/, ""), "万");
            case e >= 1e5 && e < 1e8:
              return "".concat((e / 1e4).toFixed(0), "万");
            case e >= 1e8:
              return "".concat((e / 1e8).toFixed(0), "億");
            default:
              return e;
          }
        }, [e]);
        return t;
      };
    },
    39975: function (e, t, r) {
      r.d(t, {
        He: function () {
          return p;
        },
        L0: function () {
          return a;
        },
        RZ: function () {
          return d;
        },
        S: function () {
          return h;
        },
        Sb: function () {
          return u;
        },
        TI: function () {
          return l;
        },
        jw: function () {
          return c;
        },
        xz: function () {
          return o;
        },
      });
      var i = r(94712);
      let s = [, "月", "火", "水", "木", "金", "土", "日"],
        n = (e) =>
          e.replace(/\((\d)\)/, (e, t) => "(".concat(s[parseInt(t)], ")")),
        a = (e) => (0, i.Z)(e, "yyyy/MM/dd"),
        l = (e) => (0, i.Z)(e, "yyyy年M月d日"),
        o = (e) => (0, i.Z)(e, "yyyy/MM/dd hh:mm:ss"),
        d = (e) => (0, i.Z)(e, "yyyy年M月d日H時m分s秒"),
        c = (e) => n((0, i.Z)(e, "M月d日(i)")),
        u = (e) => n((0, i.Z)(e, "M/d(i)")),
        h = (e) => n((0, i.Z)(e, "yyyy年M月d日(i)")),
        p = (e) => (0, i.Z)(e, "yyyy-MM-dd");
    },
    67474: function (e, t, r) {
      r.d(t, {
        H: function () {
          return n;
        },
      });
      var i = r(94712);
      let s = globalThis.crypto,
        n = async () => {
          let e = (0, i.Z)(new Date(), "yyyy-MM-dd'T'HH:mm:ssXXXXX"),
            t = new TextEncoder().encode(
              ""
                .concat(e)
                .concat(
                  "mAtW1X8SzGS880fsjEXlM73QpS1i4kUMBhyhdaYySk8nWz533nrEunaSplg63fzT"
                )
            ),
            r = await s.subtle.digest("SHA-256", t),
            n = Array.from(new Uint8Array(r))
              .map((e) => e.toString(16).padStart(2, "0"))
              .join("");
          return { time: e, hash: n };
        };
    },
  },
]);
