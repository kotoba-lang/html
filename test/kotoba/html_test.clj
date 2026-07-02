(ns kotoba.html-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kotoba.html :as h]))

(deftest elements-and-attrs
  (is (= "<a href=\"/x\">link</a>" (h/html [:a {:href "/x"} "link"])))
  (is (= "<br>" (h/html [:br])))
  (is (= "<img src=\"a.png\">" (h/html [:img {:src "a.png"}])))
  (is (= "<input checked>" (h/html [:input {:checked true}])))
  (is (= "<input>" (h/html [:input {:checked false :id nil}])))
  (is (= "<div></div>" (h/html [:div])))
  (is (= "<p>Some <strong>bold</strong> text</p>"
         (h/html [:p "Some " [:strong "bold"] " text"]))))

(deftest escaping-and-doctype
  (is (= "<h1>a &amp; b &lt;c&gt;</h1>" (h/html [:h1 "a & b <c>"])))
  (is (= "<a title=\"x&quot;y\">t</a>" (h/html [:a {:title "x\"y"} "t"])))
  (is (str/starts-with? (h/html5 [:html [:body "hi"]]) "<!DOCTYPE html>\n<html>")))

(deftest raw-text-elements
  (is (= "<script>if (a < b && c > d) x();</script>"
         (h/html [:script "if (a < b && c > d) x();"])))
  (is (= "<style>.a > .b { color: red }</style>"
         (h/html [:style ".a > .b { color: red }"])))
  (is (= "<p>a &lt; b</p>" (h/html [:p "a < b"]))))

(deftest nested-block-indents
  (is (= "<ul>\n  <li>a</li>\n  <li>b</li>\n</ul>"
         (h/html [:ul [:li "a"] [:li "b"]]))))
