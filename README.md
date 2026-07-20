# kotoba-lang/html

Hiccup-compatible EDN HTML renderer.

This is the standalone substrate form of the renderer that previously lived in
`shitsuke.hiccup`: plain vectors render to HTML, tag sugar works, inline style
maps render to CSS text, boolean attributes are supported, and
`[:hiccup/raw "..."]` passes through trusted markup. `kotoba.html/html` and
`kotoba.html/html5` expose the stable DSL facade.

The contract also includes `:<>` fragments, sequence children and the explicit
`kotoba.html/raw` trusted wrapper. See ADR-0001.

## Consumers

`shitsuke.hiccup` closed the loop and now depends on this repo (git
coordinate in `deps.edn`, `:local/root "../html"` sibling override under the
`:local` alias — same convention as the `kotoba-lang/css` dependency in
`liquid-glass-ui`/`kotoba-ui`): it delegates tag/attribute/style parsing and
the RAWTEXT (`<script>`/`<style>`) breakout-guard to `html.core` instead of
duplicating them, so fixes made here (e.g. the `[:hiccup/raw ...]` unwrap
regression fix) now reach `shitsuke.hiccup`'s ~20 downstream consumers
without a separate patch. `shitsuke.hiccup` keeps its own compact (no
pretty-print) tree-walk on top of these primitives, since this repo's
`->html` also adds newline/indentation formatting for block-only element
children that would be a breaking output-format change for those consumers.

## Maturity

| | |
|---|---|
| Role | ui-substrate |
| Tests | JVM Clojure + Node ClojureScript contracts |
| Operator console (UI/UX) | — |
| Export (CSV/JSON) | — |
| Shared CSS design system | yes (css.core/operator-theme) |

## Compatibility boundary

- Element vectors, tag/id/class sugar, fragments, sequence children, class collections,
  style maps, boolean attributes and void elements are stable.
- Text and attributes are escaped by default. Trusted markup requires `kotoba.html/raw`.
- `script` and `style` are HTML RAWTEXT elements; embedded closing-tag sequences are rejected.
- Portable Hiccup is the shared contract. Rendered HTML is not a native-UI intermediate format.
- Malformed or unsupported extension forms have no compatibility guarantee.

## Test

```bash
clojure -M:test
clojure -M:lint
nbb test/run_nbb_contract_tests.cljs
```
