CREATE TABLE ref_Currencies (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Code TEXT NOT NULL, Symbol TEXT NOT NULL, Name TEXT NOT NULL, DecimalCount INTEGER NOT NULL, OrderNumber INTEGER, UNIQUE (Code, Deleted) ON CONFLICT ABORT);
CREATE TABLE ref_Accounts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Type INTEGER NOT NULL, Name TEXT NOT NULL, Currency INTEGER REFERENCES [ref_Currencies]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, Emitent TEXT, Last4Digits INTEGER, Comment TEXT, StartBalance REAL NOT NULL, IsClosed INTEGER NOT NULL, CustomOrder INTEGER, CreditLimit REAL, SearchString TEXT, UNIQUE (Name, Deleted) ON CONFLICT ABORT);
CREATE TABLE ref_Projects (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Name TEXT NOT NULL, Color TEXT DEFAULT '#ffffff', IsActive INTEGER NOT NULL, ParentID INTEGER REFERENCES [ref_Projects]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, FullName TEXT, SearchString TEXT, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);
CREATE TABLE ref_Departments (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Name TEXT NOT NULL, IsActive INTEGER NOT NULL, ParentID INTEGER REFERENCES [ref_Departments]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, FullName TEXT, SearchString TEXT, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);
CREATE TABLE ref_Locations (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Name TEXT NOT NULL, Lon REAL NOT NULL, Lat REAL NOT NULL, Radius INTEGER, Address TEXT, ParentID INTEGER REFERENCES [ref_Locations]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, FullName TEXT, SearchString TEXT, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);
CREATE TABLE ref_Categories (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Name TEXT NOT NULL, COLOR TEXT, ParentID INTEGER REFERENCES [ref_Categories]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER NOT NULL, Sign INTEGER NOT NULL, FullName TEXT, SearchString TEXT, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);
CREATE TABLE ref_Payees (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Name TEXT NOT NULL, DefCategory INTEGER REFERENCES [ref_Categories]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, ParentID INTEGER REFERENCES [ref_Payees]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, FullName TEXT, SearchString TEXT, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);
CREATE TABLE log_Transactions (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
DateTime INTEGER NOT NULL, SrcAccount INTEGER NOT NULL REFERENCES [ref_Accounts]([_id]) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES [ref_Payees]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES [ref_Categories]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES [ref_Projects]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, SimpleDebt INTEGER REFERENCES [ref_SimpleDebts]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES [ref_Departments]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES [ref_Locations]([_id]) ON DELETE SET NULL ON UPDATE CASCADE,Comment TEXT, File TEXT, DestAccount INTEGER NOT NULL REFERENCES [ref_Accounts]([_id]) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, AutoCreated INTEGER NOT NULL, Lon REAL, Lat REAL, Accuracy INTEGER,FN INTEGER DEFAULT 0,FD INTEGER DEFAULT 0,FP INTEGER DEFAULT 0,Split INTEGER DEFAULT 0,SearchString TEXT);
CREATE INDEX [idx] ON [log_Transactions] ([Deleted], [DateTime], [SrcAccount], [DestAccount], [Payee], [Category], [Project], [Department], [Location], [SimpleDebt]);
CREATE TABLE log_Incoming_SMS (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
DateTime TEXT NOT NULL, Sender INTEGER NOT NULL, Body TEXT NOT NULL);
CREATE TABLE ref_Sms_Parser_Patterns (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Type INTEGER NOT NULL, Object TEXT NOT NULL, Pattern TEXT NOT NULL, UNIQUE (Type, Pattern, Deleted) ON CONFLICT REPLACE);
CREATE TABLE [ref_Debts] (
_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Account INTEGER NOT NULL ON CONFLICT ABORT REFERENCES [ref_Accounts]([_id]) ON DELETE CASCADE ON UPDATE CASCADE,
Payee INTEGER NOT NULL REFERENCES [ref_Payees]([_id]) ON DELETE CASCADE ON UPDATE CASCADE,
Category INTEGER NOT NULL REFERENCES [ref_Categories]([_id]) ON DELETE SET NULL ON UPDATE CASCADE,
Closed INTEGER NOT NULL,
SrcAccount INTEGER,
Comment TEXT, UNIQUE (Account, Deleted) ON CONFLICT ABORT);
CREATE TABLE [ref_budget] (
_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Year INTEGER NOT NULL,
Month INTEGER NOT NULL,
Category INTEGER NOT NULL REFERENCES [ref_Categories]([_id]) ON DELETE CASCADE ON UPDATE CASCADE,
Amount REAL NOT NULL,
Currency INTEGER NOT NULL REFERENCES [ref_Currencies]([_id]) ON DELETE CASCADE ON UPDATE CASCADE,
UNIQUE (Deleted, Year, Month, Category, Currency) ON CONFLICT REPLACE);
CREATE TABLE [ref_budget_debts] (
_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Year INTEGER NOT NULL,
Month INTEGER NOT NULL,
Debt INTEGER NOT NULL REFERENCES [ref_Debts]([_id]) ON DELETE CASCADE ON UPDATE CASCADE,
Amount REAL NOT NULL,
UNIQUE (Deleted, Year, Month, Debt) ON CONFLICT REPLACE);
CREATE TABLE log_Templates (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
SrcAccount INTEGER NOT NULL REFERENCES [ref_Accounts]([_id]) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES [ref_Payees]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES [ref_Categories]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES [ref_Projects]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES [ref_Departments]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES [ref_Locations]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, Name TEXT NOT NULL, DestAccount INTEGER NOT NULL REFERENCES [ref_Accounts]([_id]) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, Comment TEXT, SearchString TEXT, Type INTEGER NOT NULL);
CREATE TABLE ref_SimpleDebts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Name TEXT NOT NULL, IsActive INTEGER NOT NULL, StartAmount REAL NOT NULL, Currency INTEGER REFERENCES [ref_Currencies]([_id]) ON DELETE SET NULL ON UPDATE CASCADE, SearchString TEXT, UNIQUE (Name, Deleted) ON CONFLICT ABORT);
CREATE TABLE ref_Senders (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Name TEXT NOT NULL, PhoneNo TEXT NOT NULL, AmountPos INTEGER, BalancePos INTEGER, LeadingCurrencySymbol INTEGER, DateFormat TEXT, IsActive INTEGER NOT NULL, AddCreditLimitToBalance INTEGER NOT NULL DEFAULT 0, UNIQUE (Name, Deleted) ON CONFLICT ABORT, UNIQUE (PhoneNo, Deleted) ON CONFLICT ABORT);
CREATE TABLE ref_Accounts_Sets (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Name TEXT, UNIQUE (Name, Deleted) ON CONFLICT ABORT);
CREATE TABLE log_Accounts_Sets (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
SetID INTEGER NOT NULL ON CONFLICT ABORT REFERENCES [ref_Accounts_Sets]([_id]) ON DELETE CASCADE ON UPDATE CASCADE,AccountID INTEGER NOT NULL ON CONFLICT ABORT REFERENCES [ref_Accounts]([_id]) ON DELETE CASCADE ON UPDATE CASCADE,UNIQUE (SetID, AccountID, Deleted) ON CONFLICT ABORT);
CREATE TABLE ref_Products (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
Name TEXT NOT NULL DEFAULT '', SearchString TEXT NOT NULL DEFAULT '', UNIQUE (Name, Deleted) ON CONFLICT ABORT);
CREATE TABLE  log_Products
(_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
FBID TEXT,
TS INTEGER,
Deleted INTEGER,
Dirty INTEGER,
LastEdited TEXT,
TransactionID INTEGER REFERENCES [log_Transactions]([_id]) ON DELETE SET NULL ON UPDATE CASCADE,
ProductID INTEGER REFERENCES [ref_Products]([_id]) ON DELETE SET NULL ON UPDATE CASCADE,
CategoryID INTEGER DEFAULT -1 REFERENCES [ref_Categories]([_id]) ON DELETE SET NULL ON UPDATE CASCADE,
ProjectID INTEGER DEFAULT -1 REFERENCES [ref_Projects]([_id]) ON DELETE SET NULL ON UPDATE CASCADE,
Price REAL NOT NULL DEFAULT 0,
Quantity REAL NOT NULL DEFAULT 1 CHECK (Quantity >= 0));
CREATE INDEX [idx_Products] ON [log_Products] ([Deleted], [TransactionID], [ProductID]);
CREATE TABLE log_Running_Balance (
     AccountID INTEGER NOT NULL,
     TransactionID INTEGER NOT NULL,
     DateTimeRB INTEGER NOT NULL,
     Income REAL NOT NULL,
     Expense REAL NOT NULL,
     PRIMARY KEY (AccountID, TransactionID) );
CREATE INDEX idx_RB_Accounts ON log_Running_Balance (AccountID);
CREATE INDEX idx_RB_Transactions ON log_Running_Balance (TransactionID);
CREATE INDEX idx_RB_DateTime ON log_Running_Balance (DateTimeRB);
-- Data for the migration 35 -> 36 test (DatabaseMigrationTest). Foreign keys are off while the fixture is loaded,
-- so dangling references are stored on purpose. Statements are one per line (see MigrationTestHelper.readStatements).
INSERT INTO ref_Currencies (_id, Deleted, Code, Symbol, Name, DecimalCount, OrderNumber) VALUES (100, 0, 'AAA', 'A', 'Currency minus one', -1, 0);
INSERT INTO ref_Currencies (_id, Deleted, Code, Symbol, Name, DecimalCount, OrderNumber) VALUES (101, 0, 'BBB', 'B', 'Currency two', 2, 1);
INSERT INTO ref_Currencies (_id, Deleted, Code, Symbol, Name, DecimalCount, OrderNumber) VALUES (102, 0, 'CCC', 'C', 'Currency six', 6, 2);
INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed, SearchString) VALUES (100, 0, 0, 'Acc A', 100, 10.005, 0, 'acc a');
INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed, SearchString) VALUES (101, 0, 0, 'Acc B', 101, 1234.5, 0, 'acc b');
INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed, SearchString) VALUES (102, 0, 0, 'Acc C', -1, 0.285, 0, 'acc c');
INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed, SearchString) VALUES (103, 0, 0, 'Acc D', 999, 0.285, 0, 'acc d');
INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed, SearchString) VALUES (104, 0, 0, 'Acc E', 102, 0.285, 0, 'acc e');
INSERT INTO ref_Categories (_id, Deleted, Name, COLOR, ParentID, OrderNumber, Sign, FullName, SearchString) VALUES (100, 0, 'Food', NULL, -1, 0, 1, 'Food', 'food');
INSERT INTO ref_Categories (_id, Deleted, Name, COLOR, ParentID, OrderNumber, Sign, FullName, SearchString) VALUES (101, 0, 'Food', NULL, NULL, 1, 1, 'Food', 'food');
INSERT INTO ref_Categories (_id, Deleted, Name, COLOR, ParentID, OrderNumber, Sign, FullName, SearchString) VALUES (102, 0, 'Child', NULL, 100, 0, 1, 'Food/Child', 'food/child');
INSERT INTO ref_Categories (_id, Deleted, Name, COLOR, ParentID, OrderNumber, Sign, FullName, SearchString) VALUES (103, 0, 'Lost child', NULL, 555, 0, 1, 'Lost child', 'lost child');
INSERT INTO ref_Payees (_id, Deleted, Name, DefCategory, ParentID, OrderNumber, FullName, SearchString) VALUES (100, 0, 'Ozon', -1, -1, 0, 'Ozon', 'ozon');
INSERT INTO ref_Payees (_id, Deleted, Name, DefCategory, ParentID, OrderNumber, FullName, SearchString) VALUES (101, 0, 'Ozon', NULL, NULL, 1, 'Ozon', 'ozon');
INSERT INTO ref_Payees (_id, Deleted, Name, DefCategory, ParentID, OrderNumber, FullName, SearchString) VALUES (102, 0, 'Self', 100, 102, 0, 'Self', 'self');
INSERT INTO ref_Payees (_id, Deleted, Name, DefCategory, ParentID, OrderNumber, FullName, SearchString) VALUES (500, 1, 'Gone', NULL, NULL, 0, 'Gone', 'gone');
DELETE FROM ref_Payees WHERE _id = 500;
INSERT INTO ref_Products (_id, Deleted, Name, SearchString) VALUES (0, 0, 'default_product', 'default_product');
INSERT INTO ref_Products (_id, Deleted, Name, SearchString) VALUES (1, 0, 'Milk', 'milk');
INSERT INTO ref_Senders (_id, Deleted, Name, PhoneNo, IsActive, AddCreditLimitToBalance) VALUES (100, 0, 'Bank', '+1000', 1, 0);
INSERT INTO ref_Senders (_id, Deleted, Name, PhoneNo, IsActive, AddCreditLimitToBalance) VALUES (101, 0, 'Shop', 'SHOP', 1, 0);
INSERT INTO log_Transactions (_id, Deleted, DateTime, SrcAccount, Payee, Category, Amount, Project, SimpleDebt, Department, Location, Comment, DestAccount, ExchangeRate, AutoCreated, SearchString) VALUES (100, 0, 1704110400000, 100, 77, -1, 10.005, -1, -1, -1, -1, 'T100', -1, 1.0, 0, 't100');
INSERT INTO log_Transactions (_id, Deleted, DateTime, SrcAccount, Payee, Category, Amount, Project, SimpleDebt, Department, Location, Comment, DestAccount, ExchangeRate, AutoCreated, SearchString) VALUES (101, NULL, 1704196800000, 100, NULL, NULL, 0.285, -1, -1, -1, -1, 'T101', 101, 0.0123456, 0, 't101');
INSERT INTO log_Transactions (_id, Deleted, DateTime, SrcAccount, Payee, Category, Amount, Project, SimpleDebt, Department, Location, Comment, DestAccount, ExchangeRate, AutoCreated, SearchString) VALUES (102, 0, 1704283200000, 100, NULL, NULL, -0.5, -1, -1, -1, -1, 'T102', 101, 1.0, 0, 't102');
INSERT INTO log_Templates (_id, Deleted, SrcAccount, Amount, Name, DestAccount, ExchangeRate, Type, Comment, SearchString) VALUES (100, 0, 100, 10.005, 'Tpl', -1, 1.0, 0, NULL, 'tpl');
INSERT INTO log_Templates (_id, Deleted, SrcAccount, Amount, Name, DestAccount, ExchangeRate, Type, Comment, SearchString) VALUES (101, 0, 999, 1, 'Lost tpl', -1, 1.0, 0, NULL, 'lost tpl');
INSERT INTO log_Products (_id, Deleted, TransactionID, ProductID, CategoryID, ProjectID, Price, Quantity) VALUES (100, 0, 100, 0, 100, -1, 10.005, 1);
INSERT INTO log_Products (_id, Deleted, TransactionID, ProductID, CategoryID, ProjectID, Price, Quantity) VALUES (101, 0, 101, 555, -1, -1, 1, 1);
INSERT INTO log_Products (_id, Deleted, TransactionID, ProductID, CategoryID, ProjectID, Price, Quantity) VALUES (102, 0, 101, 0, -1, -1, 0.285, 1);
INSERT INTO ref_Debts (_id, Deleted, Account, Payee, Category, Closed, SrcAccount, Comment) VALUES (100, 0, 100, 88, -1, 0, 100, NULL);
INSERT INTO ref_budget (_id, Deleted, Year, Month, Category, Amount, Currency) VALUES (100, 0, 2024, 1, 100, 0.285, 100);
INSERT INTO ref_budget_debts (_id, Deleted, Year, Month, Debt, Amount) VALUES (100, 0, 2024, 1, 100, 0.285);
INSERT INTO ref_Sms_Parser_Patterns (_id, Deleted, Type, Object, Pattern) VALUES (100, 0, 1, 'obj', 'pat');
INSERT INTO log_Incoming_SMS (_id, Deleted, DateTime, Sender, Body) VALUES (100, 0, '20240105  13:45', 100, 'sms 1');
INSERT INTO log_Incoming_SMS (_id, Deleted, DateTime, Sender, Body) VALUES (101, 0, 'мусор', 101, 'sms 2');
INSERT INTO log_Incoming_SMS (_id, Deleted, DateTime, Sender, Body) VALUES (102, 0, '20240105  13:45', 0, 'sms without sender');
INSERT INTO log_Running_Balance (AccountID, TransactionID, DateTimeRB, Income, Expense) VALUES (100, 100, 1704110400000, 0, -10.005);
INSERT INTO log_Running_Balance (AccountID, TransactionID, DateTimeRB, Income, Expense) VALUES (100, 101, 1704196800000, 0.285, -10.005);
PRAGMA user_version=35;
