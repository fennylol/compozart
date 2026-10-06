package compozart.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/** A horizontal slider whose track previews the color at each value. */
final class GradientSlider extends JComponent {
    private static final int STEPS = 64;

    private final double min, max, step;
    private double value;
    private final DoubleFunction<Color> sample;
    private final List<Runnable> changeListeners = new ArrayList<>();
    private Runnable onRelease = () -> {
    };

    GradientSlider(double min, double max, double step, DoubleFunction<Color> sample) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.sample = sample;
        this.value = min;
        setFocusable(true);
        setPreferredSize(new Dimension(160, 20));
        setMinimumSize(new Dimension(60, 20));
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!isEnabled()) return;
                requestFocusInWindow();
                setFromX(e.getX());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (isEnabled()) setFromX(e.getX());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isEnabled()) onRelease.run();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!isEnabled()) return;
                int d = e.getKeyCode() == KeyEvent.VK_LEFT ? -1 : e.getKeyCode() == KeyEvent.VK_RIGHT ? 1 : 0;
                if (d != 0) {
                    userSet(value + d * step * (e.isShiftDown() ? 10 : 1));
                    e.consume();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT) onRelease.run();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    void addChangeListener(Runnable r) {
        changeListeners.add(r);
    }

    void onRelease(Runnable r) {
        onRelease = r;
    }

    double value() {
        return value;
    }

    /** Sets the value without notifying listeners. */
    void setValue(double v) {
        value = Math.max(min, Math.min(max, v));
        repaint();
    }

    private void userSet(double v) {
        double before = value;
        setValue(v);
        if (value != before) for (Runnable r : changeListeners) r.run();
    }

    private Rectangle track() {
        return new Rectangle(6, 3, getWidth() - 12, getHeight() - 6);
    }

    private void setFromX(int x) {
        Rectangle t = track();
        double f = (x - t.x) / (double) Math.max(1, t.width);
        userSet(min + Math.max(0, Math.min(1, f)) * (max - min));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Rectangle t = track();
        Draw.checker(g, t.x, t.y, t.width, t.height, 5);
        for (int i = 0; i < STEPS; i++) {
            int x0 = t.x + t.width * i / STEPS, x1 = t.x + t.width * (i + 1) / STEPS;
            g.setColor(sample.apply(min + (max - min) * (i + 0.5) / STEPS));
            g.fillRect(x0, t.y, x1 - x0, t.height);
        }
        if (!isEnabled()) {
            g.setColor(new Color(0, 0, 0, 140));
            g.fillRect(t.x, t.y, t.width, t.height);
        }
        g.setColor(isFocusOwner() ? Draw.ACCENT : Draw.MUTED);
        g.drawRect(t.x, t.y, t.width - 1, t.height - 1);
        int tx = t.x + (int) Math.round((value - min) / (max - min) * (t.width - 1));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRoundRect(tx - 3, 0, 7, getHeight(), 3, 3);
        g.setColor(Color.WHITE);
        g.fillRoundRect(tx - 2, 1, 5, getHeight() - 2, 3, 3);
        g.dispose();
    }
}
