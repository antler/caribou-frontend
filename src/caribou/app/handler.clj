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
            [caribou.logger :as log]
            [caribou.util :as util]
            [caribou.config :as config]
            [caribou.core :as caribou]
            [caribou.app.middleware :as middleware]
            [caribou.app.pages :as pages]
            [caribou.app.error :as error]
            [caribou.app.request :as request]
            [caribou.app.routing :as routing]
            [caribou.app.template :as template]
            [caribou.app.util :as app-util]))

(defn use-public-wrapper
  [handler public-dir]
  (if public-dir
    (fn [request] ((wrap-resource handler public-dir) request))
    (fn [request] (handler request))))

(defn init-routes
  []
  (middleware/add-custom-middleware middleware/wrap-xhr-request)
  (let [routes (routing/routes-in-order (deref (config/draw :routes)))]
    (routing/add-head-routes)))

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

(defn make-handler
  [& args]
  (init-routes)
  (template/init)
  (-> (routing/router (deref (config/draw :routes)))
      (middleware/wrap-custom-middleware)))

(defn trigger-reset
  []
  ((deref (config/draw :reset))))

(defn reset-handler
  []
  (reset! (config/draw :routes) (flatland/ordered-map))
  (trigger-reset)
  (reset! (config/draw :handler) (make-handler)))

(defn handler
  [reset]
  (let [handler (make-handler)]
    (reset! (config/draw :handler) handler)
    (reset! (config/draw :reset) reset)
    (fn [request]
      (when (config/draw :controller :reload)
        ;; with-bindings: for some reason, ns.repl invokes in-ns
        ;; (repl.clj:95) which can't set! *ns* when it's not bound
        ;; thread-locally (e. g. in lein ring server)
        (with-bindings {#'*ns* *ns*}
          (ns.repl/refresh :after `reset-handler))
        (reset-handler))
      (let [handler (deref (config/draw :handler))]
        (handler request)))))
