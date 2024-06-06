package com.itnation.imagetopdf;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGES_REQUEST_CODE = 1;
    private static final int STORAGE_PERMISSION_CODE = 2;

    private Button btnSelectImages;
    private Button btnGeneratePdf;
    private RecyclerView recyclerView;
    private ImageView ic_view, info_id;
    private PreViewImageAdapter imageAdapter;
    private List<Uri> imageUris;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSelectImages = findViewById(R.id.btn_select_images);
        btnGeneratePdf = findViewById(R.id.btn_generate_pdf);
        recyclerView = findViewById(R.id.recycler_view);
        ic_view = findViewById(R.id.ic_view);
        info_id = findViewById(R.id.info_id);

        imageUris = new ArrayList<>();
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        imageAdapter = new PreViewImageAdapter(this, imageUris);
        recyclerView.setAdapter(imageAdapter);

        btnSelectImages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, STORAGE_PERMISSION_CODE);
                    } else {
                        selectImages();
                        recyclerView.setVisibility(View.VISIBLE);
                        ic_view.setVisibility(View.GONE);
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
                    } else {
                        selectImages();
                        recyclerView.setVisibility(View.VISIBLE);
                        ic_view.setVisibility(View.GONE);
                    }
                }
            }
        });

        btnGeneratePdf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!imageUris.isEmpty()) {
                    generatePdf();
                } else {
                    Toast.makeText(MainActivity.this, "Please select images first", Toast.LENGTH_SHORT).show();
                }
            }
        });

        info_id.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCustomDialog();
            }
        });
    }

    private void showCustomDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View customView = inflater.inflate(R.layout.custom_alert_dialog, null);
        Button dialogButton = customView.findViewById(R.id.okButton);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(customView);
        final AlertDialog alertDialog = builder.create();
        dialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.dismiss();
            }
        });
        alertDialog.show();
    }

    private void selectImages() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Select Images"), PICK_IMAGES_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    selectImages();
                    recyclerView.setVisibility(View.VISIBLE);
                    ic_view.setVisibility(View.GONE);
                } else {
                    Toast.makeText(this, "Storage permission is required to select images", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == PICK_IMAGES_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                imageUris.clear();
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        Uri imageUri = data.getClipData().getItemAt(i).getUri();
                        imageUris.add(imageUri);
                    }
                } else if (data.getData() != null) {
                    Uri imageUri = data.getData();
                    imageUris.add(imageUri);
                }
                imageAdapter.notifyDataSetChanged();
            }
        }
    }

    private void generatePdf() {
        PdfDocument pdfDocument = new PdfDocument();

        for (Uri imageUri : imageUris) {
            try {
                Bitmap bitmap = getBitmapFromUri(imageUri);
                if (bitmap != null) {
                    Bitmap scaledBitmap = scaleBitmapToFitMargins(bitmap);
                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(612, 850, 1).create();
                    PdfDocument.Page page = pdfDocument.startPage(pageInfo);
                    float x = 36;
                    float y = 36;
                    page.getCanvas().drawBitmap(scaledBitmap, x, y, null);
                    pdfDocument.finishPage(page);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        File pdfFolder = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ImageToPdf");
        if (!pdfFolder.exists()) {
            pdfFolder.mkdirs();
        }

        String fileName = "PDF_" + UUID.randomUUID().toString() + ".pdf";
        File pdfFile = new File(pdfFolder, fileName);

        try {
            pdfDocument.writeTo(new FileOutputStream(pdfFile));
            Toast.makeText(this, "PDF generated: " + pdfFile.getPath(), Toast.LENGTH_LONG).show();
            openGeneratedPDF(pdfFile);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error generating PDF", Toast.LENGTH_SHORT).show();
        } finally {
            pdfDocument.close();
        }
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        return BitmapFactory.decodeStream(inputStream);
    }

    private Bitmap scaleBitmapToFitMargins(Bitmap bitmap) {
        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int targetWidth = 540;
        int targetHeight = 778;
        float scaleFactor = Math.min((float) targetWidth / originalWidth, (float) targetHeight / originalHeight);
        int scaledWidth = Math.round(scaleFactor * originalWidth);
        int scaledHeight = Math.round(scaleFactor * originalHeight);
        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
    }

    private void openGeneratedPDF(File pdfFile) {
        Uri pdfUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", pdfFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(pdfUri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectImages();
            } else {
                Toast.makeText(this, "Storage permission is required to select images", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
