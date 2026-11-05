package app;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.Timer;

final class Ui {
    static final Color ACCENT = new Color(0xFF7A1A), ACCENT2 = new Color(0xFF4A12), TEXT = new Color(0xFFFFFF),
            MUTED = new Color(0xB4BDD3), HEAD = new Color(0xB7C6FF), OFF = new Color(0x3A3F52),
            BORDER = new Color(0x4A5370);

    static final String[] LANG_CODES = {"fa", "en", "de", "ru"};
    static final String[] LANG_EN = {"Persian", "English", "German", "Russian"};
    static final String[] LANG_NATIVE = {"\u0641\u0627\u0631\u0633\u06CC", "English", "Deutsch", "\u0420\u0443\u0441\u0441\u043A\u0438\u0439"};

    private Ui() {}

    private static final BufferedImage LOGO = loadImage("/logo.png"), PLATE = loadImage("/plate.jpg");
    private static final java.util.Map<Integer, BufferedImage> LOGOS = new java.util.HashMap<>();

    private static BufferedImage loadImage(String path) {
        try (java.io.InputStream in = Ui.class.getResourceAsStream(path)) {
            return in == null ? null : javax.imageio.ImageIO.read(in);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static BufferedImage resize(BufferedImage src, int s) {
        BufferedImage out = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, s, s, null);
        g.dispose();
        return out;
    }

    static synchronized BufferedImage logo(int size) {
        if (LOGO == null) return null;
        BufferedImage cached = LOGOS.get(size);
        if (cached == null) {
            BufferedImage cur = LOGO;
            while (cur.getWidth() / 2 >= size) cur = resize(cur, cur.getWidth() / 2);
            cached = cur.getWidth() == size ? cur : resize(cur, size);
            LOGOS.put(size, cached);
        }
        return cached;
    }

    static void drawLogo(Graphics2D g, double x, double y, int size) {
        BufferedImage img = logo(size);
        if (img != null) g.drawImage(img, (int) Math.round(x), (int) Math.round(y), null);
    }

    static void drawPlate(Graphics2D g, int w, int h, double focusY) {
        if (PLATE == null) return;
        double sc = Math.max(w / (double) PLATE.getWidth(), h / (double) PLATE.getHeight());
        int dw = (int) Math.ceil(PLATE.getWidth() * sc), dh = (int) Math.ceil(PLATE.getHeight() * sc);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(PLATE, 0, -(int) ((dh - h) * focusY), dw, dh, null);
    }

    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    static Font font(int style, float size) { return new Font("Segoe UI", style, 12).deriveFont(style, size); }

    static String nativeName(String code) {
        for (int i = 0; i < LANG_CODES.length; i++) if (LANG_CODES[i].equals(code)) return LANG_NATIVE[i];
        return code;
    }

    static String langName(String code) {
        for (int i = 0; i < LANG_CODES.length; i++) if (LANG_CODES[i].equals(code)) return LANG_EN[i];
        return code;
    }

    static Color mix(Color a, Color b, float t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static JComponent section(String text) {
        JComponent c = new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(200, 34); }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 34); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                aa(g2);
                g2.setColor(ACCENT);
                g2.fillRoundRect(2, 9, 3, 17, 3, 3);
                g2.setFont(font(Font.BOLD, 14f));
                g2.setColor(HEAD);
                StringBuilder sp = new StringBuilder();
                for (char ch : text.toUpperCase().toCharArray()) sp.append(ch);
                g2.drawString(sp.toString(), 16, 23);
                g2.dispose();
            }
        };
        c.setAlignmentX(0f);
        return c;
    }

    static Component gap(int h) {
        JComponent c = (JComponent) Box.createRigidArea(new Dimension(0, h));
        c.setAlignmentX(0f);
        return c;
    }

    static final class Note extends JComponent {
        private record Word(String text, boolean bold, boolean glue) {}

        private final List<Word> words = new ArrayList<>();
        private final int baseWidth;
        private int laidOutHeight;
        private boolean glueNext, sawSpace = true;
        private final Font plain = font(Font.PLAIN, 12.5f), bold = font(Font.BOLD, 12.5f);

        Note(String html, int baseWidth) {
            this.baseWidth = baseWidth;
            setAlignmentX(0f);
            boolean b = false;
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < html.length(); i++) {
                char c = html.charAt(i);
                if (c == '<') {
                    int e = html.indexOf('>', i);
                    String tag = html.substring(i + 1, e).toLowerCase();
                    flush(cur, b);
                    if (tag.equals("b")) b = true;
                    else if (tag.equals("/b")) b = false;
                    else if (tag.equals("br")) words.add(new Word("\n", false, false));
                    i = e;
                } else if (c == ' ') {
                    flush(cur, b);
                    sawSpace = true;
                } else {
                    if (cur.length() == 0) glueNext = !sawSpace && !words.isEmpty() && !words.get(words.size() - 1).text().equals("\n");
                    cur.append(c);
                    sawSpace = false;
                }
            }
            flush(cur, b);
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentResized(java.awt.event.ComponentEvent e) {
                    if (heightFor(getWidth()) != laidOutHeight) revalidate();
                }
            });
        }

        private void flush(StringBuilder cur, boolean b) {
            if (cur.length() > 0) { words.add(new Word(cur.toString(), b, glueNext)); cur.setLength(0); }
        }

        private int heightFor(int width) {
            if (width <= 0) width = baseWidth;
            FontMetrics fp = getFontMetrics(plain), fb = getFontMetrics(bold);
            int lineH = fp.getHeight() + 3, x = 0, lines = 1, space = fp.charWidth(' ');
            for (Word w : words) {
                if (w.text().equals("\n")) { lines++; x = 0; continue; }
                int ww = (w.bold() ? fb : fp).stringWidth(w.text());
                if (x > 0 && !w.glue()) x += space;
                if (x > 0 && x + ww > width) { lines++; x = 0; }
                x += ww;
            }
            laidOutHeight = lines * lineH;
            return laidOutHeight;
        }

        @Override public Dimension getPreferredSize() {
            int w = getParent() != null && getParent().getWidth() > 40 && getWidth() > 0 ? getWidth() : baseWidth;
            return new Dimension(w, heightFor(w));
        }

        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            FontMetrics fp = g2.getFontMetrics(plain), fb = g2.getFontMetrics(bold);
            int width = getWidth(), lineH = fp.getHeight() + 3, x = 0, y = fp.getAscent(), space = fp.charWidth(' ');
            for (Word w : words) {
                if (w.text().equals("\n")) { y += lineH; x = 0; continue; }
                FontMetrics fm = w.bold() ? fb : fp;
                int ww = fm.stringWidth(w.text());
                if (x > 0 && !w.glue()) x += space;
                if (x > 0 && x + ww > width) { y += lineH; x = 0; }
                g2.setFont(w.bold() ? bold : plain);
                g2.setColor(w.bold() ? TEXT : MUTED);
                g2.drawString(w.text(), x, y);
                x += ww;
            }
            g2.dispose();
        }
    }

    static JComponent note(String html, int width) { return new Note(html, width); }

    static final class Switch extends JComponent {
        private boolean on;
        private float pos;
        private final List<Consumer<Boolean>> listeners = new ArrayList<>();
        private final Timer anim;
        private final boolean small;

        Switch(boolean on) { this(on, false); }

        Switch(boolean on, boolean small) {
            this.on = on;
            this.small = small;
            this.pos = on ? 1f : 0f;
            Dimension size = small ? new Dimension(46, 28) : new Dimension(52, 30);
            setPreferredSize(size);
            setMinimumSize(size);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);
            anim = new Timer(15, e -> {
                float target = this.on ? 1f : 0f;
                pos += (target - pos) * 0.35f;
                if (Math.abs(target - pos) < 0.02f) { pos = target; ((Timer) e.getSource()).stop(); }
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseReleased(MouseEvent e) { if (contains(e.getPoint())) toggle(); }
            });
            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle");
            getActionMap().put("toggle", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { toggle(); }
            });
        }

        boolean isOn() { return on; }

        void onChange(Consumer<Boolean> c) { listeners.add(c); }

        void toggle() { setOn(!on, true); }

        void setOn(boolean v, boolean fire) {
            if (v == on) return;
            on = v;
            anim.start();
            if (fire) for (Consumer<Boolean> c : listeners) c.accept(on);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = small ? 42 : 48, h = small ? 24 : 28, x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
            g2.setPaint(new GradientPaint(x, y, mix(OFF, new Color(0xFF6A1A), pos), x + w, y, mix(OFF, new Color(0xFF9A2E), pos)));
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setColor(alpha(Color.WHITE, 40 + (int) (pos * 40)));
            g2.drawRoundRect(x, y, w - 1, h - 1, h, h);
            int d = small ? 18 : 22, kx = x + 3 + Math.round(pos * (w - d - 6));
            g2.setColor(new Color(0, 0, 0, 70));
            g2.fillOval(kx, y + 4, d, d);
            g2.setColor(new Color(0xF4F8FF));
            g2.fillOval(kx, y + 3, d, d);
            if (isFocusOwner()) {
                g2.setColor(alpha(ACCENT, 130));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(x - 2, y - 2, w + 3, h + 3, h + 4, h + 4);
            }
            g2.dispose();
        }
    }

    static class Card extends JPanel {
        Card(int pad) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(pad, pad + 4, pad, pad + 4));
            setAlignmentX(0f);
        }

        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            RoundRectangle2D.Float r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 24, 24);
            g2.setPaint(new GradientPaint(0, 0, new Color(0x121216, false), 0, getHeight(), new Color(0x08080B, false)));
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.96f));
            g2.fill(r);
            g2.setComposite(java.awt.AlphaComposite.SrcOver);
            g2.setPaint(new GradientPaint(0, 0, alpha(BORDER, 200), 0, getHeight(), alpha(BORDER, 90)));
            g2.draw(r);
            g2.dispose();
        }
    }

    static JComponent iconTile(String icon) { return iconTile(icon, 50); }

    static JComponent iconTile(String icon, int size) {
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                aa(g2);
                RoundRectangle2D.Float r = new RoundRectangle2D.Float(0.5f, 0.5f, size - 1, size - 1, 16, 16);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x1F1F28), 0, size, new Color(0x121217)));
                g2.fill(r);
                g2.setColor(alpha(BORDER, 150));
                g2.draw(r);
                icon(g2, icon, size * 0.22, size * 0.22, size * 0.56, new Color(0xFF8A2B));
                g2.dispose();
            }
        };
        c.setPreferredSize(new Dimension(size, size));
        c.setMinimumSize(new Dimension(size, size));
        return c;
    }

    static JPanel switchRow(String icon, String title, String subtitle, Switch sw) { return switchRow(icon, title, subtitle, sw, false); }

    static JPanel switchRow(String icon, String title, String subtitle, Switch sw, boolean compact) {
        JPanel row = new JPanel(new BorderLayout(compact ? 8 : 12, 0));
        row.setOpaque(false);
        row.setAlignmentX(0f);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(font(Font.BOLD, compact ? 13f : 14f));
        t.setForeground(TEXT);
        text.add(Box.createVerticalGlue());
        text.add(t);
        if (subtitle != null) {
            JLabel s = new JLabel(subtitle);
            s.setFont(font(Font.PLAIN, compact ? 11f : 12f));
            s.setForeground(MUTED);
            text.add(s);
        }
        text.add(Box.createVerticalGlue());
        if (icon != null) row.add(iconTile(icon, compact ? 40 : 50), BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        row.add(sw, BorderLayout.EAST);
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 4));
        return row;
    }

    static final class Pill extends JButton {
        private final boolean primary;
        private final String icon;
        private boolean hover, small;

        Pill(String text, String icon, boolean primary) {
            super(text);
            this.primary = primary;
            this.icon = icon;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(font(Font.BOLD, 13f));
            setForeground(Color.WHITE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        Pill small() {
            small = true;
            setFont(font(Font.BOLD, 12f));
            return this;
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + (small ? 24 : 34) + (icon != null ? 24 : 0), small ? 30 : 38);
        }

        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = getWidth() - 1, h = getHeight() - 1;
            boolean en = isEnabled();
            if (primary) {
                g2.setPaint(new GradientPaint(0, 0, hover ? new Color(0xFF9A4D) : ACCENT, w, 0, hover ? new Color(0xFF6B3D) : ACCENT2));
                g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, en ? 1f : 0.35f));
                g2.fillRoundRect(0, 0, w, h, h, h);
            } else {
                g2.setColor(alpha(hover ? new Color(0x2E2E3A) : new Color(0x1A1A22), en ? 230 : 120));
                g2.fillRoundRect(0, 0, w, h, h, h);
                g2.setColor(hover && en ? ACCENT : alpha(new Color(0x6A7396), en ? 240 : 90));
                g2.drawRoundRect(0, 0, w, h, h, h);
            }
            g2.setComposite(java.awt.AlphaComposite.SrcOver);
            FontMetrics fm = g2.getFontMetrics(getFont());
            int tw = fm.stringWidth(getText()), iw = icon != null ? 24 : 0;
            int x = (getWidth() - tw - iw) / 2;
            Color fg = en ? Color.WHITE : alpha(Color.WHITE, 120);
            if (icon != null) { icon(g2, icon, x, (getHeight() - 18) / 2.0, 18, fg); x += iw; }
            g2.setFont(getFont());
            g2.setColor(fg);
            g2.drawString(getText(), x, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    static Pill pill(String text, String icon, boolean primary) { return new Pill(text, icon, primary); }

    static final class Hero extends JComponent {
        private final String sub;

        Hero(String sub) {
            this.sub = sub;
            setAlignmentX(0f);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(300, 150); }

        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 150); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = getWidth() - 1, h = getHeight() - 1;
            RoundRectangle2D.Float r = new RoundRectangle2D.Float(0.5f, 0.5f, w, h, 28, 28);
            g2.setClip(r);
            drawPlate(g2, w + 1, h + 1, 0.4);
            g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 130), w * 0.7f, 0, new Color(0, 0, 0, 0)));
            g2.fillRect(0, 0, w + 1, h + 1);
            g2.setClip(null);
            g2.setColor(alpha(ACCENT, 130));
            g2.draw(r);
            int logo = 92;
            drawLogo(g2, 22, (h - logo) / 2.0 - 2, logo);
            int tx = 22 + logo + 14;
            glowText(g2, "FireFix", tx, h / 2 + 8, font(Font.BOLD, 54f));
            g2.setFont(font(Font.PLAIN, 15f));
            g2.setColor(new Color(0xF6ECE6));
            g2.drawString(sub, tx + 2, h / 2 + 42);
            g2.dispose();
        }
    }

    static final class Banner extends JComponent {
        private final String title, sub;
        private final int height;

        Banner(String title, String sub, int height) {
            this.title = title;
            this.sub = sub;
            this.height = height;
            setAlignmentX(0f);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(300, height); }

        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, height); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int w = getWidth() - 1, h = getHeight() - 1;
            RoundRectangle2D.Float r = new RoundRectangle2D.Float(0.5f, 0.5f, w, h, 28, 28);
            g2.setClip(r);
            drawPlate(g2, w + 1, h + 1, 0.3);
            g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 130), w * 0.7f, 0, new Color(0, 0, 0, 0)));
            g2.fillRect(0, 0, w + 1, h + 1);
            g2.setClip(null);
            g2.setColor(alpha(ACCENT, 130));
            g2.draw(r);
            int logo = Math.min(h - 20, 80);
            drawLogo(g2, 18, (h - logo) / 2.0, logo);
            int tx = 18 + logo + 14;
            int ty = sub == null ? h / 2 + 12 : h / 2 - 2;
            glowText(g2, title, tx, ty, font(Font.BOLD, h > 100 ? 36f : 28f));
            if (sub != null) {
                g2.setFont(font(Font.PLAIN, 14f));
                g2.setColor(new Color(0xF6ECE6));
                g2.drawString(sub, tx + 2, ty + 26);
            }
            g2.dispose();
        }
    }

    static void glowText(Graphics2D g, String text, int x, int y, Font f) {
        g.setFont(f);
        for (int i = 1; i <= 4; i++) {
            g.setColor(new Color(255, 140, 40, 20));
            g.drawString(text, x - i, y);
            g.drawString(text, x + i, y);
            g.drawString(text, x, y - i);
            g.drawString(text, x, y + i);
        }
        g.setColor(Color.WHITE);
        g.drawString(text, x, y);
    }

    static JPanel linkCard(String icon, Color tint, String title, String subtitle, JButton... buttons) {
        Card card = new Card(14);
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        row.setAlignmentX(0f);
        JComponent badge = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                aa(g2);
                g2.setPaint(new GradientPaint(0, 0, tint.brighter(), 0, 44, tint.darker()));
                g2.fillOval(0, 0, 44, 44);
                g2.setColor(alpha(Color.WHITE, 70));
                g2.drawOval(0, 0, 43, 43);
                icon(g2, icon, 9, 9, 26, Color.WHITE);
                g2.dispose();
            }
        };
        badge.setPreferredSize(new Dimension(44, 44));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(font(Font.BOLD, 15f));
        t.setForeground(TEXT);
        JLabel s = new JLabel(subtitle);
        s.setFont(font(Font.PLAIN, 12.5f));
        s.setForeground(MUTED);
        text.add(Box.createVerticalGlue());
        text.add(t);
        text.add(s);
        text.add(Box.createVerticalGlue());
        JPanel btns = new JPanel(new GridLayout(1, buttons.length, 6, 0));
        btns.setOpaque(false);
        for (JButton b : buttons) btns.add(b);
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(btns, BorderLayout.CENTER);
        row.add(badge, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        card.add(row);
        return card;
    }

    private static final class LangCard extends JComponent {
        private final String code, nativeName, engName;
        private boolean selected;

        LangCard(String code, String nativeName, String engName) {
            this.code = code;
            this.nativeName = nativeName;
            this.engName = engName;
            setPreferredSize(new Dimension(196, 92));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            RoundRectangle2D.Float r = new RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 3, 22, 22);
            g2.setPaint(new GradientPaint(0, 0, selected ? new Color(0x3C2112) : new Color(0x17171D), 0, getHeight(), selected ? new Color(0x26140A) : new Color(0x0C0C10)));
            g2.fill(r);
            g2.setColor(selected ? ACCENT : alpha(BORDER, 90));
            g2.setStroke(new BasicStroke(selected ? 2.4f : 1.3f));
            g2.draw(r);
            flag(g2, code, 16, 16, 34);
            g2.setColor(TEXT);
            g2.setFont(font(Font.BOLD, 20f));
            g2.drawString(nativeName, 62, 40);
            g2.setColor(MUTED);
            g2.setFont(font(Font.PLAIN, 13f));
            g2.drawString(engName, 63, 62);
            if (selected) {
                g2.setColor(ACCENT);
                g2.fillOval(getWidth() - 32, 12, 20, 20);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D chk = new Path2D.Float();
                chk.moveTo(getWidth() - 27, 22);
                chk.lineTo(getWidth() - 23, 26);
                chk.lineTo(getWidth() - 17, 18);
                g2.draw(chk);
            }
            g2.dispose();
        }
    }

    static String[] pickLanguages(Window owner, String[] initial, boolean firstRun) {
        final String[][] result = {null};
        final Deque<String> chosen = new ArrayDeque<>();
        if (initial != null) for (String s : initial) chosen.add(s);

        JDialog d = new JDialog(owner, "FireFix", JDialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        Space root = new Space(null);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));

        root.add(new Banner(firstRun ? "Welcome" : "Languages", firstRun ? "Quick setup" : null, firstRun ? 96 : 70));
        root.add(gap(16));
        JLabel head = new JLabel("Pick your two languages");
        head.setFont(font(Font.BOLD, 17f));
        head.setForeground(TEXT);
        head.setAlignmentX(0f);
        root.add(head);
        root.add(gap(6));
        root.add(note("You can change them later.", 420));
        root.add(gap(14));

        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));
        grid.setOpaque(false);
        grid.setAlignmentX(0f);
        List<LangCard> cards = new ArrayList<>();
        for (int i = 0; i < LANG_CODES.length; i++) {
            LangCard c = new LangCard(LANG_CODES[i], LANG_NATIVE[i], LANG_EN[i]);
            cards.add(c);
            grid.add(c);
        }
        root.add(grid);
        root.add(gap(16));

        JLabel count = new JLabel();
        count.setFont(font(Font.PLAIN, 13f));
        count.setForeground(MUTED);
        Pill ok = pill("Continue", null, true);
        Runnable refresh = () -> {
            for (LangCard c : cards) { c.selected = chosen.contains(c.code); c.repaint(); }
            count.setText(chosen.size() + " of 2 selected");
            ok.setEnabled(chosen.size() == 2);
        };
        for (LangCard c : cards) {
            c.addMouseListener(new MouseAdapter() {
                @Override public void mouseReleased(MouseEvent e) {
                    if (!c.contains(e.getPoint())) return;
                    if (chosen.contains(c.code)) chosen.remove(c.code);
                    else { if (chosen.size() == 2) chosen.removeFirst(); chosen.add(c.code); }
                    refresh.run();
                }
            });
        }
        ok.addActionListener(e -> {
            result[0] = chosen.toArray(new String[0]);
            d.dispose();
        });
        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.setAlignmentX(0f);
        foot.add(count, BorderLayout.WEST);
        foot.add(ok, BorderLayout.EAST);
        root.add(foot);
        refresh.run();

        d.setContentPane(root);
        d.getRootPane().setDefaultButton(ok);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return result[0];
    }

    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    static void icon(Graphics2D g0, String name, double x, double y, double size, Color c) {
        Graphics2D g = (Graphics2D) g0.create();
        aa(g);
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Double();
        switch (name) {
            case "home":
                p.moveTo(3, 11.5); p.lineTo(12, 4); p.lineTo(21, 11.5);
                p.moveTo(5.5, 10); p.lineTo(5.5, 20); p.lineTo(18.5, 20); p.lineTo(18.5, 10);
                p.moveTo(10, 20); p.lineTo(10, 14); p.lineTo(14, 14); p.lineTo(14, 20);
                g.draw(p);
                break;
            case "sounds":
                p.moveTo(4, 9.5); p.lineTo(8, 9.5); p.lineTo(13, 5); p.lineTo(13, 19); p.lineTo(8, 14.5); p.lineTo(4, 14.5); p.closePath();
                g.draw(p);
                g.draw(new Arc2D.Double(8, 7, 10, 10, -50, 100, Arc2D.OPEN));
                g.draw(new Arc2D.Double(5, 4, 16, 16, -45, 90, Arc2D.OPEN));
                break;
            case "convert":
                p.moveTo(4, 8); p.lineTo(19, 8); p.moveTo(15, 4.5); p.lineTo(19, 8); p.lineTo(15, 11.5);
                p.moveTo(20, 16); p.lineTo(5, 16); p.moveTo(9, 12.5); p.lineTo(5, 16); p.lineTo(9, 19.5);
                g.draw(p);
                break;
            case "sparkle":
                p.moveTo(12, 2); p.quadTo(13, 11, 22, 12); p.quadTo(13, 13, 12, 22); p.quadTo(11, 13, 2, 12); p.quadTo(11, 11, 12, 2); p.closePath();
                g.fill(p);
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(p);
                break;
            case "talk":
                g.draw(new RoundRectangle2D.Double(9, 3, 6, 11, 6, 6));
                g.draw(new Arc2D.Double(5.5, 6, 13, 13, 180, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(12, 19.5, 12, 22));
                g.draw(new Line2D.Double(8.5, 22, 15.5, 22));
                break;
            case "keyboard":
                g.draw(new RoundRectangle2D.Double(2, 6, 20, 12, 3.5, 3.5));
                for (int i = 0; i < 5; i++) g.fill(new RoundRectangle2D.Double(5 + i * 3.2, 9, 1.8, 1.8, 0.8, 0.8));
                for (int i = 0; i < 4; i++) g.fill(new RoundRectangle2D.Double(6.6 + i * 3.2, 12, 1.8, 1.8, 0.8, 0.8));
                g.draw(new Line2D.Double(8, 15.6, 16, 15.6));
                break;
            case "globe":
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.draw(new Ellipse2D.Double(8, 3, 8, 18));
                g.draw(new Line2D.Double(3, 12, 21, 12));
                g.draw(new Line2D.Double(4.6, 7.5, 19.4, 7.5));
                g.draw(new Line2D.Double(4.6, 16.5, 19.4, 16.5));
                break;
            case "aa":
                g.setFont(new Font("Segoe UI", Font.BOLD, 1).deriveFont(Font.BOLD, 15f));
                FontMetrics fm = g.getFontMetrics();
                g.drawString("Aa", (24 - fm.stringWidth("Aa")) / 2f, 17.5f);
                break;
            case "close":
                g.draw(new Line2D.Double(6, 6, 18, 18));
                g.draw(new Line2D.Double(18, 6, 6, 18));
                break;
            case "minimize":
                g.draw(new Line2D.Double(6, 17, 18, 17));
                break;
            case "maximize":
                g.draw(new RoundRectangle2D.Double(6, 6, 12, 12, 2, 2));
                break;
            case "restore":
                g.draw(new RoundRectangle2D.Double(5, 9, 10, 10, 2, 2));
                p.moveTo(9, 9); p.lineTo(9, 6.5); p.quadTo(9, 5, 10.5, 5); p.lineTo(17.5, 5); p.quadTo(19, 5, 19, 6.5); p.lineTo(19, 13.5);
                p.quadTo(19, 15, 17.5, 15); p.lineTo(15, 15);
                g.draw(p);
                break;
            case "mail":
                g.draw(new RoundRectangle2D.Double(3, 5.5, 18, 13, 3.5, 3.5));
                p.moveTo(4, 7.5); p.lineTo(12, 13.5); p.lineTo(20, 7.5);
                g.draw(p);
                break;
            case "plane":
                p.moveTo(2.5, 11); p.lineTo(21, 3.5); p.lineTo(17.5, 20.5); p.lineTo(12, 15.5); p.lineTo(9, 19); p.lineTo(8.5, 13.5); p.closePath();
                g.fill(p);
                break;
            case "code":
                p.moveTo(8, 6.5); p.lineTo(2.8, 12); p.lineTo(8, 17.5);
                p.moveTo(16, 6.5); p.lineTo(21.2, 12); p.lineTo(16, 17.5);
                p.moveTo(13.5, 4.5); p.lineTo(10.5, 19.5);
                g.draw(p);
                break;
            default:
                g.draw(new Ellipse2D.Double(4, 4, 16, 16));
        }
        g.dispose();
    }

    static void flag(Graphics2D g0, String lang, int x, int y, int d) {
        Graphics2D g = (Graphics2D) g0.create();
        aa(g);
        g.translate(x, y);
        g.setClip(new Ellipse2D.Double(0, 0, d, d));
        switch (lang) {
            case "fa":
                g.setColor(new Color(0x239F40)); g.fillRect(0, 0, d, d / 3 + 1);
                g.setColor(Color.WHITE); g.fillRect(0, d / 3, d, d / 3 + 1);
                g.setColor(new Color(0xDA0000)); g.fillRect(0, 2 * d / 3, d, d / 3 + 1);
                g.setColor(new Color(0xDA0000)); g.setStroke(new BasicStroke(Math.max(1.2f, d / 16f)));
                g.draw(new Ellipse2D.Double(d * 0.38, d * 0.38, d * 0.24, d * 0.24));
                break;
            case "en":
                g.setColor(new Color(0x012169)); g.fillRect(0, 0, d, d);
                g.setColor(Color.WHITE); g.setStroke(new BasicStroke(d * 0.22f));
                g.draw(new Line2D.Double(0, 0, d, d)); g.draw(new Line2D.Double(d, 0, 0, d));
                g.setColor(new Color(0xC8102E)); g.setStroke(new BasicStroke(d * 0.09f));
                g.draw(new Line2D.Double(0, 0, d, d)); g.draw(new Line2D.Double(d, 0, 0, d));
                g.setColor(Color.WHITE); g.setStroke(new BasicStroke(d * 0.36f));
                g.draw(new Line2D.Double(d / 2.0, 0, d / 2.0, d)); g.draw(new Line2D.Double(0, d / 2.0, d, d / 2.0));
                g.setColor(new Color(0xC8102E)); g.setStroke(new BasicStroke(d * 0.2f));
                g.draw(new Line2D.Double(d / 2.0, 0, d / 2.0, d)); g.draw(new Line2D.Double(0, d / 2.0, d, d / 2.0));
                break;
            case "de":
                g.setColor(Color.BLACK); g.fillRect(0, 0, d, d / 3 + 1);
                g.setColor(new Color(0xDD0000)); g.fillRect(0, d / 3, d, d / 3 + 1);
                g.setColor(new Color(0xFFCE00)); g.fillRect(0, 2 * d / 3, d, d / 3 + 1);
                break;
            default:
                g.setColor(Color.WHITE); g.fillRect(0, 0, d, d / 3 + 1);
                g.setColor(new Color(0x0039A6)); g.fillRect(0, d / 3, d, d / 3 + 1);
                g.setColor(new Color(0xD52B1E)); g.fillRect(0, 2 * d / 3, d, d / 3 + 1);
        }
        g.setClip(null);
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Double(0.6, 0.6, d - 1.2, d - 1.2));
        g.dispose();
    }

    private static void glow(Graphics2D g, double cx, double cy, double r, Color c, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) r, new float[]{0f, 1f},
                new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    static BufferedImage fire(int w, int h) {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        aa(g);
        g.setPaint(new GradientPaint(0, 0, new Color(0x000000), 0, h, new Color(0x060608)));
        g.fillRect(0, 0, w, h);
        glow(g, w * 0.95, h * 0.9, 330, new Color(0xFF6A1A), 80);

        Path2D wave = new Path2D.Double();
        wave.moveTo(w, h * 0.58);
        wave.curveTo(w * 0.88, h * 0.78, w * 0.72, h * 0.90, w * 0.46, h);
        wave.lineTo(w, h);
        wave.closePath();
        g.setPaint(new GradientPaint((float) (w * 0.95), (float) (h * 0.65), new Color(255, 120, 30, 150), (float) (w * 0.5), (float) h, new Color(255, 70, 10, 0)));
        g.fill(wave);
        Path2D wave2 = new Path2D.Double();
        wave2.moveTo(w, h * 0.72);
        wave2.curveTo(w * 0.9, h * 0.86, w * 0.8, h * 0.93, w * 0.62, h);
        wave2.lineTo(w, h);
        wave2.closePath();
        g.setPaint(new GradientPaint((float) w, (float) (h * 0.74), new Color(255, 170, 60, 170), (float) (w * 0.6), (float) h, new Color(255, 90, 20, 0)));
        g.fill(wave2);
        g.setStroke(new BasicStroke(2f));
        g.setPaint(new GradientPaint((float) w, (float) (h * 0.58), new Color(255, 190, 90, 200), (float) (w * 0.46), (float) h, new Color(255, 120, 30, 0)));
        Path2D edge = new Path2D.Double();
        edge.moveTo(w, h * 0.58);
        edge.curveTo(w * 0.88, h * 0.78, w * 0.72, h * 0.90, w * 0.46, h);
        g.draw(edge);

        Random r = new Random(5);
        for (int i = 0; i < 40; i++) {
            double ex = w * (0.55 + r.nextDouble() * 0.45), ey = h * (0.55 + r.nextDouble() * 0.4);
            g.setColor(new Color(255, 170 + r.nextInt(60), 70, 40 + r.nextInt(120)));
            double sz = 1 + r.nextDouble() * 1.8;
            g.fill(new Ellipse2D.Double(ex, ey, sz, sz));
        }
        g.dispose();
        return img;
    }

    static class Space extends JPanel {
        private BufferedImage cache;

        Space(LayoutManager lm) { super(lm); }

        @Override protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            if (cache == null || cache.getWidth() != w || cache.getHeight() != h) cache = fire(w, h);
            g.drawImage(cache, 0, 0, null);
        }
    }

    static final class Overlay {
        static final Color EN = new Color(0x1E88E5), FA = new Color(0x7A4DFF), RU = new Color(0xF57C00),
                DE = new Color(0xC62828), OTHER = new Color(0x757575);

        volatile boolean badgeOn = true;

        private final JWindow badge = new JWindow();
        private final Tag tag = new Tag();
        private final Timer hide;
        private boolean styled;

        Overlay() {
            badge.setAlwaysOnTop(true);
            badge.setFocusableWindowState(false);
            badge.setBackground(new Color(0, 0, 0, 0));
            badge.setContentPane(new JPanel(new BorderLayout()));
            ((JComponent) badge.getContentPane()).setOpaque(false);
            badge.getContentPane().add(tag);
            hide = new Timer(1600, e -> badge.setVisible(false));
            hide.setRepeats(false);
        }

        static Color color(String l) {
            return switch (l) { case "fa" -> FA; case "en" -> EN; case "ru" -> RU; case "de" -> DE; default -> OTHER; };
        }

        static String code(String l) {
            return switch (l) { case "fa" -> "FA"; case "en" -> "EN"; case "ru" -> "RU"; case "de" -> "DE"; default -> "??"; };
        }

        void hideNow() {
            hide.stop();
            badge.setVisible(false);
        }

        void typing(String lang) {
            if (!badgeOn) return;
            Rectangle c = Layouts.caretOrMouse();
            tag.set(code(lang), color(lang));
            badge.pack();
            Rectangle all = new Rectangle();
            for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
                all = all.union(d.getDefaultConfiguration().getBounds());
            int x = Math.max(all.x, Math.min(c.x + 8, all.x + all.width - badge.getWidth()));
            int y = Math.max(all.y, Math.min(c.y - badge.getHeight() - 3, all.y + all.height - badge.getHeight()));
            badge.setLocation(x, y);
            if (!badge.isVisible()) badge.setVisible(true);
            if (!styled) {
                styled = true;
                Layouts.style(badge, true);
            }
            hide.restart();
        }

        private static final class Tag extends JComponent {
            private String text = "";
            private Color bg = Color.GRAY;
            private final Font font = font(Font.BOLD, 12f);

            void set(String t, Color c) {
                text = t;
                bg = c;
                FontMetrics fm = getFontMetrics(font);
                setPreferredSize(new Dimension(fm.stringWidth(t) + 18, fm.getHeight() + 6));
                repaint();
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                aa(g2);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(font);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }
    }
}
