(ns caribou.app.handler
  (:use [ring.middleware.content-type :only (wrap-content-type)]
        [ring.middleware.file :only (wrap-file)]
        [ring.middleware.resource :only (wrap-resource)]
        [ring.middleware.file-info :only (wrap-file-info)]
        [ring.middleware.head :only (wrap-head)]
        [ring.middleware.json :only (wrap-json-params)]
        [ring.middleware.multipart-params :only (wrap-multipart-params)]
        [ring.middleware.session :only (wrap-session)]
        [ring.util.response :only (resource-response file-response)])
  (:require [flatland.ordered.map :as flatland]
            [clojure.tools.namespace.repl :as ns.repl]
            [clojure.string :as string]
            [polaris.core :as polaris]
            [caribou.logger :as log]
            [caribou.util :as util]
            [caribou.config :as config]
            [caribou.core :as caribou]
            [caribou.app.middleware :as middleware]
            [caribou.app.error :as error]
            [caribou.app.request :as request]
            [caribou.app.template :as template]
            [caribou.app.util :as app-util]))

(defn use-public-wrapper
  [handler public-dir]
  (if public-dir
    (fn [request] ((wrap-resource handler public-dir) request))
    (fn [request] (handler request))))

(defn wrap-caribou
  [handler config]
  (fn [request]
    (caribou/with-caribou config
      (if (config/draw :error :catch-exceptions)
        (try 
          (handler request)
          (catch Exception e 
            (let [trace (.getStackTrace e)
                  stack (map #(str "ERROR    |--> " %) trace)]
              (log/error (string/join "\n" (cons (str e) stack)))
              (if (config/draw :error :show-stacktrace)
                (throw e)
                (error/render-error :500 request)))))
        (handler request)))))

(defn make-router
  [routes]
  (let [routes (polaris/build-routes routes)]
    (reset! (config/draw :routes) routes)
    (polaris/router routes)))

(defn handler
  [reset]
  (let [get-routes (if (symbol? reset)
                      #((-> reset
                            resolve))
                      reset)
        handler (atom (make-router (get-routes)))
        reset-handler! #(reset! handler (make-router (get-routes)))]
    (fn [request]
      (when (config/draw :controller :reload)
        ;; with-bindings: for some reason, ns.repl invokes in-ns
        ;; (repl.clj:95) which can't set! *ns* when it's not bound
        ;; thread-locally (e. g. in lein ring server)
        (with-bindings {#'*ns* *ns*}
          (ns.repl/refresh))
        (reset-handler!))
      (let [response (@handler request)]
        (if (:reset-handler response)
          (do
            (reset-handler!)
            (dissoc response :reset-handler))
          response)))))
