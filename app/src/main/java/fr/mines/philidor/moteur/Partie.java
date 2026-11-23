package fr.mines.philidor.moteur;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Une partie d'échecs : la position, les coups joués et toutes les règles.
 *
 * <p>Les cases sont repérées par une colonne et une ligne de 0 à 7 :
 * a1 est (0, 0), h8 est (7, 7). Les coups s'écrivent en notation UCI :
 * case de départ puis case d'arrivée ({@code "e2e4"}), suivies de la pièce
 * de promotion s'il y a lieu ({@code "e7e8q"}). Le roque s'écrit avec le
 * déplacement du roi ({@code "e1g1"}).</p>
 *
 * <p>Les pièces sont des caractères : {@code 'K' 'Q' 'R' 'B' 'N' 'P'} pour
 * les Blancs (roi, dame, tour, fou, cavalier, pion), les mêmes en minuscules
 * pour les Noirs, et {@code ' '} pour une case vide.</p>
 *
 * <p>Cette classe est fournie : vous n'avez pas à la modifier. Elle n'utilise
 * rien d'Android et se teste avec JUnit.</p>
 */
public class Partie {

    /** L'état de la partie, renvoyé par {@link #etat()}. */
    public enum Etat {
        /** La partie continue. */
        EN_COURS,
        /** Le camp qui a le trait est mat : il a perdu. */
        MAT,
        /** Le camp qui a le trait n'a aucun coup légal mais n'est pas en échec : nulle. */
        PAT,
        /** Aucun des deux camps ne peut plus mater : nulle. */
        NULLE_MATERIEL,
        /** 50 coups de chaque camp sans prise ni coup de pion : nulle. */
        NULLE_50_COUPS,
        /** La même position est apparue trois fois : nulle. */
        NULLE_REPETITION
    }

    private static final char VIDE = ' ';
    private static final String COLONNES = "abcdefgh";
    private static final String LIGNES = "12345678";
    private static final String POSITION_DE_DEPART =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    // Déplacements, en (colonne, ligne).
    private static final int[][] SAUTS_CAVALIER = {
            {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] PAS_DU_ROI = {
            {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}};
    private static final int[][] LIGNES_DROITES = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    private static final int[][] DIAGONALES = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};

    // ------------------------------------------------------------------
    // La position
    // ------------------------------------------------------------------

    /** plateau[colonne][ligne] : la pièce posée sur la case, ou VIDE. */
    private char[][] plateau = new char[8][8];
    private boolean traitAuxBlancs;

    // Droits de roque : perdus dès que le roi ou la tour concernée bouge
    // (ou que la tour est prise).
    private boolean petitRoqueBlanc;
    private boolean grandRoqueBlanc;
    private boolean petitRoqueNoir;
    private boolean grandRoqueNoir;

    /** Colonne du pion qui vient d'avancer de deux cases, ou -1. */
    private int colonnePriseEnPassant = -1;

    /** Demi-coups joués depuis la dernière prise ou le dernier coup de pion. */
    private int demiCoupsSansPriseNiPion;

    /** Numéro du coup en cours (il augmente après chaque coup des Noirs). */
    private int numeroDuCoup = 1;

    // ------------------------------------------------------------------
    // L'historique
    // ------------------------------------------------------------------

    private final List<String> coupsUci = new ArrayList<>();
    private final List<String> coupsEnSan = new ArrayList<>();

    /** Une copie de la position avant chaque coup, pour pouvoir annuler. */
    private final List<Sauvegarde> sauvegardes = new ArrayList<>();

    /** Une « empreinte » de chaque position atteinte, pour la répétition. */
    private final List<String> empreintes = new ArrayList<>();

    /** Un coup, sous une forme commode pour le calcul. */
    private static final class Coup {
        final int colonneDepart;
        final int ligneDepart;
        final int colonneArrivee;
        final int ligneArrivee;
        /** 'q', 'r', 'b', 'n' pour une promotion, VIDE sinon. */
        final char promotion;

        Coup(int colonneDepart, int ligneDepart, int colonneArrivee, int ligneArrivee,
             char promotion) {
            this.colonneDepart = colonneDepart;
            this.ligneDepart = ligneDepart;
            this.colonneArrivee = colonneArrivee;
            this.ligneArrivee = ligneArrivee;
            this.promotion = promotion;
        }

        String uci() {
            String texte = nomCase(colonneDepart, ligneDepart) + nomCase(colonneArrivee, ligneArrivee);
            if (promotion != VIDE) {
                texte += promotion;
            }
            return texte;
        }
    }

    /** Tout ce qu'il faut pour revenir à la position d'avant un coup. */
    private static final class Sauvegarde {
        final char[][] plateau = new char[8][8];
        final boolean traitAuxBlancs;
        final boolean petitRoqueBlanc;
        final boolean grandRoqueBlanc;
        final boolean petitRoqueNoir;
        final boolean grandRoqueNoir;
        final int colonnePriseEnPassant;
        final int demiCoupsSansPriseNiPion;
        final int numeroDuCoup;

        Sauvegarde(Partie p) {
            for (int c = 0; c < 8; c++) {
                plateau[c] = p.plateau[c].clone();
            }
            traitAuxBlancs = p.traitAuxBlancs;
            petitRoqueBlanc = p.petitRoqueBlanc;
            grandRoqueBlanc = p.grandRoqueBlanc;
            petitRoqueNoir = p.petitRoqueNoir;
            grandRoqueNoir = p.grandRoqueNoir;
            colonnePriseEnPassant = p.colonnePriseEnPassant;
            demiCoupsSansPriseNiPion = p.demiCoupsSansPriseNiPion;
            numeroDuCoup = p.numeroDuCoup;
        }

        void restaurer(Partie p) {
            for (int c = 0; c < 8; c++) {
                p.plateau[c] = plateau[c].clone();
            }
            p.traitAuxBlancs = traitAuxBlancs;
            p.petitRoqueBlanc = petitRoqueBlanc;
            p.grandRoqueBlanc = grandRoqueBlanc;
            p.petitRoqueNoir = petitRoqueNoir;
            p.grandRoqueNoir = grandRoqueNoir;
            p.colonnePriseEnPassant = colonnePriseEnPassant;
            p.demiCoupsSansPriseNiPion = demiCoupsSansPriseNiPion;
            p.numeroDuCoup = numeroDuCoup;
        }
    }

    // ==================================================================
    // Création
    // ==================================================================

    /** Une nouvelle partie : position de départ, trait aux Blancs. */
    public Partie() {
        lireFen(POSITION_DE_DEPART);
    }

    /**
     * Reconstruit une partie à partir de ses coups, joués depuis la position
     * de départ.
     *
     * @throws IllegalArgumentException si l'un des coups est illégal.
     */
    public static Partie depuisCoups(List<String> uci) {
        Partie partie = new Partie();
        for (String coup : uci) {
            if (!partie.jouer(coup)) {
                throw new IllegalArgumentException("Coup illégal : " + coup);
            }
        }
        return partie;
    }

    /**
     * Une partie qui commence dans une position quelconque, décrite en
     * notation FEN. Réservé aux tests du kit (visibilité « package »).
     */
    static Partie depuisFen(String fen) {
        Partie partie = new Partie();
        partie.empreintes.clear();
        partie.lireFen(fen);
        return partie;
    }

    // ==================================================================
    // Consultation
    // ==================================================================

    /**
     * La pièce d'une case : 'K' 'Q' 'R' 'B' 'N' 'P' pour les Blancs, en
     * minuscules pour les Noirs, ' ' si la case est vide.
     */
    public char piece(int colonne, int ligne) {
        if (!surLePlateau(colonne, ligne)) {
            throw new IllegalArgumentException("Case hors du plateau : " + colonne + ", " + ligne);
        }
        return plateau[colonne][ligne];
    }

    /** Vrai si c'est aux Blancs de jouer. */
    public boolean traitAuxBlancs() {
        return traitAuxBlancs;
    }

    /** Les coups joués depuis le début, en UCI. */
    public List<String> coups() {
        return Collections.unmodifiableList(new ArrayList<>(coupsUci));
    }

    /** Les coups joués depuis le début, en notation algébrique (e4, Nf3, O-O, Qxf7#). */
    public List<String> coupsSan() {
        return Collections.unmodifiableList(new ArrayList<>(coupsEnSan));
    }

    /** Le dernier coup joué en UCI, ou null si aucun coup n'a été joué. */
    public String dernierCoup() {
        if (coupsUci.isEmpty()) {
            return null;
        }
        return coupsUci.get(coupsUci.size() - 1);
    }

    /** Vrai si le camp qui a le trait est en échec. */
    public boolean enEchec() {
        return roiAttaque(traitAuxBlancs);
    }

    /** Tous les coups légaux de la position, en UCI. */
    public List<String> coupsLegaux() {
        List<String> resultat = new ArrayList<>();
        for (Coup coup : coupsLegauxInternes()) {
            resultat.add(coup.uci());
        }
        return resultat;
    }

    /** Les coups légaux de la pièce posée sur cette case (liste vide s'il n'y en a pas). */
    public List<String> coupsDepuis(int colonne, int ligne) {
        List<String> resultat = new ArrayList<>();
        for (Coup coup : coupsLegauxInternes()) {
            if (coup.colonneDepart == colonne && coup.ligneDepart == ligne) {
                resultat.add(coup.uci());
            }
        }
        return resultat;
    }

    /** L'état de la partie : en cours, mat, pat ou l'une des nulles. */
    public Etat etat() {
        if (coupsLegauxInternes().isEmpty()) {
            return enEchec() ? Etat.MAT : Etat.PAT;
        }
        if (materielInsuffisant()) {
            return Etat.NULLE_MATERIEL;
        }
        if (demiCoupsSansPriseNiPion >= 100) {
            return Etat.NULLE_50_COUPS;
        }
        if (nombreDApparitions(empreinte()) >= 3) {
            return Etat.NULLE_REPETITION;
        }
        return Etat.EN_COURS;
    }

    // ==================================================================
    // Jouer et annuler
    // ==================================================================

    /**
     * Joue le coup s'il est légal et renvoie true ; sinon ne fait rien et
     * renvoie false. Une promotion doit préciser la pièce : "e7e8q" (ou
     * "e7e8Q" : la casse ne compte pas) ; "e7e8" seul est refusé.
     */
    public boolean jouer(String uci) {
        if (uci == null) {
            return false;
        }
        uci = uci.toLowerCase(Locale.ROOT);
        List<Coup> legaux = coupsLegauxInternes();
        Coup coup = null;
        for (Coup candidat : legaux) {
            if (candidat.uci().equals(uci)) {
                coup = candidat;
            }
        }
        if (coup == null) {
            return false;
        }

        // La notation algébrique se calcule en deux temps : le coup lui-même
        // avant de le jouer, puis « + » ou « # » une fois qu'il est joué.
        String san = sanSansSuffixe(coup, legaux);
        appliquer(coup);
        if (enEchec()) {
            san += coupsLegauxInternes().isEmpty() ? "#" : "+";
        }

        coupsUci.add(uci);
        coupsEnSan.add(san);
        empreintes.add(empreinte());
        return true;
    }

    /** Annule le dernier coup joué (ne fait rien si aucun coup n'a été joué). */
    public void annuler() {
        if (coupsUci.isEmpty()) {
            return;
        }
        defaire();
        coupsUci.remove(coupsUci.size() - 1);
        coupsEnSan.remove(coupsEnSan.size() - 1);
        empreintes.remove(empreintes.size() - 1);
    }

    // ==================================================================
    // Génération des coups
    // ==================================================================

    /**
     * Les coups légaux : on génère les coups « pseudo-légaux » (qui suivent
     * le déplacement des pièces), on joue chacun d'eux, et on ne garde que
     * ceux qui ne laissent pas son propre roi en échec.
     */
    private List<Coup> coupsLegauxInternes() {
        List<Coup> legaux = new ArrayList<>();
        boolean blancs = traitAuxBlancs;
        for (Coup coup : coupsPseudoLegaux()) {
            appliquer(coup);
            if (!roiAttaque(blancs)) {
                legaux.add(coup);
            }
            defaire();
        }
        return legaux;
    }

    private List<Coup> coupsPseudoLegaux() {
        List<Coup> coups = new ArrayList<>();
        for (int c = 0; c < 8; c++) {
            for (int l = 0; l < 8; l++) {
                char p = plateau[c][l];
                if (p == VIDE || estBlanche(p) != traitAuxBlancs) {
                    continue;
                }
                switch (Character.toLowerCase(p)) {
                    case 'p':
                        ajouterCoupsDePion(coups, c, l);
                        break;
                    case 'n':
                        ajouterSauts(coups, c, l, SAUTS_CAVALIER);
                        break;
                    case 'b':
                        ajouterGlissements(coups, c, l, DIAGONALES);
                        break;
                    case 'r':
                        ajouterGlissements(coups, c, l, LIGNES_DROITES);
                        break;
                    case 'q':
                        ajouterGlissements(coups, c, l, DIAGONALES);
                        ajouterGlissements(coups, c, l, LIGNES_DROITES);
                        break;
                    case 'k':
                        ajouterSauts(coups, c, l, PAS_DU_ROI);
                        ajouterRoques(coups, c, l);
                        break;
                    default:
                        break;
                }
            }
        }
        return coups;
    }

    /** Cavalier et roi : un seul pas dans chaque direction. */
    private void ajouterSauts(List<Coup> coups, int c, int l, int[][] deplacements) {
        for (int[] d : deplacements) {
            int c2 = c + d[0];
            int l2 = l + d[1];
            if (surLePlateau(c2, l2) && !occupeeParMonCamp(c2, l2)) {
                coups.add(new Coup(c, l, c2, l2, VIDE));
            }
        }
    }

    /** Fou, tour, dame : on avance dans chaque direction jusqu'à un obstacle. */
    private void ajouterGlissements(List<Coup> coups, int c, int l, int[][] directions) {
        for (int[] d : directions) {
            int c2 = c + d[0];
            int l2 = l + d[1];
            while (surLePlateau(c2, l2)) {
                if (plateau[c2][l2] == VIDE) {
                    coups.add(new Coup(c, l, c2, l2, VIDE));
                } else {
                    if (!occupeeParMonCamp(c2, l2)) {
                        coups.add(new Coup(c, l, c2, l2, VIDE));   // une prise
                    }
                    break;                                         // la route est barrée
                }
                c2 += d[0];
                l2 += d[1];
            }
        }
    }

    private void ajouterCoupsDePion(List<Coup> coups, int c, int l) {
        int sens = traitAuxBlancs ? 1 : -1;            // les Blancs montent, les Noirs descendent
        int ligneDeDepart = traitAuxBlancs ? 1 : 6;
        int l2 = l + sens;
        if (!surLePlateau(c, l2)) {
            return;
        }

        // Avancer d'une case, ou de deux depuis la ligne de départ.
        if (plateau[c][l2] == VIDE) {
            ajouterCoupDePion(coups, c, l, c, l2);
            int l3 = l + 2 * sens;
            if (l == ligneDeDepart && plateau[c][l3] == VIDE) {
                coups.add(new Coup(c, l, c, l3, VIDE));
            }
        }

        // Prendre en diagonale, y compris en passant.
        int ligneEnPassant = traitAuxBlancs ? 5 : 2;
        for (int dc = -1; dc <= 1; dc += 2) {
            int c2 = c + dc;
            if (!surLePlateau(c2, l2)) {
                continue;
            }
            boolean prise = plateau[c2][l2] != VIDE && !occupeeParMonCamp(c2, l2);
            boolean enPassant = c2 == colonnePriseEnPassant && l2 == ligneEnPassant;
            if (prise || enPassant) {
                ajouterCoupDePion(coups, c, l, c2, l2);
            }
        }
    }

    /** Un coup de pion ; sur la dernière ligne, il devient quatre promotions. */
    private void ajouterCoupDePion(List<Coup> coups, int c, int l, int c2, int l2) {
        if (l2 == 7 || l2 == 0) {
            for (char promotion : new char[]{'q', 'r', 'b', 'n'}) {
                coups.add(new Coup(c, l, c2, l2, promotion));
            }
        } else {
            coups.add(new Coup(c, l, c2, l2, VIDE));
        }
    }

    /**
     * Le roque : le roi et la tour n'ont jamais bougé (droits de roque), les
     * cases entre eux sont vides, et le roi ne part pas d'une case attaquée,
     * ne traverse pas de case attaquée et n'arrive pas sur une case attaquée.
     */
    private void ajouterRoques(List<Coup> coups, int c, int l) {
        boolean petit = traitAuxBlancs ? petitRoqueBlanc : petitRoqueNoir;
        boolean grand = traitAuxBlancs ? grandRoqueBlanc : grandRoqueNoir;
        int ligne = traitAuxBlancs ? 0 : 7;
        if (c != 4 || l != ligne || (!petit && !grand)) {
            return;
        }
        boolean adversaire = !traitAuxBlancs;
        if (caseAttaquee(4, ligne, adversaire)) {
            return;                                        // pas de roque quand on est en échec
        }
        if (petit
                && plateau[5][ligne] == VIDE && plateau[6][ligne] == VIDE
                && !caseAttaquee(5, ligne, adversaire) && !caseAttaquee(6, ligne, adversaire)) {
            coups.add(new Coup(4, ligne, 6, ligne, VIDE));
        }
        if (grand
                && plateau[3][ligne] == VIDE && plateau[2][ligne] == VIDE && plateau[1][ligne] == VIDE
                && !caseAttaquee(3, ligne, adversaire) && !caseAttaquee(2, ligne, adversaire)) {
            coups.add(new Coup(4, ligne, 2, ligne, VIDE));
        }
    }

    // ==================================================================
    // Échec : les cases attaquées
    // ==================================================================

    /** Vrai si le roi du camp donné est attaqué par l'adversaire. */
    private boolean roiAttaque(boolean roiBlanc) {
        char roi = roiBlanc ? 'K' : 'k';
        for (int c = 0; c < 8; c++) {
            for (int l = 0; l < 8; l++) {
                if (plateau[c][l] == roi) {
                    return caseAttaquee(c, l, !roiBlanc);
                }
            }
        }
        return false;                                      // pas de roi : ne devrait pas arriver
    }

    /** Vrai si une pièce du camp donné attaque la case (c, l). */
    private boolean caseAttaquee(int c, int l, boolean parLesBlancs) {
        // Un pion attaque en diagonale vers l'avant : un pion blanc qui
        // attaque (c, l) est donc une ligne plus bas.
        int lignePion = parLesBlancs ? l - 1 : l + 1;
        char pion = parLesBlancs ? 'P' : 'p';
        if (pieceEn(c - 1, lignePion) == pion || pieceEn(c + 1, lignePion) == pion) {
            return true;
        }
        if (attaqueParSaut(c, l, SAUTS_CAVALIER, parLesBlancs ? 'N' : 'n')) {
            return true;
        }
        if (attaqueParSaut(c, l, PAS_DU_ROI, parLesBlancs ? 'K' : 'k')) {
            return true;
        }
        char dame = parLesBlancs ? 'Q' : 'q';
        if (attaqueParGlissement(c, l, LIGNES_DROITES, parLesBlancs ? 'R' : 'r', dame)) {
            return true;
        }
        return attaqueParGlissement(c, l, DIAGONALES, parLesBlancs ? 'B' : 'b', dame);
    }

    private boolean attaqueParSaut(int c, int l, int[][] deplacements, char piece) {
        for (int[] d : deplacements) {
            if (pieceEn(c + d[0], l + d[1]) == piece) {
                return true;
            }
        }
        return false;
    }

    private boolean attaqueParGlissement(int c, int l, int[][] directions, char piece, char dame) {
        for (int[] d : directions) {
            int c2 = c + d[0];
            int l2 = l + d[1];
            while (surLePlateau(c2, l2)) {
                char p = plateau[c2][l2];
                if (p != VIDE) {
                    if (p == piece || p == dame) {
                        return true;
                    }
                    break;                                 // la première pièce rencontrée bloque
                }
                c2 += d[0];
                l2 += d[1];
            }
        }
        return false;
    }

    // ==================================================================
    // Appliquer et défaire un coup (sans aucune vérification)
    // ==================================================================

    private void appliquer(Coup coup) {
        sauvegardes.add(new Sauvegarde(this));

        int c1 = coup.colonneDepart;
        int l1 = coup.ligneDepart;
        int c2 = coup.colonneArrivee;
        int l2 = coup.ligneArrivee;
        char piece = plateau[c1][l1];
        char type = Character.toLowerCase(piece);
        boolean prise = plateau[c2][l2] != VIDE;

        // Prise en passant : un pion qui change de colonne vers une case vide
        // prend le pion adverse posé à côté de lui.
        if (type == 'p' && c1 != c2 && !prise) {
            plateau[c2][l1] = VIDE;
            prise = true;
        }

        // Déplacer la pièce (ou la remplacer par la pièce de promotion).
        if (coup.promotion != VIDE) {
            piece = traitAuxBlancs ? Character.toUpperCase(coup.promotion) : coup.promotion;
        }
        plateau[c2][l2] = piece;
        plateau[c1][l1] = VIDE;

        // Roque : le roi se déplace de deux colonnes, la tour saute par-dessus.
        if (type == 'k' && Math.abs(c2 - c1) == 2) {
            int colonneTour = c2 == 6 ? 7 : 0;
            int nouvelleColonneTour = c2 == 6 ? 5 : 3;
            plateau[nouvelleColonneTour][l1] = plateau[colonneTour][l1];
            plateau[colonneTour][l1] = VIDE;
        }

        // Un roi ou une tour qui part de sa case, ou une tour prise chez elle :
        // les droits de roque correspondants sont perdus.
        retirerDroitsDeRoque(c1, l1);
        retirerDroitsDeRoque(c2, l2);

        colonnePriseEnPassant = (type == 'p' && Math.abs(l2 - l1) == 2) ? c1 : -1;
        demiCoupsSansPriseNiPion = (type == 'p' || prise) ? 0 : demiCoupsSansPriseNiPion + 1;
        if (!traitAuxBlancs) {
            numeroDuCoup++;
        }
        traitAuxBlancs = !traitAuxBlancs;
    }

    private void defaire() {
        Sauvegarde sauvegarde = sauvegardes.remove(sauvegardes.size() - 1);
        sauvegarde.restaurer(this);
    }

    private void retirerDroitsDeRoque(int c, int l) {
        if (c == 4 && l == 0) {
            petitRoqueBlanc = false;
            grandRoqueBlanc = false;
        } else if (c == 7 && l == 0) {
            petitRoqueBlanc = false;
        } else if (c == 0 && l == 0) {
            grandRoqueBlanc = false;
        } else if (c == 4 && l == 7) {
            petitRoqueNoir = false;
            grandRoqueNoir = false;
        } else if (c == 7 && l == 7) {
            petitRoqueNoir = false;
        } else if (c == 0 && l == 7) {
            grandRoqueNoir = false;
        }
    }

    // ==================================================================
    // Notation algébrique (SAN)
    // ==================================================================

    /** Le coup en notation algébrique, sans le « + » ni le « # » final. */
    private String sanSansSuffixe(Coup coup, List<Coup> legaux) {
        char piece = plateau[coup.colonneDepart][coup.ligneDepart];
        char type = Character.toUpperCase(piece);
        String arrivee = nomCase(coup.colonneArrivee, coup.ligneArrivee);
        boolean prise = plateau[coup.colonneArrivee][coup.ligneArrivee] != VIDE;

        if (type == 'K' && Math.abs(coup.colonneArrivee - coup.colonneDepart) == 2) {
            return coup.colonneArrivee == 6 ? "O-O" : "O-O-O";
        }

        StringBuilder san = new StringBuilder();
        if (type == 'P') {
            // Un pion qui prend est désigné par sa colonne : exd5.
            if (coup.colonneDepart != coup.colonneArrivee) {
                san.append(COLONNES.charAt(coup.colonneDepart)).append('x');
            }
            san.append(arrivee);
            if (coup.promotion != VIDE) {
                san.append('=').append(Character.toUpperCase(coup.promotion));
            }
            return san.toString();
        }

        san.append(type);
        san.append(desambiguisation(coup, type, legaux));
        if (prise) {
            san.append('x');
        }
        san.append(arrivee);
        return san.toString();
    }

    /**
     * Quand deux pièces du même type peuvent aller sur la même case, on
     * précise la colonne de départ (Nbd7), sinon la ligne (R1e2), sinon les
     * deux (Qh4e1).
     */
    private String desambiguisation(Coup coup, char type, List<Coup> legaux) {
        boolean ambigu = false;
        boolean memeColonne = false;
        boolean memeLigne = false;
        for (Coup autre : legaux) {
            boolean autrePiece = autre.colonneDepart != coup.colonneDepart
                    || autre.ligneDepart != coup.ligneDepart;
            boolean memeType = Character.toUpperCase(
                    plateau[autre.colonneDepart][autre.ligneDepart]) == type;
            boolean memeArrivee = autre.colonneArrivee == coup.colonneArrivee
                    && autre.ligneArrivee == coup.ligneArrivee;
            if (autrePiece && memeType && memeArrivee) {
                ambigu = true;
                if (autre.colonneDepart == coup.colonneDepart) {
                    memeColonne = true;
                }
                if (autre.ligneDepart == coup.ligneDepart) {
                    memeLigne = true;
                }
            }
        }
        if (!ambigu) {
            return "";
        }
        char colonne = COLONNES.charAt(coup.colonneDepart);
        char ligne = LIGNES.charAt(coup.ligneDepart);
        if (!memeColonne) {
            return String.valueOf(colonne);
        }
        if (!memeLigne) {
            return String.valueOf(ligne);
        }
        return "" + colonne + ligne;
    }

    // ==================================================================
    // Les nulles
    // ==================================================================

    /**
     * Matériel insuffisant pour mater : ni pion, ni tour, ni dame, et soit
     * au plus un cavalier sans aucun fou (roi contre roi, roi et cavalier
     * contre roi), soit des fous tous sur des cases de même couleur sans
     * aucun cavalier.
     */
    private boolean materielInsuffisant() {
        int cavaliers = 0;
        int fousSurCaseClaire = 0;
        int fousSurCaseFoncee = 0;
        for (int c = 0; c < 8; c++) {
            for (int l = 0; l < 8; l++) {
                char type = Character.toLowerCase(plateau[c][l]);
                if (type == 'p' || type == 'r' || type == 'q') {
                    return false;
                }
                if (type == 'n') {
                    cavaliers++;
                } else if (type == 'b') {
                    if ((c + l) % 2 == 0) {
                        fousSurCaseFoncee++;                // a1 (0 + 0) est une case foncée
                    } else {
                        fousSurCaseClaire++;
                    }
                }
            }
        }
        int fous = fousSurCaseClaire + fousSurCaseFoncee;
        if (fous == 0) {
            return cavaliers <= 1;
        }
        boolean fousDeMemeCouleur = fousSurCaseClaire == 0 || fousSurCaseFoncee == 0;
        return cavaliers == 0 && fousDeMemeCouleur;
    }

    /**
     * L'empreinte d'une position, pour la règle de la triple répétition :
     * deux positions sont « les mêmes » si les pièces sont sur les mêmes
     * cases, avec le même trait, les mêmes droits de roque et les mêmes
     * prises en passant réellement jouables.
     */
    private String empreinte() {
        StringBuilder texte = new StringBuilder();
        for (int l = 0; l < 8; l++) {
            for (int c = 0; c < 8; c++) {
                texte.append(plateau[c][l]);
            }
        }
        texte.append(traitAuxBlancs ? 'w' : 'b');
        texte.append(petitRoqueBlanc ? 'K' : '-').append(grandRoqueBlanc ? 'Q' : '-');
        texte.append(petitRoqueNoir ? 'k' : '-').append(grandRoqueNoir ? 'q' : '-');
        texte.append(caseEnPassantJouable());
        return texte.toString();
    }

    private int nombreDApparitions(String empreinte) {
        int n = 0;
        for (String e : empreintes) {
            if (e.equals(empreinte)) {
                n++;
            }
        }
        return n;
    }

    /** La case de prise en passant si une prise en passant est légale, sinon "-". */
    private String caseEnPassantJouable() {
        if (colonnePriseEnPassant < 0) {
            return "-";
        }
        int ligne = traitAuxBlancs ? 5 : 2;
        for (Coup coup : coupsLegauxInternes()) {
            char type = Character.toLowerCase(plateau[coup.colonneDepart][coup.ligneDepart]);
            if (type == 'p' && coup.colonneArrivee == colonnePriseEnPassant
                    && coup.ligneArrivee == ligne && coup.colonneDepart != coup.colonneArrivee) {
                return nomCase(colonnePriseEnPassant, ligne);
            }
        }
        return "-";
    }

    // ==================================================================
    // Notation FEN (utilisée par les tests du kit)
    // ==================================================================

    private void lireFen(String fen) {
        String[] champs = fen.trim().split("\\s+");
        String[] rangees = champs[0].split("/");
        for (int c = 0; c < 8; c++) {
            for (int l = 0; l < 8; l++) {
                plateau[c][l] = VIDE;
            }
        }
        for (int i = 0; i < 8; i++) {
            int ligne = 7 - i;                              // la FEN commence par la 8e rangée
            int colonne = 0;
            for (char car : rangees[i].toCharArray()) {
                if (Character.isDigit(car)) {
                    colonne += car - '0';
                } else {
                    plateau[colonne][ligne] = car;
                    colonne++;
                }
            }
        }
        traitAuxBlancs = champs[1].equals("w");
        String roques = champs[2];
        petitRoqueBlanc = roques.contains("K");
        grandRoqueBlanc = roques.contains("Q");
        petitRoqueNoir = roques.contains("k");
        grandRoqueNoir = roques.contains("q");
        colonnePriseEnPassant = champs[3].equals("-") ? -1 : COLONNES.indexOf(champs[3].charAt(0));
        demiCoupsSansPriseNiPion = champs.length > 4 ? Integer.parseInt(champs[4]) : 0;
        numeroDuCoup = champs.length > 5 ? Integer.parseInt(champs[5]) : 1;
        empreintes.add(empreinte());
    }

    /** La position en notation FEN, comme python-chess. Réservé aux tests. */
    String fen() {
        StringBuilder texte = new StringBuilder();
        for (int l = 7; l >= 0; l--) {
            int vides = 0;
            for (int c = 0; c < 8; c++) {
                if (plateau[c][l] == VIDE) {
                    vides++;
                } else {
                    if (vides > 0) {
                        texte.append(vides);
                        vides = 0;
                    }
                    texte.append(plateau[c][l]);
                }
            }
            if (vides > 0) {
                texte.append(vides);
            }
            if (l > 0) {
                texte.append('/');
            }
        }
        texte.append(traitAuxBlancs ? " w " : " b ");
        String roques = (petitRoqueBlanc ? "K" : "") + (grandRoqueBlanc ? "Q" : "")
                + (petitRoqueNoir ? "k" : "") + (grandRoqueNoir ? "q" : "");
        texte.append(roques.isEmpty() ? "-" : roques);
        texte.append(' ').append(caseEnPassantJouable());
        texte.append(' ').append(demiCoupsSansPriseNiPion);
        texte.append(' ').append(numeroDuCoup);
        return texte.toString();
    }

    // ==================================================================
    // Petits outils
    // ==================================================================

    private static boolean surLePlateau(int c, int l) {
        return c >= 0 && c < 8 && l >= 0 && l < 8;
    }

    /** La pièce d'une case, ou VIDE si la case est hors du plateau. */
    private char pieceEn(int c, int l) {
        return surLePlateau(c, l) ? plateau[c][l] : VIDE;
    }

    private static boolean estBlanche(char piece) {
        return Character.isUpperCase(piece);
    }

    private boolean occupeeParMonCamp(int c, int l) {
        char p = plateau[c][l];
        return p != VIDE && estBlanche(p) == traitAuxBlancs;
    }

    private static String nomCase(int c, int l) {
        return "" + COLONNES.charAt(c) + LIGNES.charAt(l);
    }
}
