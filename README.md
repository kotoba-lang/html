# kotoba-lang/html

Hiccup-compatible EDN HTML renderer.

This is the standalone substrate form of the renderer that previously lived in
`shitsuke.hiccup`: plain vectors render to HTML, tag sugar works, inline style
maps render to CSS text, boolean attributes are supported, and
`[:hiccup/raw "..."]` passes through trusted markup.


## Maturity

| | |
|---|---|
| Role | ui-substrate |
| Tests | 12 assertions, all green |
| Operator console (UI/UX) | — |
| Export (CSV/JSON) | — |
| Shared CSS design system | yes (css.core/operator-theme) |
