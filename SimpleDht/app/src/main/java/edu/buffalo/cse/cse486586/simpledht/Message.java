package edu.buffalo.cse.cse486586.simpledht;


import java.io.Serializable;
import java.util.List;

/**
 * Created by rahul on 3/26/15.
 */
public class Message implements Serializable{
    String senderNodeId;
    Object message;
    String type;
    String hashedKey;
    String key;
    String value;

    public Message( String sender, Object message, String type , String key){
        this.senderNodeId = sender;
        this.message = message;
        this.type = type;
        this.hashedKey = key;
    }


    public String getSenderNodeId() {
        return senderNodeId;
    }

    public void setSenderNodeId(String senderNodeId) {
        this.senderNodeId = senderNodeId;
    }

    public Object getMessage() {
        return message;
    }

    public void setMessage(Object message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHashedKey() {
        return hashedKey;
    }

    public void setHashedKey(String hashedKey) {
        this.hashedKey = hashedKey;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
