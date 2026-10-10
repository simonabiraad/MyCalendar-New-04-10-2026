package com.example.mycalendar2026sar;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class SecurityHelper {

    public static final String PREFS_NAME = "SecuritySettings";
    public static final String KEY_CUSTOM_PASSWORD = "custom_password";
    public static final String KEY_PASSWORD_DISABLED = "password_disabled";
    public static final String KEY_SB_PASSWORD_DISABLED = "sb_password_disabled";
    public static final String KEY_EXP_PASSWORD_DISABLED = "exp_password_disabled";
    public static final String KEY_MV_PASSWORD_PROTECTED = "mv_password_protected";
    public static final String PREFIX_ACCOUNT_PROTECTED = "account_protected_";

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isGlobalPasswordDisabled(Context context) {
        return getPrefs(context).getBoolean(KEY_PASSWORD_DISABLED, false);
    }

    public static boolean isExpensesProtected(Context context) {
        if (isGlobalPasswordDisabled(context)) return false;
        return !getPrefs(context).getBoolean(KEY_EXP_PASSWORD_DISABLED, false);
    }

    public static boolean isMoneyVaultProtected(Context context) {
        if (isGlobalPasswordDisabled(context)) return false;
        return getPrefs(context).getBoolean(KEY_MV_PASSWORD_PROTECTED, false);
    }

    public static boolean isAccountProtected(Context context, String accountName) {
        if (accountName == null || accountName.trim().isEmpty()) return false;
        String cleanName = accountName.trim();
        if (cleanName.equalsIgnoreCase("Expenses")) {
            return isExpensesProtected(context);
        }
        if (isGlobalPasswordDisabled(context)) return false;
        return getPrefs(context).getBoolean(PREFIX_ACCOUNT_PROTECTED + cleanName, false);
    }

    public static void setExpensesProtected(Context context, boolean protectedState) {
        getPrefs(context).edit().putBoolean(KEY_EXP_PASSWORD_DISABLED, !protectedState).apply();
    }

    public static void setMoneyVaultProtected(Context context, boolean protectedState) {
        getPrefs(context).edit().putBoolean(KEY_MV_PASSWORD_PROTECTED, protectedState).apply();
    }

    public static void setAccountProtected(Context context, String accountName, boolean protectedState) {
        if (accountName == null || accountName.trim().isEmpty()) return;
        String cleanName = accountName.trim();
        if (cleanName.equalsIgnoreCase("Expenses")) {
            setExpensesProtected(context, protectedState);
            return;
        }
        getPrefs(context).edit().putBoolean(PREFIX_ACCOUNT_PROTECTED + cleanName, protectedState).apply();
    }

    public static void authenticateIfProtected(Activity activity, String title, boolean isProtected, Runnable onSuccess) {
        authenticateIfProtected(activity, title, isProtected, onSuccess, null);
    }

    public static void authenticateIfProtected(Activity activity, String title, boolean isProtected, Runnable onSuccess, Runnable onFailure) {
        if (!isProtected) {
            if (onSuccess != null) onSuccess.run();
            return;
        }
        authenticate(activity, title, onSuccess, onFailure);
    }

    public static void authenticateIfExpensesProtected(Activity activity, Runnable onSuccess) {
        authenticateIfProtected(activity, "Expenses Access", isExpensesProtected(activity), onSuccess);
    }

    public static void authenticateIfMoneyVaultProtected(Activity activity, Runnable onSuccess) {
        authenticateIfProtected(activity, "Money Vault Access", isMoneyVaultProtected(activity), onSuccess);
    }

    public static void authenticateIfAccountProtected(Activity activity, String accountName, Runnable onSuccess) {
        if (accountName == null || accountName.trim().isEmpty()) {
            if (onSuccess != null) onSuccess.run();
            return;
        }
        authenticateIfProtected(activity, accountName + " Access", isAccountProtected(activity, accountName), onSuccess);
    }

    public static void authenticate(Activity activity, String title, Runnable onSuccess) {
        authenticate(activity, title, onSuccess, null);
    }

    public static void authenticate(Activity activity, String title, Runnable onSuccess, Runnable onFailure) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        SharedPreferences prefs = getPrefs(activity);
        String customPass = prefs.getString(KEY_CUSTOM_PASSWORD, null);

        if (customPass != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
            builder.setTitle(title != null ? title : "Authentication Required");
            builder.setMessage("Enter your custom password:");

            final EditText input = new EditText(activity);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            builder.setView(input);

            builder.setPositiveButton("Access", (dialog, which) -> {
                String entered = input.getText().toString().trim();
                if (entered.equals(customPass)) {
                    if (onSuccess != null) onSuccess.run();
                } else {
                    Toast.makeText(activity, "Incorrect Password", Toast.LENGTH_SHORT).show();
                    if (onFailure != null) onFailure.run();
                }
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                if (onFailure != null) onFailure.run();
            });
            ThemeManager.showDialog(builder, activity);
        } else if (activity instanceof FragmentActivity) {
            Executor executor = ContextCompat.getMainExecutor(activity);
            BiometricPrompt biometricPrompt = new BiometricPrompt((FragmentActivity) activity, executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                    errorCode != BiometricPrompt.ERROR_CANCELED) {
                                Toast.makeText(activity.getApplicationContext(), "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                            }
                            if (onFailure != null) onFailure.run();
                        }

                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            if (onSuccess != null) onSuccess.run();
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            Toast.makeText(activity.getApplicationContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
                            if (onFailure != null) onFailure.run();
                        }
                    });

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title != null ? title : "Authentication Required")
                    .setSubtitle("Use your phone's PIN, Pattern, or Biometrics")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();

            biometricPrompt.authenticate(promptInfo);
        } else {
            if (onSuccess != null) onSuccess.run();
        }
    }

    public static void verifyIdentity(Activity activity, String title, Runnable onSuccess) {
        authenticate(activity, title, onSuccess);
    }

    public static void showPasswordConfigurationDialog(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        boolean expProt = isExpensesProtected(activity);
        boolean mvProt = isMoneyVaultProtected(activity);

        String expLabel = "Expenses " + (expProt ? "(Protected)" : "(Unprotected)");
        String mvLabel = "Money Vault " + (mvProt ? "(Protected)" : "(Unprotected)");

        String[] options = {
                expLabel,
                mvLabel,
                "Accounts",
                "Authentication Method (Password / Biometrics)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        builder.setTitle("Set Password Protection");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                toggleExpensesProtection(activity);
            } else if (which == 1) {
                toggleMoneyVaultProtection(activity);
            } else if (which == 2) {
                showAccountProtectionListDialog(activity);
            } else if (which == 3) {
                showSetAuthMethodDialog(activity);
            }
        });
        builder.setNegativeButton("Close", null);
        ThemeManager.showDialog(builder, activity);
    }

    private static void toggleExpensesProtection(Activity activity) {
        boolean currentlyProtected = isExpensesProtected(activity);
        String[] opts = {"Enable Protection", "Disable Protection"};
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        builder.setTitle("Expenses Protection");
        builder.setItems(opts, (dialog, which) -> {
            if (which == 0) {
                setExpensesProtected(activity, true);
                getPrefs(activity).edit().putBoolean(KEY_PASSWORD_DISABLED, false).apply();
                Toast.makeText(activity, "Expenses is now password protected.", Toast.LENGTH_SHORT).show();
            } else {
                if (currentlyProtected) {
                    verifyIdentity(activity, "Verify Identity to Disable Expenses Security", () -> {
                        setExpensesProtected(activity, false);
                        Toast.makeText(activity, "Expenses protection disabled.", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    setExpensesProtected(activity, false);
                    Toast.makeText(activity, "Expenses protection disabled.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        ThemeManager.showDialog(builder, activity);
    }

    private static void toggleMoneyVaultProtection(Activity activity) {
        boolean currentlyProtected = isMoneyVaultProtected(activity);
        String[] opts = {"Enable Protection", "Disable Protection"};
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        builder.setTitle("Money Vault Protection");
        builder.setItems(opts, (dialog, which) -> {
            if (which == 0) {
                setMoneyVaultProtected(activity, true);
                getPrefs(activity).edit().putBoolean(KEY_PASSWORD_DISABLED, false).apply();
                Toast.makeText(activity, "Money Vault is now password protected.", Toast.LENGTH_SHORT).show();
            } else {
                if (currentlyProtected) {
                    verifyIdentity(activity, "Verify Identity to Disable Money Vault Security", () -> {
                        setMoneyVaultProtected(activity, false);
                        Toast.makeText(activity, "Money Vault protection disabled.", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    setMoneyVaultProtected(activity, false);
                    Toast.makeText(activity, "Money Vault protection disabled.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        ThemeManager.showDialog(builder, activity);
    }

    public static void showAccountProtectionListDialog(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        List<Account> accounts = BalanceManager.loadAccounts(activity);
        if (accounts.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
            builder.setTitle("Protect Accounts");
            builder.setMessage("No accounts have been created yet. Please create an account first to enable protection.");
            builder.setPositiveButton("OK", null);
            ThemeManager.showDialog(builder, activity);
            return;
        }

        List<String> items = new ArrayList<>();
        for (Account a : accounts) {
            String name = a.getName() != null ? a.getName().trim() : "";
            if (name.isEmpty()) continue;
            boolean isProt = isAccountProtected(activity, name);
            items.add(name + (isProt ? " (Protected)" : " (Unprotected)"));
        }

        if (items.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
            builder.setTitle("Protect Accounts");
            builder.setMessage("No valid accounts found.");
            builder.setPositiveButton("OK", null);
            ThemeManager.showDialog(builder, activity);
            return;
        }

        String[] itemArray = items.toArray(new String[0]);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        builder.setTitle("Select Account to Protect");
        builder.setItems(itemArray, (dialog, which) -> {
            Account selectedAccount = accounts.get(which);
            String accName = selectedAccount.getName() != null ? selectedAccount.getName().trim() : "";
            configureSingleAccountProtection(activity, accName);
        });
        builder.setNegativeButton("Back", (dialog, which) -> showPasswordConfigurationDialog(activity));
        ThemeManager.showDialog(builder, activity);
    }

    private static void configureSingleAccountProtection(Activity activity, String accountName) {
        boolean isProt = isAccountProtected(activity, accountName);
        String[] opts = {"Enable Protection", "Disable Protection"};
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        builder.setTitle("Account Protection: " + accountName);
        builder.setItems(opts, (dialog, which) -> {
            if (which == 0) {
                setAccountProtected(activity, accountName, true);
                getPrefs(activity).edit().putBoolean(KEY_PASSWORD_DISABLED, false).apply();
                Toast.makeText(activity, accountName + " is now password protected.", Toast.LENGTH_SHORT).show();
            } else {
                if (isProt) {
                    verifyIdentity(activity, "Verify Identity to Disable " + accountName + " Security", () -> {
                        setAccountProtected(activity, accountName, false);
                        Toast.makeText(activity, accountName + " protection disabled.", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    setAccountProtected(activity, accountName, false);
                    Toast.makeText(activity, accountName + " protection disabled.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        ThemeManager.showDialog(builder, activity);
    }

    public static void showSetAuthMethodDialog(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        String[] options = {"Use Phone Lock Screen (Fingerprint/PIN)", "Set a New Custom Password", "Disable All Security"};
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        builder.setTitle("Security Authentication Method");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                getPrefs(activity).edit()
                        .remove(KEY_CUSTOM_PASSWORD)
                        .putBoolean(KEY_PASSWORD_DISABLED, false)
                        .apply();
                Toast.makeText(activity, "Security enabled (Synced with phone lock / biometrics).", Toast.LENGTH_SHORT).show();
            } else if (which == 1) {
                showSetCustomPasswordDialog(activity);
            } else if (which == 2) {
                verifyIdentity(activity, "Disable All Security", () -> {
                    getPrefs(activity).edit().putBoolean(KEY_PASSWORD_DISABLED, true).apply();
                    Toast.makeText(activity, "All security protection disabled.", Toast.LENGTH_SHORT).show();
                });
            }
        });
        builder.setNegativeButton("Cancel", null);
        ThemeManager.showDialog(builder, activity);
    }

    private static void showSetCustomPasswordDialog(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        builder.setTitle("Set New Password");
        builder.setMessage("Enter the custom password you want to set:");

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newPass = input.getText().toString().trim();
            if (!newPass.isEmpty()) {
                getPrefs(activity).edit()
                        .putString(KEY_CUSTOM_PASSWORD, newPass)
                        .putBoolean(KEY_PASSWORD_DISABLED, false)
                        .apply();
                Toast.makeText(activity, "Custom password saved successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "Password cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        ThemeManager.showDialog(builder, activity);
    }
}
