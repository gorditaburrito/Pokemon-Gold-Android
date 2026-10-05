package com.sky.SkyEmu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** RetroForge V1: per-install one-time ROM import and direct launch. */
public class MainActivity extends Activity {
    private static final int PICK_ROM = 101;
    private File romFile() { return new File(getFilesDir(), "game.rom"); }
    private File romExtensionFile() { return new File(getFilesDir(), "game-extension.txt"); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (romFile().isFile() && romFile().length() > 256 && romExtensionFile().isFile()) {
            launchGame();
        } else {
            new AlertDialog.Builder(this)
                .setTitle("Import game")
                .setMessage("Select your legally obtained .gb, .gbc or .gba ROM. It will be copied into this app for offline play.")
                .setPositiveButton("Choose ROM", (d, w) -> chooseRom())
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setCancelable(false).show();
        }
    }
    private void chooseRom() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, PICK_ROM);
    }
    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != PICK_ROM) return;
        if (result != RESULT_OK || data == null || data.getData() == null) { finish(); return; }
        Uri uri = data.getData();
        String name = "";
        try (android.database.Cursor c = getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) { }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        String ext = lower.endsWith(".gba") ? ".gba" : lower.endsWith(".gbc") ? ".gbc" : lower.endsWith(".gb") ? ".gb" : null;
        if (ext == null) {
            Toast.makeText(this, "Choose a .gb, .gbc or .gba file", Toast.LENGTH_LONG).show();
            chooseRom(); return;
        }
        File tmp = new File(getFilesDir(), "game.partial");
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new java.io.IOException("Cannot read selected file");
            byte[] buf = new byte[65536]; int n; long total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > 64L * 1024 * 1024) throw new java.io.IOException("ROM too large");
                out.write(buf, 0, n);
            }
            if (total < 256) throw new java.io.IOException("ROM file is empty or invalid");
            if (romFile().exists() && !romFile().delete()) throw new java.io.IOException("Cannot replace ROM");
            if (!tmp.renameTo(romFile())) throw new java.io.IOException("Cannot store ROM");
            try (java.io.FileWriter writer = new java.io.FileWriter(romExtensionFile())) { writer.write(ext); }
            launchGame();
        } catch (Exception e) {
            tmp.delete();
            new AlertDialog.Builder(this).setTitle("Import failed").setMessage(e.getMessage())
                .setPositiveButton("Retry", (d,w) -> chooseRom())
                .setNegativeButton("Cancel", (d,w) -> finish()).show();
        }
    }
    private void launchGame() {
        String ext = ".gbc";
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(romExtensionFile()))) {
            String stored = reader.readLine();
            if (".gb".equals(stored) || ".gbc".equals(stored) || ".gba".equals(stored)) ext = stored;
        } catch (Exception ignored) { }
        // SkyEmu's existing native loader copies a file URI to externalFilesDir and loads it.
        // A named extension is necessary for emulator format detection.
        File externalDir = getExternalFilesDir(null);
        if (externalDir == null) {
            Toast.makeText(this, "Game storage unavailable", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        File playable = new File(externalDir, "retroforge-game" + ext);
        try (InputStream in = new java.io.FileInputStream(romFile());
             OutputStream out = new FileOutputStream(playable)) {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to prepare game", Toast.LENGTH_LONG).show(); finish(); return;
        }
        Intent i = new Intent(this, EnhancedNativeActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.fromFile(playable));
        startActivity(i);
        finish();
    }
}
