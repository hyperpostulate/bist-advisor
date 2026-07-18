package org.mesutormanli.bistadvisor.portfolio;

import org.mesutormanli.bistadvisor.config.AdvisorMode;
import org.mesutormanli.bistadvisor.config.ModelType;

/**
 * Tek bir pozisyon: sembol, lot ve agirlikli ortalama giris maliyeti.
 */
public class Position {
    public String symbol;
    public int lots;
    public double avgCost;

    public Position() {
    }

    public Position(String symbol, int lots, double avgCost) {
        this.symbol = symbol;
        this.lots = lots;
        this.avgCost = avgCost;
    }
}
