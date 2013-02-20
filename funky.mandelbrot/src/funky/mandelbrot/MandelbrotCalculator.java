/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotCalculator {
    
    public static final int DEFAULT_ITERATION_LIMIT = 1000;
    public static final double DEFAULT_DIVERGENCE_THRESHOLD = 2.0;
    public static final double DIVERGE_FACTOR = 0.4;
    public static final double NON_DIVERGE_FACTOR = 3.0;
    
    public interface CalculationListener {
        void pixelCalculated(int x, int y, double value);
    }
    
    private List<CalculationListener> listeners = new ArrayList<CalculationListener>();
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private int pixelWidth = 0;
    private int pixelHeight = 0;
    private double[][] buffer = new double[pixelWidth][pixelHeight];
    private int iterationLimit;
    private double threshold;
    private double centerRe = 0.0;
    private double centerIm = 0.0;
    private double realWidth = 4.0;
    private double imaginaryHeight = 4.0;
    
    public MandelbrotCalculator(int iterationLimit, double divergeceThreshold) {
        this.iterationLimit = iterationLimit;
        this.threshold = divergeceThreshold;
    }
    
    public MandelbrotCalculator() {
        this(DEFAULT_ITERATION_LIMIT, DEFAULT_DIVERGENCE_THRESHOLD);
    }
    
    public int getIterationLimit() {
        return iterationLimit;
    }
    
    public double[][] getBuffer() {
        return buffer;
    }
    
    public void addListener(CalculationListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(CalculationListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
    
    public void resize(final int newWidth, final int newHeight) {
        assert newWidth >= 0 && newHeight >= 0;
        int oldWidth = this.pixelWidth;
        int oldHeight = this.pixelHeight;
        double[][] oldBuffer = this.buffer;
        this.buffer = new double[newWidth][newHeight];
        this.pixelWidth = newWidth;
        this.pixelHeight = newHeight;
        
        recalculate();
    }
    
    private void recalculate() {
        for (int x = 0; x < pixelWidth; x++) {
            for (int y = 0; y < pixelHeight; y++) {
                final int thex = x;
                final int they = y;
                executorService.submit(new Runnable() {
                    public void run() {
                        double value = calculate(thex, they);
                        buffer[thex][they] = value;
                        synchronized (listeners) {
                            for (CalculationListener listener : listeners) {
                                listener.pixelCalculated(thex, they, value);
                            }
                        }
                    }
                });
            }
        }
    }
    
    private double calculate(int x, int y) {
        double maxAbsolute = threshold * threshold;
        double cre = centerRe + (((double) x + 0.5) / pixelWidth - 0.5) * realWidth;
        double cim = centerIm + (((double) y + 0.5) / pixelHeight - 0.5) * imaginaryHeight;
        
        double absolute;
        int i = 0;
        double re = 0;
        double im = 0;
        do {
            double nextre = re * re - im * im + cre;
            double nextim = 2 * re * im + cim;
            re = nextre;
            im = nextim;
            i++;
            absolute = re * re + im * im;
        } while (absolute <= maxAbsolute && i < iterationLimit);
        
        if (i < iterationLimit) {
            return 2 * Math.atan(DIVERGE_FACTOR * i) / Math.PI;
        } else {
            return -2 * Math.atan(NON_DIVERGE_FACTOR * absolute) / Math.PI;
        }
    }

}