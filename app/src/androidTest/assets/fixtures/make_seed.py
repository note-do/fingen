#!/usr/bin/env python3
"""
Builds the test fixture fingen_v35_100.db: a clean Fingen DB (user_version 35, schema created by
DBHelper.onCreate on first install) filled with ~100 rows per significant table.

Usage:
    python3 make_seed.py <clean.db> <out.db> [anchor-date YYYY-MM-DD]

<clean.db> - fingen.db copied from a freshly installed app (adb exec-out run-as com.yoshione.fingen
cat databases/fingen.db). The script copies it to <out.db>, so the input is not modified.
The random seed is fixed, the "now" of the data is the anchor date (default 2026-11-07), so the
output is reproducible. All names start with "Тест" / "Test".

Conventions (as in the app): "no value" foreign key = -1 (not NULL), root ParentID = -1, expense
Amount < 0, transfer: DestAccount >= 0 and Amount < 0 in the source currency, the destination gets
Amount * ExchangeRate * -1. Incoming SMS DateTime format is "yyyyMMdd  HH:mm". log_Running_Balance is
rebuilt with the app's own script (assets/sql/update_running_balance.sql in main).
"""
import datetime
import os
import random
import shutil
import sqlite3
import sys

TRANSLIT = dict(zip(
    "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЬЫЪЭЮЯабвгдеёжзийклмнопрстуфхцчшщьыъэюя",
    ["A", "B", "V", "G", "D", "E", "E", "ZH", "Z", "I", "I", "K", "L", "M", "N", "O", "P", "R", "S", "T",
     "U", "F", "H", "C", "CH", "SH", "SH", "'", "Y", "'", "E", "U", "YA",
     "a", "b", "v", "g", "d", "e", "e", "zh", "z", "i", "i", "k", "l", "m", "n", "o", "p", "r", "s", "t",
     "u", "f", "h", "c", "ch", "sh", "sh", "'", "y", "'", "e", "u", "ya"]))


def translit(text):
    """Port of utils/Translit.toTranslit applied to text.lower()."""
    return "".join(TRANSLIT.get(ch, ch) for ch in text.lower())


SYNC = "FBID, TS, Deleted, Dirty, LastEdited"


def sync(deleted=0):
    return ("", -1, deleted, 0, "")


class Seed:
    def __init__(self, con, anchor):
        self.con = con
        self.anchor = anchor
        self.rnd = random.Random(35)

    def insert(self, table, columns, rows):
        cols = SYNC + ", " + ", ".join(columns)
        marks = ", ".join(["?"] * (5 + len(columns)))
        self.con.executemany("INSERT INTO %s (%s) VALUES (%s)" % (table, cols, marks), rows)

    def ids(self, sql):
        return [r[0] for r in self.con.execute(sql)]

    def ms(self, day_offset, hour=None):
        hour = self.rnd.randint(8, 21) if hour is None else hour
        minute = self.rnd.randint(0, 59)
        dt = datetime.datetime.combine(self.anchor, datetime.time(hour, minute)) - datetime.timedelta(days=day_offset)
        return int(dt.replace(tzinfo=datetime.timezone.utc).timestamp() * 1000)


def month_back(anchor, n):
    """(year, 0-based month) n months before the anchor month."""
    idx = anchor.year * 12 + anchor.month - 1 - n
    return idx // 12, idx % 12


def build(clean, out, anchor):
    shutil.copyfile(clean, out)
    for ext in ("-journal", "-wal", "-shm"):
        if os.path.exists(out + ext):
            os.remove(out + ext)
    con = sqlite3.connect(out)
    s = Seed(con, anchor)
    r = s.rnd
    currencies = s.ids("SELECT _id FROM ref_Currencies ORDER BY _id")  # 1 RUB .. 6 KZT

    # ---- ref_Accounts: 24, types 0..6 cycling ----
    type_names = ["Наличные", "Банк", "Дебетовая", "Кредитная", "Актив", "Долг", "Другое"]
    rows = []
    for i in range(24):
        t = i % 7
        name = "Тест счёт %02d %s" % (i + 1, type_names[t])
        cur = currencies[0] if i % 3 != 2 else currencies[(i // 3) % len(currencies)]
        deleted = 1 if i in (21, 23) else 0
        closed = 1 if i in (6, 13, 20) else 0
        start = float(r.choice([0, 1000, 5000, 15000]))
        limit = float(r.choice([50000, 100000])) if t == 3 else 0.0
        rows.append(sync(deleted) + (t, name, cur, "Тест банк %d" % (i % 4 + 1), 1000 + i * 37 % 9000,
                                     "Тест комментарий %d" % (i + 1), start, closed, i + 1, limit, translit(name)))
    s.insert("ref_Accounts", ["Type", "Name", "Currency", "Emitent", "Last4Digits", "Comment", "StartBalance",
                              "IsClosed", "CustomOrder", "CreditLimit", "SearchString"], rows)
    accounts = con.execute("SELECT _id, Currency, Type, Deleted, IsClosed FROM ref_Accounts ORDER BY _id").fetchall()
    live = [a for a in accounts if a[3] == 0 and a[4] == 0]  # open and not deleted

    # ---- ref_Categories: 20 (8 root expense, 4 root income, 8 children) ----
    roots = ["Продукты", "Транспорт", "Жильё", "Здоровье", "Развлечения", "Одежда", "Связь", "Образование",
             "Зарплата", "Подарки", "Проценты", "Прочие доходы"]
    colors = ["#E53935", "#8E24AA", "#3949AB", "#039BE5", "#00897B", "#7CB342", "#FDD835", "#FB8C00",
              "#43A047", "#00ACC1", "#6D4C41", "#546E7A"]
    cat_rows = []
    for i, n in enumerate(roots):
        cat_rows.append(sync() + ("Тест " + n, colors[i], -1, i + 1, 1, "Тест " + n, translit("Тест " + n)))
    s.insert("ref_Categories", ["Name", "COLOR", "ParentID", "OrderNumber", "Sign", "FullName", "SearchString"],
             cat_rows)
    root_ids = s.ids("SELECT _id FROM ref_Categories ORDER BY _id")
    children = [(0, "Магазин"), (0, "Рынок"), (1, "Такси"), (1, "Бензин"), (2, "Аренда"), (2, "Коммунальные"),
                (4, "Кино"), (8, "Премия")]
    cat_rows = []
    for k, (p, n) in enumerate(children):
        full = "Тест %s\\Тест %s" % (roots[p], n)
        cat_rows.append(sync() + ("Тест " + n, colors[(k + 3) % 12], root_ids[p], k + 1, 1, full, translit(full)))
    s.insert("ref_Categories", ["Name", "COLOR", "ParentID", "OrderNumber", "Sign", "FullName", "SearchString"],
             cat_rows)
    cats = s.ids("SELECT _id FROM ref_Categories ORDER BY _id")
    inc_cats = root_ids[8:] + [cats[-1]]
    # expense categories: roots 0..7 and children except "Премия" (income child)
    exp_cats = root_ids[:8] + cats[12:19]

    # ---- ref_Payees: 100 (5 of them are children) ----
    cat_choices = exp_cats + [-1]
    rows = []
    for i in range(100):
        name = "Тест получатель %03d" % (i + 1)
        rows.append(sync() + (name, r.choice(cat_choices), -1, i + 1, name, translit(name)))
    s.insert("ref_Payees", ["Name", "DefCategory", "ParentID", "OrderNumber", "FullName", "SearchString"], rows)
    payees = s.ids("SELECT _id FROM ref_Payees ORDER BY _id")
    for i in range(5):  # nest the last 5 payees under the first 5
        child, parent = payees[-1 - i], payees[i]
        pname = con.execute("SELECT Name FROM ref_Payees WHERE _id = ?", (parent,)).fetchone()[0]
        cname = con.execute("SELECT Name FROM ref_Payees WHERE _id = ?", (child,)).fetchone()[0]
        full = pname + "\\" + cname
        con.execute("UPDATE ref_Payees SET ParentID = ?, FullName = ?, SearchString = ? WHERE _id = ?",
                    (parent, full, translit(full), child))

    # ---- ref_Projects / ref_Departments / ref_Locations / ref_SimpleDebts: 10 each ----
    s.insert("ref_Projects", ["Name", "Color", "IsActive", "ParentID", "OrderNumber", "FullName", "SearchString"],
             [sync() + ("Тест проект %02d" % i, colors[i % 12], 0 if i == 9 else 1, -1, i, "Тест проект %02d" % i,
                        translit("Тест проект %02d" % i)) for i in range(1, 11)])
    s.insert("ref_Departments", ["Name", "IsActive", "ParentID", "OrderNumber", "FullName", "SearchString"],
             [sync() + ("Тест подразделение %02d" % i, 0 if i == 10 else 1, -1, i, "Тест подразделение %02d" % i,
                        translit("Тест подразделение %02d" % i)) for i in range(1, 11)])
    s.insert("ref_Locations", ["Name", "Lon", "Lat", "Radius", "Address", "ParentID", "OrderNumber", "FullName",
                               "SearchString"],
             [sync() + ("Тест место %02d" % i, 37.6 + i * 0.01, 55.7 + i * 0.01, 100, "Тест адрес %d" % i, -1, i,
                        "Тест место %02d" % i, translit("Тест место %02d" % i)) for i in range(1, 11)])
    s.insert("ref_SimpleDebts", ["Name", "IsActive", "StartAmount", "Currency", "SearchString"],
             [sync() + ("Тест долг %02d" % i, 0 if i == 10 else 1, float(r.choice([0, 500, 2000, -1500])),
                        currencies[0] if i % 2 else currencies[1], translit("Тест долг %02d" % i))
              for i in range(1, 11)])
    projects = s.ids("SELECT _id FROM ref_Projects")
    deps = s.ids("SELECT _id FROM ref_Departments")
    locs = s.ids("SELECT _id FROM ref_Locations")
    sdebts = s.ids("SELECT _id FROM ref_SimpleDebts")

    # ---- log_Transactions: 100 over 12 months, 8 deleted ----
    cur_of = {a[0]: a[1] for a in accounts}
    cabbage_rate = {(1, 2): 0.011, (2, 1): 90.0}

    def rate(src, dst):
        a, b = cur_of[src], cur_of[dst]
        return 1.0 if a == b else cabbage_rate.get((a, b), 1.5)

    rows = []
    comment_words = ["обед", "бензин", "кофе", "аптека", "такси", "подписка", "кино", "ремонт", "подарок", "аванс"]
    for i in range(100):
        day = int(i * 360 / 100) + r.randint(0, 3)  # even spread, most recent first
        if i < 6:
            day = i  # a few within the last days (for "today" and "this week" filters)
        kind = r.choices(["expense", "income", "transfer"], weights=[70, 18, 12])[0]
        src = r.choice(live)[0]
        deleted = 1 if i % 12 == 5 else 0
        comment = "Тест %s %d" % (r.choice(comment_words), i + 1)
        pr = r.choice(projects + [-1, -1, -1])
        dp = r.choice(deps + [-1, -1])
        lc = r.choice(locs + [-1, -1, -1, -1])
        sd = r.choice(sdebts + [-1] * 8)
        if kind == "transfer":
            dst = r.choice([a for a in live if a[0] != src])[0]
            amount = -float(r.choice([500, 1000, 2500, 10000]))
            row = (src, -1, -1, amount, pr, -1, dp, lc, comment, dst, rate(src, dst))
        elif kind == "income":
            amount = float(r.choice([1500, 20000, 45000, 70000]) + r.randint(0, 99) / 100.0)
            row = (src, r.choice(payees), r.choice(inc_cats), amount, pr, sd, dp, lc, comment, -1, 1.0)
        else:
            amount = -float(r.randint(50, 9000)) - r.randint(0, 99) / 100.0
            row = (src, r.choice(payees), r.choice(exp_cats), amount, pr, sd, dp, lc, comment, -1, 1.0)
        rows.append(sync(deleted) + (s.ms(day), row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7],
                                     row[8], "", row[9], row[10], 0, -1.0, -1.0, -1, 0, 0, 0, 0,
                                     translit(row[8])))
    s.insert("log_Transactions",
             ["DateTime", "SrcAccount", "Payee", "Category", "Amount", "Project", "SimpleDebt", "Department",
              "Location", "Comment", "File", "DestAccount", "ExchangeRate", "AutoCreated", "Lon", "Lat",
              "Accuracy", "FN", "FD", "FP", "Split", "SearchString"], rows)

    # ---- ref_Products: 10 (the default product _id 0 already exists), log_Products ----
    # The app joins log_Transactions with log_Products (INNER JOIN), so every transaction needs the
    # default entry (ProductID 0, Price = Amount - sum of the other entries, Quantity 1).
    s.insert("ref_Products", ["Name", "SearchString"],
             [sync() + ("Тест товар %02d" % i, translit("Тест товар %02d" % i)) for i in range(1, 11)])
    products = s.ids("SELECT _id FROM ref_Products WHERE _id > 0 ORDER BY _id")
    prod_rows = []
    split_left = 8
    for tid, amount, dest, deleted in con.execute(
            "SELECT _id, Amount, DestAccount, Deleted FROM log_Transactions ORDER BY _id").fetchall():
        default_price = amount
        if split_left and dest < 0 and amount < 0 and not deleted:
            split_left -= 1
            for k in range(2):
                price = -float(r.randint(1, 4) * 10)
                qty = float(r.randint(1, 3))
                default_price -= price * qty
                prod_rows.append(sync(deleted) + (tid, r.choice(products), -1, -1, price, qty))
            con.execute("UPDATE log_Transactions SET Split = 1 WHERE _id = ?", (tid,))
        prod_rows.append(sync(deleted) + (tid, 0, -1, -1, round(default_price, 2), 1.0))
    s.insert("log_Products", ["TransactionID", "ProductID", "CategoryID", "ProjectID", "Price", "Quantity"],
             prod_rows)

    # ---- ref_budget: 100 (distinct month x category x currency), ref_budget_debts later ----
    combos = [(m, c, cu) for m in range(12) for c in exp_cats + inc_cats[:2] for cu in (currencies[0], currencies[1])]
    r.shuffle(combos)
    rows = []
    for m, c, cu in combos[:100]:
        y, mo = month_back(s.anchor, m)
        rows.append(sync() + (y, mo, c, float(r.choice([3000, 5000, 8000, 12000, 20000])), cu))
    # the app stores Month as 0-based (Calendar.MONTH), see CreditsDAO: c.set(year, month, 1)
    s.insert("ref_budget", ["Year", "Month", "Category", "Amount", "Currency"], rows)

    # ---- log_Templates: 30 ----
    rows = []
    for i in range(30):
        t = r.choice([-1, -1, -1, 1, 0])
        src = r.choice(live)[0]
        name = "Тест шаблон %02d" % (i + 1)
        if t == 0:
            dst = r.choice([a for a in live if a[0] != src])[0]
            rows.append(sync() + (src, -1, -1, -float(r.choice([500, 1000, 3000])), -1, -1, -1, name, dst,
                                  rate(src, dst), "Тест комментарий", name, t))
        else:
            sign = 1 if t > 0 else -1
            rows.append(sync() + (src, r.choice(payees), r.choice(inc_cats if t > 0 else exp_cats),
                                  sign * float(r.choice([100, 300, 1500, 5000])), r.choice(projects + [-1]),
                                  r.choice(deps + [-1]), r.choice(locs + [-1]), name, -1, 1.0,
                                  "Тест комментарий", name, t))
    # templates: SearchString = Name
    rows = [x[:-2] + (translit(x[-2]), x[-1]) for x in rows]
    s.insert("log_Templates", ["SrcAccount", "Payee", "Category", "Amount", "Project", "Department", "Location",
                               "Name", "DestAccount", "ExchangeRate", "Comment", "SearchString", "Type"], rows)

    # ---- ref_Debts: on accounts of type 3, 4, 5, 6 (credit, active, passive, other), not deleted ----
    debt_accs = [a[0] for a in accounts if a[2] in (3, 4, 5, 6) and a[3] == 0]
    rows = []
    for k, acc in enumerate(debt_accs):
        rows.append(sync() + (acc, payees[k], exp_cats[k % len(exp_cats)], 1 if k % 5 == 4 else 0, -1,
                              "Тест долг-комментарий %d" % (k + 1)))
    s.insert("ref_Debts", ["Account", "Payee", "Category", "Closed", "SrcAccount", "Comment"], rows)
    debts = s.ids("SELECT _id FROM ref_Debts ORDER BY _id")
    rows = []
    for k, d in enumerate(debts):
        for m in range(3):
            y, mo = month_back(s.anchor, m)
            rows.append(sync() + (y, mo, d, float(r.choice([1000, 5000, 10000]))))
    s.insert("ref_budget_debts", ["Year", "Month", "Debt", "Amount"], rows)

    # ---- ref_Senders: 10, log_Incoming_SMS: 100 ----
    rows = []
    for i in range(1, 11):
        rows.append(sync() + ("Тест отправитель %02d" % i, "TESTBANK%02d" % i, 3, 5, 0, "dd.MM.yy HH:mm", 1, 0))
    s.insert("ref_Senders", ["Name", "PhoneNo", "AmountPos", "BalancePos", "LeadingCurrencySymbol", "DateFormat",
                             "IsActive", "AddCreditLimitToBalance"], rows)
    senders = s.ids("SELECT _id FROM ref_Senders ORDER BY _id")
    rows = []
    for i in range(100):
        dt = datetime.datetime.combine(anchor, datetime.time(9, 0)) - datetime.timedelta(
            days=int(i * 3.6), minutes=r.randint(0, 600))
        body = r.choice([
            "Покупка %.2f RUB, карта *%04d, Тест магазин %d. Баланс: %.2f RUB",
            "Списание %.2f RUB, карта *%04d, Тест оплата %d. Остаток: %.2f RUB",
            "Зачисление %.2f RUB на карту *%04d, Тест перевод %d. Баланс: %.2f RUB",
            "Test purchase %.2f USD, card *%04d, Test shop %d. Balance: %.2f USD"
        ]) % (r.randint(50, 9000) + r.randint(0, 99) / 100.0, 1000 + i * 37 % 9000, i + 1,
              r.randint(1000, 90000) + r.randint(0, 99) / 100.0)
        rows.append(sync() + (dt.strftime("%Y%m%d  %H:%M"), senders[i % len(senders)], body))
    s.insert("log_Incoming_SMS", ["DateTime", "Sender", "Body"], rows)

    # ---- ref_Sms_Parser_Patterns: 100, types 0..4 and 6 (5 = legacy phone markers, migrated away) ----
    # Object: 0 account id, 1 currency id, 2 transaction type (-1/0/1), 3 payee id, 4 "Ignore", 6 dest account id
    all_acc = [a[0] for a in accounts if a[3] == 0]
    markers = []
    for i in range(30):
        markers.append((0, str(all_acc[i % len(all_acc)]), "*%04d тест%d" % (1000 + i * 37 % 9000, i)))
    for i in range(12):
        markers.append((1, str(currencies[i % len(currencies)]), ("RUB", "USD", "EUR", "UAH", "BYN", "KZT")[i % 6]
                        + " тест%d" % i))
    for i in range(12):
        markers.append((2, str((-1, 0, 1)[i % 3]), ("покупка", "перевод", "зачисление")[i % 3] + " тест%d" % i))
    for i in range(24):
        markers.append((3, str(payees[i]), "Тест магазин %d" % (i + 1)))
    for i in range(10):
        markers.append((4, "Ignore", "Тест реклама %d" % (i + 1)))
    for i in range(12):
        markers.append((6, str(all_acc[(i * 2) % len(all_acc)]), "на карту *%04d тест%d" % (2000 + i * 53 % 7000, i)))
    s.insert("ref_Sms_Parser_Patterns", ["Type", "Object", "Pattern"], [sync() + m for m in markers])

    con.commit()

    # ---- log_Running_Balance: the app's own script ----
    here = os.path.dirname(os.path.abspath(__file__))
    sql_path = os.path.normpath(os.path.join(
        here, "..", "..", "..", "main", "assets", "sql", "update_running_balance.sql"))
    with open(sql_path, encoding="utf-8") as f:
        con.executescript(f.read())
    con.execute("PRAGMA user_version = 35")
    con.commit()
    con.execute("VACUUM")
    con.close()


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    when = datetime.date.fromisoformat(sys.argv[3]) if len(sys.argv) > 3 else datetime.date(2026, 11, 7)
    build(sys.argv[1], sys.argv[2], when)
