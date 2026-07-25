package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class TriviaQuizAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode meta = MAPPER.createObjectNode();
        meta.put("brand_id", "default");
        meta.put("campaign_id", "23");
        root.set("meta", meta);

        ObjectNode branding = MAPPER.createObjectNode();
        branding.put("main_logo_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        branding.put("watermark_logo_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        root.set("branding", branding);

        root.set("game_config", MAPPER.createObjectNode());

        ObjectNode game = MAPPER.createObjectNode();
        ArrayNode questions = MAPPER.createArrayNode();

        ObjectNode q1 = MAPPER.createObjectNode();
        q1.put("id", 1);
        q1.put("question", "¿Cuál es la capital de Colombia?");
        ArrayNode q1Options = MAPPER.createArrayNode();
        q1Options.add("Bogotá").add("Medellín").add("Cali").add("Barranquilla");
        q1.set("options", q1Options);
        q1.put("correct_answer_index", 0);
        questions.add(q1);

        ObjectNode q2 = MAPPER.createObjectNode();
        q2.put("id", 2);
        q2.put("question", "¿Cuántos jugadores tiene un equipo de fútbol en cancha?");
        ArrayNode q2Options = MAPPER.createArrayNode();
        q2Options.add("9").add("10").add("11").add("12");
        q2.set("options", q2Options);
        q2.put("correct_answer_index", 2);
        questions.add(q2);

        ObjectNode q3 = MAPPER.createObjectNode();
        q3.put("id", 3);
        q3.put("question", "¿Cuál es el río más largo del mundo?");
        ArrayNode q3Options = MAPPER.createArrayNode();
        q3Options.add("Nilo").add("Amazonas").add("Yangtsé").add("Misisipi");
        q3.set("options", q3Options);
        q3.put("correct_answer_index", 1);
        questions.add(q3);

        ObjectNode q4 = MAPPER.createObjectNode();
        q4.put("id", 4);
        q4.put("question", "¿En qué año llegó el ser humano a la Luna?");
        ArrayNode q4Options = MAPPER.createArrayNode();
        q4Options.add("1965").add("1969").add("1972").add("1959");
        q4.set("options", q4Options);
        q4.put("correct_answer_index", 1);
        questions.add(q4);

        ObjectNode q5 = MAPPER.createObjectNode();
        q5.put("id", 5);
        q5.put("question", "¿Cuál es el planeta más grande del sistema solar?");
        ArrayNode q5Options = MAPPER.createArrayNode();
        q5Options.add("Saturno").add("Tierra").add("Júpiter").add("Neptuno");
        q5.set("options", q5Options);
        q5.put("correct_answer_index", 2);
        questions.add(q5);

        ObjectNode q6 = MAPPER.createObjectNode();
        q6.put("id", 6);
        q6.put("question", "¿Quién pintó la Mona Lisa?");
        ArrayNode q6Options = MAPPER.createArrayNode();
        q6Options.add("Pablo Picasso").add("Leonardo da Vinci").add("Vincent van Gogh").add("Miguel Ángel");
        q6.set("options", q6Options);
        q6.put("correct_answer_index", 1);
        questions.add(q6);

        ObjectNode q7 = MAPPER.createObjectNode();
        q7.put("id", 7);
        q7.put("question", "¿Cuántos continentes hay en el mundo?");
        ArrayNode q7Options = MAPPER.createArrayNode();
        q7Options.add("5").add("6").add("7").add("8");
        q7.set("options", q7Options);
        q7.put("correct_answer_index", 2);
        questions.add(q7);

        ObjectNode q8 = MAPPER.createObjectNode();
        q8.put("id", 8);
        q8.put("question", "¿Cuál es el metal más abundante en la corteza terrestre?");
        ArrayNode q8Options = MAPPER.createArrayNode();
        q8Options.add("Hierro").add("Aluminio").add("Cobre").add("Zinc");
        q8.set("options", q8Options);
        q8.put("correct_answer_index", 1);
        questions.add(q8);

        ObjectNode q9 = MAPPER.createObjectNode();
        q9.put("id", 9);
        q9.put("question", "¿En qué país se originaron los Juegos Olímpicos?");
        ArrayNode q9Options = MAPPER.createArrayNode();
        q9Options.add("Italia").add("Grecia").add("Egipto").add("Francia");
        q9.set("options", q9Options);
        q9.put("correct_answer_index", 1);
        questions.add(q9);

        ObjectNode q10 = MAPPER.createObjectNode();
        q10.put("id", 10);
        q10.put("question", "¿Cuál es el idioma más hablado del mundo por número de hablantes nativos?");
        ArrayNode q10Options = MAPPER.createArrayNode();
        q10Options.add("Inglés").add("Español").add("Mandarín").add("Hindi");
        q10.set("options", q10Options);
        q10.put("correct_answer_index", 2);
        questions.add(q10);

        game.set("questions", questions);
        root.set("game", game);

        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("key_win_url", "");
        audio.put("victory_url", "");
        root.set("audio", audio);

        ObjectNode texts = MAPPER.createObjectNode();
        texts.set("victory_messages", MAPPER.createArrayNode().add("¡Excelente conocimiento!"));
        root.set("texts", texts);

        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("keys_per_action", 3);
        rewards.put("keys_on_completion", 50);
        root.set("rewards", rewards);

        // reward_popup
        ObjectNode rewardPopup = MAPPER.createObjectNode();
        rewardPopup.put("popup_title", "Recompensas desbloqueadas");

        ArrayNode products = MAPPER.createArrayNode();

        // Producto 1
        ObjectNode prod1 = MAPPER.createObjectNode();
        prod1.put("id", 1);
        prod1.put("name", "Membresia de 3 meses PlayStation plus");
        prod1.put("image_url", "https://cdn.verygana.com/public/products/commercial-2/1779407655456-52126342.png");
        prod1.put("image_message", "SUPER DESCUENTO");
        prod1.put("commercial", "CommercialTest");
        prod1.put("regular_price", 89900);
        prod1.put("keys_message", "Con [[4.495]] llaves pagas [[SOLO 44.495 COP]]");
        prod1.put("rating", 0.0);
        prod1.put("max_keys_allowed", 4495);
        prod1.put("min_cash_cents", 4449500);
        prod1.put("stock", 10);
        prod1.put("category_name", "Videojuegos");
        products.add(prod1);

        rewardPopup.set("products", products);
        root.set("reward_popup", rewardPopup);

        ASSETS = root;
    }

    private TriviaQuizAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS); }
        catch (Exception e) { return ASSETS.toString(); }
    }
}