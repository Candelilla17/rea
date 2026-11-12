package com.rea.terreno;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.rea.terreno.map.Reports;
import com.rea.terreno.map.Symbols;

/** Verificador de simbología: leyenda de todos los iconos con su nombre. */
public class SimbologiaActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_simbologia);
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> finish());

        LinearLayout c = findViewById(R.id.container);

        section(c, "Waypoints y simbología militar");
        for (int i = 0; i < 9; i++) row(c, Symbols.drawable(Symbols.IDS[i]), Symbols.LABELS[i]);

        section(c, "Ambientales / ecológicos");
        for (int i = 9; i < 17; i++) row(c, Symbols.drawable(Symbols.IDS[i]), Symbols.LABELS[i]);

        section(c, "Simbología OTAN (APP-6)");
        for (int i = 17; i < Symbols.IDS.length; i++)
            row(c, Symbols.drawable(Symbols.IDS[i]), Symbols.LABELS[i]);

        section(c, "Reportes de vía (estilo Waze)");
        for (int i = 0; i < Reports.IDS.length; i++)
            row(c, Reports.drawable(Reports.IDS[i]), Reports.LABELS[i]);
    }

    private void section(LinearLayout c, String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(ContextCompat.getColor(this, R.color.brand));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        int p = dp(12);
        t.setPadding(0, p, 0, dp(4));
        c.addView(t);
        View line = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        line.setLayoutParams(lp);
        line.setBackgroundColor(0x33000000);
        c.addView(line);
    }

    private void row(LinearLayout c, int iconRes, String label) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(6);
        r.setPadding(0, p, 0, p);

        ImageView iv = new ImageView(this);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(36), dp(36));
        ip.rightMargin = dp(14);
        iv.setLayoutParams(ip);
        iv.setImageDrawable(ContextCompat.getDrawable(this, iconRes));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.DKGRAY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);

        r.addView(iv);
        r.addView(tv);
        c.addView(r);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
