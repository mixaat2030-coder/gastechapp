package com.gastech.sunmi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * يرسم الفاتورة الكاملة على Bitmap باستخدام خط Cairo المضمن
 * — هذا يحل مشكلة "الرموز" تماماً لأن Sunmi يطبع الصور بدون مشاكل تشفير.
 */
public class BitmapReceiptRenderer {

    private static final int WIDTH_80MM = 576; // pixels at 203dpi
    private static final int WIDTH_58MM = 384;

    public static Bitmap render(Context ctx, JSONObject p) {
        boolean is58 = "58mm".equals(p.optString("paperWidth", "80mm"));
        int width = is58 ? WIDTH_58MM : WIDTH_80MM;
        boolean isAr = "ar".equals(p.optString("lang", "ar"));
        boolean hidePrices = p.optBoolean("hidePrices", false);

        Typeface tf;
        try {
            tf = Typeface.createFromAsset(ctx.getAssets(), "fonts/Cairo-Regular.ttf");
        } catch (Exception e) {
            tf = Typeface.DEFAULT;
        }
        Typeface tfBold;
        try {
            tfBold = Typeface.createFromAsset(ctx.getAssets(), "fonts/Cairo-Bold.ttf");
        } catch (Exception e) {
            tfBold = Typeface.DEFAULT_BOLD;
        }

        // First pass: estimate height
        int height = 1800;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);

        TextPaint normal = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        normal.setColor(Color.BLACK);
        normal.setTextSize(is58 ? 22 : 26);
        normal.setTypeface(tf);

        TextPaint bold = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bold.setColor(Color.BLACK);
        bold.setTextSize(is58 ? 26 : 32);
        bold.setTypeface(tfBold);

        TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(Color.BLACK);
        title.setTextSize(is58 ? 32 : 40);
        title.setTypeface(tfBold);

        Layout.Alignment alignStart = isAr ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL;
        Layout.Alignment alignCenter = Layout.Alignment.ALIGN_CENTER;

        int y = 10;

        y = drawText(canvas, p.optString("branchName", ""), title, width, y, alignCenter);
        y = drawText(canvas, p.optString("branchPhone", ""), normal, width, y, alignCenter);
        y = drawText(canvas, p.optString("branchAddress", ""), normal, width, y, alignCenter);
        y = drawText(canvas, p.optString("headerText", ""), normal, width, y, alignCenter);
        y = drawDivider(canvas, width, y);

        JSONObject L = p.optJSONObject("labels");
        String lblNo       = L != null ? L.optString("no", "No.") : "No.";
        String lblCustomer = L != null ? L.optString("customer", "Customer") : "Customer";
        String lblPhone    = L != null ? L.optString("customerPhone", "Phone") : "Phone";
        String lblAddr     = L != null ? L.optString("customerAddress", "Address") : "Address";
        String lblCode     = L != null ? L.optString("customerCode", "Code") : "Code";
        String lblItem     = L != null ? L.optString("item", "Item") : "Item";
        String lblQty      = L != null ? L.optString("qty", "Qty") : "Qty";
        String lblPrice    = L != null ? L.optString("price", "Price") : "Price";
        String lblTotal    = L != null ? L.optString("total", "Total") : "Total";
        String lblSubtotal = L != null ? L.optString("subtotal", "Subtotal") : "Subtotal";
        String lblTax      = L != null ? L.optString("tax", "Tax") : "Tax";
        String lblDisc     = L != null ? L.optString("discount", "Discount") : "Discount";
        String lblThanks   = L != null ? L.optString("thanks", "Thank you") : "Thank you";

        y = drawText(canvas, lblNo + ": " + p.optString("receiptNumber", ""), normal, width, y, alignStart);
        y = drawText(canvas, p.optString("date", ""), normal, width, y, alignStart);

        // Customer info
        if (!p.optString("customerName", "").isEmpty())
            y = drawText(canvas, lblCustomer + ": " + p.optString("customerName"), normal, width, y, alignStart);
        if (!p.optString("customerCode", "").isEmpty())
            y = drawText(canvas, lblCode + ": " + p.optString("customerCode"), normal, width, y, alignStart);
        if (!p.optString("customerPhone", "").isEmpty())
            y = drawText(canvas, lblPhone + ": " + p.optString("customerPhone"), normal, width, y, alignStart);
        if (!p.optString("customerAddress", "").isEmpty())
            y = drawText(canvas, lblAddr + ": " + p.optString("customerAddress"), normal, width, y, alignStart);

        y = drawDivider(canvas, width, y);

        // Items
        JSONArray items = p.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) continue;
                String name = it.optString("name");
                int qty = it.optInt("quantity");
                String line;
                if (hidePrices) {
                    line = name + "  ×" + qty;
                } else {
                    double lineTotal = it.optDouble("lineTotal", 0);
                    line = name + "  ×" + qty + "  = " + String.format("%.2f", lineTotal);
                }
                y = drawText(canvas, line, normal, width, y, alignStart);
            }
        }

        y = drawDivider(canvas, width, y);

        if (!hidePrices) {
            String cur = p.optString("currency", "");
            y = drawText(canvas, lblSubtotal + ": " + String.format("%.2f", p.optDouble("subtotal", 0)) + " " + cur, normal, width, y, alignStart);
            y = drawText(canvas, lblTax      + ": " + String.format("%.2f", p.optDouble("tax", 0)) + " " + cur, normal, width, y, alignStart);
            if (p.optDouble("discount", 0) > 0)
                y = drawText(canvas, lblDisc + ": " + String.format("%.2f", p.optDouble("discount", 0)) + " " + cur, normal, width, y, alignStart);
            y = drawText(canvas, lblTotal + ": " + String.format("%.2f", p.optDouble("total", 0)) + " " + cur, bold, width, y, alignStart);
            y = drawDivider(canvas, width, y);

            JSONArray pays = p.optJSONArray("payments");
            if (pays != null) {
                for (int i = 0; i < pays.length(); i++) {
                    JSONObject pay = pays.optJSONObject(i);
                    if (pay == null) continue;
                    y = drawText(canvas, pay.optString("method") + ": " + String.format("%.2f", pay.optDouble("amount", 0)) + " " + cur, normal, width, y, alignStart);
                }
            }
        }

        y += 10;
        y = drawText(canvas, lblThanks, bold, width, y, alignCenter);
        y = drawText(canvas, p.optString("footerText", ""), normal, width, y, alignCenter);
        y += 30;

        // Crop to actual used height
        return Bitmap.createBitmap(bmp, 0, 0, width, Math.min(y, height));
    }

    private static int drawText(Canvas canvas, String text, TextPaint paint, int width, int y, Layout.Alignment align) {
        if (text == null || text.isEmpty()) return y;
        StaticLayout layout = StaticLayout.Builder
                .obtain(text, 0, text.length(), paint, width - 8)
                .setAlignment(align)
                .setLineSpacing(2f, 1f)
                .setIncludePad(false)
                .build();
        canvas.save();
        canvas.translate(4, y);
        layout.draw(canvas);
        canvas.restore();
        return y + layout.getHeight() + 4;
    }

    private static int drawDivider(Canvas canvas, int width, int y) {
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        p.setStrokeWidth(1);
        canvas.drawLine(4, y + 4, width - 4, y + 4, p);
        return y + 14;
    }
}
