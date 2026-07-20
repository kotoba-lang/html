# html agent rules

- ADR-0001のHiccup-compatible contractを正本とする。
- valid Hiccup element、fragment、seq child、class/style/boolean attrs、void tagの意味を変えない。
- textとattributeは既定でescapeし、raw markupは明示的なtrusted wrapperだけに限定する。
- HTML文字列をnative UIの中間表現にしない。portable Hiccup treeを共有契約とする。
- app固有rendererへHiccup normalizationを複製しない。
