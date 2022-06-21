package fr.ign.artiscales.pm.division;

/**
 * SuperClass for every Division profiles.
 */
public abstract class Division {
    /**
     * Show every information on console and export intermediate states
     */
    private static boolean DEBUG;

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
