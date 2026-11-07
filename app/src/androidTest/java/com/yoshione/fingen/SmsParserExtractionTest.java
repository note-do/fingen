package com.yoshione.fingen;

import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.dao.AccountsDAO;
import com.yoshione.fingen.dao.CabbagesDAO;
import com.yoshione.fingen.dao.SendersDAO;
import com.yoshione.fingen.dao.SmsMarkersDAO;
import com.yoshione.fingen.model.Account;
import com.yoshione.fingen.model.Cabbage;
import com.yoshione.fingen.model.Sender;
import com.yoshione.fingen.model.Sms;
import com.yoshione.fingen.model.SmsMarker;
import com.yoshione.fingen.model.Transaction;
import com.yoshione.fingen.utils.SmsParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Извлечение суммы, знака (типа транзакции), баланса и даты из текста SMS.
 * Тексты взяты из примеров SmsParserTest и обрабатываются кодом SmsParser.
 * Маркеры и счета создаются на каждый тест, после теста DbSnapshot откатывает БД.
 */
@RunWith(AndroidJUnit4.class)
public class SmsParserExtractionTest {

    private static final Date SMS_DATE = new Date(1700000000000L);

    private Context mContext;
    private DbSnapshot mSnapshot;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mSnapshot = DbSnapshot.take(mContext);
        // Маркеры SmsParser читает все подряд, поэтому чужие маркеры исказили бы результат.
        assertTrue("В БД до теста есть маркеры SMS, оставленные другим тестом (например SmsParserTest). "
                        + "Запустите класс отдельно или переустановите приложение.",
                SmsMarkersDAO.getInstance(mContext).getAllModels().isEmpty());
    }

    @After
    public void tearDown() {
        mSnapshot.restore(mContext);
    }

    /**
     * Создает валюту, счет в этой валюте, отправителя с позициями суммы и баланса и маркеры
     * для одного текста, затем возвращает парсер. Маркеры должны быть созданы до конструктора,
     * так как SmsParser читает их в конструкторе.
     */
    private SmsParser createParser(String body, String currency, String accountMarker,
                                   int trType, String trTypeMarker, int amountPos, int balancePos) throws Exception {
        // Код валюты уникален в БД (ref_Currencies.Code), поэтому берется отдельный код теста.
        // Маркер валюты (currency) задает текст, по которому парсер ищет сумму.
        String code = "T" + System.nanoTime();
        Cabbage cabbage = (Cabbage) CabbagesDAO.getInstance(mContext).createModel(
                new Cabbage(-1, code, currency, currency, 2));
        Account account = (Account) AccountsDAO.getInstance(mContext).createModel(
                new Account(-1, "Account", cabbage.getID(), "", "", BigDecimal.ZERO,
                        Account.AccountType.atCash, 0, false, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO));
        Sender sender = (Sender) SendersDAO.getInstance(mContext).createModel(
                new Sender(-1, "Bank", true, "9000", amountPos, balancePos, "", false, false));

        SmsMarkersDAO markersDAO = SmsMarkersDAO.getInstance(mContext);
        markersDAO.createModel(new SmsMarker(-1, SmsParser.MARKER_TYPE_ACCOUNT, String.valueOf(account.getID()), accountMarker));
        markersDAO.createModel(new SmsMarker(-1, SmsParser.MARKER_TYPE_CABBAGE, String.valueOf(cabbage.getID()), currency));
        markersDAO.createModel(new SmsMarker(-1, SmsParser.MARKER_TYPE_TRTYPE, String.valueOf(trType), trTypeMarker));

        return new SmsParser(new Sms(1, SMS_DATE, sender.getID(), body), mContext);
    }

    private static BigDecimal amountOf(SmsParser parser) {
        return parser.extractAmount(parser.extractAccount());
    }

    /** Сравнение с точностью до копеек: BigDecimal из double не всегда точный. */
    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull("Сумма не извлечена (null)", actual);
        assertEquals(new BigDecimal(expected), actual.setScale(2, RoundingMode.HALF_UP));
    }

    private static void assertZeroAmount(BigDecimal actual) {
        assertEquals("Ожидался ноль", 0, actual.compareTo(BigDecimal.ZERO));
    }

    // ---------- Сумма ----------

    @Test
    public void amount_rurBeforeBalance_takesFirstAmount() throws Exception {
        // Тот же текст, что в SmsParserTest (пример 1)
        SmsParser parser = createParser(
                "Pokupka 300.00 RUR schet **1234 Torgovaya tochka: BURGER KING Data 05/01/14 Balans: 1100.55 RUR",
                "RUR", "**1234", Transaction.TRANSACTION_TYPE_EXPENSE, "Pokupka", 0, 1);

        assertMoney("300.00", amountOf(parser));
    }

    @Test
    public void amount_rubWithDateInText_dateDoesNotAffectAmount() throws Exception {
        // Пример 2: в тексте есть дата 25.07.2014, сумма и баланс в RUB
        SmsParser parser = createParser(
                "Pokupka. Karta *6741. Summa 924.43 RUB. AUCHAN KHIMKI, KHIMKI. 25.07.2014 21:25. Dostupno 109784.70 RUB.",
                "RUB", "*6741", Transaction.TRANSACTION_TYPE_EXPENSE, "Pokupka", 0, 1);

        assertMoney("924.43", amountOf(parser));
    }

    @Test
    public void amount_spaceAsThousandsSeparator() throws Exception {
        // Пример 21: "5 000.00 RUR"
        SmsParser parser = createParser(
                "2014-12-16 11:44:08 Pokupka (IBANK.KHMB.RU) RRN 435082179616 Summa 5 000.00 RUR Dostupno 3 834.92 RUR Podderzhka +73467390700",
                "RUR", "(IBANK.KHMB.RU)", Transaction.TRANSACTION_TYPE_EXPENSE, "Pokupka", 0, 1);

        assertMoney("5000.00", amountOf(parser));
    }

    @Test
    public void amount_commaDecimalWithoutSpace() throws Exception {
        // Пример 31: "123,00RUR" в кириллическом тексте
        SmsParser parser = createParser(
                "Карта ***4114 123,00RUR Оплата картой APTEKA 24 CHASA; Успешно выполнено. Доступно:60209.62RUR",
                "RUR", "***4114", Transaction.TRANSACTION_TYPE_EXPENSE, "Оплата картой", 0, 1);

        assertMoney("123.00", amountOf(parser));
    }

    @Test
    public void amount_cyrillicCurrencyRub_withSpaceThousandsInBalance() throws Exception {
        // Пример 35: валюта "руб", баланс "3 086.54 руб"
        SmsParser parser = createParser(
                "14:22 12/07/16 Покупка Сумма:10.10 руб по карте *4114 W.QIWI.RU, WWW.QIWI.COM, Баланс до jht: 3 086.54 руб Доступно: 3 076.44 ру",
                "руб", "*4114", Transaction.TRANSACTION_TYPE_EXPENSE, "Покупка", 0, 1);

        assertMoney("10.10", amountOf(parser));
        assertMoney("3086.54", parser.extractBalance());
    }

    @Test
    public void amount_dollarSignCurrency() throws Exception {
        // Пример 32: валюта "$" (маркер и текст как в SmsParserTest)
        SmsParser parser = createParser(
                "Pokupka 300.00 $ schet **1234 Torgovaya tochka: BURGER KING Data 05/01/14 Balans: 1100.55$",
                "$", "**1234", Transaction.TRANSACTION_TYPE_EXPENSE, "Pokupka", 0, 1);

        assertMoney("300.00", amountOf(parser));
        assertMoney("1100.55", parser.extractBalance());
    }

    @Test
    public void amount_selectedByAmountPosition() throws Exception {
        // Пример 44: в тексте три суммы в UAH, amountPos=0 выбирает первую
        SmsParser parser = createParser(
                "*1352 2018-05-06 12:03 SPYSANNIA 6313.50SEK (8891.29UAH ZA KURSOM 2.99) KOMISIIA 0.00UAH BALANCE -7205.89UAH VLASNI KOSHTY 0.00UAH RADISSON",
                "UAH", "*1352", Transaction.TRANSACTION_TYPE_EXPENSE, "SPYSANNIA", 0, 2);

        assertMoney("8891.29", amountOf(parser));
    }

    @Test
    public void amount_positionOutOfRange_isZero() throws Exception {
        SmsParser parser = createParser(
                "Balans: 1100.55 RUR, schet *1234",
                "RUR", "*1234", Transaction.TRANSACTION_TYPE_EXPENSE, "Balans", 1, 0);

        assertZeroAmount(amountOf(parser));
    }

    // ---------- Знак (тип транзакции) и знак суммы ----------

    @Test
    public void sign_negativeAmountInText_isReturnedAbsoluteAndTypeFromMarker() throws Exception {
        // Пример 26: "-940.59 RUR" с маркером "Spisanie" дает расход на 940.59
        SmsParser parser = createParser(
                "Spisanie s karty MC *3053: -940.59 RUR 21/02/2015 02:34:03 MSK. Dostupno: 5840.44 RUR. Tel: 88007752424",
                "RUR", "MC *3053", Transaction.TRANSACTION_TYPE_EXPENSE, "Spisanie", 0, 1);

        assertEquals(Transaction.TRANSACTION_TYPE_EXPENSE, parser.extractTrType());
        assertMoney("940.59", amountOf(parser));
    }

    @Test
    public void sign_incomeByMarker() throws Exception {
        // Пример 11: маркер "Popolnenie" дает доход
        SmsParser parser = createParser(
                "Popolnenie, karta:4970 summa:2000.00 RUR balans:21550.04 RUR (MSK 18:54:37 20.01.2015)",
                "RUR", "karta:4970", Transaction.TRANSACTION_TYPE_INCOME, "Popolnenie", 0, 1);

        assertEquals(Transaction.TRANSACTION_TYPE_INCOME, parser.extractTrType());
        assertMoney("2000.00", amountOf(parser));
        assertMoney("21550.04", parser.extractBalance());
    }

    @Test
    public void sign_incomeCyrillicMarker_singleLetterCurrency() throws Exception {
        // Пример 6: зачисление зарплаты, валюта "р"
        SmsParser parser = createParser(
                "VISAХХХ: 06.10.14 14:52 операция зачисления зарплаты на сумму 5155.56р. Баланс: 5328.80р.",
                "р", "VISAХХХ", Transaction.TRANSACTION_TYPE_INCOME, "операция зачисления", 0, 1);

        assertEquals(Transaction.TRANSACTION_TYPE_INCOME, parser.extractTrType());
        assertMoney("5155.56", amountOf(parser));
        assertMoney("5328.80", parser.extractBalance());
    }

    @Test
    public void sign_noTypeMarker_defaultsToExpense() throws Exception {
        // Маркер дохода не встречается в тексте: код возвращает расход по умолчанию
        SmsParser parser = createParser(
                "Pokupka, Summa:500,00 RUR, Karta:4..5604, Data:02.06.15, Inf:STRELKA, Kod:192579. Dostupno:22945.32 RUR",
                "RUR", "Karta:4..5604", Transaction.TRANSACTION_TYPE_INCOME, "Zachislenie", 0, 1);

        assertEquals(Transaction.TRANSACTION_TYPE_EXPENSE, parser.extractTrType());
        assertMoney("500.00", amountOf(parser));
    }

    @Test
    public void sign_negativeBalanceKeepsSign() throws Exception {
        // Пример 44: баланс "-7205.89UAH" сохраняет минус, в отличие от суммы
        SmsParser parser = createParser(
                "*1352 2018-05-06 12:03 SPYSANNIA 6313.50SEK (8891.29UAH ZA KURSOM 2.99) KOMISIIA 0.00UAH BALANCE -7205.89UAH VLASNI KOSHTY 0.00UAH RADISSON",
                "UAH", "*1352", Transaction.TRANSACTION_TYPE_EXPENSE, "SPYSANNIA", 0, 2);

        assertMoney("-7205.89", parser.extractBalance());
    }

    // ---------- Баланс ----------

    @Test
    public void balance_secondCurrencyAmountByPosition() throws Exception {
        // Пример 2: баланс - вторая сумма в RUB
        SmsParser parser = createParser(
                "Pokupka. Karta *6741. Summa 924.43 RUB. AUCHAN KHIMKI, KHIMKI. 25.07.2014 21:25. Dostupno 109784.70 RUB.",
                "RUB", "*6741", Transaction.TRANSACTION_TYPE_EXPENSE, "Pokupka", 0, 1);

        assertMoney("109784.70", parser.extractBalance());
    }

    @Test
    public void balance_absentInText_isNull() throws Exception {
        // Пример 19: в тексте нет баланса
        SmsParser parser = createParser(
                "VC: *0700 03.03.2015 07:17:14. Oplata tovarov/uslug v AZK 9 na summu 604.00 RUR vypolnena uspeshno.",
                "RUR", "*0700", Transaction.TRANSACTION_TYPE_EXPENSE, "Oplata", 0, 1);

        assertMoney("604.00", amountOf(parser));
        assertNull(parser.extractBalance());
    }

    // ---------- Дата ----------

    @Test
    public void date_isSmsReceiveTime_notParsedFromText() throws Exception {
        // В тексте есть дата 25.07.2014 21:25, но парсер берет дату транзакции из Sms
        SmsParser parser = createParser(
                "Pokupka. Karta *6741. Summa 924.43 RUB. AUCHAN KHIMKI, KHIMKI. 25.07.2014 21:25. Dostupno 109784.70 RUB.",
                "RUB", "*6741", Transaction.TRANSACTION_TYPE_EXPENSE, "Pokupka", 0, 1);

        assertEquals(SMS_DATE, parser.extractTransaction().getDateTime());
    }

    // ---------- Негативные случаи ----------

    @Test
    public void emptyText_doesNotThrow_andYieldsZeroAmountAndNullBalance() throws Exception {
        SmsParser parser = createParser(
                "", "RUR", "*1234", Transaction.TRANSACTION_TYPE_EXPENSE, "Pokupka", 0, 1);

        assertZeroAmount(amountOf(parser));
        assertNull(parser.extractBalance());
        assertEquals(Transaction.TRANSACTION_TYPE_EXPENSE, parser.extractTrType());
    }

    @Test
    public void textWithoutAmount_doesNotThrow_andYieldsZeroAmountAndNullBalance() throws Exception {
        // Пример 29: служебное SMS без суммы в валюте
        SmsParser parser = createParser(
                "Вход в Сбербанк Онлайн 05:35 16.02.16. Не вводите пароль для отмены или подтверждения операций, которые не совершали.",
                "RUR", "Сбербанк Онлайн", Transaction.TRANSACTION_TYPE_INCOME, "Вход", 0, 1);

        assertZeroAmount(amountOf(parser));
        assertNull(parser.extractBalance());
    }

    @Test
    public void amountWithoutCurrencyMarker_isNotRecognised() throws Exception {
        // Сумма без маркера валюты не считается суммой: маркер валюты обязателен
        SmsParser parser = createParser(
                "Pokupka 300.00 schet *1234 Torgovaya tochka: BURGER KING",
                "RUR", "*1234", Transaction.TRANSACTION_TYPE_EXPENSE, "Pokupka", 0, 1);

        assertZeroAmount(amountOf(parser));
    }
}
