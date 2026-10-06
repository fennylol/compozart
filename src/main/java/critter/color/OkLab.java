package critter.color;

/**
 * OKLab and OKLCH conversions (Björn Ottosson, 2020), with sRGB gamut mapping by chroma reduction.
 * Hue is in degrees.
 */
public final class OkLab {
    private OkLab() {
    }

    public record Lab(double l, double a, double b) {
        public Lch toLch() {
            double c = Math.hypot(a, b);
            double h = Math.toDegrees(Math.atan2(b, a));
            if (h < 0) h += 360;
            return new Lch(l, c, h);
        }
    }

    public record Lch(double l, double c, double h) {
        public Lab toLab() {
            double r = Math.toRadians(h);
            return new Lab(l, c * Math.cos(r), c * Math.sin(r));
        }
    }

    /** The result of mapping a color into sRGB. */
    public record Mapped(int rgb, boolean clamped) {
    }

    public static double toLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static double toGamma(double c) {
        return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
    }

    /** sRGB (0xRRGGBB, alpha ignored) to OKLab. */
    public static Lab fromRgb(int rgb) {
        double r = toLinear(((rgb >> 16) & 0xff) / 255.0);
        double g = toLinear(((rgb >> 8) & 0xff) / 255.0);
        double b = toLinear((rgb & 0xff) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new Lab(
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s);
    }

    /** OKLab to linear sRGB, unclamped. */
    public static double[] toLinearRgb(Lab c) {
        double l = c.l + 0.3963377774 * c.a + 0.2158037573 * c.b;
        double m = c.l - 0.1055613458 * c.a - 0.0638541728 * c.b;
        double s = c.l - 0.0894841775 * c.a - 1.2914855480 * c.b;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        return new double[]{
                4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s};
    }

    private static final double EPS = 1e-7;

    public static boolean inGamut(Lab c) {
        for (double v : toLinearRgb(c)) if (v < -EPS || v > 1 + EPS) return false;
        return true;
    }

    /** Packs an in-gamut (or nearly so) OKLab color into 0xRRGGBB. */
    public static int toRgb(Lab c) {
        double[] lin = toLinearRgb(c);
        int rgb = 0;
        for (double v : lin) {
            int ch = (int) Math.round(toGamma(Math.min(1, Math.max(0, v))) * 255);
            rgb = (rgb << 8) | ch;
        }
        return rgb;
    }

    /**
     * Maps an OKLCH color into sRGB. Out-of-gamut colors keep their lightness and hue
     * and lose chroma until they fit.
     */
    public static Mapped map(Lch c) {
        double l = Math.min(1, Math.max(0, c.l));
        Lch lch = new Lch(l, Math.max(0, c.c), c.h);
        if (inGamut(lch.toLab())) return new Mapped(toRgb(lch.toLab()), false);
        double lo = 0, hi = lch.c;
        for (int i = 0; i < 32; i++) {
            double mid = (lo + hi) / 2;
            if (inGamut(new Lch(l, mid, c.h).toLab())) lo = mid;
            else hi = mid;
        }
        return new Mapped(toRgb(new Lch(l, lo, c.h).toLab()), true);
    }

    public static Mapped map(Lab c) {
        return map(c.toLch());
    }
}
