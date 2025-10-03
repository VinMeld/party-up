package me.ocv.paprashare;

import static java.lang.String.format;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.preference.PreferenceManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;

import me.ocv.paprashare.databinding.ActivityXferBinding;

class F {
    public Uri handle;
    public String name;
    public long size;
    public String full_url;
    public String share_url;
    public String desc;
    public String documentId;
}

public class XferActivity extends AppCompatActivity {
    ActivityXferBinding binding;
    SharedPreferences prefs;
    Intent the_intent;
    String token;
    String orgId;
    String papra_url;
    boolean upping;
    String the_msg;
    long bytes_done, bytes_total, t0;
    F[] files;

    ArrayList<PapraTag> allTags = new ArrayList<>();
    ArrayList<PapraTag> selectedTags = new ArrayList<>();
    Button btnSelectTags;
    ChipGroup tagGroup;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        upping = false;

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        binding = ActivityXferBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        btnSelectTags = findViewById(R.id.btnSelectTags);
        tagGroup = findViewById(R.id.tag_group);
        btnSelectTags.setEnabled(false);

        the_intent = getIntent();
        String etype = the_intent.getType();
        String action = the_intent.getAction();
        boolean one = Intent.ACTION_SEND.equals(action);
        boolean many = Intent.ACTION_SEND_MULTIPLE.equals(action);
        if (etype == null || (!one && !many)) {
            show_msg("cannot share content;\naction: " + action + "\ntype: " + etype);
            return;
        }

        Uri[] handles = null;
        if (many) {
            ArrayList<Uri> x = the_intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            handles = x.toArray(new Uri[0]);
        } else if (one) {
            Uri uri = (Uri) the_intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null)
                handles = new Uri[]{uri};
            else
                the_msg = the_intent.getStringExtra(Intent.EXTRA_TEXT);
        }
        if (handles != null) {
            files = new F[handles.length];
            for (int a = 0; a < handles.length; a++) {
                F f = new F();
                f.handle = handles[a];
                f.name = null;
                f.size = -1;
                files[a] = f;
            }
            handleSendImage();
        } else if (the_msg != null) {
            handleSendText();
        } else {
            show_msg("cannot decide on what to send for " + the_intent.getType());
            return;
        }

        token = prefs.getString("papra_token", "");
        orgId = prefs.getString("papra_org_id", "");
        papra_url = prefs.getString("papra_url", "");

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            fab.setVisibility(View.GONE);
            do_up();
        });

        btnSelectTags.setOnClickListener(v -> showTagSelectionDialog());

        fetchTags();
    }

    private void fetchTags() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String base_url = papra_url;
                if (base_url == null || base_url.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(XferActivity.this, "Error: Papra URL is not set", Toast.LENGTH_LONG).show());
                    return;
                }

                if (!base_url.toLowerCase().startsWith("http")) {
                    base_url = "https://" + base_url;
                }

                if (!base_url.endsWith("/"))
                    base_url += "/";

                URL url = new URL(base_url + "api/organizations/" + orgId + "/tags");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int rc = conn.getResponseCode();
                InputStream is = (rc >= 200 && rc < 300) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                String responseString = sb.toString();

                Log.d("me.ocv.paprashare", "fetchTags response code: " + rc);
                Log.d("me.ocv.paprashare", "fetchTags response body: " + responseString);

                if (rc == 200) {
                    JSONObject json = new JSONObject(responseString);
                    JSONArray tags = json.getJSONArray("tags");
                    allTags.clear();
                    for (int i = 0; i < tags.length(); i++) {
                        JSONObject tag = tags.getJSONObject(i);
                        allTags.add(new PapraTag(tag.getString("id"), tag.getString("name")));
                    }

                    runOnUiThread(() -> {
                        btnSelectTags.setEnabled(true);
                        String successMsg = "Loaded " + allTags.size() + " tags.";
                        Toast.makeText(XferActivity.this, successMsg, Toast.LENGTH_SHORT).show();
                    });
                } else {
                    final String errorMsg = "Error fetching tags: " + rc;
                    runOnUiThread(() -> Toast.makeText(XferActivity.this, errorMsg, Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                Log.e("me.ocv.paprashare", "Exception in fetchTags", e);
                final String errorMsg = "Network Error: " + e.getMessage();
                runOnUiThread(() -> Toast.makeText(XferActivity.this, errorMsg, Toast.LENGTH_LONG).show());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void showTagSelectionDialog() {
        if (allTags.isEmpty()) {
            Toast.makeText(this, "No tags available to select.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] tagNames = new String[allTags.size()];
        boolean[] checkedItems = new boolean[allTags.size()];
        for (int i = 0; i < allTags.size(); i++) {
            tagNames[i] = allTags.get(i).name;
            //checkedItems[i] = selectedTags.contains(allTags.get(i));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Tags");
        builder.setMultiChoiceItems(tagNames, checkedItems, (dialog, which, isChecked) -> {
            if (isChecked) {
                selectedTags.add(allTags.get(which));
            } else {
                selectedTags.remove(allTags.get(which));
            }
        });
        builder.setPositiveButton("OK", (dialog, which) -> {
            updateTagGroup();
        });
        builder.setNegativeButton("Cancel", null);
        builder.create().show();
    }

    private void updateTagGroup() {
        tagGroup.removeAllViews();
        for (PapraTag tag : selectedTags) {
            Chip chip = new Chip(this);
            chip.setText(tag.name);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                selectedTags.remove(tag);
                updateTagGroup();
            });
            tagGroup.addView(chip);
        }
    }

    private void show_msg(String txt) {
        ((TextView) findViewById(R.id.upper_info)).setText(txt);
    }

    private void tshow_msg(String txt) {
        final TextView tv = (TextView) findViewById(R.id.upper_info);
        tv.post(() -> tv.setText(txt));
    }

    void need_storage(String exmsg) {
        if (Build.VERSION.SDK_INT > 29 && exmsg.contains("EACCES"))
            show_msg(exmsg + "\n\nYou must update the app you shared the file from; it is using a dead/forbidden API for sharing files, and Android is preventing new versions of PapraShare from using this API. Older versions of PapraShare such as 1.6.0 may work.");

        String perm = Manifest.permission.READ_EXTERNAL_STORAGE;
        if (this.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED)
            return;  // already have it, so that's not why it failed

        if (!shouldShowRequestPermissionRationale(perm)) {
            request_storage();
            return;
        }
        AlertDialog.Builder ab = new AlertDialog.Builder(findViewById(R.id.upper_info).getContext());
        ab.setMessage("Papra Share! needs additional permissions to read that file, because the app you shared it from is using old APIs."
        ).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                request_storage();
            }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        }).show();
    }

    void request_storage() {
        String perm = Manifest.permission.READ_EXTERNAL_STORAGE;
        requestPermissions(new String[]{perm}, 573);
    }

    @Override
    public void onRequestPermissionsResult(int permRequestCode, String perms[], int[] grantRes) {
        String perm = Manifest.permission.READ_EXTERNAL_STORAGE;
        if (permRequestCode != 573)
            return;

        for (int a = 0; a < grantRes.length; a++) {
            if (!perms[a].equals(perm))
                continue;

            if (grantRes[a] != PackageManager.PERMISSION_GRANTED)
                return;

            handleSendImage();
        }
    }

    String getext(String mime) {
        if (mime == null)
            return "bin";

        mime = mime.replace(';', ' ').split(" ")[0];

        switch (mime) {
            case "audio/ogg":
                return "ogg";
            case "audio/mpeg":
                return "mp3";
            case "audio/mp4":
                return "m4a";
            case "image/jpeg":
                return "jpg";
        }

        if (mime.startsWith("text/"))
            return "txt";

        if (mime.contains("/")) {
            mime = mime.split("/")[1];
            if (mime.matches("^[a-zA-Z0-9]{1,8}$"))
                return mime;
        }

        return "bin";
    }

    private void handleSendText() {
        show_msg("Post the following link?\n\n" + the_msg);
        if (prefs.getBoolean("autosend", false))
            do_up();
    }

    @SuppressLint("DefaultLocale")
    private void handleSendImage() {
        for (F f : files) {
            Log.d("me.ocv.paprashare", format("handle [%s]", f.handle));
            if (f.handle.toString().startsWith("file:///")) {
                f.name = Paths.get(f.handle.getPath()).getFileName().toString();
            } else {
                // contentresolver returns the wrong filesize (off by 626 bytes)
                // but we want the name so lets go
                try {
                    Cursor cur = getContentResolver().query(f.handle, null, null, null, null);
                    assert cur != null;
                    int iname = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int isize = cur.getColumnIndex(OpenableColumns.SIZE);
                    cur.moveToFirst();
                    f.name = cur.getString(iname);
                    f.size = cur.getLong(isize);
                    cur.close();
                } catch (Exception ex) {
                    Log.w("me.ocv.paprashare", "contentresolver: " + ex.toString());
                }
            }

            MessageDigest md = null;
            if (f.name == null) {
                try {
                    md = MessageDigest.getInstance("SHA-512");
                } catch (Exception ex) {
                }
            }

            // get correct filesize
            try {
                InputStream ins = getContentResolver().openInputStream(f.handle);
                assert ins != null;
                byte[] buf = new byte[128 * 1024];
                long sz = 0;
                while (true) {
                    int n = ins.read(buf);
                    if (n <= 0)
                        break;

                    sz += n;
                    if (md != null)
                        md.update(buf, 0, n);
                }
                f.size = sz;
            } catch (Exception ex) {
                String exmsg = "Error3: " + ex.toString();
                show_msg(exmsg);
                need_storage(exmsg);
                return;
            }

            if (md != null) {
                String csum = new String(Base64.getUrlEncoder().encode(md.digest())).substring(0, 15);
                f.name = format("mystery-file-%s.%s", csum, getext(the_intent.getType()));
            }

            f.desc = format("%s\n\nsize: %,d byte\ntype: %s", f.name, f.size, the_intent.getType());
        }

        String msg;
        bytes_done = bytes_total = 0;
        if (files.length == 1) {
            msg = "Upload the following file?\n\n" + files[0].desc;
            bytes_total = files[0].size;
        } else {
            msg = "Upload the following " + files.length + " files?\n\n";
            for (int a = 0; a < Math.min(10, files.length); a++) {
                msg += "  ► " + files[a].name + "\n";
                bytes_total += files[a].size;
            }

            if (files.length > 10)
                msg += "[...]\n";

            msg += format("\n(total %,d bytes)", bytes_total);
        }
        show_msg(msg);
        if (prefs.getBoolean("autosend", false))
            do_up();
    }

    private void do_up() {
        if (upping)
            return;

        upping = true;
        new Thread(this::do_up2).start();
    }

    private void do_up2() {
        try {
            if (papra_url == null || papra_url.isEmpty()) {
                throw new Exception("papra_url config is invalid");
            }

            if (token == null || token.isEmpty()) {
                throw new Exception("papra_token config is invalid");
            }

            if (orgId == null || orgId.isEmpty()) {
                throw new Exception("papra_org_id config is invalid");
            }

            String base_url = papra_url;
            if (!base_url.toLowerCase().startsWith("http")) {
                base_url = "https://" + base_url;
            }

            if (!base_url.endsWith("/"))
                base_url += "/";

            String documents_url = base_url + "api/organizations/" + orgId + "/documents";

            t0 = System.currentTimeMillis();
            tshow_msg("Sending to " + documents_url + " ...");

            int nfiles = files == null ? 1 : files.length;
            for (int a = 0; a < nfiles; a++) {
                String full_url = documents_url;
                if (files != null) {
                    F f = files[a];
                    tshow_msg("Sending to " + documents_url + " ...\n\n" + f.desc);
                    f.full_url = full_url;
                }

                URL url = new URL(full_url);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Bearer " + token);

                if (files == null)
                    do_textmsg(conn);
                else if (!do_fileput(conn, a))
                    return;
            }
            findViewById(R.id.upper_info).post(() -> onsuccess());
        } catch (Exception ex) {
            tshow_msg("Error2: " + ex.toString());
        }
    }

    String read_err(HttpURLConnection conn) {
        try {
            byte[] buf = new byte[1024];
            int n = Math.max(0, conn.getErrorStream().read(buf));
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return ex.toString();
        }
    }

    private void do_textmsg(HttpURLConnection conn) throws Exception {
        byte[] body = ("msg=" + URLEncoder.encode(the_msg, "UTF-8")).getBytes(StandardCharsets.UTF_8);
        conn.setRequestMethod("POST");
        conn.setFixedLengthStreamingMode(body.length);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        conn.connect();
        OutputStream os = conn.getOutputStream();
        os.write(body);
        os.flush();
        int rc = conn.getResponseCode();
        if (rc >= 300) {
            tshow_msg("Server error " + rc + ":\n" + read_err(conn));
            conn.disconnect();
            return;
        }
        conn.disconnect();
    }

    @SuppressLint("DefaultLocale")
    private boolean do_fileput(HttpURLConnection conn, int nfile) throws Exception {
        F f = files[nfile];
        conn.setRequestMethod("POST");
        String boundary = "*****" + System.currentTimeMillis() + "*****";
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        OutputStream os = conn.getOutputStream();

        // Write file part
        String header = "--" + boundary + "\r\n";
        header += "Content-Disposition: form-data; name=\"file\"; filename=\"" + f.name + "\"\r\n";
        header += "Content-Type: application/octet-stream\r\n";
        header += "\r\n";
        os.write(header.getBytes(StandardCharsets.UTF_8));

        InputStream ins = getContentResolver().openInputStream(f.handle);
        byte[] buf = new byte[128 * 1024];
        assert ins != null;
        while (true) {
            int n = ins.read(buf);
            if (n <= 0)
                break;

            bytes_done += n;
            os.write(buf, 0, n);
        }

        os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        os.flush();

        int rc = conn.getResponseCode();
        if (rc >= 300) {
            tshow_msg("Server error " + rc + ":\n" + read_err(conn));
            conn.disconnect();
            return false;
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        conn.disconnect();

        JSONObject json = new JSONObject(sb.toString());
        f.documentId = json.getJSONObject("document").getString("id");

        addTagsToDocument(f);

        return true;
    }

    private void addTagsToDocument(F f) {
        if (selectedTags.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                String base_url = papra_url;
                if (!base_url.toLowerCase().startsWith("http")) {
                    base_url = "https://" + base_url;
                }

                if (!base_url.endsWith("/"))
                    base_url += "/";

                for (PapraTag tag : selectedTags) {
                    URL url = new URL(base_url + "api/organizations/" + orgId + "/documents/" + f.documentId + "/tags");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                    conn.setRequestProperty("Content-Type", "application/json");

                    JSONObject body = new JSONObject();
                    body.put("tagId", tag.id);

                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    os.flush();

                    int rc = conn.getResponseCode();
                    if (rc >= 300) {
                        Log.e("me.ocv.paprashare", "Error adding tag: " + rc);
                    }
                    conn.disconnect();
                }
            } catch (Exception e) {
                Log.e("me.ocv.paprashare", "Error adding tags: " + e.toString());
            }
        }).start();
    }

    void onsuccess() {
        String msg = "✅ 👍\n\nCompleted successfully";
        if (files != null) {
            msg += "\n\n" + files.length + (files.length == 1 ? " file" : " files") + " uploaded.";
        }
        show_msg(msg);
        ((TextView) findViewById(R.id.upper_info)).setGravity(Gravity.CENTER);

        String act = prefs.getString("on_up_ok", "close");
        if (act != null && !act.equals("menu")) {
            Toast.makeText(getApplicationContext(), "Upload OK", Toast.LENGTH_SHORT).show();
            finishAndRemoveTask();
            return;
        }

        findViewById(R.id.progbar).setVisibility(View.GONE);
        findViewById(R.id.successbuttons).setVisibility(View.VISIBLE);

        Button btn = (Button) findViewById(R.id.btnExit);
        btn.setOnClickListener(v -> finishAndRemoveTask());
    }
}
