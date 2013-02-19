/**
 * Funky Mandelbrot Application (c) 2013 by Miro
 */
package funky.mandelbrot;

import java.awt.Graphics;

import javax.swing.JPanel;

/**
 * @author msp@informatik.uni-kiel.de
 */
public class MandelbrotCanvas extends JPanel {

    private static final long serialVersionUID = 7892870465027356734L;
    
    private MandelbrotCalculator calculator = new MandelbrotCalculator();
    
    public void paint(Graphics g) {
        
    }

}
