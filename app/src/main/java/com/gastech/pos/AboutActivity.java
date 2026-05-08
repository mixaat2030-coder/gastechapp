package com.gastech.pos;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView phone = findViewById(R.id.about_phone);
        TextView email = findViewById(R.id.about_email);

        phone.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+96566954408"));
            startActivity(i);
        });
        email.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:Elsangary@hotmail.com"));
            startActivity(i);
        });
    }
}
