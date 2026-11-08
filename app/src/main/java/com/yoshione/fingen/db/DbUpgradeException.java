package com.yoshione.fingen.db;

/**
 * Ошибка миграции БД, после которой апгрейд откатывается и версия 35 остается нетронутой.
 */
public class DbUpgradeException extends RuntimeException {

    public static final int CODE_NO_SPACE = 1;
    public static final int CODE_FAILED = 2;
    public static final int CODE_VERIFY = 3;

    private final int mCode;
    private final long mRequiredBytes;

    public DbUpgradeException(int code, String message) {
        this(code, message, 0, null);
    }

    public DbUpgradeException(int code, String message, long requiredBytes) {
        this(code, message, requiredBytes, null);
    }

    public DbUpgradeException(int code, String message, Throwable cause) {
        this(code, message, 0, cause);
    }

    private DbUpgradeException(int code, String message, long requiredBytes, Throwable cause) {
        super(message, cause);
        mCode = code;
        mRequiredBytes = requiredBytes;
    }

    public int getCode() {
        return mCode;
    }

    /** Сколько байт нужно свободного места (для CODE_NO_SPACE). */
    public long getRequiredBytes() {
        return mRequiredBytes;
    }
}
