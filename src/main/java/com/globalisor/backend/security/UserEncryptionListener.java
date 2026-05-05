package com.globalisor.backend.security;

import com.globalisor.backend.model.User;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterLoadEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;
import org.springframework.stereotype.Component;

@Component
public class UserEncryptionListener extends AbstractMongoEventListener<User> {

    @Autowired
    private EncryptionUtils encryptionUtils;

    @Override
    public void onBeforeSave(BeforeSaveEvent<User> event) {
        Document doc = event.getDocument();
        if (doc != null) {
            if (doc.containsKey("firstName")) {
                doc.put("firstName", encryptionUtils.encryptStrong(doc.getString("firstName")));
            }
            if (doc.containsKey("lastName")) {
                doc.put("lastName", encryptionUtils.encryptStrong(doc.getString("lastName")));
            }
            if (doc.containsKey("email")) {
                doc.put("email", encryptionUtils.encryptQueryable(doc.getString("email")));
            }
        }
    }

    @Override
    public void onAfterLoad(AfterLoadEvent<User> event) {
        Document doc = event.getDocument();
        if (doc != null) {
            if (doc.containsKey("firstName")) {
                try {
                    doc.put("firstName", encryptionUtils.decryptStrong(doc.getString("firstName")));
                } catch (Exception e) {
                    // Ignore decryption error for already existing plain text
                }
            }
            if (doc.containsKey("lastName")) {
                try {
                    doc.put("lastName", encryptionUtils.decryptStrong(doc.getString("lastName")));
                } catch (Exception e) {
                }
            }
            if (doc.containsKey("email")) {
                try {
                    doc.put("email", encryptionUtils.decryptQueryable(doc.getString("email")));
                } catch (Exception e) {
                }
            }
        }
    }
}
