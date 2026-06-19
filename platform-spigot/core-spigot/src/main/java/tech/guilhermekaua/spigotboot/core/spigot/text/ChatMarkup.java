/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.core.spigot.text;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A small, dependency-free chat markup language that renders consistently across MC versions.
 * Built on the bundled BungeeCord chat API only (no Adventure / MiniMessage), so it stays usable on
 * 1.8.8. A single forward pass produces two outputs:
 * <ul>
 *   <li>{@link #parse(String)} &rarr; {@code BaseComponent[]} for players: discrete {@link TextComponent}
 *       runs (no {@code ComponentBuilder} format-bleed) carrying colours, styles, click and hover.</li>
 *   <li>{@link #legacy(String)} &rarr; a legacy {@code §}-string for item meta / console: colours and
 *       hex are resolved to section codes and the interactive tags are stripped (inner text kept).</li>
 * </ul>
 * Grammar: {@code &}/{@code §} colour + style codes ({@code k l m n o r}), {@code #rrggbb} hex
 * (version-gated via {@link HexSupport}), {@code [click=run|suggest|url:value]…[/click]},
 * {@code [hover=text]…[/hover]}, and {@code \} escapes.
 */
public final class ChatMarkup {

    private static final char SECTION = '§';

    private ChatMarkup() {
    }

    public static BaseComponent[] parse(String src) {
        return parse(src, HexSupport.NATIVE_HEX);
    }

    public static String legacy(String src) {
        return legacy(src, HexSupport.NATIVE_HEX);
    }

    /** Colours/hex resolved to {@code §}-codes; interactive tags stripped (their inner text kept). For item meta. */
    public static String legacy(String src, boolean nativeHex) {
        StringBuilder out = new StringBuilder(src.length());
        int n = src.length();
        for (int i = 0; i < n; i++) {
            char c = src.charAt(i);
            if (c == '\\' && i + 1 < n) {
                out.append(src.charAt(++i));
                continue;
            }
            if (c == '&' && i + 1 < n && isCode(Character.toLowerCase(src.charAt(i + 1)))) {
                out.append(SECTION).append(Character.toLowerCase(src.charAt(++i)));
                continue;
            }
            if (c == '#' && i + 6 < n && isHex(src, i + 1, 6)) {
                out.append(HexSupport.encode(src.substring(i, i + 7), nativeHex));
                i += 6;
                continue;
            }
            if (c == '[') {
                int close = src.indexOf(']', i);
                if (close > i) {
                    String tag = src.substring(i + 1, close);
                    if (tag.startsWith("click=") || tag.startsWith("hover=")
                            || tag.equals("/click") || tag.equals("/hover")) {
                        i = close; // drop the tag; keep inner text
                        continue;
                    }
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    public static BaseComponent[] parse(String src, boolean nativeHex) {
        List<BaseComponent> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        net.md_5.bungee.api.ChatColor color = null;
        boolean bold = false, italic = false, under = false, strike = false, obf = false;
        Deque<ClickEvent> clicks = new ArrayDeque<>();
        Deque<HoverEvent> hovers = new ArrayDeque<>();

        int n = src.length();
        for (int i = 0; i < n; i++) {
            char c = src.charAt(i);

            if (c == '\\' && i + 1 < n) {
                buf.append(src.charAt(++i));
                continue;
            }

            if ((c == '&' || c == SECTION) && i + 1 < n) {
                char code = Character.toLowerCase(src.charAt(i + 1));
                if (isHexNibbleColor(code)) {
                    flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek());
                    color = net.md_5.bungee.api.ChatColor.getByChar(code);
                    bold = italic = under = strike = obf = false;
                    i++;
                    continue;
                }
                switch (code) {
                    case 'l': flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek()); bold = true; i++; continue;
                    case 'o': flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek()); italic = true; i++; continue;
                    case 'n': flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek()); under = true; i++; continue;
                    case 'm': flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek()); strike = true; i++; continue;
                    case 'k': flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek()); obf = true; i++; continue;
                    case 'r': flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek());
                        color = null; bold = italic = under = strike = obf = false; i++; continue;
                    default: break;
                }
            }

            if (c == '#' && i + 6 < n && isHex(src, i + 1, 6)) {
                flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek());
                String seq = HexSupport.encode(src.substring(i, i + 7), nativeHex);
                BaseComponent[] probe = TextComponent.fromLegacyText(seq + " ");
                color = probe.length > 0 ? probe[0].getColor() : null;
                bold = italic = under = strike = obf = false;
                i += 6;
                continue;
            }

            if (c == '[') {
                int close = src.indexOf(']', i);
                if (close > i) {
                    String tag = src.substring(i + 1, close);
                    if (tag.startsWith("click=")) {
                        flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek());
                        clicks.push(parseClick(tag.substring(6)));
                        i = close; continue;
                    } else if (tag.startsWith("hover=")) {
                        flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek());
                        hovers.push(new HoverEvent(HoverEvent.Action.SHOW_TEXT, parse(tag.substring(6), nativeHex)));
                        i = close; continue;
                    } else if (tag.equals("/click")) {
                        flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek());
                        if (!clicks.isEmpty()) clicks.pop();
                        i = close; continue;
                    } else if (tag.equals("/hover")) {
                        flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek());
                        if (!hovers.isEmpty()) hovers.pop();
                        i = close; continue;
                    }
                }
            }

            buf.append(c);
        }
        flush(out, buf, color, bold, italic, under, strike, obf, clicks.peek(), hovers.peek());
        if (out.isEmpty()) {
            out.add(new TextComponent(""));
        }
        return out.toArray(new BaseComponent[0]);
    }

    private static ClickEvent parseClick(String body) {
        int colon = body.indexOf(':');
        String kind = colon < 0 ? body : body.substring(0, colon);
        String val = colon < 0 ? "" : body.substring(colon + 1);
        ClickEvent.Action a;
        if ("run".equalsIgnoreCase(kind)) {
            a = ClickEvent.Action.RUN_COMMAND;
        } else if ("suggest".equalsIgnoreCase(kind)) {
            a = ClickEvent.Action.SUGGEST_COMMAND;
        } else {
            a = ClickEvent.Action.OPEN_URL;
        }
        return new ClickEvent(a, val);
    }

    private static void flush(List<BaseComponent> out, StringBuilder buf,
                              net.md_5.bungee.api.ChatColor color, boolean b, boolean it,
                              boolean un, boolean st, boolean ob, ClickEvent click, HoverEvent hover) {
        if (buf.length() == 0) {
            return;
        }
        TextComponent t = new TextComponent(buf.toString());
        if (color != null) t.setColor(color);
        if (b) t.setBold(true);
        if (it) t.setItalic(true);
        if (un) t.setUnderlined(true);
        if (st) t.setStrikethrough(true);
        if (ob) t.setObfuscated(true);
        if (click != null) t.setClickEvent(click);
        if (hover != null) t.setHoverEvent(hover);
        out.add(t);
        buf.setLength(0);
    }

    private static boolean isHexNibbleColor(char code) {
        return "0123456789abcdef".indexOf(code) >= 0;
    }

    private static boolean isCode(char code) {
        return "0123456789abcdefklmnor".indexOf(code) >= 0;
    }

    private static boolean isHex(String s, int off, int len) {
        for (int i = 0; i < len; i++) {
            if (Character.digit(s.charAt(off + i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
