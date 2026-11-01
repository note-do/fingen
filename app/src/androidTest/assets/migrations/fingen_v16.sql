-- Schema of database version 16, RECONSTRUCTED from the code; it is the LEAST accurate fixture, no real v16 database was available.
-- Method: column lists of tableInfo17 of UpdateHelper (the schema update17 starts from), 15 tables.
-- Assumptions, each one can differ from a real v16 database:
--  1. Column types and NOT NULL are taken from tableInfo18 (the first schema in the code that has them), with these exceptions:
--     log_Transactions.DateTime is TEXT ("yyyy-MM-dd HH:mm:ss", update18 parses it into milliseconds),
--     log_Incoming_SMS.Sender is TEXT (phone number, update17 replaces it with the id of ref_Senders).
--  2. No synchronization columns (FBID, TS, Deleted, Dirty, LastEdited): update18 adds them. Fixture rows therefore have no Deleted.
--  3. No UNIQUE, REFERENCES and ON CONFLICT clauses (update18 "changes constraints"); the real v16 may have had some.
--  4. No indexes, no ref_Senders (created by update17), no ref_Categories.ParentID (column is ParentCategory),
--     no log_Templates.Comment, no ref_Currencies.OrderNumber.
--  5. Column order is the order of tableInfo17; the real order may differ, tests do not depend on it.
--  6. Foreign keys are not enforced; identifier case as in tableInfo17 (isActive, COLOR).
CREATE TABLE ref_Currencies (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Code TEXT NOT NULL, Symbol TEXT NOT NULL, Name TEXT NOT NULL, DecimalCount INTEGER NOT NULL);
CREATE TABLE ref_Accounts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Type INTEGER NOT NULL, Name TEXT NOT NULL, Currency INTEGER, Emitent TEXT, Last4Digits INTEGER, Comment TEXT, StartBalance REAL NOT NULL, IsClosed INTEGER NOT NULL, CustomOrder INTEGER, CreditLimit REAL);
CREATE TABLE ref_Categories (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Name TEXT NOT NULL, COLOR TEXT, ParentCategory INTEGER, OrderNumber INTEGER NOT NULL, Sign INTEGER NOT NULL);
CREATE TABLE ref_Payees (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Name TEXT NOT NULL, DefCategory INTEGER);
CREATE TABLE ref_Projects (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Name TEXT NOT NULL, isActive INTEGER NOT NULL);
CREATE TABLE ref_Locations (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Name TEXT NOT NULL, Lon REAL NOT NULL, Lat REAL NOT NULL, Radius INTEGER, Address TEXT);
CREATE TABLE ref_Departments (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Name TEXT NOT NULL, isActive INTEGER NOT NULL);
CREATE TABLE ref_SimpleDebts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Name TEXT NOT NULL, isActive INTEGER NOT NULL);
CREATE TABLE log_Transactions (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, DateTime TEXT NOT NULL, SrcAccount INTEGER NOT NULL, Payee INTEGER, Category INTEGER, Amount REAL NOT NULL, Project INTEGER, Location INTEGER, Comment TEXT, File TEXT, DestAccount INTEGER NOT NULL, ExchangeRate REAL NOT NULL, AutoCreated INTEGER NOT NULL, Lat REAL, Lon REAL, Accuracy INTEGER, Department INTEGER, SimpleDebt INTEGER);
CREATE TABLE log_Templates (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, SrcAccount INTEGER NOT NULL, Payee INTEGER, Category INTEGER, Amount REAL NOT NULL, Project INTEGER, Location INTEGER, Name TEXT NOT NULL, DestAccount INTEGER NOT NULL, ExchangeRate REAL NOT NULL, Type INTEGER NOT NULL, Department INTEGER);
CREATE TABLE log_Incoming_SMS (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, DateTime TEXT NOT NULL, Sender TEXT NOT NULL, Body TEXT NOT NULL);
CREATE TABLE ref_Sms_Parser_Patterns (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Type INTEGER NOT NULL, Object TEXT NOT NULL, Pattern TEXT NOT NULL);
CREATE TABLE ref_Debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Account INTEGER NOT NULL, Payee INTEGER NOT NULL, Category INTEGER NOT NULL, Closed INTEGER NOT NULL, SrcAccount INTEGER, Comment TEXT);
CREATE TABLE ref_budget (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Category INTEGER NOT NULL, Amount REAL NOT NULL, Currency INTEGER NOT NULL);
CREATE TABLE ref_budget_debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Debt INTEGER NOT NULL, Amount REAL NOT NULL);
PRAGMA user_version=16;
