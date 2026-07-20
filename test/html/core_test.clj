(ns html.core-test
  (:require [clojure.test :refer [deftest is]]
            [html.core :as html]))

(deftest renders-hiccup
  (is (= "<div class=\"page x\" id=\"app\"><h1>Hello</h1><input disabled></div>"
         (html/->html [:div.page#app {:class [:x nil]} [:h1 "Hello"] [:input {:disabled true}]]))))

(deftest escapes-and-raw
  (is (= "&lt;tag&gt;" (html/->html "<tag>")))
  (is (= "<span>ok</span>" (html/->html [:hiccup/raw "<span>ok</span>"]))))

(deftest renders-style-map
  (is (= "<p style=\"font-size:12px;color:red;\">x</p>"
         (html/->html [:p {:style {:font-size "12px" :color "red"}} "x"]))))

(deftest void-tags-self-close
  (is (= "<br>" (html/->html [:br])))
  (is (= "<img src=\"x.png\">" (html/->html [:img {:src "x.png"}])))
  (is (= "<hr>" (html/->html [:hr]))))

(deftest renders-numbers-and-nested-seqs
  (is (= "42" (html/->html 42)))
  (is (= "<ul><li>a</li><li>b</li></ul>"
         (html/->html [:ul [[:li "a"] [:li "b"]]]))))

(deftest nil-is-invisible
  (is (= "" (html/->html nil)))
  (is (= "<p></p>" (html/->html [:p nil]))))

(deftest escapes-attribute-values
  (is (= "<a href=\"a&quot;b\">link</a>" (html/->html [:a {:href "a\"b"} "link"]))))

(deftest hiccup-fragment-and-raw-contract
  (is (= "<span>a</span><span>b</span>"
         (html/->html [:<> [:span "a"] [:span "b"]])))
  (is (= "<b>trusted</b>" (html/->html (html/raw "<b>trusted</b>"))))
  (is (= "&lt;b&gt;escaped&lt;/b&gt;" (html/->html "<b>escaped</b>"))))

(deftest raw-text-contract
  (is (= "<style>body{color:red}</style>"
         (html/->html [:style [:hiccup/raw "body{color:red}"]])))
  (is (= "<script>const x = \"<b>\";</script>"
         (html/->html [:script "const x = \"<b>\";"])))
  (is (thrown? clojure.lang.ExceptionInfo
               (html/->html [:script "x</SCRIPT><img src=x>"])))
  (is (thrown? clojure.lang.ExceptionInfo
               (html/->html [:style [:hiccup/raw "x</style><script>x</script>"]]))))
