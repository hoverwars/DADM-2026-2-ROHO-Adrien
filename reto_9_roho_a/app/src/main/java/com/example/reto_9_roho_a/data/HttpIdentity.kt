package com.example.reto_9_roho_a.data

/**
 * User agent envoyé aux services OpenStreetMap (tuiles, Overpass, Nominatim).
 * OSM bloque (403) les user agents génériques comme un package "com.example.*";
 * il doit identifier l'application.
 */
const val HTTP_USER_AGENT = "PuntosDeInteres-DADM-Reto9/1.0 (Android; proyecto academico)"
