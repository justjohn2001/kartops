(ns kartops.core
  (:require [clojure.java.io :as io])
  (:import [java.net InetAddress]
           [org.pcap4j.packet UdpPacket]
           [org.pcap4j.core
            PcapNetworkInterface
            PcapNetworkInterface$PromiscuousMode
            BpfProgram$BpfCompileMode]
           [org.pcap4j.core Pcaps]))

(def ^:private ordered-track-list (read-string (slurp (io/resource "ordered-track-list.edn"))))

(def ^:private event-names ["Grand Prix" "Team Grand Prix" "Balloon Battle" "Coin Battle"])

(def millis-per-minute (* 60 1000))

(def message-last-sent-at (atom 0))

(def event-last-started-at (atom 0))


(defn to-number
  [coll]
  (reduce (fn [r v] (+ (* 256 r) v)) 0 coll))

(defn header
  [v]
  (loop [v' v
         m [4 4 1 1 1 1 1 1 1 1 1]
         r []]
    (if (empty? m)
      r
      (let [n (first m)]
        (recur (drop n v') (rest m)
               (as-> v' <>
                 (take n <>)
                 (to-number <>)
                 (conj r <>)))))))

(defn validate-header
  [p]
  (let [[b1 b2 b3 b4] (drop 8 p)]
    (and (= b1 16)
         (or (= b2 0) (= b2 40))
         (or (= b3 0) (= b3 40))
         (or (= b4 0) (= b4 4) (= b4 56)))))

(defn validate-room
  [p]
  (= (nth p 11) 4))

(defn find-racedata-1
  [s]
  (let [h (header s)
        racedata-1-len (nth h 3)]
    (if (> racedata-1-len 0)
      (let [offset (first (drop 2 h))]
        (take racedata-1-len (drop offset s)))
      nil)))

(defn parse-racedata-1
  [v]
  {:track-id (nth v 22)})

(defn find-racedata_2
  [s]
  (let [h (header s)
        racedata_2-len (nth h 4)]
    (if (> racedata_2-len 0)
      (let [offset (apply + (take 2 (drop 2 h)))]
        (take racedata_2-len (drop offset s)))
      nil)))

(defn parse-racedata_2
  [v]
  (let [end-time-1 (as-> v <>
                     (drop 6 <>)
                     (take 4 <>)
                     (to-number <>)
                     (unsigned-bit-shift-right <> 5)
                     (bit-and 0x7ffff <>))
        end-time-2 (as-> v <>
                     (drop 6 <>)
                     (take 4 <>)
                     (to-number <>)
                     (unsigned-bit-shift-right <> 5)
                     (bit-and 0x7ffff <>))]
    end-time-1))

(defn find-room
  [s]
  (let [h (header s)]
    (let [offset (apply + (take 3 (drop 2 h)))]
      (take 4 (drop offset s)))))

(defn parse-room
  [v]
  {:mode (first v)
   :param1 (nth v 2)})

(defn track-id->name [id]
  (nth ordered-track-list id))

(defn debounce-event-started []
  (let [current-time (System/currentTimeMillis)
        diff-in-minutes (/ (- current-time @event-last-started-at) millis-per-minute)]
    (> diff-in-minutes 1.5)))

(defn log-event! [log-name type details]
  (let [timestamp (str (java.time.LocalDateTime/now))]
    (spit log-name (format "%s %s: \"%s\"\n" timestamp type details) :append true)))

(defn notify-event-started [log-name event-name]
  (println (format "Started a %s!" event-name))
  (swap! event-last-started-at (constantly (System/currentTimeMillis)))
  (log-event! log-name "STARTED_EVENT" event-name))

(defn debounce-race-ended []
  (let [current-time (System/currentTimeMillis)
        diff-in-minutes (/ (- current-time @message-last-sent-at) millis-per-minute)]
    (> diff-in-minutes 1.5)))

(defn notify-race-ended [log-name track-name]
  (println (format "Race finished on %s." track-name))
  (swap! message-last-sent-at (constantly (System/currentTimeMillis)))
  (log-event! log-name "RACE_FINISHED" track-name))

;; 16 in 10th byte of payload

(defn create-handle [iface]
  (let [timeout 100 ;; in milliseconds
        snap-len 65536
        mode PcapNetworkInterface$PromiscuousMode/PROMISCUOUS
        nif (Pcaps/getDevByName iface)
        handle (.openLive nif snap-len mode timeout)]
    (.setFilter handle "udp" BpfProgram$BpfCompileMode/NONOPTIMIZE)
    handle))



(defn process-packet [packet log-name]
  (let [raw-data (.getRawData packet)
        udp-packet (UdpPacket/newPacket raw-data 34 (- (count (vec raw-data)) 34))
        payload (-> udp-packet .getPayload .getRawData vec)
        cleaned-payload (map #(bit-and % 0xFF) payload)
        parsed-racedata-2 (parse-racedata_2 (find-racedata_2 cleaned-payload))]
    (when (validate-header cleaned-payload)
      (when (and (not= 0 parsed-racedata-2))
        (let [parsed-racedata-1 (parse-racedata-1 (find-racedata-1 cleaned-payload))
              track-name (track-id->name (:track-id parsed-racedata-1))]
          (when (debounce-race-ended)
            (notify-race-ended log-name track-name))))
      (when (validate-room cleaned-payload)
        (let [parsed-room (parse-room (find-room cleaned-payload))]
          (when (and (= (:mode parsed-room) 1)
                     (debounce-event-started))
            (notify-event-started log-name (nth event-names (:param1 parsed-room)))))))))

(defn -main
  [& [iface log-name]]
  ;; TODO: give nice error when args are missing
  (let [handle (create-handle iface)]
    (loop [packet (.getNextPacket handle)]
      (some-> packet (process-packet log-name))
      (recur (.getNextPacket handle)))))
