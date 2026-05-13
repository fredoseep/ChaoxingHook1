package com.fredoseep.chaoxinghook;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class SettingsActivity extends AppCompatActivity {

    private Switch switchLocationModify;
    private Switch switchAddressModify;
    private Switch switchNameModify;
    private Switch switchRandomDeviceFlag;
    private Switch switchAutoCalculate;
    private Switch switchExamCheatBypass;
    private Switch switchCopyRestriction;
    private Switch switchExamScreenshotReplace;

    private EditText etLatitude;
    private EditText etLongitude;
    private EditText etLatitudeBlast;
    private EditText etLongitudeBlast;
    private EditText etAddress;
    private EditText etName;
    private EditText etScreenshotPath;

    private LinearLayout layoutLatitude;
    private LinearLayout layoutLongitude;
    private LinearLayout layoutLatitudeBlast;
    private LinearLayout layoutLongitudeBlast;
    private LinearLayout layoutAddress;
    private LinearLayout layoutName;
    private LinearLayout layoutScreenshotPath;

    private static final String CONFIG_PATH = "/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        loadConfig();
        setupListeners();
    }

    private void initViews() {
        switchLocationModify = findViewById(R.id.switch_location_modify);
        switchAddressModify = findViewById(R.id.switch_address_modify);
        switchNameModify = findViewById(R.id.switch_name_modify);
        switchRandomDeviceFlag = findViewById(R.id.switch_random_device_flag);
        switchAutoCalculate = findViewById(R.id.switch_auto_calculate);
        switchExamCheatBypass = findViewById(R.id.switch_exam_cheat_bypass);
        switchCopyRestriction = findViewById(R.id.switch_copy_restriction);
        switchExamScreenshotReplace = findViewById(R.id.switch_exam_screenshot_replace);

        etLatitude = findViewById(R.id.et_latitude);
        etLongitude = findViewById(R.id.et_longitude);
        etLatitudeBlast = findViewById(R.id.et_latitude_blast);
        etLongitudeBlast = findViewById(R.id.et_longitude_blast);
        etAddress = findViewById(R.id.et_address);
        etName = findViewById(R.id.et_name);
        etScreenshotPath = findViewById(R.id.et_screenshot_path);

        layoutLatitude = findViewById(R.id.layout_latitude);
        layoutLongitude = findViewById(R.id.layout_longitude);
        layoutLatitudeBlast = findViewById(R.id.layout_latitude_blast);
        layoutLongitudeBlast = findViewById(R.id.layout_longitude_blast);
        layoutAddress = findViewById(R.id.layout_address);
        layoutName = findViewById(R.id.layout_name);
        layoutScreenshotPath = findViewById(R.id.layout_screenshot_path);

        LinearLayout btnSave = findViewById(R.id.btn_save);
        LinearLayout btnReset = findViewById(R.id.btn_reset);

        btnSave.setOnClickListener(v -> saveConfig());
        btnReset.setOnClickListener(v -> resetConfig());
    }

    private void setupListeners() {
        switchLocationModify.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                switchAutoCalculate.setChecked(false);
            }
            layoutLatitude.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            layoutLongitude.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        switchAddressModify.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutAddress.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        switchNameModify.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutName.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        switchAutoCalculate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                switchLocationModify.setChecked(false);
            }
            layoutLatitudeBlast.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            layoutLongitudeBlast.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        switchExamScreenshotReplace.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutScreenshotPath.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
    }

    private void loadConfig() {
        File file = new File(CONFIG_PATH);
        if (!file.exists()) {
            createDefaultConfig();
            return;
        }

        switchExamCheatBypass.setChecked(true);
        switchCopyRestriction.setChecked(true);
        switchExamScreenshotReplace.setChecked(false);

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("是否开启定位修改:")) {
                    switchLocationModify.setChecked(parseBoolean(line));
                } else if (line.startsWith("经度:")) {
                    String v = parseString(line);
                    etLongitude.setText(v);
                    etLongitudeBlast.setText(v);
                } else if (line.startsWith("纬度:")) {
                    String v = parseString(line);
                    etLatitude.setText(v);
                    etLatitudeBlast.setText(v);
                } else if (line.startsWith("是否开启地址名修改:")) {
                    switchAddressModify.setChecked(parseBoolean(line));
                } else if (line.startsWith("地址名:")) {
                    etAddress.setText(parseString(line));
                } else if (line.startsWith("是否开启名字修改:")) {
                    switchNameModify.setChecked(parseBoolean(line));
                } else if (line.startsWith("名字:")) {
                    etName.setText(parseString(line));
                } else if (line.startsWith("是否开启随机指纹:")) {
                    switchRandomDeviceFlag.setChecked(parseBoolean(line));
                } else if (line.startsWith("是否开启经纬度爆破:")) {
                    switchAutoCalculate.setChecked(parseBoolean(line));
                } else if (line.startsWith("是否开启考试风控拦截:")) {
                    switchExamCheatBypass.setChecked(parseBoolean(line));
                } else if (line.startsWith("是否开启复制限制解除:")) {
                    switchCopyRestriction.setChecked(parseBoolean(line));
                } else if (line.startsWith("是否开启考试截图替换:")) {
                    switchExamScreenshotReplace.setChecked(parseBoolean(line));
                } else if (line.startsWith("截图替换路径:")) {
                    etScreenshotPath.setText(parseString(line));
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "加载配置失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        layoutLatitude.setVisibility(switchLocationModify.isChecked() ? View.VISIBLE : View.GONE);
        layoutLongitude.setVisibility(switchLocationModify.isChecked() ? View.VISIBLE : View.GONE);
        layoutLatitudeBlast.setVisibility(switchAutoCalculate.isChecked() ? View.VISIBLE : View.GONE);
        layoutLongitudeBlast.setVisibility(switchAutoCalculate.isChecked() ? View.VISIBLE : View.GONE);
        layoutAddress.setVisibility(switchAddressModify.isChecked() ? View.VISIBLE : View.GONE);
        layoutName.setVisibility(switchNameModify.isChecked() ? View.VISIBLE : View.GONE);
        layoutScreenshotPath.setVisibility(switchExamScreenshotReplace.isChecked() ? View.VISIBLE : View.GONE);
    }

    private void saveConfig() {
        String configContent = "是否开启定位修改: " + switchLocationModify.isChecked() + "\n" +
                "经度: " + (switchAutoCalculate.isChecked() ? etLongitudeBlast.getText().toString() : etLongitude.getText().toString()) + "\n" +
                "纬度: " + (switchAutoCalculate.isChecked() ? etLatitudeBlast.getText().toString() : etLatitude.getText().toString()) + "\n" +
                "是否开启地址名修改: " + switchAddressModify.isChecked() + "\n" +
                "地址名: " + etAddress.getText().toString() + "\n" +
                "是否开启名字修改: " + switchNameModify.isChecked() + "\n" +
                "名字: " + etName.getText().toString() + "\n" +
                "是否开启随机指纹: " + switchRandomDeviceFlag.isChecked() + "\n" +
                "是否开启经纬度爆破: " + switchAutoCalculate.isChecked() + "\n" +
                "是否开启考试风控拦截: " + switchExamCheatBypass.isChecked() + "\n" +
                "是否开启复制限制解除: " + switchCopyRestriction.isChecked() + "\n" +
                "是否开启考试截图替换: " + switchExamScreenshotReplace.isChecked() + "\n" +
                "截图替换路径: " + etScreenshotPath.getText().toString() + "\n";

        if (writeFileWithRoot(CONFIG_PATH, configContent)) {
            Toast.makeText(this, "配置保存成功", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "保存配置失败，可能需要Root权限", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean writeFileWithRoot(String path, String content) {
        try {
            java.io.File file = new java.io.File(path);
            java.io.File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                Process mkdirProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", "mkdir -p " + parentDir.getAbsolutePath()});
                mkdirProcess.waitFor();
            }

            java.io.File tempFile = java.io.File.createTempFile("chaoxing_config", ".tmp");
            java.io.FileWriter fw = new java.io.FileWriter(tempFile);
            fw.write(content);
            fw.close();

            String command = "cp " + tempFile.getAbsolutePath() + " " + path;
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            int exitCode = process.waitFor();
            
            tempFile.delete();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void resetConfig() {
        switchLocationModify.setChecked(false);
        switchAddressModify.setChecked(false);
        switchNameModify.setChecked(false);
        switchRandomDeviceFlag.setChecked(true);
        switchAutoCalculate.setChecked(false);
        switchExamCheatBypass.setChecked(true);
        switchCopyRestriction.setChecked(true);
        switchExamScreenshotReplace.setChecked(false);

        etLatitude.setText("");
        etLongitude.setText("");
        etLatitudeBlast.setText("");
        etLongitudeBlast.setText("");
        etAddress.setText("");
        etName.setText("");
        etScreenshotPath.setText("");

        layoutLatitude.setVisibility(View.GONE);
        layoutLongitude.setVisibility(View.GONE);
        layoutLatitudeBlast.setVisibility(View.GONE);
        layoutLongitudeBlast.setVisibility(View.GONE);
        layoutAddress.setVisibility(View.GONE);
        layoutName.setVisibility(View.GONE);
        layoutScreenshotPath.setVisibility(View.GONE);
        saveConfig();
    }

    private void createDefaultConfig() {
        String defaultConfig = "是否开启定位修改: false\n" +
                "经度: \n" +
                "纬度: \n" +
                "是否开启地址名修改: false\n" +
                "地址名: \n" +
                "是否开启名字修改: false\n" +
                "名字: \n" +
                "是否开启随机指纹: true\n" +
                "是否开启经纬度爆破: false\n" +
                "是否开启考试风控拦截: true\n" +
                "是否开启复制限制解除: true\n" +
                "是否开启考试截图替换: false\n" +
                "截图替换路径: /storage/emulated/0/Download/fake_exam_image.png\n";
        
        writeFileWithRoot(CONFIG_PATH, defaultConfig);
    }

    private boolean parseBoolean(String line) {
        try {
            return "true".equalsIgnoreCase(line.substring(line.indexOf(":") + 1).trim());
        } catch (Exception e) {
            return false;
        }
    }

    private String parseString(String line) {
        try {
            return line.substring(line.indexOf(":") + 1).trim();
        } catch (Exception e) {
            return "";
        }
    }
}