package fr.mines.philidor.moteur;

import android.content.Context;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * Philidor 51M : un modèle de langage qui a appris à jouer aux échecs en
 * lisant des centaines de millions de coups joués par des humains.
 *
 * <p>Le modèle ne connaît pas les règles. Il lit la suite des coups de la
 * partie (un « jeton » par coup UCI) et donne un score à chacun des 1971
 * jetons de son vocabulaire. On ne garde que les coups légaux, puis on prend
 * le meilleur (créativité 0) ou on tire au sort selon les scores.</p>
 *
 * <p>Usage, toujours hors du fil principal :</p>
 * <pre>
 * Philidor philidor = Philidor.charger(context);         // 1 à 2 secondes
 * String coup = philidor.choisirCoup(partie, 0.6);       // quelques dizaines de ms
 * philidor.fermer();                                     // dans onDestroy
 * </pre>
 *
 * <p>Cette classe est fournie : vous n'avez pas à la modifier.</p>
 */
public class Philidor {

    /** La créativité maximale acceptée ; au-delà, elle est ramenée à cette valeur. */
    public static final double CREATIVITE_MAX = 1.5;

    /** Le fichier du modèle, dans app/src/main/assets/. */
    static final String FICHIER_MODELE = "philidor51m.onnx";

    /** Le modèle lit au plus 256 jetons. */
    static final int CONTEXTE = 256;

    /** Le jeton qui marque le début d'une partie. */
    static final int ID_BOS = 1;

    /** Le vocabulaire : l'identifiant d'un jeton est sa position dans la liste. */
    private static final List<String> JETONS = construireVocabulaire();
    private static final Map<String, Integer> IDENTIFIANTS = indexer(JETONS);

    /**
     * Ce qui calcule les scores (logits) : le modèle ONNX, ou un faux modèle
     * pour tester le reste de la classe.
     */
    interface Reseau {
        /** Les scores des 1971 jetons pour le coup qui suit la séquence. */
        float[] logits(long[] identifiants) throws OrtException;

        void fermer();
    }

    private final Reseau reseau;
    private final Random hasard;

    // Fermeture sans risque : si fermer() arrive pendant un calcul (l'écran
    // est quitté pendant que Philidor réfléchit), on attend la fin du calcul
    // pour libérer le modèle, sans bloquer le fil principal.
    private final Object verrou = new Object();
    private boolean ferme;
    private boolean enCalcul;

    Philidor(Reseau reseau, Random hasard) {
        this.reseau = reseau;
        this.hasard = hasard;
    }

    // ==================================================================
    // API publique
    // ==================================================================

    /**
     * Charge le modèle {@code philidor51m.onnx} depuis les assets de
     * l'application et crée la session ONNX Runtime.
     *
     * <p><b>Prend une à deux secondes</b> (un peu plus au tout premier
     * lancement) : ne jamais l'appeler sur le fil principal.</p>
     *
     * @throws FileNotFoundException si le fichier est absent des assets.
     * @throws IOException si le fichier ne peut pas être lu.
     * @throws OrtException si ONNX Runtime ne peut pas charger le modèle.
     */
    public static Philidor charger(Context ctx) throws IOException, OrtException {
        return charger(ctx, FICHIER_MODELE);
    }

    /** Comme {@link #charger(Context)}, avec un autre nom de fichier (pour les tests). */
    static Philidor charger(Context ctx, String nomAsset) throws IOException, OrtException {
        File fichier = copierLeModele(ctx, nomAsset);
        return new Philidor(new ReseauOnnx(fichier.getAbsolutePath()), new Random());
    }

    /**
     * Renvoie un coup légal de la partie, en UCI. Ne modifie pas la partie.
     * Quelques dizaines de millisecondes : à appeler hors du fil principal.
     *
     * @param creativite entre 0 (toujours le coup le plus probable) et 1,5
     *                   (au-delà, ramenée à 1,5).
     * @throws OrtException si le calcul du modèle échoue.
     * @throws IllegalStateException si la partie n'a plus aucun coup légal.
     */
    public String choisirCoup(Partie p, double creativite) throws OrtException {
        List<String> legaux = p.coupsLegaux();
        if (legaux.isEmpty()) {
            throw new IllegalStateException("Aucun coup légal : la partie est terminée.");
        }
        float[] logits = calculerLogits(sequence(p.coups()));
        if (logits == null) {
            // Philidor a été fermé : un coup légal au hasard plutôt qu'un plantage.
            return auHasard(legaux);
        }
        return choisirParmi(legaux, logits, creativite);
    }

    /** Libère la mémoire du modèle. Philidor ne peut plus servir ensuite. */
    public void fermer() {
        synchronized (verrou) {
            if (ferme) {
                return;
            }
            ferme = true;
            if (!enCalcul) {
                reseau.fermer();
            }
        }
    }

    // ==================================================================
    // Le cœur : séquence, masque des coups légaux, choix
    // ==================================================================

    /**
     * La séquence lue par le modèle : &lt;bos&gt; (le début de la partie),
     * puis un jeton par coup joué.
     *
     * <p>Le modèle lit au plus 256 jetons. Au-delà de 255 demi-coups, on
     * garde &lt;bos&gt; puis les 255 coups les plus récents.</p>
     */
    static long[] sequence(List<String> coups) {
        int debut = Math.max(0, coups.size() - (CONTEXTE - 1));
        long[] resultat = new long[1 + coups.size() - debut];
        resultat[0] = ID_BOS;
        for (int i = debut; i < coups.size(); i++) {
            Integer id = IDENTIFIANTS.get(coups.get(i));
            // Un coup sans jeton ne peut pas arriver avec Partie ; par sécurité, <pad>.
            resultat[1 + i - debut] = id == null ? 0 : id;
        }
        return resultat;
    }

    /**
     * Choisit un coup parmi les coups légaux. Les autres jetons sont ignorés :
     * c'est le « masque » des coups illégaux.
     *
     * <ul>
     * <li>Créativité 0 : le coup légal au plus haut score.</li>
     * <li>Créativité t &gt; 0 : tirage au sort avec les probabilités
     * softmax(score / t). Plus t est grand, plus les probabilités se
     * rapprochent et plus Philidor ose des coups inattendus.</li>
     * </ul>
     */
    String choisirParmi(List<String> legaux, float[] logits, double creativite) {
        double t = Double.isNaN(creativite) ? 0 : Math.max(0, Math.min(CREATIVITE_MAX, creativite));

        // Le masque : on ne garde que les scores des coups légaux.
        List<String> candidats = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        double meilleur = Double.NEGATIVE_INFINITY;
        int indiceMeilleur = -1;
        for (String coup : legaux) {
            Integer id = IDENTIFIANTS.get(coup);
            if (id == null || Double.isNaN(logits[id]) || logits[id] == Float.NEGATIVE_INFINITY) {
                continue;                                  // pas de score utilisable pour ce coup
            }
            candidats.add(coup);
            scores.add((double) logits[id]);
            if (logits[id] > meilleur) {
                meilleur = logits[id];
                indiceMeilleur = candidats.size() - 1;
            }
        }
        if (indiceMeilleur < 0) {
            return auHasard(legaux);                       // aucun score : un coup légal au hasard
        }
        if (t < 1e-6) {
            return candidats.get(indiceMeilleur);
        }

        // softmax(score / t) ; on retire d'abord le meilleur score pour que
        // exp() ne déborde jamais.
        double somme = 0;
        double[] poids = new double[candidats.size()];
        for (int i = 0; i < poids.length; i++) {
            poids[i] = Math.exp((scores.get(i) - meilleur) / t);
            somme += poids[i];
        }
        if (!(somme > 0) || Double.isInfinite(somme)) {
            return auHasard(legaux);                       // probabilités inutilisables
        }
        double tirage = hasard.nextDouble() * somme;
        for (int i = 0; i < poids.length; i++) {
            tirage -= poids[i];
            if (tirage < 0) {
                return candidats.get(i);
            }
        }
        return candidats.get(indiceMeilleur);              // arrondi numérique
    }

    private String auHasard(List<String> legaux) {
        return legaux.get(hasard.nextInt(legaux.size()));
    }

    /** Les logits pour la séquence, ou null si Philidor a été fermé. */
    private float[] calculerLogits(long[] ids) throws OrtException {
        synchronized (verrou) {
            if (ferme) {
                return null;
            }
            enCalcul = true;
        }
        try {
            float[] logits = reseau.logits(ids);
            if (logits == null || logits.length != JETONS.size()) {
                throw new OrtException("Sortie inattendue du modèle");
            }
            return logits;
        } finally {
            synchronized (verrou) {
                enCalcul = false;
                if (ferme) {
                    reseau.fermer();                       // fermer() a été appelé pendant le calcul
                }
            }
        }
    }

    // ==================================================================
    // Le vocabulaire (identique à vocab.py du projet Philidor)
    // ==================================================================

    /**
     * Trois jetons spéciaux (&lt;pad&gt;, &lt;bos&gt;, &lt;eos&gt;), puis tous les
     * coups UCI géométriquement possibles sur un échiquier vide, triés par
     * ordre alphabétique : déplacements de dame, de cavalier, et promotions.
     */
    static List<String> construireVocabulaire() {
        TreeSet<String> coups = new TreeSet<>();
        int[][] directionsDame = {{0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}};
        int[][] sautsCavalier = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
        for (int c = 0; c < 8; c++) {
            for (int l = 0; l < 8; l++) {
                for (int[] d : directionsDame) {
                    for (int distance = 1; distance < 8; distance++) {
                        int c2 = c + d[0] * distance;
                        int l2 = l + d[1] * distance;
                        if (c2 < 0 || c2 > 7 || l2 < 0 || l2 > 7) {
                            break;
                        }
                        coups.add(nomCase(c, l) + nomCase(c2, l2));
                    }
                }
                for (int[] d : sautsCavalier) {
                    int c2 = c + d[0];
                    int l2 = l + d[1];
                    if (c2 >= 0 && c2 <= 7 && l2 >= 0 && l2 <= 7) {
                        coups.add(nomCase(c, l) + nomCase(c2, l2));
                    }
                }
            }
        }
        // Promotions : de la 7e à la 8e rangée (Blancs), de la 2e à la 1re (Noirs),
        // tout droit ou en prenant en diagonale.
        int[][] rangees = {{6, 7}, {1, 0}};
        for (int[] r : rangees) {
            for (int c = 0; c < 8; c++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int c2 = c + dc;
                    if (c2 < 0 || c2 > 7) {
                        continue;
                    }
                    for (char piece : new char[]{'q', 'r', 'b', 'n'}) {
                        coups.add(nomCase(c, r[0]) + nomCase(c2, r[1]) + piece);
                    }
                }
            }
        }
        List<String> jetons = new ArrayList<>();
        jetons.add("<pad>");
        jetons.add("<bos>");
        jetons.add("<eos>");
        jetons.addAll(coups);                              // TreeSet : déjà trié
        return Collections.unmodifiableList(jetons);
    }

    static List<String> jetons() {
        return JETONS;
    }

    private static Map<String, Integer> indexer(List<String> jetons) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < jetons.size(); i++) {
            index.put(jetons.get(i), i);
        }
        return index;
    }

    private static String nomCase(int colonne, int ligne) {
        return "" + "abcdefgh".charAt(colonne) + "12345678".charAt(ligne);
    }

    // ==================================================================
    // Le modèle ONNX
    // ==================================================================

    /**
     * Copie le modèle des assets vers la mémoire interne de l'app, une seule
     * fois. ONNX Runtime lit ensuite ce fichier lui-même, en mémoire native :
     * les ~50 Mo ne passent jamais par la mémoire Java, limitée à moins de
     * 100 Mo sur certains téléphones (OutOfMemoryError sinon).
     */
    private static File copierLeModele(Context ctx, String nomAsset) throws IOException {
        InputStream entree;
        try {
            entree = ctx.getAssets().open(nomAsset);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException(nomAsset + " absent : téléchargez-le depuis la page "
                    + "Releases et placez-le dans app/src/main/assets/");
        }
        File fichier = new File(ctx.getFilesDir(), nomAsset);
        try {
            // Pour un asset, available() donne la taille totale du fichier.
            long taille = entree.available();
            if (fichier.isFile() && fichier.length() == taille) {
                return fichier;                            // déjà copié
            }
            File temporaire = new File(ctx.getFilesDir(), nomAsset + ".tmp");
            try (OutputStream sortie = new FileOutputStream(temporaire)) {
                byte[] tampon = new byte[64 * 1024];
                int lus;
                while ((lus = entree.read(tampon)) != -1) {
                    sortie.write(tampon, 0, lus);
                }
            }
            // Renommer à la fin : une copie interrompue ne laisse jamais un modèle tronqué.
            if (!temporaire.renameTo(fichier)) {
                throw new IOException("Copie du modèle impossible");
            }
            return fichier;
        } finally {
            entree.close();
        }
    }

    /** Le réseau exécuté par ONNX Runtime : input_ids [1, n] -> logits [1, 1971]. */
    private static final class ReseauOnnx implements Reseau {
        private final OrtEnvironment environnement = OrtEnvironment.getEnvironment();
        private final OrtSession session;

        ReseauOnnx(String chemin) throws OrtException {
            session = environnement.createSession(chemin, options());
        }

        private static OrtSession.SessionOptions options() throws OrtException {
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            return options;
        }

        @Override
        public float[] logits(long[] identifiants) throws OrtException {
            long[] forme = {1, identifiants.length};
            try (OnnxTensor entree = OnnxTensor.createTensor(
                    environnement, LongBuffer.wrap(identifiants), forme);
                 OrtSession.Result resultat = session.run(
                         Collections.singletonMap("input_ids", entree))) {
                float[][] sortie = (float[][]) resultat.get("logits")
                        .orElseThrow(() -> new OrtException("Pas de sortie « logits »"))
                        .getValue();
                return sortie[0];
            }
        }

        @Override
        public void fermer() {
            try {
                session.close();
            } catch (OrtException e) {
                // Rien de plus à faire : le modèle est abandonné de toute façon.
            }
            environnement.close();
        }
    }
}
