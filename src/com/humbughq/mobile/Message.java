package com.humbughq.mobile;

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;
import android.util.Log;

import com.j256.ormlite.dao.Dao;

public class Message {

    private Person sender;
    private MessageType type;
    private String content;
    private String subject;
    private Date timestamp;
    private Person[] recipients;
    private int id;
    private Stream stream;

    /**
     * Construct an empty Message object.
     */
    public Message() {

    }

    /**
     * Convenience function to return either the Recipient specified or the
     * sender of the message as appropriate.
     * 
     * @param you
     * 
     * @param other
     *            a Recipient object you want to analyse
     * @return Either the specified Recipient's full name, or the sender's name
     *         if you are the Recipient.
     */
    private boolean getNotYouRecipient(Person you, JSONObject other) {
        try {
            if (you != null && !other.getString("email").equals(you.getEmail())) {
                return true;
            } else {
                return false;
            }
        } catch (JSONException e) {
            Log.e("message", "Couldn't parse JSON sender list!");
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Populate a Message object based off a parsed JSON hash.
     * 
     * @param context
     *            The HumbugActivity that created the message.
     * 
     * @param message
     *            the JSON object as returned by the server.
     * @throws JSONException
     */
    public Message(HumbugActivity context, JSONObject message)
            throws JSONException {

        this.setSender(new Person(message.getString("sender_full_name"),
                message.getString("sender_email"), message
                        .getString("avatar_url")));

        if (message.getString("type").equals("stream")) {
            this.setType(MessageType.STREAM_MESSAGE);

            try {
                Dao<Stream, String> streams = context.databaseHelper
                        .getDao(Stream.class);
                setStream(streams.queryForId(message
                        .getString("display_recipient")));
            } catch (SQLException e) {
                Log.w("message",
                        "We received a stream message for a stream we don't have data for. Fake it until you make it.");
                e.printStackTrace();
                setStream(new Stream(message.getString("display_recipient")));
            }
        } else if (message.getString("type").equals("private")) {
            this.setType(MessageType.PRIVATE_MESSAGE);

            JSONArray jsonRecipients = message
                    .getJSONArray("display_recipient");
            int display_recipients = jsonRecipients.length() - 1;
            if (display_recipients == 0) {
                display_recipients = 1;
            }

            recipients = new Person[display_recipients];

            for (int i = 0, j = 0; i < jsonRecipients.length(); i++) {
                JSONObject obj = jsonRecipients.getJSONObject(i);

                if (getNotYouRecipient(context.you, obj) ||
                // If you sent a message to yourself, we still show your as the
                // other party.
                        jsonRecipients.length() == 1) {
                    recipients[j] = new Person(obj.getString("full_name"),
                            obj.getString("email"));
                    j++;
                }
            }
        }

        this.setContent(message.getString("content"));
        if (this.getType() == MessageType.STREAM_MESSAGE) {
            this.setSubject(message.getString("subject"));
        } else {
            this.setSubject(null);
        }

        this.setTimestamp(new Date(message.getLong("timestamp") * 1000));
        this.setID(message.getInt("id"));
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType streamMessage) {
        this.type = streamMessage;
    }

    public void setRecipient(Person[] recipients) {
        this.recipients = recipients;
    }

    /**
     * Convenience function to set the recipients without requiring the caller
     * to construct a full Person[] array.
     * 
     * Do not call this method if you want to be able to get the recipient's
     * names for this message later; construct a Person[] array and use
     * setRecipient(Person[] recipients) instead.
     * 
     * @param emails
     *            The emails of the recipients.
     */
    public void setRecipient(String[] emails) {
        this.recipients = new Person[emails.length];
        for (int i = 0; i < emails.length; i++) {
            this.recipients[i] = new Person(null, emails[i]);
        }
    }

    /**
     * Convenience function to set the recipient in case of a single recipient.
     * 
     * @param recipient
     *            The sole recipient of the message.
     */
    public void setRecipient(Person recipient) {
        Person[] recipients = new Person[1];
        recipients[0] = recipient;
        this.recipients = recipients;
    }

    /**
     * Constructs a pretty-printable-to-the-user string consisting of the names
     * of all of the participants in the message, minus you.
     * 
     * For MessageType.STREAM_MESSAGE, return the stream name instead.
     * 
     * @return A String of the names of each Person in recipients[],
     *         comma-separated, or the stream name.
     */
    public String getDisplayRecipient() {
        if (this.getType() == MessageType.STREAM_MESSAGE) {
            return this.getStream().getName();
        } else {
            String[] names = new String[this.recipients.length];

            for (int i = 0; i < this.recipients.length; i++) {
                names[i] = recipients[i].getName();
            }
            return TextUtils.join(", ", names);
        }
    }

    /**
     * Creates a comma-separated String of the email addressed of all the
     * recipients of the message, as would be suitable to place in the compose
     * box.
     * 
     * @return the aforementioned String.
     */
    public String getReplyTo() {
        if (this.getType() == MessageType.STREAM_MESSAGE) {
            return this.getSender().getEmail();
        }
        Person[] people = getPersonalReplyTo();
        String[] emails = new String[people.length];
        for (int i = 0; i < people.length; i++) {
            emails[i] = people[i].getEmail();
        }
        return TextUtils.join(", ", emails);
    }

    /**
     * Returns a Person array of the email addresses of the parties of the
     * message, the user excluded.
     * 
     * @return said Person[].
     */
    public Person[] getPersonalReplyTo() {
        if (this.recipients.length == 0) {
            throw new WrongMessageType();
        }
        return this.recipients.clone();
    }

    public String getFormattedTimestamp() {
        DateFormat format = new SimpleDateFormat("MMM dd HH:mm");
        format.setTimeZone(TimeZone.getDefault());
        return format.format(this.getTimestamp());
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Person getSender() {
        return sender;
    }

    public void setSender(Person sender) {
        this.sender = sender;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date curDateTime) {
        this.timestamp = curDateTime;
    }

    public int getID() {
        return id;
    }

    public void setID(int id) {
        this.id = id;
    }

    public Stream getStream() {
        return stream;
    }

    public void setStream(Stream stream) {
        this.stream = stream;
    }

}
