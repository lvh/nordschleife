(ns nordschleife.gathering
  "Gather information about the state of the cloud.")



(defn ^:private gather-servers
  "Gather all available servers."
  [compute]
  (nodes compute))

(defn gather
  "Gather all of the available information."
  [compute]
  {:servers (gather-servers compute)})
