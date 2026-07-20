# ADR-0001: Hiccup-compatible HTML contract

- Status: Accepted
- Date: 2026-07-20

## Decision

`kotoba-lang/html` follows Hiccup semantics for element vectors, tag/id/class sugar, attribute maps,
sequence children, fragments, class collections, style maps, boolean attributes, void elements and escaped
text/attributes. Trusted raw markup is explicit through `kotoba.html/raw`; untrusted input is always escaped.

The portable Hiccup tree is the shared UI data contract. HTML rendering and `kotoba:dom` compilation are
separate consumers; applications must not use rendered HTML strings as the native intermediate format.
Extensions must remain additive and must not reinterpret valid Hiccup forms.
