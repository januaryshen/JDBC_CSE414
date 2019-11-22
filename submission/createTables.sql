CREATE TABLE CARRIERS (cid varchar(7) PRIMARY KEY, name varchar(83));
--.import carriers.csv carriers

CREATE TABLE MONTHS (mid int PRIMARY KEY, month varchar(9));
--.import months.csv months

CREATE TABLE WEEKDAYS (did int PRIMARY KEY, day_of_week varchar(9));
--.import weekdays.csv weekdays

CREATE TABLE FLIGHTS (fid int PRIMARY KEY, 
         month_id int REFERENCES MONTHS(mid),        -- 1-12
         day_of_month int,    -- 1-31 
         day_of_week_id int REFERENCES WEEKDAYS(did),  -- 1-7, 1 = Monday, 2 = Tuesday, etc
         carrier_id varchar(7) REFERENCES CARRIERS(cid), 
         flight_num int,
         origin_city varchar(34), 
         origin_state varchar(47), 
         dest_city varchar(34), 
         dest_state varchar(46), 
         departure_delay int, -- in mins
         taxi_out int,        -- in mins
         arrival_delay int,   -- in mins
         canceled int,        -- 1 means canceled
         actual_time int,     -- in mins
         distance int,        -- in miles
         capacity int, 
         price int            -- in $             
         );
--.import flights-small.csv flights

CREATE TABLE Users(username VARCHAR(20) PRIMARY KEY, password VARBINARY(20), balance INT);  --password VARBINARY(20)
CREATE TABLE Itineraries(t_iid INT PRIMARY KEY, fid1 INT, fid2 INT, num_of_flight INT, minutes INT, capacity INT, day INT, price INT);
CREATE TABLE Reservations(rid INT PRIMARY KEY, iid INT, username VARCHAR(20), day INT, paid INT, cancelled INT, price INT);
CREATE TABLE TempFlight(t_iid INT, fid INT, day_of_month INT, carrier_id VARCHAR(2), flight_num VARCHAR(4), origin_city VARCHAR(20), dest_city VARCHAR(20), actual_time INT, capacity INT, price INT, PRIMARY KEY(t_iid, fid));


/*
DROP TABLE TempFlight;
DROP TABLE Reservations;
DROP TABLE Itineraries;
DROP TABLE Users;
DROP TABLE FLIGHTS;
DROP TABLE WEEKDAYS;
DROP TABLE MONTHS;
DROP TABLE CARRIERS;


*/



/*

CREATE EXTERNAL DATA SOURCE cse344blob
WITH (  TYPE = BLOB_STORAGE,
        LOCATION = 'https://cse344.blob.core.windows.net/flights'
);

bulk insert Carriers from 'carriers.csv'
with (ROWTERMINATOR = '0x0a',
DATA_SOURCE = 'cse344blob', FORMAT='CSV', CODEPAGE = 65001, --UTF-8 encoding
FIRSTROW=1,TABLOCK);

bulk insert Months from 'months.csv'
with (ROWTERMINATOR = '0x0a',
DATA_SOURCE = 'cse344blob', FORMAT='CSV', CODEPAGE = 65001, --UTF-8 encoding
FIRSTROW=1,TABLOCK);

bulk insert Weekdays from 'weekdays.csv'
with (ROWTERMINATOR = '0x0a',
DATA_SOURCE = 'cse344blob', FORMAT='CSV', CODEPAGE = 65001, --UTF-8 encoding
FIRSTROW=1,TABLOCK);

-- Import for the large Flights table
-- This last import may take ~5 minutes on the provided server settings
bulk insert Flights from 'flights-small.csv'
with (ROWTERMINATOR = '0x0a',
DATA_SOURCE = 'cse344blob', FORMAT='CSV', CODEPAGE = 65001, --UTF-8 encoding
FIRSTROW=1,TABLOCK);


*/




