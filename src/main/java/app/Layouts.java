package app;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;

final class Layouts {
    private static final Map<String, String> L = new LinkedHashMap<>();

    static {

        L.put("en", "qwertyuiop[]\\asdfghjkl;'zxcvbnm,./`1234567890-=");
        L.put("fa", "ضصثقفغعهخحجچپشسیبلاتنمکگظطزرذدئو./`۱۲۳۴۵۶۷۸۹۰-=");
        L.put("de", "qwertzuiopü+#asdfghjklöäyxcvbnm,.-^1234567890ß´");
        L.put("ru", "йцукенгшщзхъ\\фывапролджэячсмитьбю.ё1234567890-=");
    }

    public static String scriptOf(String lang) {
        return switch (lang) { case "fa" -> "arab"; case "ru" -> "cyr"; default -> "latin"; };
    }

    public static String convert(String text, String from, String to) {
        String a = L.get(from), b = L.get(to);
        if (a == null || b == null || from.equals(to)) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            int i = a.indexOf(Character.toLowerCase(ch));
            if (i >= 0 && i < b.length()) {
                char o = b.charAt(i);
                sb.append(Character.isUpperCase(ch) ? Character.toUpperCase(o) : o);
            } else sb.append(ch);
        }
        return sb.toString();
    }

    public static String detectScript(String text) {
        int latin = 0, arab = 0, cyr = 0;
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                if (c >= 0x0600 && c <= 0x06FF) arab++;
                else if (c >= 0x0400 && c <= 0x04FF) cyr++;
                else latin++;
            }
        }
        return arab > latin && arab >= cyr ? "arab" : cyr > latin ? "cyr" : "latin";
    }

    private static String sourceOf(String text, String a, String b) {
        String script = detectScript(text);
        String src = scriptOf(a).equals(script) ? a : scriptOf(b).equals(script) ? b : null;
        if (src == null) src = script.equals("arab") ? "fa" : script.equals("cyr") ? "ru" : "en";
        return src;
    }

    public static String targetOf(String text, String a, String b) {
        return sourceOf(text, a, b).equals(a) ? b : a;
    }

    public static String auto(String text, String a, String b) {
        return convert(text, sourceOf(text, a, b), targetOf(text, a, b));
    }

    public static String current() {
        if (broken) return "en";
        try {
            WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
            int thread = User32.INSTANCE.GetWindowThreadProcessId(hwnd, null);
            WinDef.HKL hkl = User32.INSTANCE.GetKeyboardLayout(thread);
            switch (hkl.getLanguageIdentifier() & 0x3FF) {
                case 0x09: return "en";
                case 0x29: return "fa";
                case 0x19: return "ru";
                case 0x07: return "de";
                default:   return "xx";
            }
        } catch (Throwable t) {
            broken = true;
            return "en";
        }
    }

    private interface U32 extends StdCallLibrary {
        U32 I = Native.load("user32", U32.class);
        boolean ClientToScreen(WinDef.HWND h, WinDef.POINT p);
        Pointer LoadKeyboardLayoutW(WString id, int flags);
    }

    private static boolean broken = !System.getProperty("os.name").toLowerCase().contains("win");

    private static double scale() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration().getDefaultTransform().getScaleX();
        } catch (Throwable t) {
            return 1.0;
        }
    }

    static Rectangle caret() {
        if (broken) return null;
        try {
            WinDef.HWND fg = User32.INSTANCE.GetForegroundWindow();
            if (fg == null) return null;
            int tid = User32.INSTANCE.GetWindowThreadProcessId(fg, null);
            WinUser.GUITHREADINFO gi = new WinUser.GUITHREADINFO();
            gi.cbSize = gi.size();
            if (!User32.INSTANCE.GetGUIThreadInfo(tid, gi) || gi.hwndCaret == null) return null;
            WinDef.RECT r = gi.rcCaret;
            if (r.left == 0 && r.top == 0 && r.right == 0 && r.bottom == 0) return null;
            WinDef.POINT p = new WinDef.POINT(r.left, r.top);
            U32.I.ClientToScreen(gi.hwndCaret, p);
            double sc = scale();
            return new Rectangle((int) (p.x / sc), (int) (p.y / sc), 2, (int) (Math.max(16, r.bottom - r.top) / sc));
        } catch (Throwable t) {
            if (t instanceof LinkageError) broken = true;
            return null;
        }
    }

    static Rectangle caretOrMouse() {
        Rectangle c = caret();
        if (c != null) return c;
        PointerInfo pi = MouseInfo.getPointerInfo();
        Point m = pi == null ? new Point(200, 200) : pi.getLocation();
        return new Rectangle(m.x, m.y, 1, 18);
    }

    static void style(Window w, boolean clickThrough) {
        if (broken) return;
        try {
            WinDef.HWND h = new WinDef.HWND(Native.getWindowPointer(w));
            int ex = User32.INSTANCE.GetWindowLong(h, WinUser.GWL_EXSTYLE);
            ex |= 0x08000000 | 0x00000080;
            if (clickThrough) ex |= 0x00000020;
            User32.INSTANCE.SetWindowLong(h, WinUser.GWL_EXSTYLE, ex);
        } catch (Throwable t) {
            if (t instanceof LinkageError) broken = true;
        }
    }

    static void switchLayout(String lang) {
        if (broken) return;
        String id;
        switch (lang) {
            case "en": id = "00000409"; break;
            case "fa": id = "00000429"; break;
            case "ru": id = "00000419"; break;
            case "de": id = "00000407"; break;
            default: return;
        }
        try {
            Pointer hkl = U32.I.LoadKeyboardLayoutW(new WString(id), 0);
            WinDef.HWND fg = User32.INSTANCE.GetForegroundWindow();
            if (hkl != null && fg != null)
                User32.INSTANCE.PostMessage(fg, 0x0050, new WinDef.WPARAM(0), new WinDef.LPARAM(Pointer.nativeValue(hkl)));
        } catch (Throwable t) {
            if (t instanceof LinkageError) broken = true;
        }
    }
}
