package com.example.mycalendar2026sar;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Single shared SQLite database for the app's transactions (Cash In / Cash Out).
 * All expense-related screens should read/write through this helper so there is
 * only ever one source of truth for transaction data.
 */
public class TransactionDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "mycalendar.db";
    private static final int DB_VERSION = 10;

    public static final String TABLE_TRANSACTIONS = "transactions";
    public static final String COL_ID = "_id";
    public static final String COL_TITLE = "title";
    public static final String COL_AMOUNT = "amount";
    public static final String COL_CURRENCY = "currency";
    public static final String COL_TYPE = "type";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_ACCOUNT = "account";
    public static final String COL_NOTES = "notes";
    public static final String COL_VOICE_PATH = "voice_path";
    public static final String COL_BILLS = "bills";
    public static final String COL_DELETED = "deleted";

    public static final String TABLE_TX_NAMES = "transaction_names";
    public static final String COL_NAME_ID = "_id";
    public static final String COL_NAME_TEXT = "name";

    public static final String TABLE_NOTIFICATIONS = "notifications";
    public static final String COL_NOTIF_ID = "_id";
    public static final String COL_NOTIF_TITLE = "title";
    public static final String COL_NOTIF_NOTES = "notes";
    public static final String COL_NOTIF_DATE = "date";
    public static final String COL_NOTIF_START_TIME = "start_time";
    public static final String COL_NOTIF_END_TIME = "end_time";
    public static final String COL_NOTIF_PRIORITY = "priority";
    public static final String COL_NOTIF_STATUS = "status";
    public static final String COL_NOTIF_REPEAT = "repeat";
    public static final String COL_NOTIF_REMINDER = "reminder";
    public static final String COL_NOTIF_LOCATION = "location";
    public static final String COL_NOTIF_ATTACHMENTS = "attachments";
    public static final String COL_NOTIF_VOICE_PATH = "voice_path";
    public static final String COL_NOTIF_HISTORY = "history";
    public static final String COL_NOTIF_CATEGORY = "category";
    public static final String COL_NOTIF_COLOR = "color";
    public static final String COL_NOTIF_ALL_DAY = "all_day";
    public static final String COL_NOTIF_DELETED = "deleted";

    // Money Vault Tables
    public static final String TABLE_SAVINGS_VAULTS = "savings_vaults";
    public static final String COL_VAULT_ID = "_id";
    public static final String COL_VAULT_NAME = "name";
    public static final String COL_VAULT_CURRENCY = "currency";
    public static final String COL_VAULT_TARGET_AMOUNT = "target_amount";
    public static final String COL_VAULT_CURRENT_AMOUNT = "current_amount";
    public static final String COL_VAULT_CREATED_AT = "created_at";

    public static final String TABLE_VAULT_TRANSACTIONS = "vault_transactions";
    public static final String COL_VTX_ID = "_id";
    public static final String COL_VTX_VAULT_ID = "vault_id";
    public static final String COL_VTX_AMOUNT = "amount";
    public static final String COL_VTX_CURRENCY = "currency";
    public static final String COL_VTX_TYPE = "type";
    public static final String COL_VTX_DATE = "date";
    public static final String COL_VTX_NOTE = "note";

    public static final String TABLE_PLANNED_PAYMENTS = "planned_payments";
    public static final String COL_PP_ID = "_id";
    public static final String COL_PP_NAME = "name";
    public static final String COL_PP_TOTAL_AMOUNT = "total_amount";
    public static final String COL_PP_CURRENCY = "currency";
    public static final String COL_PP_START_DATE = "start_date";
    public static final String COL_PP_END_DATE = "end_date";
    public static final String COL_PP_FREQUENCY = "frequency";
    public static final String COL_PP_PAYMENT_DAY = "payment_day";
    public static final String COL_PP_REMINDER_DAYS_BEFORE = "reminder_days_before";
    public static final String COL_PP_DESCRIPTION = "description";

    public static final String TABLE_PAYMENT_SCHEDULE_ITEMS = "payment_schedule_items";
    public static final String COL_PSI_ID = "_id";
    public static final String COL_PSI_PLANNED_PAYMENT_ID = "planned_payment_id";
    public static final String COL_PSI_DUE_DATE = "due_date";
    public static final String COL_PSI_AMOUNT = "amount";
    public static final String COL_PSI_STATUS = "status";
    public static final String COL_PSI_PAID_TIMESTAMP = "paid_timestamp";
    public static final String COL_PSI_CASH_OUT_TX_ID = "cash_out_tx_id";

    // Flexible Save Transactions Table
    public static final String TABLE_SAVE_TRANSACTIONS = "save_transactions";
    public static final String COL_SAVE_ID = "_id";
    public static final String COL_SAVE_AMOUNT = "amount";
    public static final String COL_SAVE_TYPE = "type";
    public static final String COL_SAVE_CURRENCY = "currency";
    public static final String COL_SAVE_TIMESTAMP = "timestamp";
    public static final String COL_SAVE_BALANCE_AFTER = "balance_after";
    public static final String COL_SAVE_NOTE = "note";

    private static TransactionDbHelper instance;

    public static synchronized TransactionDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new TransactionDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private TransactionDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TRANSACTIONS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TITLE + " TEXT NOT NULL, " +
                COL_AMOUNT + " REAL NOT NULL, " +
                COL_CURRENCY + " TEXT DEFAULT 'USD', " +
                COL_TYPE + " TEXT NOT NULL, " +
                COL_TIMESTAMP + " INTEGER NOT NULL, " +
                COL_ACCOUNT + " TEXT, " +
                COL_NOTES + " TEXT, " +
                COL_VOICE_PATH + " TEXT, " +
                COL_BILLS + " TEXT, " +
                COL_DELETED + " INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TX_NAMES + " (" +
                COL_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NAME_TEXT + " TEXT UNIQUE NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NOTIFICATIONS + " (" +
                COL_NOTIF_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NOTIF_TITLE + " TEXT NOT NULL, " +
                COL_NOTIF_NOTES + " TEXT, " +
                COL_NOTIF_DATE + " TEXT NOT NULL, " +
                COL_NOTIF_START_TIME + " TEXT, " +
                COL_NOTIF_END_TIME + " TEXT, " +
                COL_NOTIF_PRIORITY + " TEXT, " +
                COL_NOTIF_STATUS + " TEXT, " +
                COL_NOTIF_REPEAT + " TEXT, " +
                COL_NOTIF_REMINDER + " TEXT, " +
                COL_NOTIF_LOCATION + " TEXT, " +
                COL_NOTIF_ATTACHMENTS + " TEXT, " +
                COL_NOTIF_VOICE_PATH + " TEXT, " +
                COL_NOTIF_HISTORY + " TEXT, " +
                COL_NOTIF_CATEGORY + " TEXT, " +
                COL_NOTIF_COLOR + " TEXT, " +
                COL_NOTIF_ALL_DAY + " INTEGER DEFAULT 0, " +
                COL_NOTIF_DELETED + " INTEGER DEFAULT 0)");
        createMoneyVaultTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_NOTES + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_VOICE_PATH + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_BILLS + " TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TX_NAMES + " (" +
                    COL_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_NAME_TEXT + " TEXT UNIQUE NOT NULL)");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_DELETED + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 5) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NOTIFICATIONS + " (" +
                    COL_NOTIF_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_NOTIF_TITLE + " TEXT NOT NULL, " +
                    COL_NOTIF_NOTES + " TEXT, " +
                    COL_NOTIF_DATE + " TEXT NOT NULL, " +
                    COL_NOTIF_START_TIME + " TEXT, " +
                    COL_NOTIF_END_TIME + " TEXT, " +
                    COL_NOTIF_PRIORITY + " TEXT, " +
                    COL_NOTIF_STATUS + " TEXT, " +
                    COL_NOTIF_REPEAT + " TEXT, " +
                    COL_NOTIF_REMINDER + " TEXT, " +
                    COL_NOTIF_LOCATION + " TEXT, " +
                    COL_NOTIF_ATTACHMENTS + " TEXT, " +
                    COL_NOTIF_VOICE_PATH + " TEXT, " +
                    COL_NOTIF_HISTORY + " TEXT, " +
                    COL_NOTIF_DELETED + " INTEGER DEFAULT 0)");
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE " + TABLE_TRANSACTIONS + " ADD COLUMN " + COL_CURRENCY + " TEXT DEFAULT 'USD'");
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE " + TABLE_NOTIFICATIONS + " ADD COLUMN " + COL_NOTIF_CATEGORY + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE_NOTIFICATIONS + " ADD COLUMN " + COL_NOTIF_COLOR + " TEXT");
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE " + TABLE_NOTIFICATIONS + " ADD COLUMN " + COL_NOTIF_ALL_DAY + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 9) {
            createMoneyVaultTables(db);
        }
        if (oldVersion < 10) {
            createSaveTable(db);
        }
    }

    private void createMoneyVaultTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SAVINGS_VAULTS + " (" +
                COL_VAULT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_VAULT_NAME + " TEXT NOT NULL, " +
                COL_VAULT_CURRENCY + " TEXT NOT NULL, " +
                COL_VAULT_TARGET_AMOUNT + " REAL, " +
                COL_VAULT_CURRENT_AMOUNT + " REAL DEFAULT 0.0, " +
                COL_VAULT_CREATED_AT + " INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_VAULT_TRANSACTIONS + " (" +
                COL_VTX_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_VTX_VAULT_ID + " INTEGER NOT NULL, " +
                COL_VTX_AMOUNT + " REAL NOT NULL, " +
                COL_VTX_CURRENCY + " TEXT NOT NULL, " +
                COL_VTX_TYPE + " TEXT NOT NULL, " +
                COL_VTX_DATE + " INTEGER NOT NULL, " +
                COL_VTX_NOTE + " TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PLANNED_PAYMENTS + " (" +
                COL_PP_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PP_NAME + " TEXT NOT NULL, " +
                COL_PP_TOTAL_AMOUNT + " REAL NOT NULL, " +
                COL_PP_CURRENCY + " TEXT NOT NULL, " +
                COL_PP_START_DATE + " TEXT NOT NULL, " +
                COL_PP_END_DATE + " TEXT NOT NULL, " +
                COL_PP_FREQUENCY + " TEXT NOT NULL, " +
                COL_PP_PAYMENT_DAY + " INTEGER NOT NULL, " +
                COL_PP_REMINDER_DAYS_BEFORE + " INTEGER DEFAULT 0, " +
                COL_PP_DESCRIPTION + " TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PAYMENT_SCHEDULE_ITEMS + " (" +
                COL_PSI_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PSI_PLANNED_PAYMENT_ID + " INTEGER NOT NULL, " +
                COL_PSI_DUE_DATE + " TEXT NOT NULL, " +
                COL_PSI_AMOUNT + " REAL NOT NULL, " +
                COL_PSI_STATUS + " TEXT NOT NULL, " +
                COL_PSI_PAID_TIMESTAMP + " INTEGER DEFAULT 0, " +
                COL_PSI_CASH_OUT_TX_ID + " INTEGER DEFAULT -1)");

        createSaveTable(db);
    }

    private void createSaveTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SAVE_TRANSACTIONS + " (" +
                COL_SAVE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SAVE_AMOUNT + " REAL NOT NULL, " +
                COL_SAVE_TYPE + " TEXT NOT NULL, " +
                COL_SAVE_CURRENCY + " TEXT NOT NULL DEFAULT 'USD', " +
                COL_SAVE_TIMESTAMP + " INTEGER NOT NULL, " +
                COL_SAVE_BALANCE_AFTER + " REAL NOT NULL, " +
                COL_SAVE_NOTE + " TEXT)");
    }

    /** Adds a saved transaction name/payee if it doesn't already exist. Ignores duplicates. */
    public long addTransactionName(String name) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NAME_TEXT, name);
        return db.insertWithOnConflict(TABLE_TX_NAMES, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void updateTransactionName(long id, String newName) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NAME_TEXT, newName);
        db.update(TABLE_TX_NAMES, values, COL_NAME_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void deleteTransactionName(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_TX_NAMES, COL_NAME_ID + "=?", new String[]{String.valueOf(id)});
    }

    /** Returns saved transaction names as id->name pairs, alphabetical. */
    public List<NamedEntry> getAllTransactionNames() {
        List<NamedEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_TX_NAMES, null, null, null, null, null, COL_NAME_TEXT + " ASC");
        if (c != null) {
            while (c.moveToNext()) {
                list.add(new NamedEntry(
                        c.getLong(c.getColumnIndexOrThrow(COL_NAME_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_NAME_TEXT))
                ));
            }
            c.close();
        }
        return list;
    }

    /** Simple id/name pair used for the Transaction Names list. */
    public static class NamedEntry {
        public final long id;
        public final String name;

        public NamedEntry(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public long addTransaction(String title, double amount, String type, long timestamp, String account) {
        return addTransaction(title, amount, "USD", type, timestamp, account, "", "", "");
    }

    public long addTransaction(String title, double amount, String currency, String type, long timestamp, String account, String notes, String voicePath, String bills) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title);
        values.put(COL_AMOUNT, amount);
        values.put(COL_CURRENCY, currency);
        values.put(COL_TYPE, type);
        values.put(COL_TIMESTAMP, timestamp);
        values.put(COL_ACCOUNT, account);
        values.put(COL_NOTES, notes);
        values.put(COL_VOICE_PATH, voicePath);
        values.put(COL_BILLS, bills);
        return db.insert(TABLE_TRANSACTIONS, null, values);
    }

    /** Soft-deletes a transaction (moves it to the Deleted Transactions folder). */
    public void deleteTransaction(long id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_DELETED, 1);
        db.update(TABLE_TRANSACTIONS, values, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    /** Soft-deletes all transactions associated with a specific account. */
    public void deleteTransactionsByAccount(String accountName) {
        if (accountName == null) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_DELETED, 1);
        db.update(TABLE_TRANSACTIONS, values, COL_ACCOUNT + "=?", new String[]{accountName});
    }

    /** Updates the account name for all transactions associated with an account. */
    public void updateAccountNameInTransactions(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equalsIgnoreCase(newName)) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ACCOUNT, newName);
        db.update(TABLE_TRANSACTIONS, values, COL_ACCOUNT + "=?", new String[]{oldName});
    }

    /** Moves a transaction back out of the Deleted Transactions folder. */
    public void restoreTransaction(long id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_DELETED, 0);
        db.update(TABLE_TRANSACTIONS, values, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    /** Permanently removes a single soft-deleted transaction; cannot be undone. */
    public void permanentlyDeleteTransaction(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_TRANSACTIONS, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    /** Permanently empties the Deleted Transactions folder; cannot be undone. */
    public void emptyDeletedTransactions() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_TRANSACTIONS, COL_DELETED + "=1", null);
    }

    /** Returns soft-deleted transactions, newest-deleted-first (by timestamp). */
    public List<Transaction> getDeletedTransactions() {
        List<Transaction> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_TRANSACTIONS, null, COL_DELETED + "=1", null, null, null,
                COL_TIMESTAMP + " DESC, " + COL_ID + " DESC");
        if (c != null) {
            while (c.moveToNext()) {
                list.add(readTransaction(c));
            }
            c.close();
        }
        return list;
    }

    public void clearAllTransactions() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_TRANSACTIONS, null, null);
    }

    public void updateTransaction(long id, String title, double amount, String currency, String type, long timestamp, String account, String notes, String voicePath, String bills) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, title);
        values.put(COL_AMOUNT, amount);
        values.put(COL_CURRENCY, currency);
        values.put(COL_TYPE, type);
        values.put(COL_TIMESTAMP, timestamp);
        values.put(COL_ACCOUNT, account);
        values.put(COL_NOTES, notes);
        values.put(COL_VOICE_PATH, voicePath);
        values.put(COL_BILLS, bills);
        db.update(TABLE_TRANSACTIONS, values, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void updateTransactionNote(long id, String newNote) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOTES, newNote);
        db.update(TABLE_TRANSACTIONS, values, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public Transaction getTransactionById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_TRANSACTIONS, null, COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        if (c != null && c.moveToFirst()) {
            Transaction t = readTransaction(c);
            c.close();
            return t;
        }
        if (c != null) c.close();
        return null;
    }

    public void addOrUpdateMonthlyIncome(double amount) {
        SQLiteDatabase db = getWritableDatabase();
        String title = "Monthly Income";
        
        // Check if "Monthly Income" exists (including potentially deleted ones)
        Cursor c = db.query(TABLE_TRANSACTIONS, new String[]{COL_ID}, COL_TITLE + "=?", new String[]{title}, null, null, null);
        if (c != null && c.moveToFirst()) {
            long id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
            c.close();
            
            // Update existing and ensure it's active (not deleted)
            ContentValues values = new ContentValues();
            values.put(COL_AMOUNT, amount);
            values.put(COL_TIMESTAMP, System.currentTimeMillis()); 
            values.put(COL_DELETED, 0); 
            db.update(TABLE_TRANSACTIONS, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        } else {
            if (c != null) c.close();
            // Add new
            addTransaction(title, amount, Transaction.TYPE_CASH_IN, System.currentTimeMillis(), "System");
        }
    }

    /** Returns every non-deleted transaction, sorted oldest -> newest (used for running-balance math). */
    public List<Transaction> getAllTransactionsAscending() {
        List<Transaction> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_TRANSACTIONS, null, COL_DELETED + "=0 OR " + COL_DELETED + " IS NULL",
                null, null, null, COL_TIMESTAMP + " ASC, " + COL_ID + " ASC");
        if (c != null) {
            while (c.moveToNext()) {
                list.add(readTransaction(c));
            }
            c.close();
        }
        return list;
    }

    public long addNotification(NotificationEvent event) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOTIF_TITLE, event.getTitle());
        values.put(COL_NOTIF_NOTES, event.getNotes());
        values.put(COL_NOTIF_DATE, event.getDate());
        values.put(COL_NOTIF_START_TIME, event.getStartTime());
        values.put(COL_NOTIF_END_TIME, event.getEndTime());
        values.put(COL_NOTIF_PRIORITY, event.getPriority());
        values.put(COL_NOTIF_STATUS, event.getStatus());
        values.put(COL_NOTIF_REPEAT, event.getRepeat());
        values.put(COL_NOTIF_REMINDER, event.getReminder());
        values.put(COL_NOTIF_LOCATION, event.getLocation());
        values.put(COL_NOTIF_ATTACHMENTS, event.getAttachments());
        values.put(COL_NOTIF_VOICE_PATH, event.getVoiceNotePath());
        values.put(COL_NOTIF_HISTORY, event.getHistory());
        values.put(COL_NOTIF_CATEGORY, event.getCategory());
        values.put(COL_NOTIF_COLOR, event.getEventColor());
        values.put(COL_NOTIF_ALL_DAY, event.isAllDay() ? 1 : 0);
        values.put(COL_NOTIF_DELETED, 0);
        return db.insert(TABLE_NOTIFICATIONS, null, values);
    }

    public void updateNotification(NotificationEvent event) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOTIF_TITLE, event.getTitle());
        values.put(COL_NOTIF_NOTES, event.getNotes());
        values.put(COL_NOTIF_DATE, event.getDate());
        values.put(COL_NOTIF_START_TIME, event.getStartTime());
        values.put(COL_NOTIF_END_TIME, event.getEndTime());
        values.put(COL_NOTIF_PRIORITY, event.getPriority());
        values.put(COL_NOTIF_STATUS, event.getStatus());
        values.put(COL_NOTIF_REPEAT, event.getRepeat());
        values.put(COL_NOTIF_REMINDER, event.getReminder());
        values.put(COL_NOTIF_LOCATION, event.getLocation());
        values.put(COL_NOTIF_ATTACHMENTS, event.getAttachments());
        values.put(COL_NOTIF_VOICE_PATH, event.getVoiceNotePath());
        values.put(COL_NOTIF_HISTORY, event.getHistory());
        values.put(COL_NOTIF_CATEGORY, event.getCategory());
        values.put(COL_NOTIF_COLOR, event.getEventColor());
        values.put(COL_NOTIF_ALL_DAY, event.isAllDay() ? 1 : 0);
        db.update(TABLE_NOTIFICATIONS, values, COL_NOTIF_ID + "=?", new String[]{String.valueOf(event.getId())});
    }

    public void deleteNotification(long id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NOTIF_DELETED, 1);
        db.update(TABLE_NOTIFICATIONS, values, COL_NOTIF_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void permanentlyDeleteNotification(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NOTIFICATIONS, COL_NOTIF_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<NotificationEvent> getNotificationsByDate(String date) {
        List<NotificationEvent> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_NOTIFICATIONS, null, COL_NOTIF_DATE + "=? AND " + COL_NOTIF_DELETED + "=0", new String[]{date}, null, null, COL_NOTIF_START_TIME + " ASC");
        if (c != null) {
            while (c.moveToNext()) {
                list.add(readNotification(c));
            }
            c.close();
        }
        return list;
    }

    public List<NotificationEvent> getAllActiveNotifications() {
        List<NotificationEvent> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_NOTIFICATIONS, null, COL_NOTIF_DELETED + "=0", null, null, null, COL_NOTIF_DATE + " ASC");
        if (c != null) {
            while (c.moveToNext()) {
                list.add(readNotification(c));
            }
            c.close();
        }
        return list;
    }

    public List<NotificationEvent> getAllNotificationsForBackup() {
        List<NotificationEvent> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_NOTIFICATIONS, null, null, null, null, null, COL_NOTIF_ID + " ASC");
        if (c != null) {
            while (c.moveToNext()) {
                list.add(readNotification(c));
            }
            c.close();
        }
        return list;
    }

    public void clearAllNotificationsPermanently() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NOTIFICATIONS, null, null);
    }

    public NotificationEvent getNotificationById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_NOTIFICATIONS, null, COL_NOTIF_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        if (c != null && c.moveToFirst()) {
            NotificationEvent e = readNotification(c);
            c.close();
            return e;
        }
        if (c != null) c.close();
        return null;
    }

    private NotificationEvent readNotification(Cursor c) {
        return new NotificationEvent(
                c.getLong(c.getColumnIndexOrThrow(COL_NOTIF_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_TITLE)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_NOTES)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_DATE)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_START_TIME)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_END_TIME)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_PRIORITY)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_STATUS)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_REPEAT)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_REMINDER)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_LOCATION)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_CATEGORY)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_COLOR)),
                c.getInt(c.getColumnIndexOrThrow(COL_NOTIF_ALL_DAY)) == 1,
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_ATTACHMENTS)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_VOICE_PATH)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTIF_HISTORY))
        );
    }

    private Transaction readTransaction(Cursor c) {
        return new Transaction(
                c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_TITLE)),
                c.getDouble(c.getColumnIndexOrThrow(COL_AMOUNT)),
                c.getString(c.getColumnIndexOrThrow(COL_CURRENCY)),
                c.getString(c.getColumnIndexOrThrow(COL_TYPE)),
                c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP)),
                c.getString(c.getColumnIndexOrThrow(COL_ACCOUNT)),
                c.getString(c.getColumnIndexOrThrow(COL_NOTES)),
                c.getString(c.getColumnIndexOrThrow(COL_VOICE_PATH)),
                c.getString(c.getColumnIndexOrThrow(COL_BILLS))
        );
    }

}
