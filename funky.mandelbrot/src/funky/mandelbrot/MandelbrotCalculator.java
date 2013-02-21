/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotCalculator {
    
    public static final int DEFAULT_ITERATION_LIMIT = 1000;
    public static final double DEFAULT_DIVERGENCE_THRESHOLD = 2.0;
    
    private static final double DIVERGE_FACTOR = 0.4;
    private static final double NON_DIVERGE_FACTOR = 3.0;
    private static final long CALCULATION_REPORT_PERIOD = 40;
    
    public interface CalculationListener {
        void calculated(Rectangle area, double[][] buffer);
    }
    
    private List<CalculationListener> listeners = new ArrayList<CalculationListener>();
    private Set<CalculationWorker> runningCalculators = new HashSet<CalculationWorker>();
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private int pixelWidth = 0;
    private int pixelHeight = 0;
    private double[][] valueBuffer = new double[pixelWidth][pixelHeight];
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
    
    public void shutdown() {
        executorService.shutdownNow();
    }
    
    public double[][] getBuffer() {
        return valueBuffer;
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
        if (newWidth != oldWidth || newHeight != oldHeight) {
            double[][] oldBuffer = this.valueBuffer;
            double[][] newBuffer = new double[newWidth][newHeight];
            
            int copyWidth = Math.min(oldWidth, newWidth);
            int copyHeight = Math.min(oldHeight, newHeight);
            int oldxStart = 0;
            int newxStart = 0;
            if (newWidth > oldWidth) {
                newxStart = (newWidth - oldWidth) / 2;
            } else if (newWidth < oldWidth) {
                oldxStart = (oldWidth - newWidth) / 2;
            }
            int oldyStart = 0;
            int newyStart = 0;
            if (newHeight > oldHeight) {
                newyStart = (newHeight - oldHeight) / 2;
            } else if (newHeight < oldHeight) {
                oldyStart = (oldHeight - newHeight) / 2;
            }
            
            for (int x = 0; x < copyWidth; x++) {
                for (int y = 0; y < copyHeight; y++) {
                    newBuffer[newxStart + x][newyStart + y] = oldBuffer[oldxStart + x][oldyStart + y];
                }
            }

            if (oldWidth != 0) {
                realWidth = realWidth * newWidth / oldWidth;
            }
            if (oldHeight != 0) {
                imaginaryHeight = imaginaryHeight * newHeight / oldHeight;
            }
            pixelWidth = newWidth;
            pixelHeight = newHeight;
            valueBuffer = newBuffer;
            
            abortCalculations();
            
            if (newWidth > oldWidth) {
                int xmargin = (newWidth - oldWidth) / 2;
                recalculate(newBuffer, new Rectangle(0, 0, xmargin, newHeight));
                recalculate(newBuffer, new Rectangle(newWidth - xmargin, 0, xmargin, newHeight));
            }
            if (newHeight > oldHeight && oldWidth > 0) {
                int ymargin = (newHeight - oldHeight) / 2;
                recalculate(newBuffer, new Rectangle(newxStart, 0, oldWidth, ymargin));
                recalculate(newBuffer, new Rectangle(newxStart, newHeight - ymargin, oldWidth, ymargin));
            }
        }
    }
    
    private void abortCalculations() {
        synchronized (runningCalculators) {
            for (CalculationWorker pointCalculator : runningCalculators) {
                pointCalculator.aborted = true;
            }
        }
    }
    
    private void recalculate(double[][] buffer, Rectangle area) {
        CalculationWorker pointCalculator = new CalculationWorker(buffer, area);
        synchronized (runningCalculators) {
            runningCalculators.add(pointCalculator);
        }
        executorService.submit(pointCalculator);
    }
    
    private class CalculationWorker implements Runnable {
        
        private double[][] buffer;
        private Rectangle area;
        private boolean aborted = false;
        
        CalculationWorker(double[][] buffer, Rectangle area) {
            this.buffer = buffer;
            this.area = area;
        }
        
        public void run() {
            try {
                int reportStart = area.x;
                long lastReport = System.currentTimeMillis();
                for (int x = area.x; x < area.x + area.width; x++) {
                    for (int y = area.y; y < area.y + area.height; y++) {
                        double value = calculate(x, y);
                        
                        if (aborted) {
                            return;
                        }
                        
                        buffer[x][y] = value;
                    }
                    
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastReport >= CALCULATION_REPORT_PERIOD) {
                        Rectangle areaToReport = new Rectangle(reportStart, area.y,
                                x - reportStart + 1, area.height);
                        synchronized (listeners) {
                            for (CalculationListener listener : listeners) {
                                listener.calculated(areaToReport, buffer);
                            }
                        }
                        
                        reportStart = x + 1;
                        lastReport = currentTime;
                    }
                }
                
                if (reportStart < area.x + area.width) {
                    Rectangle areaToReport = new Rectangle(reportStart, area.y,
                            area.x + area.width - reportStart, area.height);
                    synchronized (listeners) {
                        for (CalculationListener listener : listeners) {
                            listener.calculated(areaToReport, buffer);
                        }
                    }
                }
            } finally {
                synchronized (runningCalculators) {
                    runningCalculators.remove(this);
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
            } while (!aborted && absolute <= maxAbsolute && i < iterationLimit);
            
            if (aborted) {
                return 0;
            } else if (i < iterationLimit) {
                return 2 * Math.atan(DIVERGE_FACTOR * i) / Math.PI;
            } else {
                return -2 * Math.atan(NON_DIVERGE_FACTOR * absolute) / Math.PI;
            }
        }
    }

}
