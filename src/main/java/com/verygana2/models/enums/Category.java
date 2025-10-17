package com.verygana2.models.enums;

public enum Category {
    TECHNOLOGY("Tecnología"),
    FASHION("Moda"),
    FOOD("Comida"),
    TRAVEL("Viajes"),
    HEALTH("Salud"),
    EDUCATION("Educación"),
    ENTERTAINMENT("Entretenimiento"),
    SPORTS("Deportes"),
    FINANCE("Finanzas"),
    AUTOMOTIVE("Automotriz"),
    REAL_ESTATE("Bienes Raíces"),
    OTHER("Otro");

    private final String description;

    Category(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
