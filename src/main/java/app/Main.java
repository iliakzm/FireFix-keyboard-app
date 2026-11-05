package app;

import com.formdev.flatlaf.FlatDarkLaf;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public class Main implements NativeKeyListener {
    static final String APP = "FireFix";
    static final String FAREWELL = "I hope you enjoyed it! :)";
    private static final int GUARD_PORT = 49731;
    static final String EMAIL = "iliyaKazemi599@gmail.com";
    static final String TELEGRAM = "@ilia_k2";
    static final String GITHUB_URL = "https://github.com/iliakzm";

    private static final String RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    static final String[] ACTIONS = {"Fix text", "Sounds on / off"};
    static final int[] DEF_MODS = {3, 3};
    static final int[] DEF_CODES = {NativeKeyEvent.VC_F9, NativeKeyEvent.VC_F10};
    static final int[] FKEYS = {NativeKeyEvent.VC_F1, NativeKeyEvent.VC_F2, NativeKeyEvent.VC_F3, NativeKeyEvent.VC_F4,
            NativeKeyEvent.VC_F5, NativeKeyEvent.VC_F6, NativeKeyEvent.VC_F7, NativeKeyEvent.VC_F8, NativeKeyEvent.VC_F9,
            NativeKeyEvent.VC_F10, NativeKeyEvent.VC_F11, NativeKeyEvent.VC_F12};
    private static final String[] PAGE_NAMES = {"Home", "type sound", "Converter", "Talk 2 me"};
    private static final String[] PAGE_ICONS = {"home", "sounds", "convert", "talk"};

    private static boolean startHidden;
    private static ServerSocket guard;

    private final Preferences prefs = Preferences.userNodeForPackage(Main.class);
    private final Sound snd = new Sound();
    private final Set<Integer> down = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled = true, fixerOn = true, busy = false;
    private volatile float volume = 1f;
    private volatile String[] pair = {"fa", "en"};
    private JFrame frame;
    private JPanel pages;
    private CardLayout cards;
    private NavBar nav;
    private TitleBar titleBar;
    private Ui.Switch soundsSwitch;
    private boolean trayReady, refreshing;
    private Ui.Overlay overlay;
    private String lastLang;

    private final int[] hkMods = new int[ACTIONS.length], hkCodes = new int[ACTIONS.length];
    private volatile int recording = -1;
    private final JLabel[] hkLabels = new JLabel[ACTIONS.length];
    private final List<JComboBox<String>> packBoxes = new ArrayList<>();

    public static void main(String[] args) {
        startHidden = Arrays.asList(args).contains("--tray");
        if (alreadyRunning()) return;
        FlatDarkLaf.setGlobalExtraDefaults(java.util.Map.of("@accentColor", "#FF7A1A", "@background", "#050507", "@foreground", "#F2F5FF"));
        FlatDarkLaf.setup();
        UIManager.put("defaultFont", Ui.font(Font.PLAIN, 13f));
        UIManager.put("Component.arc", 16);
        UIManager.put("Button.arc", 16);
        UIManager.put("TextComponent.arc", 14);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumb", new Color(0x4A5370));
        UIManager.put("ScrollBar.track", new Color(0, 0, 0, 0));
        UIManager.put("Slider.trackWidth", 8);
        UIManager.put("Slider.thumbSize", new Dimension(20, 20));
        UIManager.put("ComboBox.background", new Color(0x121216));
        UIManager.put("TextArea.background", new Color(0x08080A));
        UIManager.put("Component.borderColor", new Color(0x4A5370));
        for (String k : new String[]{"ComboBox.foreground", "TextArea.foreground", "Label.foreground", "List.foreground", "TextField.foreground"})
            UIManager.put(k, Ui.TEXT);
        Logger.getLogger(GlobalScreen.class.getPackage().getName()).setLevel(Level.OFF);
        SwingUtilities.invokeLater(() -> new Main().show());
    }

    private static boolean alreadyRunning() {
        try {
            guard = new ServerSocket(GUARD_PORT, 4, InetAddress.getLoopbackAddress());
            return false;
        } catch (IOException e) {
            try (Socket s = new Socket(InetAddress.getLoopbackAddress(), GUARD_PORT)) {
                s.setSoTimeout(1500);
                s.getOutputStream().write("show\n".getBytes());
                s.getOutputStream().flush();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                return "ok".equals(in.readLine());
            } catch (IOException ignored) {
                return false;
            }
        }
    }

    private void listenForSecondLaunch() {
        if (guard == null) return;
        Thread t = new Thread(() -> {
            while (!guard.isClosed()) {
                try (Socket s = guard.accept()) {
                    s.setSoTimeout(1000);
                    new BufferedReader(new InputStreamReader(s.getInputStream())).readLine();
                    s.getOutputStream().write("ok\n".getBytes());
                    s.getOutputStream().flush();
                    SwingUtilities.invokeLater(this::showFrame);
                } catch (IOException ignored) {
                }
            }
        }, "single-instance");
        t.setDaemon(true);
        t.start();
    }

    private void showFrame() {
        frame.setVisible(true);
        frame.setState(Frame.NORMAL);
        frame.toFront();
    }

    private static String[] parsePair(String s) {
        String[] p = s.split(",");
        if (p.length != 2 || p[0].equals(p[1])) return null;
        for (String c : p) if (!Arrays.asList(Ui.LANG_CODES).contains(c)) return null;
        return p;
    }

    private static BufferedImage appIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Ui.drawLogo(g, 0, 0, size);
        g.dispose();
        return img;
    }

    private void show() {
        migrateStartupEntries();
        loadHotkeys();
        String[] saved = parsePair(prefs.get("langs", ""));
        if (saved == null) {
            saved = Ui.pickLanguages(null, new String[]{"fa", "en"}, true);
            if (saved == null) System.exit(0);
            prefs.put("langs", saved[0] + "," + saved[1]);
        }
        pair = saved;
        overlay = new Ui.Overlay();

        frame = new JFrame(APP);
        frame.setUndecorated(true);
        frame.setIconImage(appIcon(64));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { closeWindow(); }
        });

        Ui.Space root = new Ui.Space(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        titleBar = new TitleBar(frame, this::closeWindow);
        titleBar.setToolTipText("Closing keeps " + APP + " running in the tray");
        cards = new CardLayout();
        pages = new JPanel(cards);
        pages.setOpaque(false);
        nav = new NavBar(PAGE_NAMES, PAGE_ICONS, this::showPage);
        JPanel navWrap = new JPanel(new BorderLayout());
        navWrap.setOpaque(false);
        navWrap.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
        navWrap.add(nav);
        root.add(titleBar, BorderLayout.NORTH);
        root.add(pages, BorderLayout.CENTER);
        root.add(navWrap, BorderLayout.SOUTH);
        buildPages();
        frame.setContentPane(root);
        makeResizable(frame, root);
        frame.setMinimumSize(new Dimension(500, 520));
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = Math.min(600, usable.width - 40), h = Math.min(860, usable.height - 30);
        frame.setSize(Math.max(w, 500), Math.max(h, 520));
        frame.setLocation(usable.x + (usable.width - frame.getWidth()) / 2, usable.y + Math.max(0, (usable.height - frame.getHeight()) / 2));

        setupTray(frame);
        frame.setVisible(!(startHidden && trayReady));
        listenForSecondLaunch();

        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Keyboard hook failed:\n" + ex.getMessage());
        }

        new javax.swing.Timer(150, e -> {
            String l = Layouts.current();
            if (!l.equals(lastLang)) {
                lastLang = l;
                titleBar.setLang(l);
            }
        }).start();
    }

    private void closeWindow() {
        if (trayReady) frame.setVisible(false);
        else exitApp();
    }

    private void buildPages() {
        pages.removeAll();
        packBoxes.clear();
        pages.add(scroll(homeTab()), "0");
        pages.add(scroll(soundsTab()), "1");
        pages.add(scroll(converterTab()), "2");
        pages.add(scroll(talkTab()), "3");
        showPage(0);
        pages.revalidate();
        pages.repaint();
    }

    private void showPage(int i) {
        cards.show(pages, String.valueOf(i));
        nav.select(i);
    }

    private void changeLanguages() {
        String[] np = Ui.pickLanguages(frame, pair, false);
        if (np == null || (np[0].equals(pair[0]) && np[1].equals(pair[1]))) return;
        pair = np;
        prefs.put("langs", np[0] + "," + np[1]);
        snd.clearSlots();
        buildPages();
    }

    private static final class Page extends JPanel implements Scrollable {
        Page() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(6, 16, 12, 16));
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private JPanel page() { return new Page(); }

    private JScrollPane scroll(JPanel p) {
        JScrollPane sp = new JScrollPane(p, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    private Ui.Switch prefSwitch(String key, boolean def, Consumer<Boolean> apply) {
        Ui.Switch sw = new Ui.Switch(prefs.getBoolean(key, def), true);
        apply.accept(sw.isOn());
        sw.onChange(v -> { apply.accept(v); prefs.putBoolean(key, v); });
        return sw;
    }

    private static JComponent labeled(String title, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(0, 5));
        p.setOpaque(false);
        JLabel l = new JLabel(title);
        l.setFont(Ui.font(Font.BOLD, 12.5f));
        l.setForeground(Ui.MUTED);
        p.add(l, BorderLayout.NORTH);
        p.add(c);
        p.setAlignmentX(0f);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 4));
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return p;
    }

    private JPanel homeTab() {
        JPanel p = page();
        p.add(new Ui.Hero("fix wrong language text and set typing sounds"));
        p.add(Ui.section("Switches"));

        soundsSwitch = prefSwitch("on.sounds", true, v -> enabled = v);
        Ui.Switch badge = prefSwitch("on.badge", true, v -> {
            overlay.badgeOn = v;
            if (!v) overlay.hideNow();
        });
        Ui.Switch fixer = prefSwitch("on.fixer", true, v -> fixerOn = v);

        JPanel row = new JPanel(new GridLayout(1, 3, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(0f);
        row.add(tile("keyboard", "Key sounds", "Identify your language by sound", soundsSwitch));
        row.add(tile("aa", "Text fixer", "Fix wrong language", fixer));
        row.add(tile("globe", "Language badge", "try me", badge));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        p.add(row);

        p.add(Ui.section("Languages"));
        p.add(languagesCard());
        p.add(Ui.section("Shortcuts"));
        p.add(shortcutsCard());
        return p;
    }

    private JPanel tile(String icon, String title, String subtitle, Ui.Switch sw) {
        Ui.Card c = new Ui.Card(10);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setAlignmentX(0f);
        top.add(Ui.iconTile(icon, 38), BorderLayout.WEST);
        top.add(sw, BorderLayout.EAST);
        JLabel t = new JLabel(title);
        t.setFont(Ui.font(Font.BOLD, 13f));
        t.setForeground(Ui.TEXT);
        t.setAlignmentX(0f);
        JLabel s = new JLabel(subtitle);
        s.setFont(Ui.font(Font.PLAIN, 11.5f));
        s.setForeground(Ui.MUTED);
        s.setAlignmentX(0f);
        c.add(top);
        c.add(Ui.gap(8));
        c.add(t);
        c.add(s);
        return c;
    }

    private JPanel languagesCard() {
        Ui.Card lang = new Ui.Card(10);
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(0f);
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        chips.setOpaque(false);
        chips.add(langChip(pair[0], true));
        JLabel plus = new JLabel("+");
        plus.setFont(Ui.font(Font.PLAIN, 20f));
        plus.setForeground(Ui.MUTED);
        chips.add(plus);
        chips.add(langChip(pair[1], false));
        Ui.Pill change = Ui.pill("Change", "convert", false).small();
        change.addActionListener(e -> changeLanguages());
        row.add(chips, BorderLayout.CENTER);
        row.add(change, BorderLayout.EAST);
        lang.add(row);
        return lang;
    }

    private JComponent langChip(String code, boolean highlight) {
        String name = Ui.nativeName(code);
        return new JComponent() {
            @Override public Dimension getPreferredSize() {
                return new Dimension(getFontMetrics(Ui.font(Font.BOLD, 14f)).stringWidth(name) + 66, 42);
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Ui.aa(g2);
                g2.setColor(highlight ? new Color(0x2A1A12) : new Color(0x121216));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g2.setColor(highlight ? Ui.alpha(Ui.ACCENT, 220) : Ui.alpha(Ui.BORDER, 200));
                g2.setStroke(new BasicStroke(highlight ? 2f : 1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                Ui.flag(g2, code, 7, 6, 30);
                g2.setFont(Ui.font(Font.BOLD, 14f));
                g2.setColor(Ui.TEXT);
                g2.drawString(name, 46, 26);
                g2.dispose();
            }
        };
    }

    private String defaultPack(String lang) {
        if (lang.equals("en")) return "Mechanical Red";
        String[] p = pair;
        if (p[0].equals("en") || p[1].equals("en")) return "Retro Terminal";
        return lang.equals(p[0]) ? "Retro Terminal" : "Mechanical Red";
    }

    private JComboBox<String> packBox(String lang) {
        JComboBox<String> c = new JComboBox<>(snd.packNames());
        c.setSelectedItem(prefs.get("sound." + lang, defaultPack(lang)));
        snd.select(lang, (String) c.getSelectedItem());
        c.addActionListener(e -> {
            String v = (String) c.getSelectedItem();
            if (refreshing || v == null) return;
            snd.select(lang, v);
            prefs.put("sound." + lang, v);
            snd.play(lang, Sound.NORMAL, volume);
        });
        c.setAlignmentX(0f);
        packBoxes.add(c);
        return c;
    }

    private void refreshPacks() {
        refreshing = true;
        for (JComboBox<String> c : packBoxes) {
            Object cur = c.getSelectedItem();
            c.setModel(new DefaultComboBoxModel<>(snd.packNames()));
            c.setSelectedItem(cur);
        }
        refreshing = false;
    }

    private JPanel soundsTab() {
        JPanel p = page();
        p.add(Ui.section("Sounds"));
        Ui.Card packs = new Ui.Card(14);
        for (String l : pair) packs.add(labeled(Ui.langName(l), packBox(l)));
        p.add(packs);

        p.add(Ui.section("Options"));
        Ui.Card feel = new Ui.Card(14);
        Ui.Switch special = prefSwitch("special.keys", true, v -> snd.special = v);
        feel.add(Ui.switchRow("keyboard", "Special keys", "Space, Enter, Backspace", special));
        feel.add(Ui.gap(12));

        int vol = prefs.getInt("volume", 100);
        volume = vol / 100f;
        JLabel volLabel = new JLabel("Volume  " + vol + "%");
        volLabel.setFont(Ui.font(Font.BOLD, 12.5f));
        volLabel.setForeground(Ui.MUTED);
        volLabel.setAlignmentX(0f);
        JSlider slider = new JSlider(0, 200, vol);
        slider.setOpaque(false);
        slider.setAlignmentX(0f);
        slider.addChangeListener(e -> {
            volume = slider.getValue() / 100f;
            volLabel.setText("Volume  " + slider.getValue() + "%");
            prefs.putInt("volume", slider.getValue());
        });
        feel.add(volLabel);
        feel.add(slider);
        feel.add(Ui.gap(10));
        Ui.Pill imp = Ui.pill("Import sounds (WAV)", "sounds", false);
        imp.setAlignmentX(0f);
        imp.addActionListener(e -> importSounds(imp));
        feel.add(imp);
        feel.add(Ui.gap(10));
        feel.add(Ui.note("Long recordings are cut into single keys.", 380));
        p.add(feel);

        p.add(Ui.section("System"));
        Ui.Card sys = new Ui.Card(14);
        Ui.Switch auto = new Ui.Switch(autostartOn());
        auto.onChange(v -> setAutostart(v, auto));
        sys.add(Ui.switchRow("home", "Start with Windows", "Runs hidden in the tray", auto));
        p.add(sys);
        return p;
    }

    private void importSounds(Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setDialogTitle("Choose WAV files");
        fc.setFileFilter(new FileNameExtensionFilter("WAV audio (*.wav)", "wav"));
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        String name = JOptionPane.showInputDialog(parent, "Pack name:", "My pack");
        if (name == null) return;
        name = name.trim().replaceAll("[^\\p{L}\\p{N} _-]", "_");
        if (name.isEmpty()) return;
        try {
            int n = Sound.importPack(fc.getSelectedFiles(), Sound.HOME.resolve("packs").resolve(name));
            refreshPacks();
            JOptionPane.showMessageDialog(parent, "Added \"" + name + "\" (" + n + " sounds). Pick it from the lists.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Import failed: " + ex.getMessage());
        }
    }

    private JPanel converterTab() {
        JPanel p = page();
        p.add(Ui.section("Fix text"));
        Ui.Card how = new Ui.Card(14);
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        chips.setOpaque(false);
        chips.setAlignmentX(0f);
        chips.add(langChip(pair[0], false));
        JComponent arrows = new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(28, 28); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Ui.icon(g2, "convert", 0, 0, 28, Ui.ACCENT);
                g2.dispose();
            }
        };
        chips.add(arrows);
        chips.add(langChip(pair[1], false));
        how.add(chips);
        how.add(Ui.gap(10));
        how.add(Ui.note("Select text in any app and press the <b>Fix</b> shortcut (Home page).", 380));
        p.add(how);

        p.add(Ui.section("Test"));
        Ui.Card test = new Ui.Card(14);
        JTextArea in = new JTextArea(4, 20), out = new JTextArea(4, 20);
        in.setLineWrap(true);
        out.setLineWrap(true);
        out.setEditable(false);
        JScrollPane inScroll = new JScrollPane(in), outScroll = new JScrollPane(out);
        Ui.Pill go = Ui.pill("Convert", "convert", true);
        go.setAlignmentX(0f);
        go.addActionListener(e -> out.setText(Layouts.auto(in.getText(), pair[0], pair[1])));
        test.add(labeled("Text", inScroll));
        test.add(go);
        test.add(Ui.gap(12));
        test.add(labeled("Result", outScroll));
        test.setMaximumSize(new Dimension(Integer.MAX_VALUE, 440));
        p.add(test);
        return p;
    }

    private JPanel shortcutsCard() {
        Ui.Card c = new Ui.Card(10);
        for (int i = 0; i < ACTIONS.length; i++) {
            final int idx = i;
            JLabel name = new JLabel(ACTIONS[i]);
            name.setFont(Ui.font(Font.BOLD, 12.5f));
            name.setForeground(Ui.TEXT);
            name.setPreferredSize(new Dimension(112, 30));
            hkLabels[i] = new JLabel(bindingText(i), SwingConstants.CENTER);
            hkLabels[i].setFont(Ui.font(Font.BOLD, 13f));
            hkLabels[i].setForeground(Ui.TEXT);
            hkLabels[i].setOpaque(true);
            hkLabels[i].setBackground(new Color(0x050507));
            hkLabels[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Ui.alpha(Ui.BORDER, 200), 1, true), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
            Ui.Pill change = Ui.pill("Change", null, false).small();
            change.addActionListener(e -> startRecording(idx));
            Ui.Pill clear = Ui.pill("Clear", null, false).small();
            clear.addActionListener(e -> {
                recording = -1;
                setBinding(idx, 0, 0);
            });
            JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
            btns.setOpaque(false);
            btns.add(change);
            btns.add(clear);
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setAlignmentX(0f);
            row.add(name, BorderLayout.WEST);
            row.add(hkLabels[i]);
            row.add(btns, BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            if (i > 0) c.add(Ui.gap(8));
            c.add(row);
        }
        return c;
    }

    private void loadHotkeys() {
        for (int i = 0; i < ACTIONS.length; i++) {
            hkMods[i] = prefs.getInt("hk.m." + i, DEF_MODS[i]);
            hkCodes[i] = prefs.getInt("hk.c." + i, DEF_CODES[i]);
        }
    }

    private void setBinding(int i, int mods, int code) {
        hkMods[i] = mods;
        hkCodes[i] = code;
        prefs.putInt("hk.m." + i, mods);
        prefs.putInt("hk.c." + i, code);
        SwingUtilities.invokeLater(() -> { if (hkLabels[i] != null) hkLabels[i].setText(bindingText(i)); });
    }

    private String bindingText(int i) {
        if (hkCodes[i] == 0) return "Not set";
        StringBuilder sb = new StringBuilder();
        if ((hkMods[i] & 1) != 0) sb.append("Ctrl+");
        if ((hkMods[i] & 2) != 0) sb.append("Shift+");
        if ((hkMods[i] & 4) != 0) sb.append("Alt+");
        if ((hkMods[i] & 8) != 0) sb.append("Win+");
        return sb.append(NativeKeyEvent.getKeyText(hkCodes[i])).toString();
    }

    private void startRecording(int i) {
        for (int j = 0; j < ACTIONS.length; j++) if (j != i) hkLabels[j].setText(bindingText(j));
        recording = i;
        hkLabels[i].setText("Press keys...");
    }

    private void chipMessage(int i, String msg) {
        SwingUtilities.invokeLater(() -> {
            hkLabels[i].setText(msg);
            javax.swing.Timer t = new javax.swing.Timer(1500, e -> { if (recording == i) hkLabels[i].setText("Press keys..."); });
            t.setRepeats(false);
            t.start();
        });
    }

    private static int modsOf(int m) {
        return ((m & NativeKeyEvent.CTRL_MASK) != 0 ? 1 : 0) | ((m & NativeKeyEvent.SHIFT_MASK) != 0 ? 2 : 0)
                | ((m & NativeKeyEvent.ALT_MASK) != 0 ? 4 : 0) | ((m & NativeKeyEvent.META_MASK) != 0 ? 8 : 0);
    }

    private static boolean isModifierKey(int c) {
        return c == NativeKeyEvent.VC_CONTROL || c == NativeKeyEvent.VC_SHIFT || c == NativeKeyEvent.VC_ALT || c == NativeKeyEvent.VC_META;
    }

    private static boolean isFKey(int c) {
        for (int k : FKEYS) if (k == c) return true;
        return false;
    }

    private void record(int code, int mods) {
        int i = recording;
        if (code == NativeKeyEvent.VC_ESCAPE) {
            recording = -1;
            SwingUtilities.invokeLater(() -> hkLabels[i].setText(bindingText(i)));
            return;
        }
        if (isModifierKey(code)) return;
        if (mods == 0 && !isFKey(code)) {
            chipMessage(i, "Add Ctrl, Shift or Alt");
            return;
        }
        for (int j = 0; j < ACTIONS.length; j++) {
            if (j != i && hkCodes[j] == code && hkMods[j] == mods) {
                chipMessage(i, "Already used");
                return;
            }
        }
        recording = -1;
        setBinding(i, mods, code);
    }

    private void trigger(int action) {
        if (action == 0) { if (fixerOn) new Thread(this::fixSelection).start(); }
        else SwingUtilities.invokeLater(() -> soundsSwitch.toggle());
    }

    private JPanel talkTab() {
        JPanel p = page();
        p.add(new Ui.Banner("Talk 2 me", "Ideas, bugs, or just hi", 112));
        p.add(Ui.section("Contact"));

        Ui.Pill write = Ui.pill("Write", null, true).small();
        write.addActionListener(e -> openLink("mailto:" + EMAIL));
        p.add(Ui.linkCard("mail", new Color(0xE5484D), "Email", EMAIL, write, copyButton(EMAIL)));
        p.add(Ui.gap(10));

        Ui.Pill tg = Ui.pill("Open", null, true).small();
        tg.addActionListener(e -> openLink("https://t.me/" + TELEGRAM.substring(1)));
        p.add(Ui.linkCard("plane", new Color(0x2AA3E0), "Telegram", TELEGRAM, tg, copyButton(TELEGRAM)));
        p.add(Ui.gap(10));

        Ui.Pill gh = Ui.pill("Open", null, true).small();
        gh.addActionListener(e -> openLink(GITHUB_URL));
        p.add(Ui.linkCard("code", new Color(0x5A5F7A), "GitHub", GITHUB_URL.replaceFirst("^https?://", ""), gh, copyButton(GITHUB_URL)));
        p.add(Ui.gap(18));

        JLabel foot = new JLabel(FAREWELL);
        foot.setFont(Ui.font(Font.PLAIN, 12.5f));
        foot.setForeground(Ui.MUTED);
        foot.setAlignmentX(0f);
        p.add(foot);
        p.add(Ui.gap(6));



        return p;
    }

    private Ui.Pill copyButton(String text) {
        Ui.Pill b = Ui.pill("Copy", null, false).small();
        b.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            b.setText("Copied");
            javax.swing.Timer t = new javax.swing.Timer(1300, ev -> b.setText("Copy"));
            t.setRepeats(false);
            t.start();
        });
        return b;
    }

    private void openLink(String url) {
        try {
            if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException();
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
            JOptionPane.showMessageDialog(frame, "Link copied to clipboard:\n" + url);
        }
    }

    private void exitApp() {
        try { GlobalScreen.unregisterNativeHook(); } catch (Exception ignored) {}
        System.exit(0);
    }

    private void setupTray(JFrame f) {
        if (!SystemTray.isSupported()) return;
        Runnable showWindow = () -> SwingUtilities.invokeLater(this::showFrame);
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open");
        open.addActionListener(e -> showWindow.run());
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> exitApp());
        menu.add(open);
        menu.addSeparator();
        menu.add(exit);

        TrayIcon icon = new TrayIcon(appIcon(32), APP, menu);
        icon.setImageAutoSize(true);
        icon.addActionListener(e -> showWindow.run());
        try {
            SystemTray.getSystemTray().add(icon);
            trayReady = true;
        } catch (AWTException ex) {
            ex.printStackTrace();
        }
    }

    private static boolean autostartOn() {
        try {
            return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void migrateStartupEntries() {
        try {
            for (String old : new String[]{"I hope you enjoyed it!", "KeyVibes"}) {
                if (!Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, old)) continue;
                String cmd = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, old);
                if (!autostartOn()) Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP, cmd);
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, old);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String startupCommand() {
        try {
            String exe = ProcessHandle.current().info().command().orElse("");
            if (!exe.isEmpty() && !exe.toLowerCase().contains("java")) return "\"" + exe + "\" --tray";
            File jar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jar.isFile() && jar.getName().endsWith(".jar")) {
                File javaw = new File(System.getProperty("java.home"), "bin\\javaw.exe");
                return "\"" + javaw + "\" -jar \"" + jar + "\" --tray";
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void setAutostart(boolean on, Ui.Switch sw) {
        try {
            if (on) {
                String cmd = startupCommand();
                if (cmd == null) {
                    JOptionPane.showMessageDialog(frame, "Build the jar or the installer first.");
                    sw.setOn(false, false);
                    return;
                }
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP, cmd);
            } else if (autostartOn()) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private String slot(String lang) {
        String[] p = pair;
        return lang.equals(p[0]) || lang.equals(p[1]) ? lang : p[0];
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        int code = e.getKeyCode(), mods = modsOf(e.getModifiers());
        if (recording >= 0) { record(code, mods); return; }
        for (int i = 0; i < ACTIONS.length; i++) {
            if (hkCodes[i] != 0 && hkCodes[i] == code && hkMods[i] == mods) { trigger(i); return; }
        }
        if (busy) return;
        boolean fresh = down.add(code);
        final String lang = Layouts.current();
        if (fresh && enabled) {
            int kind = code == NativeKeyEvent.VC_SPACE ? Sound.SPACE : code == NativeKeyEvent.VC_ENTER ? Sound.ENTER
                    : code == NativeKeyEvent.VC_BACKSPACE ? Sound.BACKSPACE : Sound.NORMAL;
            snd.play(slot(lang), kind, volume);
        }
        if (isModifierKey(code) || !overlay.badgeOn) return;
        String[] p = pair;
        if (lang.equals(p[0]) || lang.equals(p[1])) SwingUtilities.invokeLater(() -> overlay.typing(lang));
    }

    @Override public void nativeKeyReleased(NativeKeyEvent e) { down.remove(e.getKeyCode()); }
    @Override public void nativeKeyTyped(NativeKeyEvent e) {}

    private void fixSelection() {
        busy = true;
        try {
            Robot r = new Robot();
            r.setAutoDelay(20);
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            String old = null;
            try { old = (String) cb.getData(DataFlavor.stringFlavor); } catch (Exception ignored) {}

            sleep(300);
            r.keyRelease(KeyEvent.VK_ALT);
            r.keyRelease(KeyEvent.VK_SHIFT);
            r.keyRelease(KeyEvent.VK_CONTROL);
            sleep(50);

            String marker = "__firefix_" + System.nanoTime();
            cb.setContents(new StringSelection(marker), null);
            combo(r, KeyEvent.VK_C);

            String sel = null;
            for (int i = 0; i < 30; i++) {
                sleep(50);
                try {
                    String cur = (String) cb.getData(DataFlavor.stringFlavor);
                    if (cur != null && !cur.equals(marker)) { sel = cur; break; }
                } catch (Exception ignored) {}
            }

            if (sel != null && !sel.isEmpty()) {
                String[] p = pair;
                cb.setContents(new StringSelection(Layouts.auto(sel, p[0], p[1])), null);
                sleep(100);
                combo(r, KeyEvent.VK_V);
                sleep(300);
                Layouts.switchLayout(Layouts.targetOf(sel, p[0], p[1]));
                sleep(150);
            }
            if (old != null) cb.setContents(new StringSelection(old), null);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            busy = false;
        }
    }

    private static void combo(Robot r, int key) {
        r.keyPress(KeyEvent.VK_CONTROL);
        r.keyPress(key);
        r.keyRelease(key);
        r.keyRelease(KeyEvent.VK_CONTROL);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    static final class TitleBar extends JComponent {
        private final JFrame frame;
        private final Runnable onClose;
        private String lang = "en";
        private int hover = -1;
        private Point dragFrom, frameFrom;

        TitleBar(JFrame frame, Runnable onClose) {
            this.frame = frame;
            this.onClose = onClose;
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int b = button(e.getX(), e.getY());
                    if (b >= 0) return;
                    dragFrom = e.getLocationOnScreen();
                    frameFrom = frame.getLocation();
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragFrom == null || (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) return;
                    Point now = e.getLocationOnScreen();
                    frame.setLocation(frameFrom.x + now.x - dragFrom.x, frameFrom.y + now.y - dragFrom.y);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    dragFrom = null;
                    int b = button(e.getX(), e.getY());
                    if (b == 0) frame.setState(Frame.ICONIFIED);
                    else if (b == 1) toggleMax();
                    else if (b == 2) onClose.run();
                }
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && button(e.getX(), e.getY()) < 0) toggleMax();
                }
                @Override public void mouseMoved(MouseEvent e) {
                    int b = button(e.getX(), e.getY());
                    if (b != hover) { hover = b; repaint(); }
                }
                @Override public void mouseExited(MouseEvent e) { if (hover != -1) { hover = -1; repaint(); } }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        void setLang(String l) { lang = l; repaint(); }

        private void toggleMax() {
            boolean max = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
            frame.setExtendedState(max ? Frame.NORMAL : Frame.MAXIMIZED_BOTH);
            repaint();
        }

        private Rectangle buttonRect(int i) {
            int w = 46;
            return new Rectangle(getWidth() - 14 - (3 - i) * w, 8, w, getHeight() - 16);
        }

        private int button(int x, int y) {
            for (int i = 0; i < 3; i++) if (buttonRect(i).contains(x, y)) return i;
            return -1;
        }

        @Override public Dimension getPreferredSize() { return new Dimension(300, 60); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.aa(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(0, 0, 0, 225));
            g2.fillRect(0, 0, w, h);
            g2.setPaint(new GradientPaint(0, h - 1, Ui.alpha(Ui.BORDER, 20), w / 2f, h - 1, Ui.alpha(Ui.BORDER, 110)));
            g2.drawLine(0, h - 1, w, h - 1);
            Ui.drawLogo(g2, 16, 9, 42);
            g2.setFont(Ui.font(Font.BOLD, 21f));
            g2.setColor(Ui.TEXT);
            g2.drawString("FireFix", 66, 38);

            int bx = buttonRect(0).x - 52, by = (h - 38) / 2;
            g2.setPaint(new GradientPaint(bx, by, new Color(0xFF8A2B), bx + 38, by + 38, new Color(0xFF4A12)));
            g2.fillOval(bx, by, 38, 38);
            g2.setColor(Ui.alpha(Color.WHITE, 90));
            g2.drawOval(bx, by, 37, 37);
            g2.setFont(Ui.font(Font.BOLD, 14f));
            g2.setColor(Color.WHITE);
            String code = Ui.Overlay.code(lang);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(code, bx + (38 - fm.stringWidth(code)) / 2, by + (38 - fm.getHeight()) / 2 + fm.getAscent());

            boolean max = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
            String[] icons = {"minimize", max ? "restore" : "maximize", "close"};
            for (int i = 0; i < 3; i++) {
                Rectangle r = buttonRect(i);
                if (hover == i) {
                    g2.setColor(i == 2 ? new Color(0xE5484D, false) : Ui.alpha(Color.WHITE, 40));
                    g2.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 14, 14));
                }
                Ui.icon(g2, icons[i], r.x + (r.width - 22) / 2.0, r.y + (r.height - 22) / 2.0, 22, Ui.TEXT);
            }
            g2.dispose();
        }
    }

    static final class NavBar extends JComponent {
        private final String[] labels, icons;
        private final IntConsumer onSelect;
        private int selected, hover = -1;

        NavBar(String[] labels, String[] icons, IntConsumer onSelect) {
            this.labels = labels;
            this.icons = icons;
            this.onSelect = onSelect;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseReleased(MouseEvent e) {
                    int i = index(e.getX());
                    if (i >= 0 && i != selected) { selected = i; repaint(); onSelect.accept(i); }
                }
                @Override public void mouseMoved(MouseEvent e) {
                    int i = index(e.getX());
                    if (i != hover) { hover = i; repaint(); }
                }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        void select(int i) { selected = i; repaint(); }

        private int index(int x) {
            int w = getWidth() - 20;
            if (x < 10 || x > getWidth() - 10) return -1;
            return Math.min(labels.length - 1, (x - 10) * labels.length / w);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(300, 86); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.aa(g2);
            int w = getWidth() - 1, h = getHeight() - 14;
            RoundRectangle2D.Float bar = new RoundRectangle2D.Float(0.5f, 0.5f, w, h, 30, 30);
            g2.setColor(new Color(6, 6, 8, 245));
            g2.fill(bar);
            g2.setPaint(new GradientPaint(0, 0, Ui.alpha(Ui.BORDER, 150), 0, h, Ui.alpha(Ui.BORDER, 60)));
            g2.draw(bar);
            int n = labels.length, cell = (w - 20) / n;
            for (int i = 0; i < n; i++) {
                int x = 10 + i * cell;
                boolean sel = i == selected;
                if (sel) {
                    RoundRectangle2D.Float t = new RoundRectangle2D.Float(x + 3, 6, cell - 6, h - 12, 22, 22);
                    g2.setPaint(new GradientPaint(0, 6, new Color(0x40200F), 0, h, new Color(0x241209)));
                    g2.fill(t);
                    g2.setColor(Ui.alpha(Ui.ACCENT, 170));
                    g2.draw(t);
                    g2.setColor(Ui.ACCENT);
                    g2.fillRoundRect(x + cell / 2 - 18, 6, 36, 3, 3, 3);
                    g2.fillRoundRect(x + cell / 2 - 14, h - 8, 28, 3, 3, 3);
                } else if (i == hover) {
                    g2.setColor(Ui.alpha(Color.WHITE, 18));
                    g2.fill(new RoundRectangle2D.Float(x + 3, 6, cell - 6, h - 12, 22, 22));
                }
                Color c = sel ? Ui.ACCENT : new Color(0xB4BDD3);
                Ui.icon(g2, icons[i], x + (cell - 28) / 2.0, 14, 28, c);
                float fsz = 12.5f;
                g2.setFont(Ui.font(sel ? Font.BOLD : Font.PLAIN, fsz));
                FontMetrics fm = g2.getFontMetrics();
                while (fm.stringWidth(labels[i]) > cell - 8 && fsz > 9f) {
                    fsz -= 0.5f;
                    g2.setFont(Ui.font(sel ? Font.BOLD : Font.PLAIN, fsz));
                    fm = g2.getFontMetrics();
                }
                g2.setColor(c);
                g2.drawString(labels[i], x + (cell - fm.stringWidth(labels[i])) / 2, h - 14);
            }
            g2.dispose();
        }
    }

    static void makeResizable(JFrame frame, JComponent root) {
        final int edge = 7;
        MouseAdapter ma = new MouseAdapter() {
            private int dir;
            private Point from;
            private Rectangle start;

            private int dirAt(MouseEvent e) {
                if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) return 0;
                int w = root.getWidth(), h = root.getHeight(), d = 0;
                if (e.getX() < edge) d |= 1;
                if (e.getX() > w - edge) d |= 2;
                if (e.getY() < edge) d |= 4;
                if (e.getY() > h - edge) d |= 8;
                return d;
            }

            private int cursor(int d) {
                switch (d) {
                    case 1: return Cursor.W_RESIZE_CURSOR;
                    case 2: return Cursor.E_RESIZE_CURSOR;
                    case 4: return Cursor.N_RESIZE_CURSOR;
                    case 8: return Cursor.S_RESIZE_CURSOR;
                    case 5: return Cursor.NW_RESIZE_CURSOR;
                    case 6: return Cursor.NE_RESIZE_CURSOR;
                    case 9: return Cursor.SW_RESIZE_CURSOR;
                    case 10: return Cursor.SE_RESIZE_CURSOR;
                    default: return Cursor.DEFAULT_CURSOR;
                }
            }

            @Override public void mouseMoved(MouseEvent e) { root.setCursor(Cursor.getPredefinedCursor(cursor(dirAt(e)))); }

            @Override public void mousePressed(MouseEvent e) {
                dir = dirAt(e);
                from = e.getLocationOnScreen();
                start = frame.getBounds();
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (dir == 0) return;
                Point now = e.getLocationOnScreen();
                int dx = now.x - from.x, dy = now.y - from.y;
                Rectangle r = new Rectangle(start);
                int minW = 500, minH = 520;
                if ((dir & 1) != 0) { r.x = Math.min(start.x + dx, start.x + start.width - minW); r.width = Math.max(minW, start.width - dx); }
                if ((dir & 2) != 0) r.width = Math.max(minW, start.width + dx);
                if ((dir & 4) != 0) { r.y = Math.min(start.y + dy, start.y + start.height - minH); r.height = Math.max(minH, start.height - dy); }
                if ((dir & 8) != 0) r.height = Math.max(minH, start.height + dy);
                frame.setBounds(r);
            }
        };
        root.addMouseListener(ma);
        root.addMouseMotionListener(ma);

        frame.setMaximizedBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        frame.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                try {
                    boolean max = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
                    frame.setShape(max ? null : new RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(), 30, 30));
                } catch (Throwable ignored) {}
            }
        });
    }
}
