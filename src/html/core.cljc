(ns html.core
  "Dependency-free Hiccup-compatible EDN to HTML renderer."
  (:require [clojure.string :as str]))

(defn esc
  "Escape &, <, >, and double quotes for HTML text/attribute context."
  [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(def void-tags
  #{"area" "base" "br" "col" "embed" "hr" "img" "input"
    "link" "meta" "param" "source" "track" "wbr"})

(def raw-text-tags #{"script" "style"})

(defn parse-tag
  [kw]
  (let [s (name kw)
        id (second (re-find #"#([^.#]+)" s))
        classes (map second (re-seq #"\.([^.#]+)" s))
        tag (or (second (re-find #"^([^.#]+)" s)) "div")]
    [tag (cond-> {}
           (seq classes) (assoc :class (str/join " " classes))
           id (assoc :id id))]))

(defn class-str [v]
  (cond
    (string? v) v
    (keyword? v) (name v)
    (symbol? v) (name v)
    (coll? v) (str/join " " (map class-str (filter identity v)))
    :else (str v)))

(defn style-map->css
  [m]
  (->> m
       (keep (fn [[k v]]
               (when (and v (not (false? v)))
                 (str (name k) ":" (if (true? v) "true" v) ";"))))
       (str/join "")))

(defn render-attrs [attrs]
  (->> attrs
       (keep (fn [[k v]]
               (when (and v (not (false? v)))
                 (let [k (name k)]
                   (cond
                     (= k "class")
                     (str " " k "=\"" (esc (class-str v)) "\"")
                     (and (= k "style") (map? v))
                     (str " " k "=\"" (esc (style-map->css v)) "\"")
                     (true? v)
                     (str " " k)
                     :else
                     (str " " k "=\"" (esc v) "\""))))))
       (apply str)))

(declare ->html)
(declare render-node)

(defn- element-node? [x]
  (when (and (vector? x)
             (keyword? (first x))
             (not= :hiccup/raw (first x)))
    (let [[tag] (parse-tag (first x))]
      (not (contains? void-tags tag)))))

(defn- block-children? [children]
  (and (seq children)
       (every? element-node? children)))

(defn render-node
  ([node sb] (render-node node sb 0))
  ([node sb ind]
  (cond
    (nil? node) sb
    (string? node) (conj! sb (esc node))
    (number? node) (conj! sb (str node))
    (and (vector? node) (= :hiccup/raw (first node))) (conj! sb (str (second node)))
    (and (vector? node) (not (empty? node)) (vector? (first node)))
    (reduce (fn [s c] (render-node c s ind)) sb node)
    (vector? node)
    (let [[t & body] node
          [tag base] (parse-tag t)
          [attrs children] (if (map? (first body))
                             [(first body) (rest body)]
                             [{} body])
          attrs (merge-with (fn [a b] (str (class-str a) " " (class-str b))) base attrs)]
      (conj! sb (str "<" tag (render-attrs attrs) ">"))
      (when-not (contains? void-tags tag)
        (if (contains? raw-text-tags tag)
          (let [content (apply str children)]
            ;; HTML5 RAWTEXT parsing: a <script>/<style> element terminates
            ;; at the FIRST literal, case-insensitive "</tag" sequence,
            ;; regardless of surrounding quotes/strings/comments in the raw
            ;; text. Concatenating children verbatim with no check for that
            ;; sequence lets any string value containing e.g. "</script>"
            ;; break out of the element and inject real markup after it --
            ;; a script-context XSS vector (verified against Python's
            ;; html.parser, which implements the same RAWTEXT rule real
            ;; browsers do: the injected "</script>" terminates early, not
            ;; the real closing tag written by this renderer).
            (when (re-find (re-pattern (str "(?i)</" tag)) content)
              (throw (ex-info (str "html: raw-text content for <" tag "> must not contain \"</" tag "\" "
                                    "case-insensitively -- that sequence terminates the element early "
                                    "per HTML5's RAWTEXT rule and can break out into injected markup")
                               {:tag tag})))
            (conj! sb content))
          (if (block-children? children)
            (do
              (doseq [c children]
                (conj! sb "\n")
                (conj! sb (apply str (repeat (inc ind) "  ")))
                (render-node c sb (inc ind)))
              (conj! sb "\n")
              (conj! sb (apply str (repeat ind "  "))))
            (reduce (fn [s c] (render-node c s ind)) sb children)))
        (conj! sb (str "</" tag ">")))
      sb)
    (seq? node) (reduce (fn [s c] (render-node c s ind)) sb node)
    :else (conj! sb (esc node)))))

(defn ->html
  [node]
  (str/join (persistent! (render-node node (transient [])))))

(def render ->html)

(defn html
  "Render one or more hiccup forms to an HTML fragment."
  [& forms]
  (str/join "\n" (map ->html forms)))

(defn html5
  "Render a full HTML document, prepending <!DOCTYPE html>."
  [& body]
  (str "<!DOCTYPE html>\n" (apply html body)))
