package fr.mines.philidor.moteur;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests du kit sur un appareil ou un émulateur : le vrai modèle avec
 * ONNX Runtime. Il faut que philidor51m.onnx soit dans app/src/main/assets/.
 *
 * <p>Lancer : clic droit sur ce fichier → Run 'PhilidorTest', ou
 * ./gradlew connectedAndroidTest. Les temps s'affichent dans Logcat
 * (filtre : PhilidorTest).</p>
 */
@RunWith(AndroidJUnit4.class)
public class PhilidorTest {

    private static final String TAG = "PhilidorTest";

    /**
     * Une partie de 300 demi-coups jouée par le modèle (avec onnxruntime en
     * Python) : ouverture, milieu de partie, finale, et des positions au-delà
     * de 255 demi-coups, où la partie ne tient plus dans le contexte du modèle.
     */
    private static final List<String> PARTIE = Arrays.asList((
            "e2e4 e7e5 g1f3 b8c6 f1c4 f7f5 d2d4 f5e4 f3e5 d7d5 c4b5 d8d6 " +
            "e1g1 a7a6 b5c6 b7c6 c1f4 g8f6 e5g6 d6e6 g6h8 g7g6 h8g6 h7g6 " +
            "b1c3 e6g4 f4e5 f8g7 e5f6 g7f6 f2f3 g4h4 c3e2 e4e3 g2g3 h4g5 " +
            "h2h4 g5h6 g1g2 g6g5 h4g5 f6g5 f3f4 g5d8 f4f5 d8f6 d1d3 e8e7 " +
            "f1f4 a6a5 d3e3 e7d6 a1h1 f6g7 h1h6 g7h6 f5f6 c8e6 f6f7 a8f8 " +
            "f4h4 h6e3 e2f4 e6f7 h4h7 f7g6 h7g7 g6c2 f4h3 c2e4 g2h2 f8h8 " +
            "g3g4 h8f8 h3g5 f8h8 h2g3 h8f8 g5e4 d5e4 g7g5 f8f2 g5e5 f2f3 " +
            "g3h4 e3d4 e5e4 d4b2 g4g5 b2g7 e4g4 f3f1 g5g6 f1h1 h4g5 h1c1 " +
            "g4g3 d6e7 g3d3 c1c5 g5g4 c5c4 g4g3 a5a4 d3e3 e7f8 g3f3 g7h6 " +
            "e3e8 f8g7 e8e7 g7g6 e7c7 c4c5 f3e4 g6f6 c7d7 f6e6 d7d8 e6e7 " +
            "d8d3 c5c4 e4f5 h6c1 f5e5 c4c2 e5f5 c2a2 f5e5 a2c2 e5d4 c1d2 " +
            "d4e4 c2c4 d3d4 c4d4 e4d4 a4a3 d4d3 a3a2 d3d4 a2a1q d4c4 a1d4 " +
            "c4b3 d2c3 b3c2 d4d2 c2b3 d2b2 b3c4 c3d4 c4d3 b2b4 d3e4 b4c4 " +
            "e4f3 c4d3 f3g4 c6c5 g4h5 d4e3 h5g4 d3d4 g4g3 d4f4 g3g2 e3d2 " +
            "g2h3 f4a4 h3g2 c5c4 g2h3 c4c3 h3g2 c3c2 g2h3 c2c1r h3g2 a4g4 " +
            "g2h2 g4g3 h2g3 c1d1 g3f3 e7d6 f3e2 d6d5 e2f3 d5e5 f3g4 d1f1 " +
            "g4g3 e5e4 g3g4 d2e1 g4h5 f1f2 h5g6 f2b2 g6f7 e1g3 f7f8 g3f2 " +
            "f8f7 e4e3 f7g6 e3d3 g6f7 d3c3 f7g6 b2b3 g6f7 c3c2 f7g6 b3c3 " +
            "g6f7 f2c5 f7g6 c5a3 g6h5 c3d3 h5h4 d3d5 h4g3 a3b2 g3f2 d5d7 " +
            "f2f3 d7d6 f3f4 d6d4 f4f5 d4d3 f5g5 d3e3 g5f5 b2c1 f5g4 c2d1 " +
            "g4h5 e3e2 h5g4 e2e3 g4h5 e3e2 h5g4 c1f4 g4f4 d1c2 f4f3 c2d3 " +
            "f3f4 e2e4 f4f3 e4e5 f3f4 e5h5 f4g4 h5b5 g4h4 d3e4 h4g4 b5f5 " +
            "g4h4 f5f4 h4g3 f4f7 g3g4 e4e5 g4g3 e5e4 g3g4 f7f8 g4g5 e4e3 " +
            "g5g4 f8f1 g4g5 e3e4 g5g4 f1g1 g4h4 e4f4 h4h3 g1h1 h3g2 h1h8 " +
            "g2f2 h8a8 f2e2 a8a3 e2f2 a3a2 f2f1 a2b2 f1e1 f4e3 e1d1 b2a2").split(" "));

    /**
     * Le coup choisi à créativité 0 par onnxruntime en Python, après les n
     * premiers demi-coups de PARTIE : {n, coup, coup à égalité}.
     *
     * <p>Le modèle est quantifié en int8 : les calculs d'un processeur ARM
     * (téléphone) et d'un ordinateur diffèrent très légèrement. Quand les deux
     * meilleurs coups ont presque le même score (moins de 0,05 d'écart), l'un
     * ou l'autre est accepté ; sinon, le coup doit être exactement le même.</p>
     */
    private static final Object[][] REFERENCES = {
            {0, "e2e4", null},
            {1, "c7c5", null},
            {6, "d2d3", null},
            {20, "g6h8", null},
            {45, "d8f6", null},
            {80, "g7e7", null},
            {120, "d8h8", "d8a8"},   // quasi-égalité : 0.011 d'écart
            {160, "g4g5", null},
            {200, "g6h5", null},
            {260, "g4f4", null},
            {290, "f2e2", null},
    };

    private static Philidor philidor;

    @BeforeClass
    public static void charger() throws Exception {
        long debut = System.nanoTime();
        philidor = Philidor.charger(contexte());
        Log.i(TAG, "Chargement du modèle : " + (System.nanoTime() - debut) / 1_000_000 + " ms");
    }

    @AfterClass
    public static void fermer() {
        if (philidor != null) {
            philidor.fermer();
        }
    }

    @Test
    public void memesCoupsQuePython() throws Exception {
        assertEquals(300, PARTIE.size());
        for (Object[] reference : REFERENCES) {
            int n = (Integer) reference[0];
            Partie partie = Partie.depuisCoups(PARTIE.subList(0, n));
            String coup = philidor.choisirCoup(partie, 0);
            if (!coup.equals(reference[1]) && !coup.equals(reference[2])) {
                assertEquals("après " + n + " demi-coups", reference[1], coup);
            }
            if (!coup.equals(reference[1])) {
                Log.i(TAG, "Après " + n + " demi-coups : " + coup + " (quasi-égalité avec "
                        + reference[1] + ")");
            }
            assertEquals("choisirCoup ne doit pas modifier la partie", n, partie.coups().size());
        }
    }

    @Test
    public void deuxCentsCoupsContreLuiMeme() throws Exception {
        int joues = 0;
        int parties = 0;
        long total = 0;
        long pire = 0;
        while (joues < 200) {
            Partie partie = new Partie();
            parties++;
            while (partie.etat() == Partie.Etat.EN_COURS && joues < 200) {
                long debut = System.nanoTime();
                String coup = philidor.choisirCoup(partie, 0.6);
                long duree = System.nanoTime() - debut;
                total += duree;
                pire = Math.max(pire, duree);
                assertTrue("coup illégal : " + coup + " après " + partie.coups(), partie.jouer(coup));
                joues++;
            }
        }
        Log.i(TAG, "200 coups en " + parties + " partie(s) : " + total / 200 / 1_000_000
                + " ms par coup en moyenne, " + pire / 1_000_000 + " ms au pire");
    }

    @Test
    public void unFichierAbsentDonneUneErreurClaire() throws Exception {
        try {
            Philidor.charger(contexte(), "absent.onnx");
            fail("FileNotFoundException attendue");
        } catch (FileNotFoundException e) {
            assertEquals("absent.onnx absent : téléchargez-le depuis la page Releases "
                    + "et placez-le dans app/src/main/assets/", e.getMessage());
        }
    }

    @Test
    public void rechargerApresFermer() throws Exception {
        Philidor autre = Philidor.charger(contexte());
        autre.fermer();
        autre.fermer();                                    // deux fois : sans effet
        // Après fermeture : toujours un coup légal (au hasard), pas de plantage.
        assertTrue(new Partie().coupsLegaux().contains(autre.choisirCoup(new Partie(), 0)));
        // Le Philidor partagé par les autres tests fonctionne toujours.
        assertEquals("e2e4", philidor.choisirCoup(new Partie(), 0));
    }

    private static Context contexte() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
