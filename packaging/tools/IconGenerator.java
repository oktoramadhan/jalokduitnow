import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Draws the application icon and writes packaging/icons/app.png (1024 px), app.ico (Windows) and the iconset folder for macOS
 * (turn it into app.icns with: iconutil -c icns -o app.icns app.iconset).
 * Run: java packaging/tools/IconGenerator.java packaging/icons
 */
public class IconGenerator {
    static BufferedImage draw(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double s = size;
        g.setPaint(new GradientPaint(0, 0, new Color(0x4F8DF7), 0, size, new Color(0x2B5FD9)));
        g.fill(new RoundRectangle2D.Double(s * 0.06, s * 0.06, s * 0.88, s * 0.88, s * 0.22, s * 0.22));
        g.setColor(new Color(0xFFD54A));
        g.fill(new Ellipse2D.Double(s * 0.22, s * 0.22, s * 0.56, s * 0.56));
        g.setColor(new Color(0xF2B705));
        g.setStroke(new BasicStroke((float) (s * 0.025)));
        g.draw(new Ellipse2D.Double(s * 0.28, s * 0.28, s * 0.44, s * 0.44));
        g.setColor(new Color(0x2B5FD9));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (s * 0.34)));
        FontMetrics fm = g.getFontMetrics();
        String letter = "J";
        g.drawString(letter, (float) (s / 2 - fm.stringWidth(letter) / 2.0), (float) (s / 2 + (fm.getAscent() - fm.getDescent()) / 2.0));
        g.dispose();
        return img;
    }

    static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path dir = Path.of(args.length > 0 ? args[0] : "packaging/icons");
        Files.createDirectories(dir);
        Files.write(dir.resolve("app.png"), png(draw(1024)));

        // macOS iconset
        Path set = dir.resolve("app.iconset");
        Files.createDirectories(set);
        for (int base : new int[]{16, 32, 128, 256, 512}) {
            Files.write(set.resolve("icon_" + base + "x" + base + ".png"), png(draw(base)));
            Files.write(set.resolve("icon_" + base + "x" + base + "@2x.png"), png(draw(base * 2)));
        }

        // Windows .ico with PNG-compressed images
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        ByteBuffer header = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        byte[][] images = new byte[sizes.length][];
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            images[i] = png(draw(sizes[i]));
            header.put((byte) (sizes[i] == 256 ? 0 : sizes[i])).put((byte) (sizes[i] == 256 ? 0 : sizes[i]))
                    .put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32).putInt(images[i].length).putInt(offset);
            offset += images[i].length;
        }
        ByteArrayOutputStream ico = new ByteArrayOutputStream();
        ico.write(header.array());
        for (byte[] b : images) ico.write(b);
        Files.write(dir.resolve("app.ico"), ico.toByteArray());
        System.out.println("icons written to " + dir.toAbsolutePath());
    }
}
