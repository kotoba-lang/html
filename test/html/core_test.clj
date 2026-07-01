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
