package fr.mines.philidor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import ai.onnxruntime.OrtException;
import fr.mines.philidor.R;
import fr.mines.philidor.activity.MainActivity;
import fr.mines.philidor.moteur.Partie;
import fr.mines.philidor.moteur.Philidor;

public class Echiquier extends View {

    private static final String letters = "abcdefgh";


    private static final String PIECES = "KQRBNPkqrbnp";
    private static final String[] SYMBOLES = { "♔", "♕", "♖", "♗", "♘", "♙", "♚", "♛", "♜", "♝", "♞", "♟"};


    private Paint greenDarkPaint;
    private Paint greenDarkStrokePaint;
    private Paint greenLightPaint;
    private Paint greenLightStrokePaint;
    private Paint blackPaint;

    private Partie partie;
    private Philidor philidor;
    private MainActivity.COULEUR playerCouleur;
    private double creativite;

    private String coupFrom;
    private List<String> coupsLegaux = new ArrayList<>(0);


    public Echiquier(Context context) {
        super(context);
        init();
    }

    public Echiquier(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Echiquier(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }


    public void setPartie(Partie partie) {
        this.partie = partie;
        this.invalidate();
    }

    public void setPhilidor(Philidor philidor) {
        this.philidor = philidor;
    }

    public void setPlayerCouleur(MainActivity.COULEUR playerCouleur) {
        this.playerCouleur = playerCouleur;
    }
    public void setCreativite(double creativite) {
        this.creativite = creativite;
    }

    private void init() {
        this.greenDarkPaint = new Paint();
        this.greenDarkPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        this.greenDarkPaint.setColor(getResources().getColor(R.color.green_echiquier_dark));

        this.greenLightPaint = new Paint();
        this.greenLightPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        this.greenLightPaint.setColor(getResources().getColor(R.color.green_echiquier_light));

        this.greenDarkStrokePaint = new Paint();
        this.greenDarkStrokePaint.setStyle(Paint.Style.STROKE);
        this.greenDarkStrokePaint.setColor(getResources().getColor(R.color.green_echiquier_dark));
        this.greenDarkStrokePaint.setTextSize(20);

        this.greenLightStrokePaint = new Paint();
        this.greenLightStrokePaint.setStyle(Paint.Style.STROKE);
        this.greenLightStrokePaint.setColor(getResources().getColor(R.color.green_echiquier_light));
        this.greenLightStrokePaint.setTextSize(20);


        this.blackPaint = new Paint();
        this.blackPaint.setStyle(Paint.Style.STROKE);
        this.blackPaint.setColor(getResources().getColor(R.color.black));
    }



    @Override
    protected void onDraw(@NonNull Canvas canvas) {

        float textSize = getWidth()/8f - 40;
        this.blackPaint.setTextSize(textSize);

        float widthCase = getWidth() / 8f;

        boolean startLight = true;
        boolean isLight = startLight;
        for (int y=0; y<8; y++) {
            canvas.save();
            for (int x=0; x<8; x++) {
                canvas.drawRect(0, 0, widthCase, widthCase, isLight ? greenLightPaint : greenDarkPaint);
                if (x == 0) {
                    canvas.drawText("" + (8-y), 10, 25, isLight ? greenDarkStrokePaint : greenLightStrokePaint);
                }
                if (y == 7) {
                    canvas.drawText(letters.charAt(x) + "", widthCase - 25, widthCase - 15, isLight ? greenDarkStrokePaint : greenLightStrokePaint);
                }

                if (partie != null) {

                    if (partie.traitAuxBlancs() && playerCouleur.equals(MainActivity.COULEUR.noir) || !partie.traitAuxBlancs() && playerCouleur.equals(MainActivity.COULEUR.blanc))  {
                        try {
                            String coupJoue = philidor.choisirCoup(partie, creativite);
                            partie.jouer(coupJoue);
                            invalidate();
                        } catch (OrtException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    char piece = partie.piece(x, 7-y);

                    String symbole = symbole(piece);
                    boolean isWhite = isWhite(piece);
                    blackPaint.setStyle( isWhite ? Paint.Style.STROKE : Paint.Style.FILL);
                    canvas.drawText(symbole, widthCase/2 - textSize/2, widthCase/2 + textSize/2, blackPaint);

                    if (coupFrom != null) {
                        String coupUCIPossible = coupFrom + letters.charAt(x) + (8-y);
                        if (coupsLegaux.contains(coupUCIPossible)) {
                            canvas.drawCircle(widthCase/2, widthCase/2, 20, blackPaint);
                        }
                    }

                }

                isLight = !isLight;
                canvas.translate(widthCase, 0);
            }
            canvas.restore();
            canvas.translate(0, widthCase);
            startLight = ! startLight;
            isLight = startLight;
        }
    }

    private String symbole(char piece) {
        int i = PIECES.indexOf(piece);
        if (i < 0) {
            return "";
        }
        return SYMBOLES[i];
    }

    private boolean isWhite(char piece) {
        return Character.isUpperCase(piece);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;
        float x = event.getX();
        float y = event.getY();

        int col = (int) (x / (getWidth() / 8f));
        int lin = (int) (8- y / (getWidth() / 8f));


        char piece = partie.piece(col, lin);
        boolean isWhite = isWhite(piece);

        if (coupFrom != null && !coupsLegaux.isEmpty()) {
            String coupJoue = coupFrom + letters.charAt(col) + "" + (lin + 1);
            if (coupsLegaux.contains(coupJoue)) {
                boolean isOk = partie.jouer(coupJoue);
                if (isOk) {
                    coupFrom = null;
                    coupsLegaux = new ArrayList<>(0);
                    invalidate();
                }
                return true;
            }

        }

        if (partie.traitAuxBlancs() && isWhite || !partie.traitAuxBlancs() && !isWhite) {

            List<String> coupsLegaux = partie.coupsDepuis(col, lin);
            if (!coupsLegaux.isEmpty()) {
                this.coupFrom = letters.charAt(col) + "" + (lin + 1);
                this.coupsLegaux = new ArrayList<>(coupsLegaux);
                this.invalidate();
            }

        }



        return true;
    }
}
