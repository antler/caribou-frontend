(ns caribou.app.test.controller
  (:use [clojure.test])
  (:require [caribou.app.controller :as controller]))

(deftest render-test
  (let [defaulted (controller/render {:template identity})
        params {:status 500
                :template identity
                :session {:user "me"}
                :content-type "whatever/whocares"}
        provided (controller/render params)]
    (testing "Default values"
      (is (= 200 (get defaulted :status)))
      (is (= {:template identity} (get defaulted :body)))
      (is (= "text/html;charset=utf-8"
             (-> defaulted :headers (get "Content-Type")))))
    (testing "Explicit values"
      (is (= 500 (get provided :status)))
      (is (= {:user "me"} (get provided :session)))
      (is (= params (get provided :body)))
      (is (= "whatever/whocares"
             (-> provided :headers (get "Content-Type")))))))
