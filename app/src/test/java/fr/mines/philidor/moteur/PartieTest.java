package fr.mines.philidor.moteur;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests du kit : les règles de {@link Partie}. Tests locaux, sans appareil :
 * clic droit sur ce fichier → Run 'PartieTest', ou ./gradlew test.
 */
public class PartieTest {

    // ------------------------------------------------------------------
    // Perft : on compte toutes les suites de coups légaux jusqu'à une
    // profondeur donnée. Valeurs de référence :
    // https://www.chessprogramming.org/Perft_Results
    // Une seule erreur de règle change ces nombres.
    // ------------------------------------------------------------------

    private static long perft(Partie partie, int profondeur) {
        List<String> coups = partie.coupsLegaux();
        if (profondeur == 1) {
            return coups.size();
        }
        long total = 0;
        for (String coup : coups) {
            partie.jouer(coup);
            total += perft(partie, profondeur - 1);
            partie.annuler();
        }
        return total;
    }

    @Test
    public void perftDepuisLaPositionDeDepart() {
        Partie p = new Partie();
        assertEquals(20, perft(p, 1));
        assertEquals(400, perft(p, 2));
        assertEquals(8_902, perft(p, 3));
        assertEquals(197_281, perft(p, 4));
        assertTrue("annuler() doit tout remettre en place", p.coups().isEmpty());
        assertEquals(20, p.coupsLegaux().size());
    }

    @Test
    public void perftKiwipete() {
        // Une position pleine de roques, de prises en passant et de promotions.
        Partie p = Partie.depuisFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        assertEquals(48, perft(p, 1));
        assertEquals(2_039, perft(p, 2));
        assertEquals(97_862, perft(p, 3));
    }

    // ------------------------------------------------------------------
    // Cas particuliers
    // ------------------------------------------------------------------

    private static Partie jouer(String... coups) {
        return Partie.depuisCoups(Arrays.asList(coups));
    }

    @Test
    public void etape0DuGuide() {
        Partie p = new Partie();
        assertEquals(20, p.coupsLegaux().size());
        assertTrue(p.traitAuxBlancs());
        assertNull(p.dernierCoup());
        assertFalse(p.jouer("e2e5"));
        assertTrue(p.jouer("e2e4"));
        assertTrue(p.jouer("e7e5"));
        assertEquals(Arrays.asList("e4", "e5"), p.coupsSan());
        assertEquals(Arrays.asList("e2e4", "e7e5"), p.coups());
        assertEquals("e7e5", p.dernierCoup());
    }

    @Test
    public void pieces() {
        Partie p = new Partie();
        assertEquals('R', p.piece(0, 0));                  // a1
        assertEquals('Q', p.piece(3, 0));                  // d1 : dame sur sa couleur
        assertEquals('K', p.piece(4, 0));                  // e1
        assertEquals('p', p.piece(4, 6));                  // e7
        assertEquals('k', p.piece(4, 7));                  // e8
        assertEquals(' ', p.piece(4, 4));                  // e5
        assertEquals('r', p.piece(7, 7));                  // h8
    }

    @Test(expected = IllegalArgumentException.class)
    public void caseHorsDuPlateau() {
        new Partie().piece(8, 0);
    }

    @Test
    public void coupsDepuis() {
        Partie p = new Partie();
        assertEquals(Arrays.asList("g1f3", "g1h3"), sorted(p.coupsDepuis(6, 0)));
        assertEquals(Arrays.asList("e2e3", "e2e4"), sorted(p.coupsDepuis(4, 1)));
        assertTrue(p.coupsDepuis(4, 0).isEmpty());          // roi bloqué
        assertTrue(p.coupsDepuis(4, 6).isEmpty());          // pion noir : pas son tour
        assertTrue(p.coupsDepuis(4, 4).isEmpty());          // case vide
    }

    @Test
    public void lesListesNeSeModifientPasDeLExterieur() {
        Partie p = jouer("e2e4");
        try {
            p.coups().add("e7e5");
        } catch (UnsupportedOperationException attendue) {
            // c'est bien ce qu'on veut
        }
        assertEquals(1, p.coups().size());
    }

    @Test
    public void matDuBerger() {
        Partie p = jouer("e2e4", "e7e5", "d1h5", "b8c6", "f1c4", "g8f6", "h5f7");
        assertEquals(Arrays.asList("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#"), p.coupsSan());
        assertEquals(Partie.Etat.MAT, p.etat());
        assertTrue(p.enEchec());
        assertTrue(p.coupsLegaux().isEmpty());
        p.annuler();
        assertEquals(Partie.Etat.EN_COURS, p.etat());
        assertEquals("g8f6", p.dernierCoup());
    }

    @Test
    public void roques() {
        Partie p = jouer("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5");
        assertTrue(p.coupsLegaux().contains("e1g1"));
        assertTrue(p.jouer("e1g1"));
        assertEquals('R', p.piece(5, 0));
        assertEquals('K', p.piece(6, 0));
        assertEquals("O-O", p.coupsSan().get(6));
        p.annuler();
        assertEquals('R', p.piece(7, 0));
        assertEquals('K', p.piece(4, 0));
    }

    @Test
    public void pasDeRoqueATraversUneCaseAttaquee() {
        // Une tour noire sur la colonne f ouverte : le roi blanc ne peut pas
        // traverser f1, donc pas de petit roque.
        Partie p = Partie.depuisFen("4kr2/8/8/8/8/8/8/4K2R w K - 0 1");
        assertFalse(p.coupsLegaux().contains("e1g1"));
        p = Partie.depuisFen("4k3/8/8/8/8/8/8/4K2R w K - 0 1");
        assertTrue(p.coupsLegaux().contains("e1g1"));
    }

    @Test
    public void priseEnPassant() {
        Partie p = jouer("e2e4", "a7a6", "e4e5", "d7d5");
        assertTrue(p.jouer("e5d6"));
        assertEquals("exd6", p.coupsSan().get(4));
        assertEquals(' ', p.piece(3, 4));                  // le pion de d5 a disparu
        assertEquals('P', p.piece(3, 5));
        p.annuler();
        assertEquals('p', p.piece(3, 4));
    }

    @Test
    public void promotion() {
        Partie p = Partie.depuisFen("8/4P3/8/8/8/8/k7/4K3 w - - 0 1");
        assertFalse("la pièce de promotion est obligatoire", p.jouer("e7e8"));
        assertEquals(4, p.coupsDepuis(4, 6).size());
        assertTrue(p.jouer("e7e8n"));
        assertEquals('N', p.piece(4, 7));
        assertEquals("e8=N", p.coupsSan().get(0));
        p.annuler();
        assertTrue(p.jouer("e7e8q"));
        assertEquals("e8=Q", p.coupsSan().get(0));
    }

    @Test
    public void promotionAvecEchec() {
        Partie p = Partie.depuisFen("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1");
        assertTrue(p.jouer("b7b8q"));
        assertEquals("b8=Q+", p.coupsSan().get(0));
    }

    @Test
    public void desambiguisation() {
        Partie p = Partie.depuisFen("4k3/8/8/8/8/8/R6R/4K3 w - - 0 1");
        assertTrue(p.jouer("a2d2"));
        assertEquals("Rad2", p.coupsSan().get(0));
        p = Partie.depuisFen("4k3/8/8/R7/8/8/8/R3K3 w - - 0 1");
        assertTrue(p.jouer("a1a3"));
        assertEquals("R1a3", p.coupsSan().get(0));
    }

    @Test
    public void sanDuSujet() {
        Partie p = jouer("d2d4", "d7d5", "g1f3", "g8f6", "c2c4", "b8d7");
        assertEquals("Nbd7", p.coupsSan().get(5));         // les deux cavaliers peuvent aller en d7
        p = jouer("e2e4", "d7d5", "e4d5");
        assertEquals("exd5", p.coupsSan().get(2));
        p = Partie.depuisFen("k7/4P3/8/8/8/8/8/4K3 w - - 0 1");
        assertTrue(p.jouer("e7e8q"));
        assertEquals("e8=Q+", p.coupsSan().get(0));
        p = Partie.depuisFen("k7/8/8/8/8/4R3/8/4R2K w - - 0 1");
        assertTrue(p.jouer("e1e2"));
        assertEquals("R1e2", p.coupsSan().get(0));         // deux tours sur la colonne e
        p = jouer("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1g1", "f8e7",
                "b1c3", "d7d6", "d2d3", "c8g4", "c1g5", "d8d7", "d1d2", "e8c8");
        assertEquals("O-O", p.coupsSan().get(6));
        assertEquals("O-O-O", p.coupsSan().get(15));
    }

    @Test
    public void promotionSansLettreRefuseeEtCasseIgnoree() {
        Partie p = Partie.depuisFen("8/4P3/8/8/8/8/k7/4K3 w - - 0 1");
        assertFalse(p.jouer("e7e8"));
        assertTrue(p.coups().isEmpty());
        assertTrue(p.jouer("e7e8Q"));
        assertEquals("e7e8q", p.dernierCoup());
        assertEquals('Q', p.piece(4, 7));
    }

    @Test
    public void lesSixEtats() {
        assertEquals(Partie.Etat.EN_COURS, new Partie().etat());
        assertEquals(Partie.Etat.MAT, jouer("f2f3", "e7e5", "g2g4", "d8h4").etat());
        // Le pat le plus rapide connu (Sam Loyd), en 10 coups.
        assertEquals(Partie.Etat.PAT, jouer("e2e3", "a7a5", "d1h5", "a8a6", "h5a5", "h7h5",
                "h2h4", "a6h6", "a5c7", "f7f6", "c7d7", "e8f7", "d7b7", "d8d3",
                "b7b8", "d3h7", "b8c8", "f7g6", "c8e6").etat());
        assertEquals(Partie.Etat.NULLE_MATERIEL,
                Partie.depuisFen("8/8/4k3/8/8/4K3/8/8 w - - 0 1").etat());
        assertEquals(Partie.Etat.NULLE_50_COUPS,
                Partie.depuisFen("8/8/4k3/8/8/3RK3/8/8 w - - 100 80").etat());
        assertEquals(Partie.Etat.NULLE_REPETITION, jouer("g1f3", "g8f6", "f3g1", "f6g8",
                "g1f3", "g8f6", "f3g1", "f6g8").etat());
    }

    @Test
    public void lesCasDeMaterielInsuffisant() {
        assertEquals(Partie.Etat.NULLE_MATERIEL,
                Partie.depuisFen("8/8/4k3/8/8/3BK3/8/8 w - - 0 1").etat());      // R+F contre R
        assertEquals(Partie.Etat.NULLE_MATERIEL,
                Partie.depuisFen("8/8/4k3/8/8/3NK3/8/8 w - - 0 1").etat());      // R+C contre R
        assertEquals(Partie.Etat.NULLE_MATERIEL,                                  // fous sur cases noires
                Partie.depuisFen("8/8/4k3/2b5/8/4K3/8/2B5 w - - 0 1").etat());
        assertEquals(Partie.Etat.EN_COURS,                                        // fous de couleurs opposées
                Partie.depuisFen("8/8/4k3/1b6/8/4K3/8/2B5 w - - 0 1").etat());
        assertEquals(Partie.Etat.EN_COURS,                                        // deux cavaliers : pas automatique
                Partie.depuisFen("8/8/4k3/8/8/2NNK3/8/8 w - - 0 1").etat());
    }

    @Test
    public void pat() {
        Partie p = Partie.depuisFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        assertEquals(Partie.Etat.PAT, p.etat());
        assertFalse(p.enEchec());
    }

    @Test
    public void materielInsuffisant() {
        assertEquals(Partie.Etat.NULLE_MATERIEL,
                Partie.depuisFen("8/8/4k3/8/8/3NK3/8/8 w - - 0 1").etat());
        assertEquals(Partie.Etat.EN_COURS,
                Partie.depuisFen("8/8/4k3/8/8/3RK3/8/8 w - - 0 1").etat());
    }

    @Test
    public void regleDes50Coups() {
        Partie p = Partie.depuisFen("8/8/4k3/8/8/3RK3/8/8 w - - 99 80");
        assertEquals(Partie.Etat.EN_COURS, p.etat());
        assertTrue(p.jouer("d3d1"));
        assertEquals(Partie.Etat.NULLE_50_COUPS, p.etat());
    }

    @Test
    public void tripleRepetition() {
        Partie p = jouer("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1");
        assertEquals(Partie.Etat.EN_COURS, p.etat());
        assertTrue(p.jouer("f6g8"));                       // départ vu pour la 3e fois
        assertEquals(Partie.Etat.NULLE_REPETITION, p.etat());
        p.annuler();
        assertEquals(Partie.Etat.EN_COURS, p.etat());
    }

    @Test(expected = IllegalArgumentException.class)
    public void depuisCoupsRefuseUnCoupIllegal() {
        jouer("e2e4", "e2e4");
    }

    @Test
    public void annulerSansCoupNeFaitRien() {
        Partie p = new Partie();
        p.annuler();
        assertEquals(20, p.coupsLegaux().size());
        assertFalse(p.jouer(null));
        assertFalse(p.jouer(""));
        assertFalse(p.jouer("zz"));
    }

    private static List<String> sorted(List<String> liste) {
        Collections.sort(liste);
        return liste;
    }
}
