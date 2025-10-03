package me.ocv.paprashare;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String txt = "<p><em>Hello from <a href=\"https://github.com/VinMeld/party-up\">Papra Share</a> <b>version " + BuildConfig.VERSION_NAME + "</b></em></p>" +
                "<p>This app lets you upload files (images, videos) to a papra server.</p>" +
                "<hr />" +
                "<p><b>Use your favorite file manager or gallery app to open a document you'd like to upload, then hit the share button and select \"Papra Share\" \uD83C\uDF89</b></p>";

        TextView tv = ((TextView)findViewById(R.id.textView4));
        tv.setText(Html.fromHtml(txt, Html.FROM_HTML_MODE_LEGACY));
        tv.setMovementMethod(LinkMovementMethod.getInstance());

        ((Button)findViewById(R.id.settingsBtn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(i);
            }
        });
    }
}
