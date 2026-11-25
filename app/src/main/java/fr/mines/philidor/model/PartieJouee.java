package fr.mines.philidor.model;

import java.util.Date;

import fr.mines.philidor.activity.MainActivity;
import fr.mines.philidor.moteur.Partie;

public class PartieJouee {

    public enum RESULTAT {VICTOIRE, DEFAITE, NULL}

    private MainActivity.COULEUR couleur;
    private RESULTAT resultat;
    private int nombreCoups;
    private Date date;


    public PartieJouee(MainActivity.COULEUR couleur, RESULTAT resultat, int nombreCoups, Date date) {
        this.couleur = couleur;
        this.resultat = resultat;
        this.nombreCoups = nombreCoups;
        this.date = date;
    }

    public MainActivity.COULEUR getCouleur() {
        return couleur;
    }

    public void setCouleur(MainActivity.COULEUR couleur) {
        this.couleur = couleur;
    }

    public RESULTAT getResultat() {
        return resultat;
    }

    public void setResultat(RESULTAT resultat) {
        this.resultat = resultat;
    }

    public int getNombreCoups() {
        return nombreCoups;
    }

    public void setNombreCoups(int nombreCoups) {
        this.nombreCoups = nombreCoups;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }
}
