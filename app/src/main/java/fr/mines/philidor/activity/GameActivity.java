package fr.mines.philidor.activity;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;

import ai.onnxruntime.OrtException;
import fr.mines.philidor.R;
import fr.mines.philidor.databinding.ActivityGameBinding;
import fr.mines.philidor.databinding.ActivityHistoryBinding;
import fr.mines.philidor.moteur.Partie;
import fr.mines.philidor.moteur.Philidor;

public class GameActivity extends AppCompatActivity {

    private ActivityGameBinding binding;


    private MainActivity.COULEUR couleur;
    private float creativite;


    private Partie partie;
    private Philidor philidor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        this.couleur = (MainActivity.COULEUR) getIntent().getSerializableExtra("couleur");
        this.creativite = getIntent().getFloatExtra("creativite", 0);

        MainActivity.COULEUR couleurPhilidor = this.couleur.equals(MainActivity.COULEUR.blanc) ? MainActivity.COULEUR.noir : MainActivity.COULEUR.blanc;

        this.binding.tvGameSubTitle.setText(couleurPhilidor + " - " + this.creativite);
        this.binding.tvPlayerColor.setText(couleur + "");

        this.partie = new Partie();
        try {
            this.philidor = Philidor.charger(this);
            this.binding.echiquier.setPhilidor(philidor);
            this.binding.echiquier.setPlayerCouleur(couleur);
            this.binding.echiquier.setCreativite(creativite);
            this.binding.echiquier.setPartie(partie);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }



    }
}