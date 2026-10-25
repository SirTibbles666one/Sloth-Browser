package com.sloth.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * The forest behind the drawer, drawn rather than shipped.
 *
 * The concept boards paint a sloth and foliage behind each palette. This is the same idea
 * without putting a set of images in the APK: a sky that follows the palette, a low moon,
 * three canopy ridges and a scatter of fireflies, all in the accent the theme already has.
 * It costs no download size and every new palette gets artwork for free.
 */
final class SlothBackdrop extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ridge = new Path();
    private final Random seeded = new Random(20261024L);
    private final float[] flies = new float[24];
    private final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** The sloth sits on the near ridge, a little left of centre. */
    static float slothX(float width) {
        return width * 0.26f;
    }

    static float slothY(float height) {
        return height * 0.74f;
    }

    static float slothSize(float width) {
        return Math.max(width * 0.20f, 64f);
    }

    /** Body colour, mixed from the palette so he never looks pasted on. */
    static int bodyColour() {
        return SlothTheme.frosted()
                ? SlothTheme.lighten(SlothTheme.bg(), 0.26f)
                : SlothTheme.darken(SlothTheme.bg(), 0.34f);
    }

    static int faceColour() {
        return SlothTheme.frosted()
                ? SlothTheme.darken(SlothTheme.bg(), 0.30f)
                : SlothTheme.lighten(SlothTheme.bg(), 0.16f);
    }

    /** 0 when the drawer is shut, 1 when it is fully out: the scene slides with it. */
    private float depth;

    /** How far the scene moves over the full travel, as a fraction of its own height. */
    static final float PARALLAX = 0.14f;

    void setDepth(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (Math.abs(clamped - depth) < 0.001f) {
            return;
        }
        depth = clamped;
        invalidate();
    }

    float depth() {
        return depth;
    }

    SlothBackdrop(Context context) {
        super(context);
        setTag("backdrop");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int i = 0; i < flies.length; i += 2) {
            flies[i] = seeded.nextFloat();
            flies[i + 1] = 0.45f + seeded.nextFloat() * 0.5f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        int bg = SlothTheme.bg();
        int accent = SlothTheme.accent();

        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{SlothTheme.lighten(bg, 0.05f), bg, SlothTheme.darken(bg, 0.06f)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // a low moon, sitting behind the canopy
        paint.setColor((0x14 << 24) | (accent & 0x00FFFFFF));
        canvas.drawCircle(w * 0.78f, h * 0.16f, w * 0.20f, paint);
        paint.setColor((0x24 << 24) | (accent & 0x00FFFFFF));
        canvas.drawCircle(w * 0.78f, h * 0.16f, w * 0.13f, paint);

        // three ridges, each a little closer and a little stronger
        int[] alpha = {0x0C, 0x14, 0x1E};
        float[] base = {0.62f, 0.74f, 0.86f};
        float[] bulge = {0.10f, 0.08f, 0.06f};
        for (int i = 0; i < 3; i++) {
            // layers move by their distance: the far ridge barely, the near one most, which
            // is what makes the sheet read as being in front of the scene
            int layer = canvas.save();
            canvas.translate(0, -h * PARALLAX * depth * (i + 1) / 3f);
            paint.setColor((alpha[i] << 24) | (accent & 0x00FFFFFF));
            ridge.reset();
            ridge.moveTo(-w * 0.05f, h);
            ridge.lineTo(-w * 0.05f, h * base[i]);
            ridge.quadTo(w * 0.30f, h * (base[i] - bulge[i]), w * 0.62f, h * base[i]);
            ridge.quadTo(w * 0.86f, h * (base[i] + bulge[i] * 0.6f), w * 1.05f, h * (base[i] - bulge[i] * 0.4f));
            ridge.lineTo(w * 1.05f, h);
            ridge.close();
            canvas.drawPath(ridge, paint);
            canvas.restoreToCount(layer);
        }

        drawTexture(canvas, w, h);
        int near = canvas.save();
        canvas.translate(0, -h * PARALLAX * depth * 1.35f);
        drawSloth(canvas, w, h);

        // fireflies
        paint.setColor((0x55 << 24) | (accent & 0x00FFFFFF));
        float dot = Math.max(1.5f, w * 0.004f);
        for (int i = 0; i < flies.length; i += 2) {
            canvas.drawCircle(flies[i] * w, flies[i + 1] * h, dot, paint);
        }
        canvas.restoreToCount(near);
    }

    /**
     * The material of the palette: paper grain, dappled moss, night sky, bark grain or
     * sun rays. Subtle by design - it should be felt at the edge of the eye, not read.
     */
    private void drawTexture(Canvas canvas, int w, int h) {
        int style = SlothTheme.texture();
        if (style == 0) {
            return;
        }
        int ink = SlothTheme.ink();
        Random seeded = new Random(90210L + SlothTheme.index());
        switch (style) {
            case 1: { // paper
                paint.setColor((0x16 << 24) | (ink & 0x00FFFFFF));
                for (int i = 0; i < 260; i++) {
                    float x = seeded.nextFloat() * w;
                    float y = seeded.nextFloat() * h;
                    canvas.drawCircle(x, y, 0.8f + seeded.nextFloat(), paint);
                }
                break;
            }
            case 2: { // dappled moss
                for (int i = 0; i < 26; i++) {
                    float x = seeded.nextFloat() * w;
                    float y = seeded.nextFloat() * h;
                    float r = w * (0.03f + seeded.nextFloat() * 0.09f);
                    paint.setColor(((10 + seeded.nextInt(12)) << 24) | (SlothTheme.accent() & 0x00FFFFFF));
                    canvas.drawCircle(x, y, r, paint);
                }
                break;
            }
            case 3: { // night sky
                paint.setColor((0x66 << 24) | (ink & 0x00FFFFFF));
                for (int i = 0; i < 90; i++) {
                    float x = seeded.nextFloat() * w;
                    float y = seeded.nextFloat() * h * 0.8f;
                    float r = 0.7f + seeded.nextFloat() * 1.4f;
                    canvas.drawCircle(x, y, r, paint);
                }
                break;
            }
            case 4: { // bark
                paint.setStrokeWidth(Math.max(1f, w * 0.004f));
                paint.setColor((0x1E << 24) | (ink & 0x00FFFFFF));
                for (int i = 0; i < 22; i++) {
                    float x = seeded.nextFloat() * w;
                    float top = seeded.nextFloat() * h * 0.3f;
                    float bottom = h * (0.55f + seeded.nextFloat() * 0.45f);
                    float wobble = w * (0.01f + seeded.nextFloat() * 0.05f);
                    ridge.reset();
                    ridge.moveTo(x, top);
                    ridge.quadTo(x + wobble, (top + bottom) / 2f, x, bottom);
                    canvas.drawPath(ridge, paint);
                }
                break;
            }
            case 5: { // rays
                paint.setColor((0x18 << 24) | (SlothTheme.accent() & 0x00FFFFFF));
                paint.setStrokeWidth(Math.max(2f, w * 0.02f));
                for (int i = 0; i < 7; i++) {
                    float x = w * (0.1f + i * 0.14f);
                    canvas.drawLine(x, 0, x - w * 0.12f, h * 0.8f, paint);
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * The character, in the flat vector language the rest of the shell uses: a body, a
     * head with its mask, two arms up on the branch and a curl of tail. Not the rendered
     * mascot from the concept boards - that needs artwork - but the same animal in the
     * same place, and it costs nothing.
     */
    private void drawSloth(Canvas canvas, int w, int h) {
        int save = canvas.save();
        float s = slothSize(w);
        float cx = slothX(w);
        float cy = slothY(h);
        int body = bodyColour();
        int face = faceColour();

        solid.setColor(body);
        // branch
        float branchY = cy + s * 0.05f;
        canvas.drawRoundRect(cx - s * 1.15f, branchY, cx + s * 1.15f, branchY + s * 0.11f,
                s * 0.06f, s * 0.06f, solid);

        // body
        canvas.drawRoundRect(cx - s * 0.30f, cy - s * 0.28f, cx + s * 0.34f, cy + s * 0.10f,
                s * 0.30f, s * 0.30f, solid);
        // head
        canvas.drawCircle(cx + s * 0.06f, cy - s * 0.40f, s * 0.27f, solid);
        // arms reaching to the branch
        canvas.drawRoundRect(cx - s * 0.36f, cy - s * 0.42f, cx - s * 0.18f, branchY,
                s * 0.10f, s * 0.10f, solid);
        canvas.drawRoundRect(cx + s * 0.20f, cy - s * 0.36f, cx + s * 0.38f, branchY,
                s * 0.10f, s * 0.10f, solid);

        // the mask around the eyes
        solid.setColor(face);
        canvas.drawCircle(cx + s * 0.01f, cy - s * 0.42f, s * 0.10f, solid);
        canvas.drawCircle(cx + s * 0.15f, cy - s * 0.42f, s * 0.10f, solid);
        // eyes, nose, the flat mouth sloths have
        solid.setColor(body);
        canvas.drawCircle(cx + s * 0.01f, cy - s * 0.42f, s * 0.032f, solid);
        canvas.drawCircle(cx + s * 0.15f, cy - s * 0.42f, s * 0.032f, solid);
        canvas.drawCircle(cx + s * 0.08f, cy - s * 0.33f, s * 0.035f, solid);
        solid.setStrokeWidth(Math.max(1f, s * 0.022f));
        solid.setStyle(Paint.Style.STROKE);
        canvas.drawArc(cx + s * 0.00f, cy - s * 0.33f, cx + s * 0.16f, cy - s * 0.24f,
                20f, 140f, false, solid);
        solid.setStyle(Paint.Style.FILL);
        canvas.restoreToCount(save);
    }
}
