package fr.ign.artiscales.pm.division;

import org.apache.commons.math3.random.MersenneTwister;

/**
 * SuperClass for every Division profiles.
 */
public abstract class Division {
    /**
     * Show every information on console and export intermediate states
     */
    private static boolean DEBUG;
    private static MersenneTwister random = new MersenneTwister();

    public static MersenneTwister getRandom() {
        return random;
    }

    public static void setSeed(long seed) {
        random = new MersenneTwister(seed);
    }

    /**
     * Do we show every information on console and export intermediate states ?
     *
     * @return true if debug mode is on.
     */
    public static boolean isDEBUG() {
        return DEBUG;
    }

    /**
     * Turn the debung mode on (default is off).
     *
     * @param DEBUG change debug mode
     */
    public static void setDEBUG(boolean DEBUG) {
        Division.DEBUG = DEBUG;
    }
}
