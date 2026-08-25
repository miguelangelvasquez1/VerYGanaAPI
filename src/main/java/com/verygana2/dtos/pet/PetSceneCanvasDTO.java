package com.verygana2.dtos.pet;

/**
 * Sistema de coordenadas en el que el juego coloca los objetos de una escena.
 *
 * Lo consume el lienzo del editor de escenas para dibujar una referencia visual
 * sin cargar el build de Unity: sin saber el tamaño del área ni hacia dónde crece
 * la Y, unas coordenadas en píxeles no se pueden situar en ninguna parte.
 *
 * Viene de configuración y no de constantes en el front a propósito: es una
 * propiedad del build, igual para todos los diseñadores, y si el equipo del juego
 * confirma otros valores se corrige con variables de entorno en vez de con un
 * despliegue del panel.
 */
public record PetSceneCanvasDTO(

        /** Ancho del área de juego, en las mismas unidades que la x/y de los objetos. */
        int width,

        /** Alto del área de juego. */
        int height,

        /**
         * Hacia dónde crece la Y: {@code DOWN} (0 arriba, convención de UI) o
         * {@code UP} (0 abajo, convención nativa de Unity).
         */
        String yAxis,

        /**
         * Qué punto del objeto marcan x/y: {@code TOP_LEFT} o {@code CENTER}.
         */
        String anchor
) {}
