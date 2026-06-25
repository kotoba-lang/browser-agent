(ns browser-agent-ui.core
  "Reagent front-end for browser-agent-clj.

  Renders three live panes from one event stream: the plan tree, the
  MoA delegation feed, and the agent's *own* browser (frames streamed
  from `browseragent.browser.live`, with a take-over button). The plan
  / delegation / answer events come from `browseragent.events` — the
  same `.cljc` projection the server uses, so both sides agree.

  Transport is injected: point `connect!` at your SSE/WebSocket endpoint
  (or, in an all-cljs deployment, call the library in-page and feed its
  result through `events/run->events`)."
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [browseragent.events :as events]))

(defonce app
  (r/atom {:task ""
           :plan []           ; [{:step :goal :assignee}]
           :feed []           ; delegation/answer events
           :frame nil         ; latest owned-browser frame {:url :title :image}
           :takeover? false}))

;; ── ingest one server event into app-db ──────────────────────────
(defn ingest! [{:keys [channel] :as ev}]
  (swap! app
         (fn [s]
           (case channel
             :plan       (update s :plan conj ev)
             :browser    (assoc s :frame ev)
             (:delegation :final) (update s :feed conj ev)
             s))))

;; ── transport (injected) ─────────────────────────────────────────
(defn connect!
  "Wire a host transport: `subscribe` is (fn [on-event] …). Replace with
  an EventSource/WebSocket in your deployment."
  [subscribe]
  (subscribe ingest!))

;; ── views ────────────────────────────────────────────────────────
(defn plan-tree []
  [:div.pane
   [:h3 "Plan"]
   (for [{:keys [step goal assignee]} (:plan @app)]
     ^{:key step}
     [:div.step
      [:span.badge (name assignee)] " "
      [:span.n step ". "] goal])])

(defn delegation-feed []
  [:div.pane
   [:h3 "Mixture-of-Agents"]
   (for [[i {:keys [kind step assignee result]}] (map-indexed vector (:feed @app))]
     ^{:key i}
     [:div.row
      (if (= :answer kind)
        [:div.answer [:strong "Answer"] [:pre result]]
        [:div [:span.badge (name (or assignee :?))] " step " step " → " result])])])

(defn browser-pane []
  (let [{:keys [frame takeover?]} @app]
    [:div.pane
     [:h3 "Agent browser "
      [:button {:on-click #(swap! app update :takeover? not)}
       (if takeover? "Resume agent" "Take over")]]
     [:div.url (:url frame)]
     (if (:image frame)
       [:img {:src (str "data:image/png;base64," (:image frame))}]
       [:div.frame (or (:title frame) "(no frame yet)")])]))

(defn app-view []
  [:div.app
   [:h1 "browser-agent-clj"]
   [:div.cols [plan-tree] [delegation-feed] [browser-pane]]])

(defn init []
  (rdom/render [app-view] (.getElementById js/document "app")))
