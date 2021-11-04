package fr.ign.artiscales.pm.division;

public abstract class Division {
    public static boolean isDEBUG() {
        return DEBUG;
    }

    public static void setDEBUG(boolean DEBUG) {
        Division.DEBUG = DEBUG;
    }

    private static boolean DEBUG;
}
