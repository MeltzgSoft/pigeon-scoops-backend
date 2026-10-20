(ns pigeon-scoops-backend.units.schema
  (:require [pigeon-scoops-backend.units.common :as common]
            [pigeon-scoops-backend.units.mass :as mass]
            [pigeon-scoops-backend.units.volume :as vol]))

(def common-enum
  (vec (concat [:enum] common/other-units)))

(def mass-enum
  (vec (concat [:enum] (keys mass/conversion-map))))

(def volume-enum
  (vec (concat [:enum] (keys vol/conversion-map))))

(def all-units-enum
  (vec (concat [:enum] (mapcat rest [common-enum mass-enum volume-enum]))))

