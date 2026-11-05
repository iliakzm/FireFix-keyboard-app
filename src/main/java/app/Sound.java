package app;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.sound.sampled.*;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

public class Sound {
    static final String[] BUILTIN = {"Mechanical Red", "Mechanical Blue", "Typewriter", "Soft Bubble", "Cherry Brown",
            "Topre Thud", "Creamy Linear", "Clicky Jade", "Membrane Soft", "Laptop Chiclet", "Wooden Block", "Glass Tap",
            "Typewriter Classic", "Typewriter Portable", "Typewriter Noir", "Typewriter Vintage", "Typewriter Electric"};
    static final int NORMAL = 0, SPACE = 1, ENTER = 2, BACKSPACE = 3;
    static final Path HOME = home();
    private static final String[] SPECIAL_FILES = {null, "space.wav", "enter.wav", "backspace.wav"};

    private static Path home() {
        Path now = Paths.get(System.getProperty("user.home"), ".firefix");
        Path old = Paths.get(System.getProperty("user.home"), ".keyvibes");
        try { if (!Files.exists(now) && Files.isDirectory(old)) Files.move(old, now); } catch (Exception ignored) {}
        return now;
    }

    private static final int POOL = 2, VARIANTS = 3, MAX_FILES = 8;

    volatile boolean special = true;

    private static final class Pack {
        final Voice[][] kinds = new Voice[4][];
        void close() { for (Voice[] vs : kinds) if (vs != null) for (Voice v : vs) v.close(); }
    }

    private final Map<String, Pack> packs = new ConcurrentHashMap<>();
    private final Map<String, String> selected = new ConcurrentHashMap<>();
    private final Map<String, List<String>> bundled = new LinkedHashMap<>();
    private final Random rnd = new Random();

    public Sound() {
        try (InputStream in = Sound.class.getResourceAsStream("/packs/index.txt")) {
            if (in == null) return;
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (line.isEmpty() || line.startsWith("#") || eq < 1) continue;
                bundled.put(line.substring(0, eq).trim(), Arrays.asList(line.substring(eq + 1).trim().split("\\s*,\\s*")));
            }
        } catch (Exception ignored) {}
    }

    public String[] packNames() {
        Set<String> names = new LinkedHashSet<>(Arrays.asList(BUILTIN));
        names.addAll(bundled.keySet());
        Path dir = HOME.resolve("packs");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(Files::isDirectory).sorted().forEach(p -> names.add(p.getFileName().toString()));
            } catch (Exception ignored) {}
        }
        return names.toArray(new String[0]);
    }

    public synchronized void select(String slot, String pack) {
        if (pack == null) return;
        selected.put(slot, pack);
        Pack old = packs.remove(pack);
        if (old != null) old.close();
        try { packs.put(pack, build(pack)); } catch (Exception e) { e.printStackTrace(); }
        packs.entrySet().removeIf(en -> {
            if (selected.containsValue(en.getKey())) return false;
            en.getValue().close();
            return true;
        });
    }

    public synchronized void clearSlots() {
        selected.clear();
        for (Pack p : packs.values()) p.close();
        packs.clear();
    }

    public void play(String slot, int kind, float volume) {
        String name = selected.get(slot);
        if (name == null) return;
        Pack p = packs.get(name);
        if (p == null) return;
        Voice[] vs = special && kind != NORMAL && p.kinds[kind] != null && p.kinds[kind].length > 0 ? p.kinds[kind] : p.kinds[NORMAL];
        if (vs != null && vs.length > 0) vs[rnd.nextInt(vs.length)].play(volume);
    }

    private Pack build(String name) throws Exception {
        List<Pcm> base = new ArrayList<>();
        Map<Integer, Pcm> custom = new HashMap<>();
        int idx = Arrays.asList(BUILTIN).indexOf(name);
        List<String> files = bundled.get(name);
        if (idx >= 0) {
            for (int i = 0; i < VARIANTS; i++) base.add(synth(idx, 100 + i));
            if (name.startsWith("Typewriter")) {
                URL ding = Sound.class.getResource("/packs/_special/ding.wav");
                if (ding != null) custom.put(ENTER, decode(ding));
            }
        } else if (files != null) {
            for (String f : files) {
                URL u = Sound.class.getResource("/packs/" + name + "/" + f);
                if (u != null && base.size() < MAX_FILES) base.add(decode(u));
            }
            for (int k = 1; k < 4; k++) {
                URL u = Sound.class.getResource("/packs/" + name + "/" + SPECIAL_FILES[k]);
                if (u != null) custom.put(k, decode(u));
            }
        } else {
            Path dir = HOME.resolve("packs").resolve(name);
            try (Stream<Path> s = Files.list(dir)) {
                List<Path> wavs = s.filter(x -> x.toString().toLowerCase().endsWith(".wav")).sorted().toList();
                for (Path f : wavs) {
                    String fn = f.getFileName().toString().toLowerCase();
                    int kind = Arrays.asList(SPECIAL_FILES).indexOf(fn);
                    if (kind > 0) custom.put(kind, decode(f.toFile()));
                    else if (base.size() < MAX_FILES) base.add(decode(f.toFile()));
                }
            }
        }
        if (base.isEmpty()) throw new Exception("pack '" + name + "' has no usable sounds");

        Pack p = new Pack();
        p.kinds[NORMAL] = voices(base, POOL);
        Pcm b = base.get(0);
        for (int k = 1; k < 4; k++) {
            Pcm src = custom.get(k);
            if (src == null) src = k == SPACE ? shift(b, 0.74, 1.0) : k == ENTER ? shift(b, 0.60, 1.0) : shift(b, 1.32, 0.85);
            p.kinds[k] = voices(List.of(src), POOL);
        }
        return p;
    }

    private static Voice[] voices(List<Pcm> list, int pool) {
        List<Voice> out = new ArrayList<>();
        for (Pcm p : list) {
            try { out.add(new Voice(p, pool)); } catch (Exception e) { e.printStackTrace(); }
        }
        return out.toArray(new Voice[0]);
    }

    private static final int[] SYNTH_MS = {70, 70, 140, 90, 80, 110, 100, 80, 70, 45, 90, 80, 150, 100, 170, 130, 120};

    private static Pcm synth(int type, int seed) {
        Random r = new Random(seed);
        double p = 0.9 + 0.2 * r.nextDouble();
        int n = 44100 * SYNTH_MS[type] / 1000;
        double[] raw = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / 44100.0, noise = r.nextDouble() * 2 - 1, s;
            double pi2 = 2 * Math.PI;
            switch (type) {
                case 0:
                    s = noise * Math.exp(-t * 90) * 0.6 + Math.sin(pi2 * 170 * p * t) * Math.exp(-t * 55) * 0.7;
                    break;
                case 1: {
                    double t2 = Math.max(0, t - 0.012);
                    s = noise * Math.exp(-t * 160) * 0.7 + Math.sin(pi2 * 2400 * p * t) * Math.exp(-t * 140) * 0.4;
                    if (t > 0.012) s += noise * Math.exp(-t2 * 110) * 0.5 + Math.sin(pi2 * 140 * p * t2) * Math.exp(-t2 * 50) * 0.6;
                    break;
                }
                case 2:
                    s = Math.sin(pi2 * 110 * p * t) * Math.exp(-t * 40) * 0.8
                      + Math.sin(pi2 * 3300 * p * t) * Math.exp(-t * 30) * 0.25 + noise * Math.exp(-t * 120) * 0.5;
                    break;
                case 3:
                    s = Math.sin(pi2 * (600 * p * t + 3000 * t * t)) * Math.exp(-t * 45) * 0.8;
                    break;
                case 4: {
                    double bump = t > 0.004 ? Math.sin(pi2 * 900 * p * t) * Math.exp(-(t - 0.004) * 200) * 0.18 : 0;
                    s = noise * Math.exp(-t * 130) * 0.45 + Math.sin(pi2 * 200 * p * t) * Math.exp(-t * 60) * 0.7 + bump;
                    break;
                }
                case 5:
                    s = Math.sin(pi2 * 120 * p * t) * Math.exp(-t * 38) * 0.9 + Math.sin(pi2 * 260 * p * t) * Math.exp(-t * 70) * 0.3
                      + noise * Math.exp(-t * 200) * 0.2;
                    break;
                case 6:
                    s = Math.sin(pi2 * 230 * p * t) * Math.exp(-t * 45) * 0.75 + Math.sin(pi2 * 460 * p * t) * Math.exp(-t * 70) * 0.25
                      + noise * Math.exp(-t * 110) * 0.25;
                    break;
                case 7: {
                    double t2 = Math.max(0, t - 0.014);
                    s = noise * Math.exp(-t * 220) * 0.6 + Math.sin(pi2 * 3200 * p * t) * Math.exp(-t * 200) * 0.5;
                    if (t > 0.014) s += noise * Math.exp(-t2 * 200) * 0.5 + Math.sin(pi2 * 150 * p * t2) * Math.exp(-t2 * 55) * 0.5;
                    break;
                }
                case 8:
                    s = noise * Math.exp(-t * 70) * 0.3 + Math.sin(pi2 * 140 * p * t) * Math.exp(-t * 50) * 0.6;
                    break;
                case 9:
                    s = noise * Math.exp(-t * 260) * 0.55 + Math.sin(pi2 * 1500 * p * t) * Math.exp(-t * 180) * 0.5
                      + Math.sin(pi2 * 320 * p * t) * Math.exp(-t * 100) * 0.3;
                    break;
                case 10:
                    s = Math.sin(pi2 * 520 * p * t) * Math.exp(-t * 70) * 0.7 + Math.sin(pi2 * 1040 * p * t) * Math.exp(-t * 110) * 0.25
                      + noise * Math.exp(-t * 300) * 0.25;
                    break;
                case 11:
                    s = Math.sin(pi2 * 2200 * p * t) * Math.exp(-t * 90) * 0.5 + Math.sin(pi2 * 3300 * p * t) * Math.exp(-t * 140) * 0.25
                      + noise * Math.exp(-t * 350) * 0.3;
                    break;
                case 12: {
                    double t2 = Math.max(0, t - 0.028);
                    s = Math.sin(pi2 * 95 * p * t) * Math.exp(-t * 38) * 0.9 + Math.sin(pi2 * 2400 * p * t) * Math.exp(-t * 28) * 0.28
                      + Math.sin(pi2 * 3100 * p * t) * Math.exp(-t * 35) * 0.18 + noise * Math.exp(-t * 150) * 0.5;
                    if (t > 0.028) s += noise * Math.exp(-t2 * 300) * 0.25;
                    break;
                }
                case 13:
                    s = Math.sin(pi2 * 150 * p * t) * Math.exp(-t * 55) * 0.7 + Math.sin(pi2 * 3600 * p * t) * Math.exp(-t * 45) * 0.3
                      + noise * Math.exp(-t * 180) * 0.5;
                    break;
                case 14:
                    s = Math.sin(pi2 * 70 * p * t) * Math.exp(-t * 30) * 1.0 + Math.sin(pi2 * 140 * p * t) * Math.exp(-t * 45) * 0.4
                      + Math.sin(pi2 * 1800 * p * t) * Math.exp(-t * 22) * 0.2 + noise * Math.exp(-t * 120) * 0.45;
                    break;
                case 15: {
                    double t2 = Math.max(0, t - 0.045);
                    s = noise * Math.exp(-t * 200) * 0.6 + Math.sin(pi2 * 2800 * p * t) * Math.exp(-t * 40) * 0.3;
                    if (t > 0.045) s += Math.sin(pi2 * 120 * p * t2) * Math.exp(-t2 * 60) * 0.5 + noise * Math.exp(-t2 * 250) * 0.3;
                    break;
                }
                default: {
                    double chirp = t < 0.03 ? Math.sin(pi2 * (900 + 4000 * t) * t) * Math.exp(-t * 60) * 0.25 : 0;
                    s = chirp + Math.sin(pi2 * 110 * p * t) * Math.exp(-t * 45) * 0.8 + noise * Math.exp(-t * 140) * 0.4;
                }
            }
            raw[i] = s;
        }
        double peak = 1e-9;
        for (double v : raw) peak = Math.max(peak, Math.abs(v));
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = (short) (raw[i] / peak * 26000);
        return new Pcm(out, 44100);
    }

    static final class Voice {
        private final Clip[] pool;
        private int next;

        Voice(Pcm p, int size) throws Exception {
            byte[] pcm = bytes(p.s());
            pool = new Clip[size];
            try {
                for (int i = 0; i < size; i++) {
                    pool[i] = AudioSystem.getClip();
                    pool[i].open(format(p.rate()), pcm, 0, pcm.length);
                }
            } catch (Exception e) {
                close();
                throw e;
            }
        }

        void play(float vol) {
            Clip c = pool[next++ % pool.length];
            c.stop();
            c.setFramePosition(0);
            if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl g = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                g.setValue(Math.min(g.getMaximum(), Math.max(g.getMinimum(), (float) (20 * Math.log10(Math.max(vol, 0.0001f))))));
            }
            c.start();
        }

        void close() { for (Clip c : pool) if (c != null) c.close(); }
    }

    record Pcm(short[] s, int rate) {}

    static Pcm decode(File f) throws Exception { return decode(AudioSystem.getAudioInputStream(f)); }

    static Pcm decode(URL u) throws Exception { return decode(AudioSystem.getAudioInputStream(u)); }

    static Pcm decode(AudioInputStream in) throws IOException {
        AudioFormat src = in.getFormat();
        int ch = src.getChannels();
        AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16, ch, ch * 2, src.getSampleRate(), false);
        if (!src.matches(pcm)) in = AudioSystem.getAudioInputStream(pcm, in);
        byte[] data = in.readAllBytes();
        int rate = (int) pcm.getSampleRate();
        int frames = Math.min(data.length / (2 * ch), rate * 30);
        short[] mono = new short[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < ch; c++) {
                int idx = (i * ch + c) * 2;
                sum += (short) ((data[idx + 1] << 8) | (data[idx] & 0xFF));
            }
            mono[i] = (short) (sum / ch);
        }
        return new Pcm(mono, rate);
    }

    static AudioFormat format(int rate) { return new AudioFormat(rate, 16, 1, true, false); }

    static byte[] bytes(short[] s) {
        byte[] b = new byte[s.length * 2];
        for (int i = 0; i < s.length; i++) {
            b[i * 2] = (byte) s[i];
            b[i * 2 + 1] = (byte) (s[i] >> 8);
        }
        return b;
    }

    static Pcm shift(Pcm p, double factor, double gain) {
        short[] s = p.s();
        int n = Math.max(1, (int) (s.length / factor));
        short[] o = new short[n];
        int fade = Math.min(n, p.rate() * 6 / 1000);
        for (int i = 0; i < n; i++) {
            double pos = i * factor;
            int i0 = Math.min((int) pos, s.length - 1), i1 = Math.min(i0 + 1, s.length - 1);
            double v = (s[i0] + (s[i1] - s[i0]) * (pos - i0)) * gain;
            if (i >= n - fade) v *= (n - 1 - i) / (double) fade;
            o[i] = (short) Math.max(-32768, Math.min(32767, v));
        }
        return new Pcm(o, p.rate());
    }

    private static final int MAX_SOUNDS = 8;

    public static int importPack(File[] files, Path outDir) throws Exception {
        List<Pcm> all = new ArrayList<>();
        for (File f : files) all.addAll(slice(decode(f)));
        if (all.isEmpty()) throw new Exception("no usable sounds found (use WAV files)");

        List<Pcm> pick = new ArrayList<>();
        if (all.size() <= MAX_SOUNDS) pick = all;
        else for (int i = 0; i < MAX_SOUNDS; i++) pick.add(all.get(i * all.size() / MAX_SOUNDS));

        Files.createDirectories(outDir);
        try (Stream<Path> old = Files.list(outDir)) {
            for (Path p : (Iterable<Path>) old.filter(x -> x.toString().toLowerCase().endsWith(".wav"))::iterator)
                Files.delete(p);
        }
        int n = 0;
        for (Pcm c : pick) write(c, outDir.resolve(String.format("k%02d.wav", ++n)).toFile());
        return n;
    }

    private static List<Pcm> slice(Pcm d) {
        short[] s = d.s();
        int rate = d.rate(), peak = 0;
        for (short v : s) peak = Math.max(peak, Math.abs(v));
        List<Pcm> out = new ArrayList<>();
        if (peak < 300) return out;

        int hop = Math.max(1, rate / 500);
        int nb = s.length / hop;
        float[] env = new float[nb];
        for (int b = 0; b < nb; b++) {
            int m = 0;
            for (int j = 0; j < hop; j++) m = Math.max(m, Math.abs(s[b * hop + j]));
            env[b] = m;
        }
        float thr = peak * 0.2f;
        int gap = Math.max(1, (int) (0.06 * rate / hop));
        List<Integer> starts = new ArrayList<>();
        boolean armed = true;
        int last = -100000;
        for (int b = 0; b < nb; b++) {
            if (armed && env[b] >= thr && b - last >= gap) {
                starts.add(Math.max(0, b * hop - rate * 3 / 1000));
                last = b;
                armed = false;
            } else if (env[b] < thr * 0.5f) armed = true;
        }
        if (starts.size() <= 1) {
            int st = starts.isEmpty() ? 0 : starts.get(0);
            out.add(cut(s, st, (int) (1.5 * rate), rate));
            return out;
        }
        for (int k = 0; k < starts.size(); k++) {
            int st = starts.get(k), len = (int) (0.14 * rate);
            if (k + 1 < starts.size()) len = Math.min(len, starts.get(k + 1) - st);
            if (len > rate / 50) out.add(cut(s, st, len, rate));
        }
        return out;
    }

    private static Pcm cut(short[] s, int start, int len, int rate) {
        start = Math.max(0, Math.min(start, s.length - 1));
        len = Math.max(1, Math.min(len, s.length - start));
        short[] o = Arrays.copyOfRange(s, start, start + len);
        int pk = 1;
        for (short v : o) pk = Math.max(pk, Math.abs(v));
        double g = 0.9 * 32767 / pk;
        int fade = Math.max(1, Math.min(len, rate * 8 / 1000));
        for (int i = 0; i < len; i++) {
            double x = o[i] * g;
            if (i >= len - fade) x *= (len - 1 - i) / (double) fade;
            o[i] = (short) Math.max(-32768, Math.min(32767, x));
        }
        return new Pcm(o, rate);
    }

    private static void write(Pcm c, File f) throws Exception {
        byte[] b = new byte[c.s().length * 2];
        for (int i = 0; i < c.s().length; i++) {
            b[i * 2] = (byte) c.s()[i];
            b[i * 2 + 1] = (byte) (c.s()[i] >> 8);
        }
        AudioFormat fmt = new AudioFormat(c.rate(), 16, 1, true, false);
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(b), fmt, c.s().length), AudioFileFormat.Type.WAVE, f);
    }
}
