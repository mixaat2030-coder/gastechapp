package com.gastech.pos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Base64;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Renders a full receipt (Arabic + English) into a single Bitmap that can be
 * sent to Sunmi's printBitmap(). This is the same approach Loyverse uses on
 * thermal printers that don't support Arabic text natively.
 *
 * Width:
 *  - 58mm  -> 384px
 *  - 80mm  -> 576px
 */
public class BitmapReceiptRenderer {

    public static Bitmap render(Context ctx, JSONObject d) throws Exception {
        boolean is80 = d.optString("paperWidth", "58mm").startsWith("80");
        int width = is80 ? 576 : 384;
        int padding = 8;

        // Load embedded Cairo Arabic font; fall back to system default
        Typeface tf;
        try {
            tf = Typeface.createFromAsset(ctx.getAssets(), "fonts/Cairo-Regular.ttf");
        } catch (Exception e) {
            tf = Typeface.SANS_SERIF;
        }
        Typeface tfBold = Typeface.create(tf, Typeface.BOLD);

        // Paints
        TextPaint pBody  = makePaint(tf,    is80 ? 22 : 20, false);
        TextPaint pSmall = makePaint(tf,    is80 ? 18 : 16, false);
        TextPaint pBold  = makePaint(tfBold,is80 ? 24 : 22, true);
        TextPaint pBig   = makePaint(tfBold,is80 ? 32 : 28, true);
        TextPaint pTotal = makePaint(tfBold,is80 ? 30 : 26, true);

        JSONObject L = d.optJSONObject("labels");
        String currency = d.optString("currency", "");
        boolean hidePrices = d.optBoolean("hidePrices", false);
        boolean isArabic = "ar".equalsIgnoreCase(d.optString("lang", "ar"));

        // First pass: measure total height
        int contentWidth = width - padding * 2;
        int height = padding;

        // Logo
        Bitmap logo = decodeLogo(d.optString("logoBase64", ""));
        if (logo != null) {
            int lh = scaledLogoHeight(logo, contentWidth / 2);
            height += lh + 8;
        }

        // Header text blocks
        String[] headerLines = collectHeader(d);
        for (String s : headerLines) height += measure(s, pBold, contentWidth) + 4;

        height += 14; // divider

        // Meta
        String[] metaLines = collectMeta(d, L);
        for (String s : metaLines) height += measure(s, pBody, contentWidth) + 4;

        // Customer
        String[] custLines = collectCustomer(d, L);
        if (custLines.length > 0) height += 8;
        for (String s : custLines) height += measure(s, pBody, contentWidth) + 4;

        height += 14; // divider

        // Items
        JSONArray items = d.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                String name = it.optString("name", "");
                height += measure(name, pBold, contentWidth) + 2;
                height += measure("X", pBody, contentWidth) + 6;
            }
        }

        height += 14; // divider

        // Totals
        if (!hidePrices) {
            int totalLines = 1; // subtotal
            if (d.optDouble("discount", 0) > 0) totalLines++;
            if (d.optDouble("tax", 0) > 0) totalLines++;
            for (int i = 0; i < totalLines; i++) height += measure("X", pBody, contentWidth) + 4;
            height += 8;
            height += measure("X", pTotal, contentWidth) + 8;
            JSONArray pays = d.optJSONArray("payments");
            if (pays != null) {
                for (int i = 0; i < pays.length(); i++) height += measure("X", pBody, contentWidth) + 4;
            }
            if (d.optInt("pointsEarned", 0) > 0) height += measure("X", pBody, contentWidth) + 4;
        }

        // Footer
        height += 16;
        height += measure(label(L, "thanks", "Thank you"), pBold, contentWidth) + 4;
        String footer = d.optString("footerText", "");
        if (!footer.isEmpty()) height += measure(footer, pSmall, contentWidth) + 4;

        // QR
        String receiptNo = d.optString("receiptNumber", "");
        int qrSize = 0;
        if (!receiptNo.isEmpty()) {
            qrSize = is80 ? 180 : 140;
            height += qrSize + 12;
        }

        height += padding + 30; // tail margin

        // Create bitmap
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);

        int y = padding;

        // Logo
        if (logo != null) {
            int lh = scaledLogoHeight(logo, contentWidth / 2);
            int lw = (int)(logo.getWidth() * (lh / (float) logo.getHeight()));
            int lx = (width - lw) / 2;
            Bitmap scaled = Bitmap.createScaledBitmap(logo, lw, lh, true);
            c.drawBitmap(scaled, lx, y, null);
            y += lh + 8;
        }

        // Header (centered)
        for (String s : headerLines) y = drawCentered(c, s, pBold, y, width, contentWidth, padding);

        y = drawDivider(c, y, width, padding);

        // Meta (start aligned, RTL handled by StaticLayout)
        for (String s : metaLines) y = drawAligned(c, s, pBody, y, width, contentWidth, padding, isArabic);
        // Customer
        if (custLines.length > 0) y += 6;
        for (String s : custLines) y = drawAligned(c, s, pBody, y, width, contentWidth, padding, isArabic);

        y = drawDivider(c, y, width, padding);

        // Items
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                String name = it.optString("name", "");
                double qty = it.optDouble("quantity", 1);
                double price = it.optDouble("price", 0);
                double lt = it.optDouble("lineTotal", qty * price);
                y = drawAligned(c, name, pBold, y, width, contentWidth, padding, isArabic);
                String qtyLine;
                if (hidePrices) {
                    qtyLine = "  x " + fmtQty(qty);
                } else {
                    qtyLine = "  " + fmtQty(qty) + " x " + fmt(price) + "        " + fmt(lt) + " " + currency;
                }
                y = drawAligned(c, qtyLine, pBody, y, width, contentWidth, padding, isArabic);
                y += 4;
            }
        }

        y = drawDivider(c, y, width, padding);

        // Totals
        if (!hidePrices) {
            y = drawTwoCol(c, label(L, "subtotal", "Subtotal"),
                    fmt(d.optDouble("subtotal", 0)) + " " + currency,
                    pBody, y, width, contentWidth, padding, isArabic);
            double disc = d.optDouble("discount", 0);
            if (disc > 0) {
                y = drawTwoCol(c, label(L, "discount", "Discount"),
                        "-" + fmt(disc) + " " + currency,
                        pBody, y, width, contentWidth, padding, isArabic);
            }
            double tax = d.optDouble("tax", 0);
            if (tax > 0) {
                y = drawTwoCol(c, label(L, "tax", "Tax"),
                        fmt(tax) + " " + currency,
                        pBody, y, width, contentWidth, padding, isArabic);
            }
            y += 6;
            y = drawTwoCol(c, label(L, "total", "TOTAL"),
                    fmt(d.optDouble("total", 0)) + " " + currency,
                    pTotal, y, width, contentWidth, padding, isArabic);
            y += 4;
            JSONArray pays = d.optJSONArray("payments");
            if (pays != null) {
                for (int i = 0; i < pays.length(); i++) {
                    JSONObject p = pays.getJSONObject(i);
                    y = drawTwoCol(c, p.optString("method", ""),
                            fmt(p.optDouble("amount", 0)) + " " + currency,
                            pBody, y, width, contentWidth, padding, isArabic);
                }
            }
            int points = d.optInt("pointsEarned", 0);
            if (points > 0) {
                y = drawCentered(c, label(L, "points", "Points") + ": +" + points, pBody, y, width, contentWidth, padding);
            }
        }

        // Footer
        y += 12;
        y = drawCentered(c, label(L, "thanks", "Thank you"), pBold, y, width, contentWidth, padding);
        if (!footer.isEmpty()) y = drawCentered(c, footer, pSmall, y, width, contentWidth, padding);

        // QR
        if (!receiptNo.isEmpty() && qrSize > 0) {
            try {
                Bitmap qr = makeQR(receiptNo, qrSize);
                c.drawBitmap(qr, (width - qrSize) / 2f, y + 6, null);
                y += qrSize + 12;
            } catch (Exception ignored) {}
        }

        return bmp;
    }

    // ===== drawing helpers =====
    private static TextPaint makePaint(Typeface tf, int size, boolean bold) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        p.setTypeface(tf);
        p.setTextSize(size);
        if (bold) p.setFakeBoldText(true);
        return p;
    }

    private static int measure(String text, TextPaint p, int width) {
        if (text == null || text.isEmpty()) return (int) (p.getTextSize() + 4);
        StaticLayout sl = StaticLayout.Builder
                .obtain(text, 0, text.length(), p, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build();
        return sl.getHeight();
    }

    private static int drawCentered(Canvas c, String text, TextPaint p, int y, int width, int contentWidth, int padding) {
        if (text == null || text.isEmpty()) return y;
        StaticLayout sl = StaticLayout.Builder
                .obtain(text, 0, text.length(), p, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build();
        c.save();
        c.translate(padding, y);
        sl.draw(c);
        c.restore();
        return y + sl.getHeight() + 2;
    }

    private static int drawAligned(Canvas c, String text, TextPaint p, int y, int width, int contentWidth, int padding, boolean rtl) {
        if (text == null || text.isEmpty()) return y;
        StaticLayout sl = StaticLayout.Builder
                .obtain(text, 0, text.length(), p, contentWidth)
                .setAlignment(rtl ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL)
                .build();
        c.save();
        c.translate(padding, y);
        sl.draw(c);
        c.restore();
        return y + sl.getHeight() + 2;
    }

    private static int drawTwoCol(Canvas c, String left, String right, TextPaint p, int y, int width, int contentWidth, int padding, boolean rtl) {
        // For RTL: label on right, value on left. For LTR: label on left, value on right.
        TextPaint pr = new TextPaint(p);
        TextPaint pl = new TextPaint(p);
        StaticLayout slLeft = StaticLayout.Builder
                .obtain(rtl ? right : left, 0, (rtl ? right : left).length(), pl, contentWidth / 2)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
        StaticLayout slRight = StaticLayout.Builder
                .obtain(rtl ? left : right, 0, (rtl ? left : right).length(), pr, contentWidth / 2)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE).build();
        c.save(); c.translate(padding, y); slLeft.draw(c); c.restore();
        c.save(); c.translate(padding + contentWidth / 2f, y); slRight.draw(c); c.restore();
        int h = Math.max(slLeft.getHeight(), slRight.getHeight());
        return y + h + 4;
    }

    private static int drawDivider(Canvas c, int y, int width, int padding) {
        Paint dp = new Paint();
        dp.setColor(Color.BLACK);
        dp.setStrokeWidth(1.5f);
        // Dashed line
        float dash = 4f, gap = 3f;
        float x = padding;
        float endX = width - padding;
        while (x < endX) {
            c.drawLine(x, y + 6, Math.min(x + dash, endX), y + 6, dp);
            x += dash + gap;
        }
        return y + 14;
    }

    // ===== data helpers =====
    private static String[] collectHeader(JSONObject d) {
        java.util.List<String> list = new java.util.ArrayList<>();
        String h = d.optString("headerText", "");
        if (!h.isEmpty()) list.add(h);
        String b = d.optString("branchName", "");
        if (!b.isEmpty()) list.add(b);
        String p = d.optString("branchPhone", "");
        if (!p.isEmpty()) list.add(p);
        String a = d.optString("branchAddress", "");
        if (!a.isEmpty()) list.add(a);
        return list.toArray(new String[0]);
    }

    private static String[] collectMeta(JSONObject d, JSONObject L) {
        java.util.List<String> list = new java.util.ArrayList<>();
        String no = d.optString("receiptNumber", "");
        if (!no.isEmpty()) list.add(label(L, "no", "No") + ": " + no);
        String date = d.optString("date", "");
        if (!date.isEmpty()) list.add(date);
        String cashier = d.optString("cashier", "");
        if (!cashier.isEmpty()) list.add(label(L, "cashier", "Cashier") + ": " + cashier);
        return list.toArray(new String[0]);
    }

    private static String[] collectCustomer(JSONObject d, JSONObject L) {
        if (!d.optBoolean("showCustomerInfo", true)) return new String[0];
        java.util.List<String> list = new java.util.ArrayList<>();
        String n = d.optString("customerName", "");
        if (!n.isEmpty()) list.add(label(L, "customer", "Customer") + ": " + n);
        String code = d.optString("customerCode", "");
        if (!code.isEmpty()) list.add(label(L, "customerCode", "Code") + ": " + code);
        String ph = d.optString("customerPhone", "");
        if (!ph.isEmpty()) list.add(label(L, "customerPhone", "Phone") + ": " + ph);
        String addr = d.optString("customerAddress", "");
        if (!addr.isEmpty()) list.add(label(L, "customerAddress", "Address") + ": " + addr);
        return list.toArray(new String[0]);
    }

    private static String label(JSONObject L, String key, String dflt) {
        if (L == null) return dflt;
        return L.optString(key, dflt);
    }

    private static String fmt(double v) { return String.format(java.util.Locale.US, "%.2f", v); }
    private static String fmtQty(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private static Bitmap decodeLogo(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            String pure = b64.contains(",") ? b64.substring(b64.indexOf(',') + 1) : b64;
            byte[] bytes = Base64.decode(pure, Base64.DEFAULT);
            return android.graphics.BitmapFactory.decodeStream(new ByteArrayInputStream(bytes));
        } catch (Exception e) { return null; }
    }

    private static int scaledLogoHeight(Bitmap logo, int maxWidth) {
        int maxH = 90;
        float scale = Math.min((float) maxWidth / logo.getWidth(), (float) maxH / logo.getHeight());
        return Math.max(40, (int) (logo.getHeight() * scale));
    }

    private static Bitmap makeQR(String content, int size) throws Exception {
        BitMatrix m = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
        Bitmap qr = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                qr.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return qr;
    }
}
