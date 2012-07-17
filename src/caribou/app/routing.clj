(ns caribou.app.routing
  (:use [clj-time.core :only (now)]
        [clj-time.format :only (unparse formatters)]
        [compojure.core :only (routes GET POST PUT DELETE ANY)]
        [ring.middleware file file-info])
  (:require [clojure.string :as string]
            [compojure.handler :as compojure-handler]
            [caribou.app.controller :as controller]
            [caribou.app.template :as template]
            [caribou.app.util :as app-util]
            [caribou.config :as config]
            [caribou.logger :as log]
            [caribou.util :as util]))

(defonce route-funcs (atom {}))
(defonce caribou-routes (atom {}))

(defn resolve-method
  [method path func]
  (condp = method
    "GET" (GET path {params :params} func)
    "POST" (POST path {params :params} func)
    "PUT" (PUT path {params :params} func)
    "DELETE" (DELETE path {params :params} func)
    (ANY path {params :params} func)))

(defn add-route
  [slug method route func]
  (log/debug (format "adding route %s -- %s %s" slug route method) :routing)
  (swap! route-funcs assoc slug func)
  (swap! caribou-routes assoc slug (resolve-method method route func)))

(defn clear-routes
  "Clears the app's routes. Used by Halo to update the routes."
  []
  (swap! route-funcs {})
  (swap! caribou-routes {}))

(defn default-action
  "if a page doesn't have a defined action, we just send the params to the template"
  [params]
  (let [template (params :template)]
    (template params)))

(def built-in-formatter (formatters :basic-date-time))

(defn default-index
  [request]
  (format "Welcome to Caribou! Next step is to add some pages.<br /> %s" (unparse built-in-formatter (now))))

(defn add-default-route
  []
  (add-route :default "GET" "/" default-index))
