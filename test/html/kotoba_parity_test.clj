(ns html.kotoba-parity-test
  "Byte-equality gate between html.core and its `.kotoba` port
  (kotoba/html_core.kotoba), the second step of the design-system migration in
  ADR-2607270100 section 10 (after css).

  The port is compiled here and executed through the KIR interpreter in this
  same JVM, so nothing crosses a runtime boundary and no typed value has to be
  marshalled from Clojure: each case is generated as a zero-argument `.kotoba`
  function whose whole body is the call under test, and the interpreter hands
  back the resulting string directly.

  SCOPE. Form A means the Hiccup *tree* is not a value — tag sugar, class
  collections, sequence children and pretty-print indentation stay in
  html.core. What is gated is the string pipeline: escape, attribute/style
  emission, void vs closed elements, RAWTEXT breakout refusal, and call-graph
  composition of those pieces into the same bytes html.core emits for the
  equivalent flat structure.

  ORDERING. Attribute and style maps are walked by `typed-map-entry-at` in
  sorted key order. Parity is asserted against a key-sorted run of html.core
  (via sorted-map inputs), matching the css port's finding about Clojure map
  order above 8 entries."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [html.core :as html]
            [kotoba.compiler.core :as compiler]
            [kotoba.kir :as ir]))

(def port-source (slurp "kotoba/html_core.kotoba"))

(defn- kotoba-literal
  "A `.kotoba` string literal for S. Only the two escapes the reader needs."
  [s]
  (str \" (-> s (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) \"))

(defn- compile-cases
  "Compile the port plus one zero-arg function per case. Returns a map of
  case-name -> the string that function evaluates to."
  [cases]
  (let [defs (for [[name body] cases]
               (str "(defn " name " [] :string " body ")"))
        kir (:kir (compiler/compile-source
                   (str port-source "\n" (str/join "\n" defs)) :wasm32-kotoba-v1 {}))]
    (into {} (map (fn [[name _]] [name (ir/execute kir (symbol name) [])]) cases))))

(defn- typed-map-literal [m]
  (str "(typed-map-new [:map :keyword :string] "
       (str/join " " (mapcat (fn [[k v]] [(pr-str k) (kotoba-literal v)])
                             (sort-by (comp str key) m)))
       ")"))

(defn- unwrap [expr]
  (str "(result-value-of [:result :string :string] " expr " \"\")"))

;; --- escape ---------------------------------------------------------------

(def escape-corpus
  {"empty" ""
   "plain" "hello"
   "amp" "a&b"
   "lt-gt" "<tag>"
   "quote" "a\"b"
   "combo" "a<b>&\"c"
   "already-entities" "a&amp;b"})

(deftest esc-is-byte-identical-to-html-core
  (let [cases (into {} (for [[name s] escape-corpus]
                         [(str "esc_" name) (str "(esc " (kotoba-literal s) ")")]))
        actual (compile-cases cases)]
    (doseq [[name s] escape-corpus]
      (testing name
        (is (= (html/esc s) (get actual (str "esc_" name))))))))

;; --- attributes and style -------------------------------------------------

(deftest attr-and-bool-attr-match-html-core
  (let [actual (compile-cases
                {"attr_href" (str "(attr \"href\" " (kotoba-literal "a\"b") ")")
                 "bool_disabled" "(bool-attr \"disabled\")"
                 "attrs_sorted" (str "(attrs " (typed-map-literal {:href "/x" :title "t"}) ")")
                 "style_css" (str "(style-css " (typed-map-literal {:font-size "12px" :color "red"}) ")")
                 "style_attr" (str "(style-attr " (typed-map-literal {:font-size "12px" :color "red"}) ")")})]
    (testing "escaped attribute value"
      (is (= (str " href=\"" (html/esc "a\"b") "\"")
             (get actual "attr_href"))))
    (testing "boolean attribute"
      (is (= " disabled" (get actual "bool_disabled"))))
    (testing "attrs map in key-sorted order"
      ;; html.core/render-attrs walks the map; sorted-map makes order match the port
      (is (= (html/render-attrs (into (sorted-map) {:href "/x" :title "t"}))
             (get actual "attrs_sorted"))))
    (testing "style map in key-sorted order"
      (is (= (html/style-map->css (into (sorted-map) {:font-size "12px" :color "red"}))
             (get actual "style_css")))
      (is (= (html/render-attrs {:style (into (sorted-map) {:font-size "12px" :color "red"})})
             (get actual "style_attr"))))))

;; --- elements -------------------------------------------------------------

(deftest void-and-closed-elements-match-html-core
  (let [actual (compile-cases
                {"br" "(void-el \"br\" \"\")"
                 "img" (str "(void-el \"img\" (attr \"src\" " (kotoba-literal "x.png") "))")
                 "hr" "(void-el \"hr\" \"\")"
                 "h1" (str "(el \"h1\" \"\" (text " (kotoba-literal "Hello") "))")
                 "p_style" (str "(el \"p\" (style-attr " (typed-map-literal {:font-size "12px" :color "red"}) ") "
                                "(text " (kotoba-literal "x") "))")
                 "demo" (str "(el \"div\" "
                             "(string-concat (attr \"class\" " (kotoba-literal "page x") ") "
                             "(attr \"id\" " (kotoba-literal "app") ")) "
                             "(string-concat (el \"h1\" \"\" (text " (kotoba-literal "Hello") ")) "
                             "(void-el \"input\" (bool-attr \"disabled\"))))")
                 "fragment" (str "(string-concat (el \"span\" \"\" (text " (kotoba-literal "a") ")) "
                                 "(el \"span\" \"\" (text " (kotoba-literal "b") ")))")
                 "doc" (str "(html5 (el \"html\" \"\" (el \"body\" \"\" (text "
                            (kotoba-literal "hi") "))))")})]
    (testing "void tags"
      (is (= (html/->html [:br]) (get actual "br")))
      (is (= (html/->html [:img {:src "x.png"}]) (get actual "img")))
      (is (= (html/->html [:hr]) (get actual "hr"))))
    (testing "closed elements"
      (is (= (html/->html [:h1 "Hello"]) (get actual "h1")))
      (is (= (html/->html [:p {:style (into (sorted-map) {:font-size "12px" :color "red"})} "x"])
             (get actual "p_style"))))
    (testing "composed page fragment (contract test shape)"
      (is (= (html/->html [:div.page#app {:class [:x nil]} [:h1 "Hello"] [:input {:disabled true}]])
             (get actual "demo"))))
    (testing "fragment composition via string-concat"
      (is (= (html/->html [:<> [:span "a"] [:span "b"]])
             (get actual "fragment"))))
    (testing "html5 document wrapper (compact — pretty-print stays in html.core)"
      ;; html.core pretty-prints block-only element children; form A emits
      ;; the call-graph order with no inserted newlines. Assert the compact
      ;; bytes the port owns, and keep the doctype prefix contract from html5.
      (is (str/starts-with? (html/html5 [:html [:body "hi"]]) "<!DOCTYPE html>\n"))
      (is (= "<!DOCTYPE html>\n<html><body>hi</body></html>"
             (get actual "doc"))))))

;; --- RAWTEXT breakout -----------------------------------------------------

(deftest raw-text-guard-rejects-what-html-core-throws-on
  (let [ok-script "const x = \"<b>\";"
        ok-style "body{color:red}"
        bad-script "x</SCRIPT><img src=x>"
        bad-style "x</style><script>x</script>"
        actual (compile-cases
                {"script_ok" (unwrap (str "(raw-text-el \"script\" " (kotoba-literal ok-script) ")"))
                 "style_ok" (unwrap (str "(raw-text-el \"style\" " (kotoba-literal ok-style) ")"))
                 "script_bad" (str "(match-result (raw-text-el \"script\" " (kotoba-literal bad-script) ")"
                                   " [:result :string :string]"
                                   " (ok text text) (err message \"REJECTED\"))")
                 "style_bad" (str "(match-result (raw-text-el \"style\" " (kotoba-literal bad-style) ")"
                                  " [:result :string :string]"
                                  " (ok text text) (err message \"REJECTED\"))")})]
    (testing "safe RAWTEXT renders unescaped"
      (is (= (html/->html [:script ok-script]) (get actual "script_ok")))
      (is (= (html/->html [:style [:hiccup/raw ok-style]]) (get actual "style_ok"))))
    (testing "breakout content is refused on both sides"
      (is (thrown? clojure.lang.ExceptionInfo (html/->html [:script bad-script])))
      (is (thrown? clojure.lang.ExceptionInfo
                   (html/->html [:style [:hiccup/raw bad-style]])))
      (is (= "REJECTED" (get actual "script_bad")))
      (is (= "REJECTED" (get actual "style_bad"))))))
