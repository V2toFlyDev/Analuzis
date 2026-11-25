package com.analizis.app;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

/**
 * Lector de PDF de facturas, sin librerías. Hace las dos cosas que necesita la app:
 * sacar el texto (para los precios y el periodo) y devolver las imágenes (para el QR).
 *
 * El texto se descodifica con las fuentes del propio PDF: cada página tiene su mapa
 * de fuentes (/Resources /Font) y cada fuente su mapa ToUnicode cuando usa códigos
 * propios, que es lo normal en las facturas generadas desde una web.
 */
public class PdfReader {

    /** Imagen incrustada, tal cual viene en el PDF. */
    public static final class Img {
        public String dict = "";
        public byte[] data;
    }

    private static final Pattern OBJ = Pattern.compile("(\\d+)\\s+\\d+\\s+obj\\b");

    private static class Obj {
        String dict = "";
        byte[] stream;
    }

    private static class Font {
        Map<Integer, String> cmap;
        boolean twoByte;
    }

    // ================= texto =================

    public static String text(byte[] data) {
        if (data == null || data.length < 200) return "";
        String raw = new String(data, StandardCharsets.ISO_8859_1);
        if (raw.indexOf("%PDF") < 0) return "";

        Map<Integer, Obj> objs = parse(data);
        if (objs.isEmpty()) return "";

        List<Integer> pages = new ArrayList<>();
        for (Map.Entry<Integer, Obj> e : objs.entrySet()) {
            if (e.getValue().dict.contains("/Type") && e.getValue().dict.replace(" ", "").contains("/Type/Page")
                    && !e.getValue().dict.replace(" ", "").contains("/Type/Pages")) {
                pages.add(e.getKey());
            }
        }
        pages.sort((a, b) -> a - b);

        StringBuilder out = new StringBuilder();
        for (int p : pages) {
            Obj page = objs.get(p);
            if (page == null) continue;
            Map<String, Font> fonts = fonts(page, objs);
            for (byte[] cs : contents(page, objs)) {
                decode(cs, fonts, out);
            }
            out.append('\n');
        }

        // Si no se han encontrado páginas, se intenta con todos los flujos de texto.
        if (out.toString().trim().isEmpty()) {
            for (Obj o : objs.values()) {
                if (o.stream == null) continue;
                byte[] plain = inflate(o.stream);
                if (plain == null) plain = o.stream;
                String s = new String(plain, StandardCharsets.ISO_8859_1);
                if (s.indexOf("BT") >= 0 && (s.indexOf("Tj") >= 0 || s.indexOf("TJ") >= 0)) {
                    decode(plain, new HashMap<>(), out);
                }
            }
        }
        return out.toString();
    }

    // ================= imágenes =================

    public static List<Img> images(byte[] data) {
        List<Img> out = new ArrayList<>();
        if (data == null || data.length < 200) return out;
        Map<Integer, Obj> objs = parse(data);
        for (Obj o : objs.values()) {
            if (o.stream == null || !o.dict.contains("/Image")) continue;
            Img i = new Img();
            i.dict = o.dict;
            i.data = o.stream;
            out.add(i);
            if (out.size() >= 30) break;
        }
        return out;
    }

    // ================= objetos =================

    private static Map<Integer, Obj> parse(byte[] data) {
        Map<Integer, Obj> out = new LinkedHashMap<>();
        String s = new String(data, StandardCharsets.ISO_8859_1);
        Matcher m = OBJ.matcher(s);
        int cursor = 0;
        while (m.find()) {
            if (m.start() < cursor) continue;   // "N 0 obj" dentro de un flujo binario
            int num;
            try {
                num = Integer.parseInt(m.group(1));
            } catch (Exception e) {
                continue;
            }
            int end = s.indexOf("endobj", m.end());
            if (end < 0) end = s.length();

            Obj o = new Obj();
            int st = s.indexOf("stream", m.end());
            if (st >= 0 && st < end) {
                o.dict = s.substring(m.end(), st);
                int b = st + 6;
                if (b < s.length() && s.charAt(b) == '\r') b++;
                if (b < s.length() && s.charAt(b) == '\n') b++;
                int len = length(o.dict);
                int e = -1;
                if (len > 0 && b + len <= data.length) {
                    int k = b + len;
                    while (k < s.length() && (s.charAt(k) == '\r' || s.charAt(k) == '\n')) k++;
                    if (s.startsWith("endstream", k)) e = b + len;
                }
                if (e < 0) {
                    e = s.indexOf("endstream", b);
                    if (e < 0 || e < b) e = end;
                    while (e > b && (s.charAt(e - 1) == '\n' || s.charAt(e - 1) == '\r')) e--;
                }
                if (e > b) o.stream = slice(data, b, e);
                int after = s.indexOf("endobj", Math.max(e, b));
                if (after >= 0) end = after;
            } else {
                o.dict = s.substring(m.end(), end);
            }
            out.put(num, o);
            cursor = end;
        }
        expandObjStm(out);
        return out;
    }

    /**
     * Los PDF modernos guardan los diccionarios dentro de un flujo comprimido (/ObjStm).
     * Sin abrirlo no se ven ni las páginas ni las fuentes.
     */
    private static void expandObjStm(Map<Integer, Obj> out) {
        for (Obj o : new ArrayList<>(out.values())) {
            if (o.stream == null || !o.dict.contains("/ObjStm")) continue;
            byte[] plain = inflate(o.stream);
            if (plain == null) continue;
            String head = new String(plain, StandardCharsets.ISO_8859_1);
            int n = num(o.dict, "/N");
            int first = num(o.dict, "/First");
            if (n <= 0 || first <= 0 || first > plain.length) continue;

            int[] pair = new int[n * 2];
            Matcher pm = Pattern.compile("-?\\d+").matcher(head.substring(0, Math.min(first, head.length())));
            int k = 0;
            while (pm.find() && k < pair.length) pair[k++] = Integer.parseInt(pm.group());
            if (k < pair.length) continue;

            for (int q = 0; q < n; q++) {
                int id = pair[q * 2];
                int start = first + pair[q * 2 + 1];
                int stop = q + 1 < n ? first + pair[(q + 1) * 2 + 1] : head.length();
                if (id <= 0 || start < 0 || start >= head.length() || stop > head.length() || stop <= start) continue;
                if (out.containsKey(id)) continue;   // el objeto de fuera manda
                Obj inner = new Obj();
                inner.dict = head.substring(start, stop);
                out.put(id, inner);
            }
        }
    }

    private static int num(String dict, String key) {
        Matcher m = Pattern.compile(Pattern.quote(key) + "\\s+(\\d+)").matcher(dict);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** /Length solo vale si es un número; si es "21 0 R" (indirecto) se busca endstream. */
    private static int length(String dict) {
        Matcher m = Pattern.compile("/Length\\s+(\\d+)").matcher(dict);
        if (!m.find()) return 0;
        String rest = dict.substring(m.end()).trim();
        if (Pattern.compile("^\\d+\\s+R").matcher(rest).find()) return 0;
        try {
            return Integer.parseInt(m.group(1));
        } catch (Exception e) {
            return 0;
        }
    }

    private static List<byte[]> contents(Obj page, Map<Integer, Obj> objs) {
        List<byte[]> out = new ArrayList<>();
        List<Integer> refs = refs(page.dict, "/Contents");
        for (int r : refs) {
            Obj o = objs.get(r);
            if (o == null || o.stream == null) continue;
            byte[] p = inflate(o.stream);
            out.add(p != null ? p : o.stream);
        }
        // Muchas facturas no dibujan el texto en /Contents: lo meten en un XObject
        // de tipo Form (/XObject << /Fm1 7 0 R >> con /Subtype /Form).
        forms(page.dict, objs, new HashSet<Integer>(), 0, out);
        return out;
    }

    /** Añade el contenido de los XObject de tipo Form, también los anidados. */
    private static void forms(String dict, Map<Integer, Obj> objs, Set<Integer> seen,
                              int depth, List<byte[]> out) {
        if (depth > 3) return;
        String res = subDict(dict, "/Resources");
        if (res == null) {
            List<Integer> r = refs(dict, "/Resources");
            if (!r.isEmpty() && objs.containsKey(r.get(0))) res = objs.get(r.get(0)).dict;
        }
        if (res == null) return;

        String xo = subDict(res, "/XObject");
        if (xo == null) {
            List<Integer> r = refs(res, "/XObject");
            if (!r.isEmpty() && objs.containsKey(r.get(0))) xo = objs.get(r.get(0)).dict;
        }
        if (xo == null) return;

        Matcher m = Pattern.compile("/([A-Za-z0-9_.+-]+)\\s+(\\d+)\\s+\\d+\\s+R").matcher(xo);
        while (m.find()) {
            int id;
            try {
                id = Integer.parseInt(m.group(2));
            } catch (Exception e) {
                continue;
            }
            if (!seen.add(id)) continue;
            Obj o = objs.get(id);
            if (o == null || o.stream == null) continue;
            if (o.dict.contains("/Image")) continue;
            byte[] p = inflate(o.stream);
            out.add(p != null ? p : o.stream);
            forms(o.dict, objs, seen, depth + 1, out);
        }
    }

    // ================= fuentes =================

    private static Map<String, Font> fonts(Obj page, Map<Integer, Obj> objs) {
        Map<String, Font> out = new HashMap<>();
        String res = subDict(page.dict, "/Resources");
        if (res == null) {
            List<Integer> r = refs(page.dict, "/Resources");
            if (!r.isEmpty() && objs.containsKey(r.get(0))) res = objs.get(r.get(0)).dict;
        }
        if (res == null) return out;

        String fontDict = subDict(res, "/Font");
        if (fontDict == null) {
            List<Integer> r = refs(res, "/Font");
            if (!r.isEmpty() && objs.containsKey(r.get(0))) fontDict = objs.get(r.get(0)).dict;
        }
        if (fontDict == null) return out;

        Matcher m = Pattern.compile("/([A-Za-z0-9_.+-]+)\\s+(\\d+)\\s+\\d+\\s+R").matcher(fontDict);
        while (m.find()) {
            String name = m.group(1);
            Obj f = objs.get(Integer.parseInt(m.group(2)));
            if (f == null) continue;
            Font font = new Font();

            String enc = subDict(f.dict, "/Encoding");
            if (enc == null) {
                Matcher e = Pattern.compile("/Encoding\\s*/([A-Za-z0-9-]+)").matcher(f.dict);
                if (e.find()) enc = e.group(1);
            }
            font.twoByte = f.dict.contains("/Type0")
                    || f.dict.contains("Identity-H") || f.dict.contains("Identity-V")
                    || (enc != null && enc.contains("Identity"));

            List<Integer> tu = refs(f.dict, "/ToUnicode");
            if (!tu.isEmpty()) {
                Obj cm = objs.get(tu.get(0));
                if (cm != null && cm.stream != null) {
                    byte[] p = inflate(cm.stream);
                    String map = new String(p != null ? p : cm.stream, StandardCharsets.ISO_8859_1);
                    font.cmap = readCmap(map);
                }
            }
            out.put(name, font);
        }
        return out;
    }

    private static Map<Integer, String> readCmap(String s) {
        Map<Integer, String> cmap = new HashMap<>();
        Matcher m = Pattern.compile("beginbfchar(.*?)endbfchar", Pattern.DOTALL).matcher(s);
        while (m.find()) {
            Matcher p = Pattern.compile("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>").matcher(m.group(1));
            while (p.find()) {
                try {
                    cmap.put(Integer.parseInt(p.group(1), 16), utf16(p.group(2)));
                } catch (Exception ignored) {
                }
            }
        }
        m = Pattern.compile("beginbfrange(.*?)endbfrange", Pattern.DOTALL).matcher(s);
        while (m.find()) {
            Matcher p = Pattern.compile("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>")
                    .matcher(m.group(1));
            while (p.find()) {
                try {
                    int lo = Integer.parseInt(p.group(1), 16);
                    int hi = Integer.parseInt(p.group(2), 16);
                    int base = Integer.parseInt(p.group(3), 16);
                    if (hi < lo || hi - lo > 4096) continue;
                    for (int k = lo; k <= hi; k++) cmap.put(k, new String(Character.toChars(base + (k - lo))));
                } catch (Exception ignored) {
                }
            }

            // Forma con lista: <lo> <hi> [<destino1> <destino2> ...]
            Matcher list = Pattern.compile("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*\\[([^\\]]*)\\]")
                    .matcher(m.group(1));
            while (list.find()) {
                try {
                    int lo = Integer.parseInt(list.group(1), 16);
                    Matcher one = Pattern.compile("<([0-9A-Fa-f]+)>").matcher(list.group(3));
                    int k = lo;
                    while (one.find() && k - lo < 4096) {
                        cmap.put(k++, utf16(one.group(1)));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return cmap;
    }

    private static String utf16(String hex) {
        StringBuilder o = new StringBuilder();
        for (int i = 0; i + 4 <= hex.length(); i += 4) {
            o.append((char) Integer.parseInt(hex.substring(i, i + 4), 16));
        }
        return o.toString();
    }

    // ================= contenido =================

    private static void decode(byte[] content, Map<String, Font> fonts, StringBuilder out) {
        String s = new String(content, StandardCharsets.ISO_8859_1);
        List<Object> stack = new ArrayList<>();
        List<List<Object>> arrays = new ArrayList<>();
        Font cur = null;
        double lastY = 0;
        boolean hadText = false;
        int i = 0;

        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '\r' || c == '\n' || c == ' ' || c == '\t' || c == '\f' || c == 0) {
                i++;
                continue;
            }
            if (c == '(') {
                int e = literalEnd(s, i);
                push(stack, arrays, new Str(unescape(s.substring(i + 1, e)), false));
                i = e + 1;
                continue;
            }
            if (c == '<' && i + 1 < s.length() && s.charAt(i + 1) == '<') {
                int e = dictEnd(s, i);
                i = e;
                continue;
            }
            if (c == '<') {
                int e = s.indexOf('>', i);
                if (e < 0) break;
                push(stack, arrays, new Str(s.substring(i + 1, e).replaceAll("\\s+", ""), true));
                i = e + 1;
                continue;
            }
            if (c == '[') {
                arrays.add(new ArrayList<Object>());
                i++;
                continue;
            }
            if (c == ']') {
                if (!arrays.isEmpty()) {
                    List<Object> done = arrays.remove(arrays.size() - 1);
                    if (arrays.isEmpty()) stack.add(done);
                    else arrays.get(arrays.size() - 1).add(done);
                }
                i++;
                continue;
            }
            if (c == '/' ) {
                int j = i + 1;
                while (j < s.length() && !isDelim(s.charAt(j))) j++;
                push(stack, arrays, new Name(s.substring(i + 1, j)));
                i = j;
                continue;
            }
            if (c == ')' || c == '>' || c == '{' || c == '}') {
                i++;
                continue;
            }

            int j = i;
            while (j < s.length() && !isDelim(s.charAt(j))) j++;
            String w = s.substring(i, j);
            i = j;
            if (w.isEmpty()) continue;

            if (isNumber(w)) {
                push(stack, arrays, parseNum(w));
                continue;
            }

            if (!arrays.isEmpty()) continue;

            if ("Tf".equals(w)) {
                for (Object o : stack) {
                    if (o instanceof Name) cur = fonts.get(((Name) o).v);
                }
            } else if ("Tm".equals(w)) {
                double y = 0;
                int k = 0;
                for (Object o : stack) {
                    if (o instanceof Double) {
                        if (k == 5) y = (Double) o;
                        k++;
                    }
                }
                if (hadText && Math.abs(y - lastY) > 0.5) newline(out);
                lastY = y;
            } else if ("Td".equals(w) || "TD".equals(w)) {
                double ty = 0;
                List<Double> nums = nums(stack);
                if (nums.size() >= 2) ty = nums.get(1);
                if (hadText && Math.abs(ty) > 0.5) newline(out);
            } else if ("T*".equals(w)) {
                newline(out);
            } else if ("Tj".equals(w)) {
                show(stack, cur, out, true);
                hadText = true;
            } else if ("'".equals(w) || "\"".equals(w)) {
                newline(out);
                show(stack, cur, out, true);
                hadText = true;
            } else if ("TJ".equals(w)) {
                for (Object o : stack) {
                    if (o instanceof List) {
                        for (Object e : (List<?>) o) {
                            // Los trozos de un mismo TJ van pegados salvo hueco grande.
                            if (e instanceof Str) showOne((Str) e, cur, out, false);
                            else if (e instanceof Double && ((Double) e) < -170) space(out);
                        }
                        hadText = true;
                    }
                }
            } else if ("ET".equals(w)) {
                newline(out);
            }
            stack.clear();
        }
    }

    private static void push(List<Object> stack, List<List<Object>> arrays, Object o) {
        if (arrays.isEmpty()) stack.add(o);
        else arrays.get(arrays.size() - 1).add(o);
    }

    private static void show(List<Object> stack, Font f, StringBuilder out, boolean spaced) {
        for (Object o : stack) {
            if (o instanceof Str) showOne((Str) o, f, out, spaced);
        }
    }

    private static void showOne(Str st, Font f, StringBuilder out, boolean spaced) {
        String piece;
        if (st.hex) {
            piece = fromHex(st.v, f);
        } else if (f != null && (f.twoByte || f.cmap != null)) {
            // Las fuentes con mapa propio (ToUnicode) no guardan texto: guardan códigos.
            // Muchas facturas los escriben como cadena literal: (\x00A\x00B)Tj.
            piece = fromHex(toHex(st.v), f);
        } else {
            piece = st.v;
        }
        if (piece == null || piece.isEmpty()) return;
        piece = piece.replace('\u0080', '€');
        if (spaced && out.length() > 0) {
            char prev = out.charAt(out.length() - 1);
            char next = piece.charAt(0);
            boolean glued = (isNum(prev) && isNum(next)) || (prev == '-' && isNum(next)) || prev == '/';
            if (!glued && prev != ' ' && prev != '\n') out.append(' ');
        }
        out.append(piece);
    }

    /** Una cadena literal (un byte por char ISO-8859-1) pasada a hexadecimal. */
    private static String toHex(String s) {
        StringBuilder h = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            int b = s.charAt(i) & 0xFF;
            h.append(Character.forDigit(b >> 4, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return h.toString();
    }

    private static String fromHex(String hex, Font f) {
        if (hex.isEmpty()) return "";
        StringBuilder o = new StringBuilder();
        if (f != null && f.twoByte && hex.length() >= 4) {
            for (int i = 0; i + 4 <= hex.length(); i += 4) {
                int code;
                try {
                    code = Integer.parseInt(hex.substring(i, i + 4), 16);
                } catch (Exception e) {
                    continue;
                }
                String u = f.cmap != null ? f.cmap.get(code) : null;
                if (u != null) o.append(u);
                else if (code >= 32 && code < 0x3000) o.append((char) code);
            }
            return o.toString();
        }
        if (hex.length() % 2 == 1) hex = hex + "0";
        for (int i = 0; i + 2 <= hex.length(); i += 2) {
            int code;
            try {
                code = Integer.parseInt(hex.substring(i, i + 2), 16);
            } catch (Exception e) {
                continue;
            }
            String u = f != null && f.cmap != null ? f.cmap.get(code) : null;
            o.append(u != null ? u : (char) code);
        }
        return o.toString();
    }

    private static void newline(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
    }

    private static void space(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != ' ' && out.charAt(out.length() - 1) != '\n') {
            out.append(' ');
        }
    }

    private static boolean isNum(char c) {
        return (c >= '0' && c <= '9') || c == ',' || c == '.' || c == '€';
    }

    private static boolean isDelim(char c) {
        return c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\f' || c == 0
                || c == '(' || c == ')' || c == '<' || c == '>' || c == '[' || c == ']'
                || c == '{' || c == '}' || c == '/' || c == '%';
    }

    private static boolean isNumber(String w) {
        if (w.isEmpty()) return false;
        char c = w.charAt(0);
        return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.';
    }

    private static Double parseNum(String w) {
        try {
            return Double.parseDouble(w);
        } catch (Exception e) {
            return 0d;
        }
    }

    private static List<Double> nums(List<Object> stack) {
        List<Double> out = new ArrayList<>();
        for (Object o : stack) if (o instanceof Double) out.add((Double) o);
        return out;
    }

    private static int literalEnd(String s, int open) {
        int depth = 1;
        int i = open + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        return s.length();
    }

    private static int dictEnd(String s, int open) {
        int depth = 0;
        int i = open;
        while (i + 1 < s.length()) {
            if (s.charAt(i) == '<' && s.charAt(i + 1) == '<') {
                depth++;
                i += 2;
                continue;
            }
            if (s.charAt(i) == '>' && s.charAt(i + 1) == '>') {
                depth--;
                i += 2;
                if (depth <= 0) return i;
                continue;
            }
            i++;
        }
        return s.length();
    }

    private static String unescape(String s) {
        StringBuilder o = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                o.append(c);
                continue;
            }
            i++;
            if (i >= s.length()) break;
            char e = s.charAt(i);
            switch (e) {
                case 'n': o.append('\n'); break;
                case 'r': o.append('\r'); break;
                case 't': o.append('\t'); break;
                case 'b': o.append('\b'); break;
                case 'f': o.append('\f'); break;
                case '(': o.append('('); break;
                case ')': o.append(')'); break;
                case '\\': o.append('\\'); break;
                default:
                    if (e >= '0' && e <= '7') {
                        int v = e - '0';
                        int n = 1;
                        while (n < 3 && i + 1 < s.length()) {
                            char d = s.charAt(i + 1);
                            if (d < '0' || d > '7') break;
                            v = v * 8 + (d - '0');
                            i++;
                            n++;
                        }
                        o.append((char) v);
                    } else {
                        o.append(e);
                    }
            }
        }
        return o.toString();
    }

    /** Sub-diccionario que cuelga de una clave, o null. */
    private static String subDict(String dict, String key) {
        if (dict == null) return null;
        int i = dict.indexOf(key);
        if (i < 0) return null;
        int start = dict.indexOf("<<", i + key.length());
        if (start < 0) return null;
        if (start - (i + key.length()) > 4) return null;
        int end = dictEnd(dict, start);
        return dict.substring(start, end);
    }

    private static List<Integer> refs(String dict, String key) {
        List<Integer> out = new ArrayList<>();
        if (dict == null) return out;
        Matcher m = Pattern.compile(Pattern.quote(key) + "\\s*\\[?\\s*((?:\\d+\\s+\\d+\\s+R\\s*)+)").matcher(dict);
        if (!m.find()) return out;
        Matcher r = Pattern.compile("(\\d+)\\s+\\d+\\s+R").matcher(m.group(1));
        while (r.find()) {
            try {
                out.add(Integer.parseInt(r.group(1)));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    static byte[] inflate(byte[] raw) {
        if (raw == null || raw.length < 8) return null;
        Inflater inf = new Inflater();
        try {
            inf.setInput(raw);
            ByteArrayOutputStream o = new ByteArrayOutputStream(Math.max(256, raw.length * 4));
            byte[] buf = new byte[16384];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n <= 0) break;
                o.write(buf, 0, n);
                if (o.size() > 24 * 1024 * 1024) break;
            }
            return o.size() > 0 ? o.toByteArray() : null;
        } catch (Exception e) {
            return null;
        } finally {
            inf.end();
        }
    }

    private static byte[] slice(byte[] d, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(d, from, out, 0, out.length);
        return out;
    }

    private static class Str {
        final String v;
        final boolean hex;

        Str(String v, boolean hex) {
            this.v = v;
            this.hex = hex;
        }
    }

    private static class Name {
        final String v;

        Name(String v) {
            this.v = v;
        }
    }
}
