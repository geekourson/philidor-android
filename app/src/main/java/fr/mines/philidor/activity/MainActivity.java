package fr.mines.philidor.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.security.SecureRandom;
import java.util.Random;

import fr.mines.philidor.R;
import fr.mines.philidor.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    public enum COULEUR {blanc, noir, hasard};

    private ActivityMainBinding binding;
    private SecureRandom random;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        this.random = new SecureRandom();

        binding.butNewGame.setOnClickListener(v -> {
            COULEUR couleur;
            int butId = binding.couleur.getCheckedButtonId();
            if (butId == R.id.blancs) {
                couleur = COULEUR.blanc;
            } else if (butId == R.id.noirs) {
                couleur = COULEUR.noir;
            } else {
                boolean isWhite = random.nextBoolean();
                couleur = isWhite ? COULEUR.blanc : COULEUR.noir;
            }

            float creativite = binding.creativite.getValue();

            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra("couleur", couleur);
            intent.putExtra("creativite", creativite);
            startActivity(intent);

        });
        binding.butViewHisto.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
        });
    }
}
