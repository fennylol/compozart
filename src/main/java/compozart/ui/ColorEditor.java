package compozart.ui;

import compozart.color.OkLab;
import compozart.io.ProjectIO;
import compozart.model.Palette;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/** Edits the selected palette entry with OKLCH or OKLab sliders, alpha, and a hex field. */
final class ColorEditor extends JPanel {
    private final Editor ed;
    private boolean labMode;
    private boolean updating;

    // OKLCH is the source of truth while editing, so hue survives passing through gray.
    private double l = 0.5, c = 0, h = 0;
    private int alpha = 255;
    private int loadedIndex = -1;
    private int lastWritten;

    private final JToggleButton lchButton = new JToggleButton("OKLCH", true);
    private final JToggleButton labButton = new JToggleButton("OKLab");
    private final JLabel[] labels = {new JLabel("L"), new JLabel("C"), new JLabel("h")};
    private final GradientSlider[] sliders = new GradientSlider[3];
    private final JSpinner[] spinners = new JSpinner[3];
    private final GradientSlider alphaSlider;
    private final JSpinner alphaSpinner = new JSpinner(new SpinnerNumberModel(255, 0, 255, 1));
    private final JTextField hex = new JTextField(9);
    private final JLabel gamut = new JLabel(" ");
    private final JLabel disabledNote = new JLabel("Index 0 is always clear.");

    ColorEditor(Editor ed) {
        super(new GridBagLayout());
        this.ed = ed;
        setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

        ButtonGroup bg = new ButtonGroup();
        bg.add(lchButton);
        bg.add(labButton);
        lchButton.addActionListener(e -> setMode(false));
        labButton.addActionListener(e -> setMode(true));
        JPanel modes = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        modes.add(lchButton);
        modes.add(labButton);
        disabledNote.setForeground(Draw.MUTED);
        modes.add(Box.createHorizontalStrut(8));
        modes.add(disabledNote);
        add(modes, at(0, 0, 3));

        for (int i = 0; i < 3; i++) {
            int axis = i;
            sliders[i] = new GradientSlider(0, 1, 0.005, v -> sampleAxis(axis, v));
            spinners[i] = new JSpinner(new SpinnerNumberModel(0.0, -1.0, 360.0, 0.005));
            JSpinner.NumberEditor ne = new JSpinner.NumberEditor(spinners[i], "0.000");
            spinners[i].setEditor(ne);
            ne.getTextField().setColumns(5);
            sliders[i].addChangeListener(() -> fromSlider(axis));
            sliders[i].onRelease(ed::breakCoalescing);
            spinners[i].addChangeListener(e -> fromSpinner(axis));
            add(labels[i], at(i + 1, 0, 1));
            GridBagConstraints g = at(i + 1, 1, 1);
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            add(sliders[i], g);
            add(spinners[i], at(i + 1, 2, 1));
        }
        alphaSlider = new GradientSlider(0, 255, 1, v -> {
            int rgb = OkLab.map(new OkLab.Lch(l, c, h)).rgb();
            return new Color(((int) Math.round(v) << 24) | rgb, true);
        });
        alphaSlider.addChangeListener(() -> {
            if (updating) return;
            alpha = (int) Math.round(alphaSlider.value());
            write();
        });
        alphaSlider.onRelease(ed::breakCoalescing);
        alphaSpinner.addChangeListener(e -> {
            if (updating) return;
            alpha = (Integer) alphaSpinner.getValue();
            write();
        });
        add(new JLabel("A"), at(4, 0, 1));
        GridBagConstraints g = at(4, 1, 1);
        g.fill = GridBagConstraints.HORIZONTAL;
        add(alphaSlider, g);
        add(alphaSpinner, at(4, 2, 1));

        JPanel hexRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        hexRow.add(new JLabel("Hex"));
        hexRow.add(hex);
        gamut.setForeground(Draw.WARNING);
        hexRow.add(gamut);
        add(hexRow, at(5, 0, 3));
        hex.setToolTipText("#rrggbb or #rrggbbaa");
        hex.addActionListener(e -> fromHex());
        hex.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                fromHex();
            }
        });

        setMode(false);
        ed.addListener(what -> sync());
        sync();
    }

    private static GridBagConstraints at(int row, int col, int width) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = row;
        g.gridx = col;
        g.gridwidth = width;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(2, 2, 2, 4);
        return g;
    }

    // ---- axes ----

    private double[] range(int axis) {
        if (!labMode) return switch (axis) {
            case 0 -> new double[]{0, 1, 0.005};
            case 1 -> new double[]{0, 0.37, 0.002};
            default -> new double[]{0, 360, 1};
        };
        return axis == 0 ? new double[]{0, 1, 0.005} : new double[]{-0.4, 0.4, 0.002};
    }

    private double axisValue(int axis) {
        if (!labMode) return axis == 0 ? l : axis == 1 ? c : h;
        OkLab.Lab lab = new OkLab.Lch(l, c, h).toLab();
        return axis == 0 ? lab.l() : axis == 1 ? lab.a() : lab.b();
    }

    /** The color with one axis replaced, for slider track previews. */
    private Color sampleAxis(int axis, double v01) {
        double[] r = range(axis);
        double v = r[0] + v01 * (r[1] - r[0]);
        OkLab.Lch lch = withAxis(axis, v);
        return new Color(OkLab.map(lch).rgb());
    }

    private OkLab.Lch withAxis(int axis, double v) {
        if (!labMode) {
            return switch (axis) {
                case 0 -> new OkLab.Lch(v, c, h);
                case 1 -> new OkLab.Lch(l, v, h);
                default -> new OkLab.Lch(l, c, v);
            };
        }
        OkLab.Lab lab = new OkLab.Lch(l, c, h).toLab();
        OkLab.Lab next = switch (axis) {
            case 0 -> new OkLab.Lab(v, lab.a(), lab.b());
            case 1 -> new OkLab.Lab(lab.l(), v, lab.b());
            default -> new OkLab.Lab(lab.l(), lab.a(), v);
        };
        OkLab.Lch out = next.toLch();
        // keep the hue when a and b both reach zero
        return out.c() < 1e-6 ? new OkLab.Lch(out.l(), 0, h) : out;
    }

    private void setMode(boolean lab) {
        labMode = lab;
        String[] names = lab ? new String[]{"L", "a", "b"} : new String[]{"L", "C", "h"};
        for (int i = 0; i < 3; i++) {
            labels[i].setText(names[i]);
            double[] r = range(i);
            spinners[i].setModel(new SpinnerNumberModel(axisValue(i), r[0], r[1], r[2]));
            JSpinner.NumberEditor ne = new JSpinner.NumberEditor(spinners[i], i == 2 && !lab ? "0.0" : "0.000");
            ne.getTextField().setColumns(5);
            spinners[i].setEditor(ne);
        }
        updateControls();
    }

    // ---- input ----

    private void fromSlider(int axis) {
        if (updating) return;
        double[] r = range(axis);
        apply(withAxis(axis, r[0] + sliders[axis].value() * (r[1] - r[0])));
    }

    private void fromSpinner(int axis) {
        if (updating) return;
        apply(withAxis(axis, ((Number) spinners[axis].getValue()).doubleValue()));
    }

    private void apply(OkLab.Lch lch) {
        l = lch.l();
        c = lch.c();
        h = lch.h();
        write();
    }

    private void fromHex() {
        if (updating || !editable()) return;
        try {
            int argb = ProjectIO.parseColor(hex.getText().trim());
            if (argb == lastWritten) return;
            loadColor(argb);
            alpha = argb >>> 24;
            ed.breakCoalescing();
            writeArgb(argb);
            ed.breakCoalescing();
        } catch (IllegalArgumentException ex) {
            hex.setText(ProjectIO.hexColor(lastWritten));
        }
    }

    private boolean editable() {
        return ed.color() > 0 && ed.color() < ed.project().palette.size();
    }

    private void write() {
        if (!editable()) return;
        OkLab.Mapped m = OkLab.map(new OkLab.Lch(l, c, h));
        writeArgb((alpha << 24) | m.rgb());
    }

    private void writeArgb(int argb) {
        int index = ed.color();
        Palette pal = ed.project().palette;
        String name = pal.get(index).name();
        lastWritten = argb;
        updating = true;
        try {
            if (pal.argb(index) != argb) ed.edit("color:" + index, () -> pal.set(index, new Palette.Swatch(argb, name)));
        } finally {
            updating = false;
        }
        updateControls();
    }

    // ---- display ----

    private void loadColor(int argb) {
        OkLab.Lch lch = OkLab.fromRgb(argb & 0xffffff).toLch();
        l = lch.l();
        if (lch.c() > 1e-4) {
            c = lch.c();
            h = lch.h();
        } else {
            c = 0;
        }
        alpha = argb >>> 24;
    }

    private void sync() {
        if (updating) return;
        int index = ed.color();
        Palette pal = ed.project().palette;
        if (index < 0 || index >= pal.size()) return;
        int argb = pal.argb(index);
        if (index != loadedIndex || argb != lastWritten) {
            loadedIndex = index;
            lastWritten = argb;
            loadColor(argb);
        }
        updateControls();
    }

    private void updateControls() {
        updating = true;
        try {
            boolean on = editable();
            for (int i = 0; i < 3; i++) {
                double[] r = range(i);
                double v = axisValue(i);
                sliders[i].setValue((v - r[0]) / (r[1] - r[0]));
                spinners[i].setValue(Math.max(r[0], Math.min(r[1], v)));
                sliders[i].setEnabled(on);
                spinners[i].setEnabled(on);
                sliders[i].repaint();
            }
            alphaSlider.setValue(alpha);
            alphaSpinner.setValue(alpha);
            alphaSlider.setEnabled(on);
            alphaSpinner.setEnabled(on);
            alphaSlider.repaint();
            hex.setEnabled(on);
            if (!hex.hasFocus()) hex.setText(ProjectIO.hexColor(ed.project().palette.argb(Math.max(0, Math.min(ed.color(), ed.project().palette.size() - 1)))));
            boolean clamped = on && OkLab.map(new OkLab.Lch(l, c, h)).clamped();
            gamut.setText(clamped ? "Outside sRGB: chroma reduced" : " ");
            disabledNote.setVisible(!on);
        } finally {
            updating = false;
        }
    }
}
