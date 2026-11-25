package fr.mines.philidor.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DateFormat;
import java.util.List;

import fr.mines.philidor.R;
import fr.mines.philidor.activity.MainActivity;
import fr.mines.philidor.model.PartieJouee;
import fr.mines.philidor.moteur.Partie;
import fr.mines.philidor.service.HistoryService;

public class GameAdapter extends ArrayAdapter<PartieJouee> {

    private LayoutInflater layoutInflater;

    public GameAdapter(Context context, List<PartieJouee> data) {
        super(context, 0, data);
        this.layoutInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View line = this.layoutInflater.inflate(R.layout.lv_item_game, parent, false);

        PartieJouee partieJouee = getItem(position);

        TextView tvResultLogo = line.findViewById(R.id.tvResultLogo);
        TextView tvResultTitle = line.findViewById(R.id.tvResultTitle);
        TextView tvColor = line.findViewById(R.id.tvColor);
        TextView tvLineDesc = line.findViewById(R.id.tvLineDesc);
        TextView tvKing = line.findViewById(R.id.tvKing);

        String mat_pat = "";

        switch (partieJouee.getResultat()) {
            case NULL:
                tvResultLogo.setBackgroundResource(R.drawable.pastille_pat);
                tvResultLogo.setText("N");
                tvResultTitle.setText(getContext().getString(R.string.party_null));
                mat_pat = "pat";
                break;
            case VICTOIRE:
                tvResultLogo.setBackgroundResource(R.drawable.pastille_ok);
                tvResultLogo.setText("V");
                tvResultTitle.setText(getContext().getString(R.string.party_win));
                mat_pat = "mat";
                break;
            case DEFAITE:
                tvResultLogo.setBackgroundResource(R.drawable.pastille_ko);
                tvResultLogo.setText("D");
                tvResultTitle.setText(getContext().getString(R.string.party_lost));
                mat_pat = "mat";
                break;
        }

        if (partieJouee.getCouleur().equals(MainActivity.COULEUR.blanc)) {
            tvColor.setText(getContext().getString(R.string.white));
            tvKing.setText("♔");
        } else {
            tvColor.setText(getContext().getString(R.string.black));
            tvKing.setText("♚");
        }

        String desc = DateFormat.getDateInstance(DateFormat.SHORT)
                .format(partieJouee.getDate())
                + " - "
                + partieJouee.getNombreCoups()
                + " - "
                + mat_pat;

        tvLineDesc.setText(desc);

        return line;


    }
}
