package fr.mines.philidor.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import fr.mines.philidor.activity.MainActivity;
import fr.mines.philidor.model.PartieJouee;

public class HistoryService {

    private List<PartieJouee> history = new ArrayList<>();

    private static HistoryService INSTANCE;

    private HistoryService() {
        // MOCK
        history.add(new PartieJouee(MainActivity.COULEUR.noir, PartieJouee.RESULTAT.DEFAITE, 45, new Date()));
        history.add(new PartieJouee(MainActivity.COULEUR.blanc, PartieJouee.RESULTAT.VICTOIRE, 65, new Date()));
        history.add(new PartieJouee(MainActivity.COULEUR.noir, PartieJouee.RESULTAT.NULL, 23, new Date()));
        history.add(new PartieJouee(MainActivity.COULEUR.noir, PartieJouee.RESULTAT.DEFAITE, 67, new Date()));
        history.add(new PartieJouee(MainActivity.COULEUR.blanc, PartieJouee.RESULTAT.VICTOIRE, 32, new Date()));
        history.add(new PartieJouee(MainActivity.COULEUR.blanc, PartieJouee.RESULTAT.DEFAITE, 4, new Date()));
        history.add(new PartieJouee(MainActivity.COULEUR.noir, PartieJouee.RESULTAT.DEFAITE, 63, new Date()));
        history.add(new PartieJouee(MainActivity.COULEUR.noir, PartieJouee.RESULTAT.VICTOIRE, 45, new Date()));
        history.add(new PartieJouee(MainActivity.COULEUR.noir, PartieJouee.RESULTAT.NULL, 98, new Date()));

    }

    public synchronized static HistoryService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new HistoryService();
        }
        return INSTANCE;
    }


    public List<PartieJouee> getHistory() {
        return history;
    }


    public int nbParties() {
        return history.size();
    }

    public int nbPartiesWin() {
        return Math.toIntExact(history.stream().filter(p -> p.getResultat().equals(PartieJouee.RESULTAT.VICTOIRE)).count());
    }


    public void deleteHistory(int position) {
        this.history.remove(position);
    }
}
