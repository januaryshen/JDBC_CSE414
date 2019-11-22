package edu.uw.cs;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.xml.bind.*;

/**
 * Runs queries against a back-end database
 */
public class Query {
  // DB Connection
  private Connection conn;

  // Password hashing parameter constants
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;

  // Canned queries
  private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM TempFlight WHERE fid = ?";
  private PreparedStatement checkFlightCapacityStatement;

  // Jan: clearTable
  private static final String CLEAR_TABLE = "DELETE FROM Users DELETE FROM Itineraries DELETE FROM Reservations DELETE FROM TempFlight;";
  private PreparedStatement clearTableStatement;  

  // Jan: createCustomer
  private static final String INSERT_USER = "INSERT INTO Users (username, password, balance) VALUES(?, CAST(? AS VARBINARY(20)), ?);";
  private PreparedStatement insertUserStatement;  

  // Jan: login
  private static final String GET_PWD = "SELECT * FROM Users WHERE username = ? AND password = CONVERT(VARBINARY(20), ?);";
  private PreparedStatement getPwdStatement;

  // Jan: Search
  private static final String SEARCH_FLIGHT = "SELECT TOP (?) day_of_month,carrier_id,flight_num,origin_city,dest_city,actual_time,capacity,price, fid "
          + " FROM Flights"
          + " WHERE origin_city = ? AND dest_city = ? AND canceled = 0"
          + " AND day_of_month = ?"
          + " ORDER BY actual_time ASC;";
  private PreparedStatement searchFlightStatement;

  private static final String SEARCH_INDIRECT_FLIGHT = "SELECT "
          + "f1.day_of_month AS f1_day_of_month, f1.carrier_id AS f1_carrier_id, f1.flight_num AS f1_flight_num, f1.origin_city AS f1_origin_city, f1.dest_city AS f1_dest_city,f1.actual_time AS f1_actual_time, f1.capacity AS f1_capacity,f1.price AS f1_price, f1.fid AS f1_fid,"
          + "f2.day_of_month AS f2_day_of_month, f2.carrier_id AS f2_carrier_id, f2.flight_num AS f2_flight_num, f2.origin_city AS f2_origin_city, f2.dest_city AS f2_dest_city,f2.actual_time AS f2_actual_time, f2.capacity AS f2_capacity,f2.price AS f2_price, f2.fid AS f2_fid"
          + " FROM FLIGHTS AS f1, FLIGHTS AS f2"
          + " WHERE f1.origin_city = ? AND f2.dest_city = ? AND f1.dest_city = f2.origin_city"
          + " AND f1.day_of_month = ? AND f2.day_of_month = ? AND f1.canceled = 0 AND f2.canceled = 0"
          + " ORDER BY f1.actual_time + f2.actual_time, f1.fid, f2.fid ASC;";
  private PreparedStatement searchIndirectFlightStatement;

  private static final String INSERT_ITINERARIES = "INSERT INTO Itineraries VALUES(?, ?, ?, ?, ?, ?, ?, ?);";
  private PreparedStatement insertItinerariesStatement;

  private static final String INSERT_TEMPFLIGHT = "INSERT INTO TempFlight VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
  private PreparedStatement insertTempflightStatement;

  private static final String GET_FLIGHT_ORDERED = "SELECT * FROM TempFlight AS tf, Itineraries AS iti WHERE tf.t_iid = iti.t_iid ORDER BY iti.minutes;";
  private PreparedStatement getFlightOrderedStatement;

  // Jan: book
  private static final String GET_ITINERARIES = "SELECT * FROM Itineraries";
  private PreparedStatement getItinerariesStatement;

  private static final String INSERT_RESERVATIONS = "INSERT INTO Reservations VALUES(?, ?, ?, ?, ?, ?, ?);";
  private PreparedStatement insertReservationsStatement;

  private static final String GET_RES_USER_DAY = "SELECT * FROM Reservations WHERE day = ? AND username = ?;";
  private PreparedStatement getResUserDayStatement;

  private static final String UPDATE_TEMPFLIGHT_CAPACiTY = "UPDATE TempFlight SET capacity = ? WHERE fid = ?;";
  private PreparedStatement updateTempflightCapacityStatement;

  // Jan: pay
  private static final String GET_USER = "SELECT * FROM Users WHERE username = ?;";
  private PreparedStatement getUserStatement;

  private static final String GET_RES_PAID = "SELECT * FROM Reservations WHERE rid = ? AND paid = 0;";
  private PreparedStatement getResPaidStatement;

  private static final String UPDATE_USER_BALANCE = "UPDATE Users SET balance = ? WHERE username = ?";
  private PreparedStatement updateUserBalanceStatement;

  private static final String UPDATE_RES_PAID = "UPDATE Reservations SET paid = 1 WHERE rid = ?";
  private PreparedStatement updateResPaidStatement;

  // Jan: reservation
  private static final String GET_RES_USER = "SELECT * FROM Reservations WHERE username = ?;";
  private PreparedStatement getResUserStatement;

  private static final String GET_ITI_IID = "SELECT * FROM Itineraries WHERE t_iid = ?;";
  private PreparedStatement getItiIidStatement;

  private static final String GET_FLIGHT_FID = "SELECT * FROM FLIGHTS WHERE fid = ?;";
  private PreparedStatement getFlightFidStatement;

  // Jan: cancel
  private static final String UPDATE_RES_CANCEL = "UPDATE Reservations SET cancelled = 1 WHERE rid = ?;";
  private PreparedStatement updateResCancelStatement;

  private static final String GET_RES_RID_CANCEL = "SELECT * FROM Reservations WHERE rid = ? AND cancelled = 0;";
  private PreparedStatement getResRidCancelStatement;

  /**
   * Establishes a new application-to-database connection. Uses the
   * dbconn.properties configuration settings
   * 
   * @throws IOException
   * @throws SQLException
   */
  public void openConnection() throws IOException, SQLException {
    // Connect to the database with the provided connection configuration
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));
    String serverURL = configProps.getProperty("hw1.server_url");
    String dbName = configProps.getProperty("hw1.database_name");
    String adminName = configProps.getProperty("hw1.username");
    String password = configProps.getProperty("hw1.password");
    String connectionUrl = String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
        dbName, adminName, password);
    conn = DriverManager.getConnection(connectionUrl);

    // By default, automatically commit after each statement
    conn.setAutoCommit(true);

    // By default, set the transaction isolation level to serializable
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
  }

  /**
   * Closes the application-to-database connection
   */
  public void closeConnection() throws SQLException {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
      try {
          clearTableStatement.executeUpdate();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

  /*
   * prepare all the SQL statements in this method.
   */
  public void prepareStatements() throws SQLException {
    checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);

    clearTableStatement = conn.prepareStatement(CLEAR_TABLE);
    insertUserStatement = conn.prepareStatement(INSERT_USER);
    getPwdStatement = conn.prepareStatement(GET_PWD);
    searchFlightStatement = conn.prepareStatement(SEARCH_FLIGHT);
    searchIndirectFlightStatement = conn.prepareStatement(SEARCH_INDIRECT_FLIGHT);
    insertItinerariesStatement = conn.prepareStatement(INSERT_ITINERARIES);
    insertTempflightStatement = conn.prepareStatement(INSERT_TEMPFLIGHT);
    getFlightOrderedStatement = conn.prepareStatement(GET_FLIGHT_ORDERED);
    getItinerariesStatement = conn.prepareStatement(GET_ITINERARIES);
    insertReservationsStatement = conn.prepareStatement(INSERT_RESERVATIONS);
    getResUserDayStatement = conn.prepareStatement(GET_RES_USER_DAY);
    getUserStatement = conn.prepareStatement(GET_USER);
    getResPaidStatement = conn.prepareStatement(GET_RES_PAID);
    updateUserBalanceStatement = conn.prepareStatement(UPDATE_USER_BALANCE);
    updateResPaidStatement = conn.prepareStatement(UPDATE_RES_PAID);
    getResUserStatement = conn.prepareStatement(GET_RES_USER);
    getItiIidStatement = conn.prepareStatement(GET_ITI_IID);
    getFlightFidStatement = conn.prepareStatement(GET_FLIGHT_FID);
    updateResCancelStatement = conn.prepareStatement(UPDATE_RES_CANCEL);
    getResRidCancelStatement = conn.prepareStatement(GET_RES_RID_CANCEL);
    updateTempflightCapacityStatement = conn.prepareStatement(UPDATE_TEMPFLIGHT_CAPACiTY);
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged
   *         in\n" For all other errors, return "Login failed\n". Otherwise,
   *         return "Logged in as [username]\n".
   */

  public int loginStatus = 0;
  public String current_user = "";

  public String transaction_login(String username, String password) {

    if (loginStatus == 1) {
        return "User already logged in\n";
        }
    else {
      try {
        getPwdStatement.setString(1, username);
        getPwdStatement.setString(2, password);

        if (getPwdStatement.executeQuery().next()) {
          loginStatus = 1;
          current_user = username;
          return "Logged in as " + username + "\n";
        }
        else{
          return "Login failed\n";
        }
      }
      catch (SQLException e) {
      e.printStackTrace();
      return "Username does not exist.\n";
      }
    }
  }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should
   *                   be >= 0 (failure otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n"
   *         if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {

    try{
      if(initAmount >= 0){
          insertUserStatement.clearParameters();
          insertUserStatement.setString(1, username);
          insertUserStatement.setString(2, password);
          insertUserStatement.setInt(3,initAmount);
          insertUserStatement.executeUpdate();
          return "Created user " + username + "\n";}
      
      else{
        return "Failed to create user\n";
      }
    }
    catch (SQLException e) {
      e.printStackTrace();
      return "Failed to create user\n";
    }
  }

  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination
   * city, on the given day of the month. If {@code directFlight} is true, it only
   * searches for direct flights, otherwise is searches for direct flights and
   * flights with two "hops." Only searches for up to the number of itineraries
   * given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights,
   *                            otherwise include indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   *
   * @return If no itineraries were found, return "No flights match your
   *         selection\n". If an error occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total
   *         flight time] minutes\n [first flight in itinerary]\n ... [last flight
   *         in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the
   *         {@code Flight} class. Itinerary numbers in each search should always
   *         start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */

  public Map <Integer, Integer> MAP_search_book = new HashMap<Integer, Integer>();
  public String transaction_search(String originCity, String destinationCity, boolean directFlight, int dayOfMonth,
      int numberOfItineraries) {

    int COUNT_direct = 0;
    int COUNT_indirect = 0;
    int ITI_id = 0;
    StringBuffer sb = new StringBuffer();

//  This part searches for direct flight
    try {
      searchFlightStatement.clearParameters();
      searchFlightStatement.setInt(1, numberOfItineraries);
      searchFlightStatement.setString(2, originCity);
      searchFlightStatement.setString(3, destinationCity);
      searchFlightStatement.setInt(4, dayOfMonth);
      ResultSet searchFlightRS = searchFlightStatement.executeQuery();

      if(!searchFlightRS.next()){
        return "No flights match your selection\n";
      }
      else{
        do {
//      This part writes direct flights to database
          Flight flight0 = new Flight();
          flight0 = searchFlight0(flight0, searchFlightRS);

          updateItinerariesTable(COUNT_direct, flight0.fid, 0,1, flight0.time, flight0.capacity, flight0.dayOfMonth, flight0.price);
          updateTempFlightTable(COUNT_direct, flight0.fid, flight0.dayOfMonth, flight0.carrierId, flight0.flightNum, flight0.originCity, flight0.destCity, flight0.time, flight0.capacity, flight0.price);

          COUNT_direct += 1;
        } while (searchFlightRS.next() && COUNT_direct < numberOfItineraries);
      }
      searchFlightRS.close();

//      This part searches for indirect flight and writes the result into database
      if (COUNT_direct < numberOfItineraries && directFlight == false){

        searchIndirectFlightStatement.clearParameters();
        searchIndirectFlightStatement.setString(1, originCity);
        searchIndirectFlightStatement.setString(2, destinationCity);
        searchIndirectFlightStatement.setInt(3, dayOfMonth);
        searchIndirectFlightStatement.setInt(4, dayOfMonth);
        ResultSet searchIndirectFlightRS = searchIndirectFlightStatement.executeQuery();

        while (searchIndirectFlightRS.next() && COUNT_indirect < numberOfItineraries - COUNT_direct) {

          Flight flight1 = new Flight();
          Flight flight2 = new Flight();
          flight1 = searchFlight1(flight1, searchIndirectFlightRS);
          flight2 = searchFlight2(flight2, searchIndirectFlightRS);

          int itineraryId = COUNT_direct + COUNT_indirect;
          int total_flight_time = flight1.time + flight2.time;
          int capacity_threashold = Math.min(flight1.capacity, flight2.capacity);
          int one_hop_price = flight1.price + flight2.price;

          updateItinerariesTable(itineraryId, flight1.fid, flight2.fid, 2, total_flight_time, capacity_threashold, flight1.dayOfMonth, one_hop_price);
          updateTempFlightTable(itineraryId, flight1.fid, flight1.dayOfMonth, flight1.carrierId, flight1.flightNum, flight1.originCity, flight1.destCity, flight1.time, flight1.capacity, flight1.price);
          updateTempFlightTable(itineraryId, flight2.fid, flight2.dayOfMonth, flight2.carrierId, flight2.flightNum, flight2.originCity, flight2.destCity, flight2.time, flight2.capacity, flight2.price);

          COUNT_indirect += 1;
        }
        searchIndirectFlightRS.close();
      }
      // This part writes the result into stringbuffer
      ResultSet getFlightOrderedRS = getFlightOrderedStatement.executeQuery();
      if(getFlightOrderedRS.next()){
        do {
          int one_hop = getFlightOrderedRS.getInt("num_of_flight");
          int minutes = getFlightOrderedRS.getInt("minutes");
          Flight flight_ordered = new Flight();
          MAP_search_book.put(ITI_id, getFlightOrderedRS.getInt("t_iid")); // to track the display sequence vs. t_iid in SQL table

          if(one_hop == 2) {
            sb.append("Itinerary " + ITI_id + ": 2 flight(s), " + minutes + " minutes\n");
            flight_ordered = searchFlight0(flight_ordered, getFlightOrderedRS);
            sb.append(flight_ordered.toString() + "\n");
            getFlightOrderedRS.next();
            flight_ordered = searchFlight0(flight_ordered, getFlightOrderedRS);
            sb.append(flight_ordered.toString() + "\n");
          }
          else {
            sb.append("Itinerary " + ITI_id + ": 1 flight(s), " + minutes + " minutes\n");
            flight_ordered = searchFlight0(flight_ordered, getFlightOrderedRS);
            sb.append(flight_ordered.toString() + "\n");
          }
          ITI_id += 1;
        } while(getFlightOrderedRS.next());
        getFlightOrderedRS.close();
      }
//      System.out.print(MAP_search_book);
      return sb.toString();

    } catch (SQLException e) {
      e.printStackTrace();
      return "Failed to search\n";
    }
  }

  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is
   *                    returned by search in the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations,
   *         not logged in\n". If try to book an itinerary with invalid ID, then
   *         return "No such itinerary {@code itineraryId}\n". If the user already
   *         has a reservation on the same day as the one that they are trying to
   *         book now, then return "You cannot book two flights in the same
   *         day\n". For all other errors, return "Booking failed\n".
   *
   *         And if booking succeeded, return "Booked flight(s), reservation ID:
   *         [reservationId]\n" where reservationId is a unique number in the
   *         reservation system that starts from 1 and increments by 1 each time a
   *         successful reservation is made by any user in the system.
   */
  public int reservations_PK = 1;
  public String transaction_book(int itineraryId) {
    if (loginStatus == 0){
      return "Cannot book reservations, not logged in\n";
    }
    try{

      ResultSet getItinerariesRS = getItinerariesStatement.executeQuery();
      int temp_iid = 0;
      int fid1_capacity = 0;
      int fid2_capacity = 999;
      int target_date = 0;
      int flight_price = 0;
      boolean can_book = true;

      if(getItinerariesRS.next()){
        target_date = getItinerariesRS.getInt("day");
        getResUserDayStatement.setInt(1, target_date);
        getResUserDayStatement.setString(2, current_user);
        ResultSet searchReservationsResultSet = getResUserDayStatement.executeQuery();
        if (searchReservationsResultSet.next()){
          return "You cannot book two flights in the same day\n";
        }

        do {
          temp_iid = getItinerariesRS.getInt("t_iid");
          if(MAP_search_book.get(itineraryId) == temp_iid){

            fid1_capacity = checkFlightCapacity(getItinerariesRS.getInt("fid1"));
            if (getItinerariesRS.getInt("fid2") != 0) { fid2_capacity = checkFlightCapacity(getItinerariesRS.getInt("fid2")); }
            if (fid1_capacity < 1 || fid2_capacity < 1){ can_book = false;}

            if (can_book == true){
              flight_price = getItinerariesRS.getInt("price");
              updateReservationTable(reservations_PK, MAP_search_book.get(itineraryId), current_user, target_date, 0, 0, flight_price);
              getResUserDayStatement.setInt(1, target_date);
              getResUserDayStatement.setString(2, current_user);
              ResultSet searchReservationResultSet = getResUserDayStatement.executeQuery();
              while(searchReservationResultSet.next()) {
                reservations_PK += 1;
                updateTempflightCapacity(fid1_capacity - 1, getItinerariesRS.getInt("fid1"));
                if (getItinerariesRS.getInt("fid2") != 0) {
                  updateTempflightCapacity(fid2_capacity - 1, getItinerariesRS.getInt("fid2"));
                }
                return "Booked flight(s), reservation ID: " + searchReservationResultSet.getInt("rid") + "\n";
              }
            }
            else{
              return "Booking failed because of insufficient capacity\n";
            }
          }
        } while (getItinerariesRS.next());
      }
      else{
        return "No such itinerary " + itineraryId + "\n";
      }
    }
    catch (SQLException e) {
      e.printStackTrace();
    }
    return "Booking failed\n";
  }

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n"
   *         If the reservation is not found / not under the logged in user's
   *         name, then return "Cannot find unpaid reservation [reservationId]
   *         under user: [username]\n" If the user does not have enough money in
   *         their account, then return "User has only [balance] in account but
   *         itinerary costs [cost]\n" For all other errors, return "Failed to pay
   *         for reservation [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining
   *         balance: [balance]\n" where [balance] is the remaining balance in the
   *         user's account.
   */
  public String transaction_pay(int reservationId) {
    if (loginStatus == 0){
      return "Cannot pay, not logged in\n";
    }
    try {
      int userBalance = 0;
      int ticket_price = 0;
      int remained_balance = 0;
      getUserStatement.setString(1, current_user);
      ResultSet getUserRS = getUserStatement.executeQuery();

      getResPaidStatement.setInt(1, reservationId);
      ResultSet getResPaidRS = getResPaidStatement.executeQuery();

      if(getUserRS.next() && getResPaidRS.next()){
        userBalance = getUserRS.getInt("balance");
        ticket_price = getResPaidRS.getInt("price");
        remained_balance = userBalance - ticket_price;
        if(remained_balance < 0){
          return "User has only " + userBalance + " in account but itinerary costs " + ticket_price + "\n";
        }
        updateUserBalance(remained_balance, current_user);
        updateReservationPayment(reservationId);
        return "Paid reservation: " + reservationId + " remaining balance: " + remained_balance + "\n";
      }
      else{
        return "Cannot find unpaid reservation " + reservationId + " under user: " + current_user + "\n";
      }
    }
    catch (SQLException e) {
      e.printStackTrace();
    }
    return "Failed to pay for reservation " + reservationId + "\n";
  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not
   *         logged in\n" If the user has no reservations, then return "No
   *         reservations found\n" For all other errors, return "Failed to
   *         retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n" [flight 1
   *         under the reservation] [flight 2 under the reservation] Reservation
   *         [reservation ID] paid: [true or false]:\n" [flight 1 under the
   *         reservation] [flight 2 under the reservation] ...
   *
   *         Each flight should be printed using the same format as in the
   *         {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations() {
    if(loginStatus == 0){
      return "Cannot view reservations, not logged in\n";
    }
    try{
      StringBuffer sbRes = new StringBuffer();
      boolean payment_status = false;
      int reservation_id = 0;
      int itinerary_id = 0;
      int flight_id1 = 0;
      int flight_id2 = 0;
      Flight flight_res = new Flight();

      getResUserStatement.setString(1, current_user);
      ResultSet getResUserRS = getResUserStatement.executeQuery();
      if(getResUserRS.next()) {
        if (getResUserRS.getInt("paid") == 1) {
          payment_status = true;
        }
        reservation_id = getResUserRS.getInt("rid");
        itinerary_id = getResUserRS.getInt("iid");
        sbRes.append("Reservation " + reservation_id + " paid: " + payment_status + ":\n");

        getItiIidStatement.setInt(1, itinerary_id);
        ResultSet getItiIidRS = getItiIidStatement.executeQuery();
        while (getItiIidRS.next()) {
          flight_id1 = getItiIidRS.getInt("fid1");
          flight_id2 = getItiIidRS.getInt("fid2");

          getFlightFidStatement.clearParameters();
          getFlightFidStatement.setInt(1, flight_id1);
          ResultSet getFlightFidRS = getFlightFidStatement.executeQuery();
          if (getFlightFidRS.next()) {
            flight_res = searchFlight0(flight_res, getFlightFidRS);
            sbRes.append(flight_res.toString() + "\n");
          }
          if (flight_id2 != 0) {
            getFlightFidStatement.clearParameters();
            getFlightFidStatement.setInt(1, flight_id2);
            ResultSet getFlightFidRS2 = getFlightFidStatement.executeQuery();
            if (getFlightFidRS2.next()){
              flight_res = searchFlight0(flight_res, getFlightFidRS2);
              sbRes.append(flight_res.toString() + "\n");
            }
          }
        }

        getResUserRS.close();
        getItiIidRS.close();
        return sbRes.toString();
      }
      else{
        return "No reservation found\n";
      }
    }
    catch (SQLException e) {
        e.printStackTrace();
    }
    return "Failed to retrieve reservations\n";
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   *
   * @return If no user has logged in, then return "Cannot cancel reservations,
   *         not logged in\n" For all other errors, return "Failed to cancel
   *         reservation [reservationId]\n"
   *
   *         If successful, return "Canceled reservation [reservationId]\n"
   *
   *         Even though a reservation has been canceled, its ID should not be
   *         reused by the system.
   */
  public String transaction_cancel(int reservationId) {
    if(loginStatus == 0) {
      return "Cannot cancel reservations, not logged in\n";
    }
    try{
      getResRidCancelStatement.setInt(1, reservationId);
      ResultSet getResRidCancelRS = getResRidCancelStatement.executeQuery();
      if(getResRidCancelRS.next()){
        updateResCancelStatement.setInt(1, reservationId);
        updateResCancelStatement.executeUpdate();
        return "Canceled reservation " + reservationId + "\n";
      }
      else{
        return "Failed to cancel reservation " + reservationId + "\n";
      }
    }
    catch (SQLException e) {
      e.printStackTrace();
    }
    return "Failed to cancel reservation " + reservationId + "\n";
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    checkFlightCapacityStatement.clearParameters();
    checkFlightCapacityStatement.setInt(1, fid);
    ResultSet results = checkFlightCapacityStatement.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  private Flight searchFlight0(Flight flight0, ResultSet RS) throws SQLException {
    flight0.fid = RS.getInt("fid");
    flight0.dayOfMonth = RS.getInt("day_of_month");
    flight0.carrierId = RS.getString("carrier_id");
    flight0.flightNum = RS.getString("flight_num");
    flight0.originCity = RS.getString("origin_city");
    flight0.destCity = RS.getString("dest_city");
    flight0.time = RS.getInt("actual_time");
    flight0.capacity = RS.getInt("capacity");
    flight0.price = RS.getInt("price");

    return flight0;
  }

  private Flight searchFlight1(Flight flight1, ResultSet RS) throws SQLException {
    flight1.fid = RS.getInt("f1_fid");
    flight1.dayOfMonth = RS.getInt("f1_day_of_month");
    flight1.carrierId = RS.getString("f1_carrier_id");
    flight1.flightNum = RS.getString("f1_flight_num");
    flight1.originCity = RS.getString("f1_origin_city");
    flight1.destCity = RS.getString("f1_dest_city");
    flight1.time = RS.getInt("f1_actual_time");
    flight1.capacity = RS.getInt("f1_capacity");
    flight1.price = RS.getInt("f1_price");

    return flight1;
  }

  private Flight searchFlight2(Flight flight2, ResultSet RS) throws SQLException {
    flight2.fid = RS.getInt("f2_fid");
    flight2.dayOfMonth = RS.getInt("f2_day_of_month");
    flight2.carrierId = RS.getString("f2_carrier_id");
    flight2.flightNum = RS.getString("f2_flight_num");
    flight2.originCity = RS.getString("f2_origin_city");
    flight2.destCity = RS.getString("f2_dest_city");
    flight2.time = RS.getInt("f2_actual_time");
    flight2.capacity = RS.getInt("f2_capacity");
    flight2.price = RS.getInt("f2_price");

    return flight2;
  }

  private void updateItinerariesTable(int t_iid, int fid1, int fid2, int num_of_flight, int duration, int capacity, int day, int price) throws SQLException {
    insertItinerariesStatement.clearParameters();
    insertItinerariesStatement.setInt(1, t_iid);
    insertItinerariesStatement.setInt(2, fid1);
    insertItinerariesStatement.setInt(3, fid2);
    insertItinerariesStatement.setInt(4, num_of_flight);
    insertItinerariesStatement.setInt(5, duration);
    insertItinerariesStatement.setInt(6, capacity);
    insertItinerariesStatement.setInt(7, day);
    insertItinerariesStatement.setInt(8, price);
    insertItinerariesStatement.executeUpdate();
  }

  private void updateTempFlightTable(int t_iid, int t_fid, int day, String carrier, String flight_num, String origin, String dest, int duration, int capacity, int price) throws SQLException {
    insertTempflightStatement.clearParameters();
    insertTempflightStatement.setInt(1, t_iid);
    insertTempflightStatement.setInt(2, t_fid);
    insertTempflightStatement.setInt(3, day);
    insertTempflightStatement.setString(4, carrier);
    insertTempflightStatement.setString(5, flight_num);
    insertTempflightStatement.setString(6, origin);
    insertTempflightStatement.setString(7, dest);
    insertTempflightStatement.setInt(8, duration);
    insertTempflightStatement.setInt(9, capacity);
    insertTempflightStatement.setInt(10, price);
    insertTempflightStatement.executeUpdate();
  }

  private void updateReservationTable(int reservations_PK, int iid, String username, int day, int paid, int cancelled, int price) throws SQLException {
    insertReservationsStatement.clearParameters();
    insertReservationsStatement.setInt(1, reservations_PK);
    insertReservationsStatement.setInt(2, iid);
    insertReservationsStatement.setString(3, username);
    insertReservationsStatement.setInt(4, day);
    insertReservationsStatement.setInt(5, paid);
    insertReservationsStatement.setInt(6, cancelled);
    insertReservationsStatement.setInt(7, price);
    insertReservationsStatement.executeUpdate();
  }

  private void updateUserBalance(int remained_balance, String current_user) throws SQLException {
    updateUserBalanceStatement.clearParameters();
    updateUserBalanceStatement.setInt(1, remained_balance);
    updateUserBalanceStatement.setString(2, current_user);
    updateUserBalanceStatement.executeUpdate();
  }

  private void updateReservationPayment(int reservationId) throws SQLException {
    updateResPaidStatement.clearParameters();
    updateResPaidStatement.setInt(1, reservationId);
    updateResPaidStatement.executeUpdate();
  }

  private void updateTempflightCapacity(int capacity, int fid) throws SQLException {
    updateTempflightCapacityStatement.clearParameters();
    updateTempflightCapacityStatement.setInt(1, capacity);
    updateTempflightCapacityStatement.setInt(2, fid);
    updateTempflightCapacityStatement.executeUpdate();
  }
  /**
   * A class to store flight information.
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: " + flightNum + " Origin: "
          + originCity + " Dest: " + destCity + " Duration: " + time + " Capacity: " + capacity + " Price: " + price;
    }
  }
}
