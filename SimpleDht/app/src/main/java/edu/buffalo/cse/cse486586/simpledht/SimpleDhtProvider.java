package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

public class SimpleDhtProvider extends ContentProvider {

    static String[] REMOTE_PORTS = { };
    static final int SERVER_PORT = 10000;
    public static String myPort = null;

    private SQLiteDatabase database;
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "SimpleDht";
    private static final String TABLE_NAME = "MESSAGES";
    private static final String MESSAGE_TABLE = "CREATE TABLE " + TABLE_NAME + " (key TEXT PRIMARY KEY, value TEXT NOT NULL);";

    /**************************************************/
    private String successor;
    private String predecessor;
    private String smallestNode;
    private String largestNode;
    private boolean isReplyReceived = false;
    private boolean isRingFormed = false;
    private TreeMap<String,String> ringNodes = new TreeMap<>();

    private String[][] results;
    private List<String[]> dataList = new LinkedList();
    private int resultCount = 0;


    public static final Uri providerUri = Uri.parse(Constants.URI );

    private class DBHelper extends SQLiteOpenHelper {
        public DBHelper(Context context){
            super(context, DATABASE_NAME, null , DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(MESSAGE_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
    }

    /*
     * This methods checks if the ring is formed. If do then it will direct the request to delete
     * to the appropriate node based on hashed value of the key.
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub

        int noOfRowDeleted = 0;

        try{

                if( isRingFormed ){
                    String hashKey = null;
                    String hashNodekey = genHash(getNodeId(myPort));
                    String hashPredecessorNodeKey = genHash(getNodeId(predecessor));

                    if( selection.contains("*") ){
                        resultCount = 0;
                        resultCount = database.delete(TABLE_NAME,null,null);

                        Message msg = new Message(myPort, null , Constants.DHT_DELETE_REQUEST , null);
                        msg.setKey(selection);

                        unicast(msg,successor);

                        while (!isReplyReceived){

                        }

                        isReplyReceived = false;

                        noOfRowDeleted = resultCount;

                    }
                    else if( selection.contains("@")){
                        noOfRowDeleted = database.delete(TABLE_NAME,null,null);
                    }
                    else{
                        hashKey = genHash(selection);
                        if( hashKey.compareTo(hashNodekey) <= 0 && hashKey.compareTo(hashPredecessorNodeKey) > 0  ){
                            noOfRowDeleted = database.delete(TABLE_NAME,"key='"+selection+"'",null);
                        }
                        else if( myPort.equalsIgnoreCase(smallestNode) && ( hashKey.compareTo(hashPredecessorNodeKey) > 0 || hashKey.compareTo(hashNodekey) <= 0)){
                            noOfRowDeleted = database.delete(TABLE_NAME,"key='"+selection+"'",null);
                        }
                        else{

                            Message msg = new Message(myPort, null , Constants.DELETE_REQUEST , hashKey);
                            msg.setKey(selection);

                            unicast(msg, successor);
                            while (!isReplyReceived){

                            }

                            isReplyReceived = false;

                            noOfRowDeleted = resultCount;
                            resultCount = 0;

                        }

                    }
                }
                else{
                    if( selection.contains("*") ){

                        noOfRowDeleted = database.delete(TABLE_NAME,null,null);

                    }
                    else if( selection.contains("@")){

                        noOfRowDeleted = database.delete(TABLE_NAME,null,null);
                    }
                    else{

                        noOfRowDeleted = database.delete(TABLE_NAME,"key='"+selection+"'",null);
                    }
                }
            }

        catch (NoSuchAlgorithmException e){
            e.printStackTrace();
            Log.e("NoSuchAlgorithmException" , "delete method");
        }
        catch (Exception e){
            e.printStackTrace();
            Log.e("Exception in deleting " ,"delete method");
        }

        return noOfRowDeleted;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }


    /*
     * This methods checks if the ring is formed. If do then it will direct the request to insert the data
     * to the appropriate node based on hashed value of the key.
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub

        try{
            String key = (String) values.get("key");
            String value = (String) values.get("value");


            if( isRingFormed ){
                String hashKey = genHash(key);
                String hashNodekey = genHash(getNodeId(myPort));
                String hashPredecessorNodeKey = genHash(getNodeId(predecessor));

                if( hashKey.compareTo(hashNodekey) <= 0 && hashKey.compareTo(hashPredecessorNodeKey) > 0  ){

                    long ID = database.insertWithOnConflict(TABLE_NAME , null, values , SQLiteDatabase.CONFLICT_REPLACE);
                    if (ID > 0) {
                        Uri uri1 = ContentUris.withAppendedId(providerUri, ID);
                        // For updation
                        getContext().getContentResolver().notifyChange(uri1, null);
                        return uri1;
                    }
                }
                else if( myPort.equalsIgnoreCase(smallestNode) && ( hashKey.compareTo(hashPredecessorNodeKey) > 0 || hashKey.compareTo(hashNodekey) <= 0)){

                    long ID = database.insertWithOnConflict(TABLE_NAME , null, values , SQLiteDatabase.CONFLICT_REPLACE);
                    if (ID > 0) {
                        Uri uri1 = ContentUris.withAppendedId(providerUri, ID);
                        // For updation
                        getContext().getContentResolver().notifyChange(uri1, null);
                        return uri1;
                    }
                }
                else{

                    Message msg = new Message(myPort, null, Constants.INSERT_REQUEST , hashKey);
                    msg.setKey(key);
                    msg.setValue(value);
                    unicast(msg, successor);

                    while (!isReplyReceived){
                        //Log.v("!!!!!!!!!!!waiting" , "");
                    }

                    isReplyReceived = false;

                }


            }
            else{

                long ID = database.insertWithOnConflict(TABLE_NAME , null, values , SQLiteDatabase.CONFLICT_REPLACE);
                if (ID > 0) {
                    Uri uri1 = ContentUris.withAppendedId(providerUri, ID);
                    // For updation
                    getContext().getContentResolver().notifyChange(uri1, null);
                    return uri1;
                }
            }

        }
        catch (NoSuchAlgorithmException e){
            e.printStackTrace();
            Log.e("NoSuchAlgorithmException" , "insert method");
        }
        catch (Exception e){
            e.printStackTrace();
            Log.e("Exception in inserting to proper node" ,"insert method");
        }

        return uri;
    }


    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        DBHelper helper = new DBHelper(getContext());
        database = helper.getWritableDatabase();
        boolean status = false;
        if( database != null )
            status = true;
        else
            status = false;

        createClientAndServer();

        return status;
    }

    /*
     * This methods checks if the ring is formed. If do then it will direct the query request
     * to the appropriate node based on hashed value of the key requested.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // TODO Auto-generated method stub
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(TABLE_NAME);
        Cursor cursor = null;
        String[] colNames = {"key" , "value"};


        try{

            if( selection == null ){
                cursor = builder.query(database,null,null,null,null,null,null);
            }
            else{
                if( isRingFormed ){
                    String hashKey = null;
                    String hashNodekey = genHash(getNodeId(myPort));
                    String hashPredecessorNodeKey = genHash(getNodeId(predecessor));

                    if( selection.contains("*") ){
                        dataList = new LinkedList<>();
                        cursor = getContext().getContentResolver().query(providerUri , null, null,null , null);
                        if( cursor != null ){
                            while(cursor.moveToNext()){
                                String key = cursor.getString(0);
                                String val = cursor.getString(1);
                                String[] row = {key,val};
                                dataList.add(row);
                            }
                        }

                        Message msg = new Message(myPort, null ,Constants.DHT_QUERY_REQUEST , null);
                        msg.setKey(selection);

                        unicast(msg,successor);

                        while (!isReplyReceived){

                        }


                        isReplyReceived = false;

                        MatrixCursor mCursor = new MatrixCursor(colNames);

                        ListIterator<String[]> it = dataList.listIterator();
                        while (it.hasNext()){
                            mCursor.addRow((String[])it.next());
                        }

                        results = null;
                        return mCursor;

                    }
                    else if( selection.contains("@")){
                        cursor = builder.query(database,null,null,null,null,null,null);
                    }
                    else{
                        hashKey = genHash(selection);
                        if( hashKey.compareTo(hashNodekey) <= 0 && hashKey.compareTo(hashPredecessorNodeKey) > 0  ){
                            cursor = builder.query(database,null,"key='"+selection+"'",null,null,null,null);
                        }
                        else if( myPort.equalsIgnoreCase(smallestNode) && ( hashKey.compareTo(hashPredecessorNodeKey) > 0 || hashKey.compareTo(hashNodekey) <= 0)){
                            cursor = builder.query(database,null,"key='"+selection+"'",null,null,null,null);
                        }
                        else{

                            Message msg = new Message(myPort, null ,Constants.QUERY_REQUEST , hashKey);
                            msg.setKey(selection);

                            unicast(msg, successor);
                            while (!isReplyReceived){

                            }

                            isReplyReceived = false;

                            //String[] pairValues = { resultKey , resultValue };
                            MatrixCursor mCursor = new MatrixCursor(colNames);
                            for(int counter = 0; counter < results.length; counter++){
                                mCursor.addRow(results[counter]);
                            }
                            results = null;
                            return mCursor;

                        }

                    }
                }
                else{
                    if( selection.contains("*") ){

                        cursor = builder.query(database,null,null,null,null,null,null);
                        // Some processing required for fetching from all AVD's
                    }
                    else if( selection.contains("@")){

                        cursor = builder.query(database,null,null,null,null,null,null);
                    }
                    else{

                        cursor = builder.query(database,null,"key='"+selection+"'",null,null,null,null);
                    }
                }
            }

        }
        catch (NoSuchAlgorithmException e){
            e.printStackTrace();
            Log.e("NoSuchAlgorithmException" , "insert method");
        }
        catch (Exception e){
            e.printStackTrace();
            Log.e("Exception in inserting to proper node" ,"insert method");
        }

        //Log.v("query", selection);
        return cursor;
    }


    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    /*
     * SHA-1 Hash function to hash the keys and Node ID's
     */
    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }



    private void createClientAndServer(){
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e("Activity", "Can't create a ServerSocket");
            return;
        }


        String message = null;
        if( !myPort.equals("11108") ){
            message = Constants.JOIN_REQUEST;
        }
        else{
            try{
                ringNodes.put(genHash(getNodeId(myPort)), myPort);
            }
            catch (NoSuchAlgorithmException e){
                e.printStackTrace();
            }

        }

        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, myPort);
    }


    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     *
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     *
     */
    private class ServerTask extends AsyncTask<ServerSocket, Message, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            while(true){
                try{
                    // increasing sequence number for each incoming message
                    //seq++;
                    ObjectInputStream stream = new ObjectInputStream(serverSocket.accept().getInputStream());
                    Message message = (Message)stream.readObject();

                    String typeOfMessage = message.getType();

                    switch (typeOfMessage){
                        case Constants.JOIN_REQUEST:
                            Log.v("Server" , Constants.JOIN_REQUEST+" send by "+message.getSenderNodeId());
                            try{
                                ringNodes.put(genHash(getNodeId(message.getSenderNodeId())) , message.getSenderNodeId());
                            }
                            catch (NoSuchAlgorithmException e){
                                e.printStackTrace();
                            }

                            isRingFormed = true;
                            handleJoinRequest();
                            break;

                        case Constants.JOIN_REPLY:
                            Log.v("Server" , Constants.JOIN_REPLY+" send to "+message.getSenderNodeId());
                            String[] info = (String[])message.getMessage();
                            predecessor = info[0];
                            successor = info[1];

                            smallestNode = info[3];
                            largestNode = info[4];
                            isRingFormed = true;
                            break;

                        case Constants.INSERT_REPLY:
                            Log.v("Server" , Constants.INSERT_REPLY);
                            isReplyReceived = true;
                            break;

                        case Constants.INSERT_REQUEST:
                            Log.v("Server" , Constants.INSERT_REQUEST);
                            try{
                                String hashKey = message.getHashedKey();
                                String hashNodekey = genHash(getNodeId(myPort));
                                String hashPredecessorNodeKey = genHash(getNodeId(predecessor));


                                if( hashKey.compareTo(hashNodekey) <= 0 && hashKey.compareTo(hashPredecessorNodeKey) > 0  ){

                                    ContentValues keyValueToInsert = new ContentValues();
                                    keyValueToInsert.put("key",message.getKey());
                                    keyValueToInsert.put("value",message.getValue());
                                    getContext().getContentResolver().insert(
                                            SimpleDhtProvider.providerUri,
                                            keyValueToInsert
                                    );

                                    message.setType(Constants.INSERT_REPLY);
                                    unicast(message, message.getSenderNodeId());

                                }
                                else if( myPort.equalsIgnoreCase(smallestNode) && ( hashKey.compareTo(hashPredecessorNodeKey) > 0 || hashKey.compareTo(hashNodekey) <= 0) ){

                                    ContentValues keyValueToInsert = new ContentValues();
                                    keyValueToInsert.put("key",message.getKey());
                                    keyValueToInsert.put("value",message.getValue());
                                    getContext().getContentResolver().insert(
                                            SimpleDhtProvider.providerUri,
                                            keyValueToInsert
                                    );
                                    message.setType(Constants.INSERT_REPLY);
                                    unicast(message, message.getSenderNodeId());

                                }
                                else{

                                    unicast(message, successor);
                                }
                            }
                            catch (NoSuchAlgorithmException e){
                                Log.e("NoSuchAlgorithmException" , "in msgToDeliver Switch case");
                            }
                            break;

                        case Constants.QUERY_REPLY:
                            Log.v("Server" , Constants.QUERY_REPLY);
                            results = (String[][])message.getMessage();
                            isReplyReceived = true;
                            break;

                        case Constants.QUERY_REQUEST:
                            Log.v("Server" , Constants.QUERY_REQUEST);
                            try{
                                String hashKey = message.getHashedKey();
                                String hashNodekey = genHash(getNodeId(myPort));
                                String hashPredecessorNodeKey = genHash(getNodeId(predecessor));
                                String selection = message.getKey();

                                    if( hashKey.compareTo(hashNodekey) <= 0 && hashKey.compareTo(hashPredecessorNodeKey) > 0  ){
                                        Cursor cur = getContext().getContentResolver().query(providerUri , null, selection , null , null);
                                        if( cur != null ){
                                            String[][] results = new String[cur.getCount()][2];
                                            if(cur.moveToFirst()){
                                                String key = cur.getString(0);
                                                String val = cur.getString(1);
                                                results[0][0] = key;
                                                results[0][1] = val;
                                                message.setMessage(results);
                                                message.setType(Constants.QUERY_REPLY);
                                                unicast(message, message.getSenderNodeId());
                                            }
                                        }
                                    }
                                    else if( myPort.equalsIgnoreCase(smallestNode) && ( hashKey.compareTo(hashPredecessorNodeKey) > 0 || hashKey.compareTo(hashNodekey) <= 0)){
                                        Cursor cur = getContext().getContentResolver().query(providerUri , null, selection , null , null);
                                        if( cur != null ){
                                            String[][] results = new String[cur.getCount()][2];
                                            if(cur.moveToFirst()){
                                                String key = cur.getString(0);
                                                String val = cur.getString(1);
                                                results[0][0] = key;
                                                results[0][1] = val;
                                                message.setMessage(results);
                                                message.setType(Constants.QUERY_REPLY);
                                                unicast(message, message.getSenderNodeId());
                                            }
                                        }
                                    }
                                    else{

                                        unicast(message, successor);
                                    }

                            }
                            catch (NoSuchAlgorithmException e){
                                Log.e("NoSuchAlgorithmException" , "in query Switch case");

                            }
                            break;

                        case Constants.DHT_QUERY_REPLY:
                            Log.v("Server" , Constants.DHT_QUERY_REPLY);

                            List list = (LinkedList)message.getMessage();
                            dataList.addAll(list);
                            isReplyReceived = true;
                            break;

                        case Constants.DHT_QUERY_REQUEST:
                            Log.v("Server" , Constants.DHT_QUERY_REQUEST);

                            Cursor cur = getContext().getContentResolver().query(providerUri , null, null , null , null);
                            if( cur != null ){

                                while(cur.moveToNext()){
                                    String key = cur.getString(0);
                                    String val = cur.getString(1);
                                    String[] row = {key,val};
                                    dataList.add(row);
                                }

                                if( message.getMessage() != null ){
                                    ((LinkedList)message.getMessage()).addAll(dataList);
                                }
                                else {
                                    message.setMessage(dataList);
                                }

                                if( successor.equalsIgnoreCase(message.getSenderNodeId()) ){
                                    message.setType(Constants.DHT_QUERY_REPLY);
                                }

                                unicast(message, successor);

                            }
                            break;

                        case Constants.DELETE_REPLY:
                            Log.v("Server" , Constants.DELETE_REPLY);
                            resultCount = (Integer)message.getMessage();
                            isReplyReceived = true;
                            break;

                        case Constants.DELETE_REQUEST:
                            Log.v("Server" , Constants.DELETE_REQUEST);
                            resultCount = 0;
                            try{
                                String hashKey = message.getHashedKey();
                                String hashNodekey = genHash(getNodeId(myPort));
                                String hashPredecessorNodeKey = genHash(getNodeId(predecessor));
                                String selection = message.getKey();

                                    if( hashKey.compareTo(hashNodekey) <= 0 && hashKey.compareTo(hashPredecessorNodeKey) > 0  ){
                                        resultCount = getContext().getContentResolver().delete(providerUri , selection ,null);
                                        message.setMessage(resultCount);
                                        message.setType(Constants.DELETE_REPLY);
                                        unicast(message, message.getSenderNodeId());
                                    }
                                    else if( myPort.equalsIgnoreCase(smallestNode) && ( hashKey.compareTo(hashPredecessorNodeKey) > 0 || hashKey.compareTo(hashNodekey) <= 0)){
                                        resultCount = getContext().getContentResolver().delete(providerUri , selection,null);
                                        message.setMessage(resultCount);
                                        message.setType(Constants.DELETE_REPLY);
                                        unicast(message, message.getSenderNodeId());
                                    }
                                    else{
                                        Log.v("else block" , "server");
                                        unicast(message, successor);
                                    }

                            }
                            catch (NoSuchAlgorithmException e){
                                Log.e("NoSuchAlgorithmException" , "in query Switch case");

                            }
                            break;

                        case Constants.DHT_DELETE_REPLY:
                            Log.v("Server" , Constants.DHT_DELETE_REPLY);

                            resultCount += (Integer)message.getMessage();

                            isReplyReceived = true;
                            break;

                        case Constants.DHT_DELETE_REQUEST:
                            Log.v("Server" , Constants.DHT_DELETE_REQUEST);
                            resultCount = getContext().getContentResolver().delete(providerUri , null ,null);

                            if( message.getMessage() != null ){
                                message.setMessage((Integer)message.getMessage()+resultCount);
                            }
                            else {
                                message.setMessage(resultCount);
                            }

                            if( successor.equalsIgnoreCase(message.getSenderNodeId()) ){
                                message.setType(Constants.DHT_DELETE_REPLY);
                            }

                            unicast(message, successor);

                            break;
                    }
                }
                catch (ClassNotFoundException e){
                    Log.e("Server" , "Message class not found");
                }
                catch(IOException e){
                    e.printStackTrace();
                    Log.e("Server", "Error in reading message");
                }
            }

            //return null;
        }

        protected void onProgressUpdate(Message...msgs) {
            /*
             * The following code displays what is received in doInBackground().
             */
            Message msg = msgs[0];

            ContentValues keyValueToInsert = new ContentValues();
            keyValueToInsert.put("key",msg.getKey());
            keyValueToInsert.put("value",msg.getValue());

            Uri newUri = getContext().getContentResolver().insert(
                    SimpleDhtProvider.providerUri,
                    keyValueToInsert
            );

            return;
        }
    }


    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            //try {

            Socket socket = null;
            ObjectOutputStream stream = null;

            String msgToSend = msgs[0];
            if( msgToSend != null){
                Message message = new Message(myPort , null, msgToSend , null);
                unicast(message , "11108");
            }


            return null;
        }
    }


    public void unicast(Message message , String port){
        try {

            Socket socket = null;
            ObjectOutputStream stream = null;

            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                    Integer.parseInt(port));

            stream = new ObjectOutputStream( new BufferedOutputStream(socket.getOutputStream()));
            stream.writeObject(message);
            stream.flush();
            stream.close();
            socket.close();

        } catch (UnknownHostException e) {
            Log.e("Client", "ClientTask UnknownHostException");
        }
        catch (SocketTimeoutException e) {
            Log.e("Client", "ClientTask socket Timeout");
        }
        catch (IOException e) {
            e.printStackTrace();
            Log.e("Client", "ClientTask socket IOException");
        }

    }


    public String getNodeId(String port){
        String nodeId = "";
        switch (port){
            case "11108":
                nodeId = "5554";
                break;
            case "11112":
                nodeId = "5556";
                break;
            case "11116":
                nodeId = "5558";
                break;
            case "11120":
                nodeId = "5560";
                break;
            case "11124":
                nodeId = "5562";
                break;

        }
        Log.v("returned value",nodeId);
        return nodeId;
    }


    public void handleJoinRequest(){
        try{
            Iterator it = ringNodes.keySet().iterator();

            REMOTE_PORTS = new String[ringNodes.size()];

            int i = 0;
            REMOTE_PORTS[0] = (String)ringNodes.get((String)it.next());

            while (it.hasNext()){
                i++;
                String port = (String)ringNodes.get((String)it.next());
                Log.v(port , port);
                REMOTE_PORTS[i] = port;
                Log.v(port , i+"");
            }

            smallestNode = REMOTE_PORTS[0];
            largestNode = REMOTE_PORTS[ringNodes.size()-1];

            for( int counter = 0;counter < REMOTE_PORTS.length; counter++){

                if( REMOTE_PORTS[counter].equalsIgnoreCase(myPort) ){
                    if( counter == (REMOTE_PORTS.length - 1) ){
                        successor = REMOTE_PORTS[0];
                        predecessor = REMOTE_PORTS[counter-1];
                    }
                    else if( counter == 0 ){
                        successor = REMOTE_PORTS[counter+1];
                        predecessor = REMOTE_PORTS[REMOTE_PORTS.length-1];
                    }
                    else {
                        successor = REMOTE_PORTS[counter+1];
                        predecessor = REMOTE_PORTS[counter-1];

                    }

                    Log.v(REMOTE_PORTS[counter],"Succ:"+successor+" , pred:"+predecessor);

                }
                else{
                    String succ = null;
                    String pred = null;
                    if( counter == (REMOTE_PORTS.length - 1) ){
                        succ = REMOTE_PORTS[0];
                        pred = REMOTE_PORTS[counter-1];
                    }
                    else if( counter == 0 ){
                        succ = REMOTE_PORTS[counter+1];
                        pred = REMOTE_PORTS[REMOTE_PORTS.length-1];
                    }
                    else {
                        succ = REMOTE_PORTS[counter+1];
                        pred = REMOTE_PORTS[counter-1];

                    }

                    String[] info = {
                            pred, succ, myPort , smallestNode , largestNode
                    };

                    Log.v(REMOTE_PORTS[counter],"Succ:"+succ+" , pred:"+pred);

                    Message message = new Message(myPort , info , Constants.JOIN_REPLY , null);

                    unicast(message , REMOTE_PORTS[counter]);
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
            Log.e("Exception" , "handleJoinRequest()");
        }

    }


    /*public class JoinRequestHandler extends TimerTask{
        @Override
        public void run() {
            Log.v("Timer Task" , "run()");

            Collections.sort(ringNodes);

            ListIterator<String> it = ringNodes.listIterator();
            REMOTE_PORTS = new String[ringNodes.size()];

            int i = 0;
            REMOTE_PORTS[0] = String.valueOf(Integer.parseInt(it.next()) * 2);

            while (it.hasNext()){
                i++;
                String node = it.next();
                Log.v(node , "");
                REMOTE_PORTS[i] = String.valueOf(Integer.parseInt(node) * 2);
            }


            for( int counter = 0;counter < REMOTE_PORTS.length; counter++){
                if( REMOTE_PORTS[counter].equalsIgnoreCase(myPort) ){
                    if( counter == (REMOTE_PORTS.length - 1) ){
                        successor = REMOTE_PORTS[0];
                        predecessor = REMOTE_PORTS[counter-1];
                    }
                    else if( counter == 0 ){
                        successor = REMOTE_PORTS[counter+1];
                        predecessor = REMOTE_PORTS[REMOTE_PORTS.length-1];
                    }
                    else {
                        successor = REMOTE_PORTS[counter+1];
                        predecessor = REMOTE_PORTS[counter-1];

                    }
                    leader = myPort;
                }
                else{
                    String succ = null;
                    String pred = null;
                    if( counter == (REMOTE_PORTS.length - 1) ){
                        succ = REMOTE_PORTS[0];
                        pred = REMOTE_PORTS[counter-1];
                    }
                    else if( counter == 0 ){
                        succ = REMOTE_PORTS[counter+1];
                        pred = REMOTE_PORTS[REMOTE_PORTS.length-1];
                    }
                    else {
                        succ = REMOTE_PORTS[counter+1];
                        pred = REMOTE_PORTS[counter-1];

                    }

                    String[] info = {
                            succ, pred, myPort
                    };

                    Message message = new Message(myPort , info , "joinreply" , null);
                    unicast(message , REMOTE_PORTS[counter]);
                }
            }
        }
    }*/

}
