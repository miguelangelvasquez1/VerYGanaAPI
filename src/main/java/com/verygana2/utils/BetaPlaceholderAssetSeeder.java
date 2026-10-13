package com.verygana2.utils;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.verygana2.storage.service.R2Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sube a R2 un placeholder real (JPEG generado en memoria con Java2D, sin
 * depender de ffmpeg/ImageMagick ni de archivos binarios en el repo) para cada
 * asset que los seeds beta_* dejan referenciado en base de datos pero sin
 * archivo real detrás.
 *
 * Sin esto, ad_assets / product_image_assets / raffle_image_assets /
 * prize_image_assets apuntan a un object_key que el front resuelve a una URL
 * pública que responde 404: ya no hay NullPointerException (eso lo cubren las
 * columnas NOT NULL de esas filas), pero no hay nada que mostrar.
 *
 * Reutiliza el mismo camino que el flujo real de publicación de un asset
 * (putPrivateObject + makeObjectPublic) en vez de escribir directo al prefijo
 * público, para no tener que tocar R2Service por esto.
 *
 * Solo corre con el perfil beta/demo; no toca producción ni dev.
 */
@Component
@Profile({ "beta", "demo" })
@RequiredArgsConstructor
@Slf4j
public class BetaPlaceholderAssetSeeder implements CommandLineRunner {

    private final R2Service r2Service;

    private record PlaceholderAsset(String objectKey, String label, Color color) {
    }

    private static final List<PlaceholderAsset> ASSETS = List.of(
            new PlaceholderAsset("ads/beta/desayunos-con-sabor-a-hogar.jpg",
                    "Desayunos con sabor a hogar\nTrigo Dorado", new Color(0xB5651D)),
            new PlaceholderAsset("ads/beta/pan-artesanal-para-compartir.jpg",
                    "Pan artesanal para compartir\nTrigo Dorado", new Color(0xA85C2E)),
            new PlaceholderAsset("ads/beta/estilo-urbano-para-todos-los-dias.jpg",
                    "Estilo urbano para todos los días\nModa Urbana", new Color(0xB23A6B)),
            new PlaceholderAsset("ads/beta/renueva-tus-basicos-con-moda-urbana.jpg",
                    "Renueva tus básicos\nModa Urbana", new Color(0x8E2F57)),
            new PlaceholderAsset("ads/beta/tecnologia-util-para-una-vida-mas-simple.jpg",
                    "Tecnología útil para el hogar\nEcosistema Andino", new Color(0x2E7D5C)),
            new PlaceholderAsset("ads/beta/planea-tu-proxima-escapada-natural.jpg",
                    "Planea tu escapada natural\nEcosistema Andino", new Color(0x256B4E)),
            new PlaceholderAsset("products/beta/tarjeta-digital-moda-urbana.jpg",
                    "Tarjeta digital\nModa Urbana", new Color(0xB23A6B)),
            new PlaceholderAsset("products/beta/caja-panaderia-trigo-dorado.jpg",
                    "Caja de panadería artesanal\nTrigo Dorado", new Color(0xB5651D)),
            new PlaceholderAsset("raffles/beta/mercado-local-sabores-de-colombia.jpg",
                    "Mercado local: sabores de Colombia", new Color(0x2E6F95)),
            new PlaceholderAsset("raffles/beta/tecnologia-para-un-hogar-mas-eficiente.jpg",
                    "Tecnología para un hogar más eficiente", new Color(0x1F5A7D)),
            new PlaceholderAsset("raffles/beta/experiencia-de-cafe-de-altura.jpg",
                    "Experiencia de café de altura", new Color(0x184863)),
            new PlaceholderAsset("prizes/beta/bono-mercado-local.jpg",
                    "Bono de mercado local\n$250.000 COP", new Color(0xC9972C)),
            new PlaceholderAsset("prizes/beta/kit-solar-balcon.jpg",
                    "Kit solar de balcón", new Color(0xB8862A)),
            new PlaceholderAsset("prizes/beta/experiencia-cafe-para-dos.jpg",
                    "Experiencia de café para dos", new Color(0xA67323)),
            new PlaceholderAsset("categories/beta/videojuegos.jpg",
                    "Videojuegos", new Color(0x5B3A8E)),
            new PlaceholderAsset("categories/beta/supermercados.jpg",
                    "Supermercados", new Color(0xC24D2C)),
            new PlaceholderAsset("categories/beta/musica.jpg",
                    "Música", new Color(0x1F7A72)),
            new PlaceholderAsset("categories/beta/entretenimiento.jpg",
                    "Entretenimiento", new Color(0xA23E6F)),
            new PlaceholderAsset("categories/beta/fitness.jpg",
                    "Fitness", new Color(0x4C7A1F)),
            new PlaceholderAsset("categories/beta/moda-y-accesorios.jpg",
                    "Moda y accesorios", new Color(0xB23A6B)),
            new PlaceholderAsset("categories/beta/hogar-y-tecnologia.jpg",
                    "Hogar y tecnología", new Color(0x2E7D5C)));

    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;

    @Override
    public void run(String... args) {
        int uploaded = 0;
        for (PlaceholderAsset asset : ASSETS) {
            if (r2Service.objectExists("public/" + asset.objectKey())) {
                continue;
            }
            byte[] jpeg = render(asset.label(), asset.color());
            r2Service.putPrivateObject(asset.objectKey(), jpeg, "image/jpeg");
            r2Service.makeObjectPublic(asset.objectKey());
            uploaded++;
        }
        log.info("Placeholders beta subidos a R2: {} nuevos de {} esperados", uploaded, ASSETS.size());
    }

    private static byte[] render(String label, Color background) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(background);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 40));
            FontMetrics fm = g.getFontMetrics();
            List<String> lines = wrap(label, fm, WIDTH - 120);
            int totalHeight = lines.size() * fm.getHeight();
            int y = (HEIGHT - totalHeight) / 2 + fm.getAscent();
            for (String line : lines) {
                int textWidth = fm.stringWidth(line);
                g.drawString(line, (WIDTH - textWidth) / 2, y);
                y += fm.getHeight();
            }

            g.setFont(new Font("SansSerif", Font.PLAIN, 20));
            FontMetrics small = g.getFontMetrics();
            String watermark = "VeryGana · imagen de demostración (beta)";
            g.drawString(watermark, (WIDTH - small.stringWidth(watermark)) / 2, HEIGHT - 30);
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la imagen placeholder beta", e);
        }
    }

    /** Envuelve cada línea de {@code text} (separadas por \n) para que no se corte el ancho de la imagen. */
    private static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> result = new ArrayList<>();
        for (String rawLine : text.split("\n")) {
            StringBuilder current = new StringBuilder();
            for (String word : rawLine.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (fm.stringWidth(candidate) > maxWidth && !current.isEmpty()) {
                    result.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                result.add(current.toString());
            }
        }
        return result;
    }
}
